package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.ApiResponse;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SearchIndex;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmasFinder lemmasFinder;

    private ForkJoinPool forkJoinPool;
    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private static final Tika TIKA_INSTANCE = new Tika();


    @Override
    @Transactional
    public ApiResponse startIndexing() {
        if (isIndexing.get()) {
            ApiResponse response = new ApiResponse();
            response.setResult(false);
            response.setError("Индексация уже запущена");
            return response;
        }
        isIndexing.set(true);

        indexRepository.deleteAllInBatch();
        lemmaRepository.deleteAllInBatch();
        pageRepository.deleteAllInBatch();
        siteRepository.deleteAllInBatch();

        List<Site> configuredSites = sitesList.getSites().stream()
                .map(siteConfig -> {
                    Optional<Site> optionalSite = siteRepository.findByUrl(siteConfig.getUrl());
                    Site site = optionalSite.orElse(new Site());
                    site.setUrl(siteConfig.getUrl());
                    site.setName(siteConfig.getName());
                    site.setStatus(Status.INDEXING);
                    site.setStatusTime(LocalDateTime.now());
                    site.setLastError(null);
                    return siteRepository.save(site);
                })
                .collect(Collectors.toList());

        forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        for (Site site : configuredSites) {
            WebCrawler task = new WebCrawler(site.getUrl(), site, siteRepository, pageRepository, lemmaRepository, indexRepository, isIndexing, lemmasFinder);
            forkJoinPool.execute(task);
        }

        new Thread(() -> {
            try {
                forkJoinPool.shutdown();
                if (forkJoinPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)) {
                    updateSiteStatusOnCompletion(configuredSites);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                updateSiteStatusOnFailure(configuredSites, "Индексация прервана");
            } finally {
                isIndexing.set(false);
            }
        }).start();

        return ApiResponse.ok();
    }

    @Override
    public ApiResponse stopIndexing() {
        if (!isIndexing.get()) {
            ApiResponse response = new ApiResponse();
            response.setResult(false);
            response.setError("Индексация не запущена");
            return response;
        }

        isIndexing.set(false);
        if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
            forkJoinPool.shutdownNow();
        }

        List<Site> sitesToIndex = siteRepository.findByStatus(Status.INDEXING);
        updateSiteStatusOnFailure(sitesToIndex, "Индексация остановлена пользователем");
        return ApiResponse.ok();
    }

    private void updateSiteStatusOnCompletion(List<Site> sites) {
        for (Site site : sites) {
            Optional<Site> actualSiteOpt = siteRepository.findById(site.getId());
            if (actualSiteOpt.isPresent()) {
                Site actualSite = actualSiteOpt.get();
                if (actualSite.getStatus() == Status.INDEXING) {
                    actualSite.setStatus(Status.INDEXED);
                    actualSite.setStatusTime(LocalDateTime.now());
                    siteRepository.save(actualSite);
                }
            }
        }
    }

    private void updateSiteStatusOnFailure(List<Site> sites, String error) {
        for (Site site : sites) {
            if (site.getStatus() == Status.INDEXING) {
                site.setStatus(Status.FAILED);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError(error);
                siteRepository.save(site);
            }
        }
    }

    @Override
    public boolean isIndexing() {
        return isIndexing.get();
    }

    @Override
    public boolean isIndexingComplete() {
        long sitesNotIndexed = siteRepository.countByStatusNot(Status.INDEXED);
        return sitesNotIndexed == 0;
    }

    @Override
    @Transactional
    public ApiResponse indexPage(String url) {
        searchengine.config.Site siteConfig = sitesList.getSites().stream()
                .filter(s -> url.startsWith(s.getUrl()))
                .findFirst()
                .orElse(null);

        if (siteConfig == null) {
            ApiResponse response = new ApiResponse();
            response.setResult(false);
            response.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return response;
        }

        Site siteEntity = siteRepository.findByUrl(siteConfig.getUrl()).orElseGet(() -> {
            Site newSite = new Site();
            newSite.setUrl(siteConfig.getUrl());
            newSite.setName(siteConfig.getName());
            newSite.setStatus(Status.INDEXED);
            newSite.setStatusTime(LocalDateTime.now());
            return siteRepository.save(newSite);
        });

        String path = url.substring(siteEntity.getUrl().length());
        if (path.isEmpty()) {
            path = "/";
        }
        final String finalPath = path;

        pageRepository.findByPathAndSite(finalPath, siteEntity).ifPresent(page -> {
            List<SearchIndex> indices = indexRepository.findAllByPage(page);
            if (!indices.isEmpty()) {
                Set<Lemma> lemmasOnPage = indices.stream().map(SearchIndex::getLemma).collect(Collectors.toSet());
                lemmasOnPage.forEach(lemma -> lemma.setFrequency(lemma.getFrequency() - 1));

                List<Lemma> lemmasToUpdate = lemmasOnPage.stream().filter(l -> l.getFrequency() > 0).collect(Collectors.toList());
                List<Lemma> lemmasToDelete = lemmasOnPage.stream().filter(l -> l.getFrequency() <= 0).collect(Collectors.toList());

                indexRepository.deleteAllInBatch(indices);
                if (!lemmasToUpdate.isEmpty()) lemmaRepository.saveAll(lemmasToUpdate);
                if (!lemmasToDelete.isEmpty()) lemmaRepository.deleteAllInBatch(lemmasToDelete);
            }
            pageRepository.delete(page);
        });
        pageRepository.flush();

        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("HeliontSearchBot-SinglePage")
                    .timeout(30000)
                    .ignoreContentType(true)
                    .execute();

            int statusCode = response.statusCode();
            String textContent = TIKA_INSTANCE.parseToString(new ByteArrayInputStream(response.bodyAsBytes()));
            String pageContentToSave = response.contentType() != null && Objects.requireNonNull(response.contentType()).toLowerCase().contains("text/html")
                    ? response.parse().html()
                    : textContent;

            Page newPage = new Page();
            newPage.setSite(siteEntity);
            newPage.setPath(finalPath);
            newPage.setCode(statusCode);
            newPage.setContent(pageContentToSave);
            pageRepository.saveAndFlush(newPage);

            if (statusCode == 200 && !textContent.isBlank()) {
                Map<String, Integer> pageLemmasMap = lemmasFinder.findLemmas(textContent);
                if (!pageLemmasMap.isEmpty()) {
                    Collection<String> lemmaNames = pageLemmasMap.keySet();
                    List<Lemma> existingLemmas = lemmaRepository.findAllBySiteAndLemmaIn(siteEntity, lemmaNames);
                    Map<String, Lemma> dbLemmasMap = existingLemmas.stream().collect(Collectors.toMap(Lemma::getLemma, Function.identity()));
                    List<Lemma> lemmasToSave = new ArrayList<>();
                    for (String lemmaName : pageLemmasMap.keySet()) {
                        Lemma lemma = dbLemmasMap.computeIfAbsent(lemmaName, l -> new Lemma(l, siteEntity));
                        lemma.setFrequency(lemma.getFrequency() + 1);
                        lemmasToSave.add(lemma);
                    }
                    lemmaRepository.saveAll(lemmasToSave);
                    List<SearchIndex> indicesToSave = new ArrayList<>();
                    for (Map.Entry<String, Integer> entry : pageLemmasMap.entrySet()) {
                        indicesToSave.add(new SearchIndex(newPage, dbLemmasMap.get(entry.getKey()), entry.getValue().floatValue()));
                    }
                    indexRepository.saveAll(indicesToSave);
                }
            }
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ApiResponse.ok();
    }
}