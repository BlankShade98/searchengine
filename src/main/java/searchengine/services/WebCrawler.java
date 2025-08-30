package searchengine.services;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.SearchIndex;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WebCrawler extends RecursiveTask<Void> {
    private final String url;
    private final Site site;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final AtomicBoolean isIndexing;
    private final LemmasFinder lemmasFinder;
    private final Set<String> visitedUrls;

    private static final Tika TIKA_INSTANCE = new Tika();

    public WebCrawler(String url, Site site, SiteRepository siteRepository, PageRepository pageRepository, LemmaRepository lemmaRepository, IndexRepository indexRepository, AtomicBoolean isIndexing, LemmasFinder lemmasFinder) {
        this.url = url;
        this.site = site;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.isIndexing = isIndexing;
        this.lemmasFinder = lemmasFinder;
        this.visitedUrls = ConcurrentHashMap.newKeySet();
    }

    private WebCrawler(String url, Site site, SiteRepository siteRepository, PageRepository pageRepository, LemmaRepository lemmaRepository, IndexRepository indexRepository, AtomicBoolean isIndexing, LemmasFinder lemmasFinder, Set<String> visitedUrls) {
        this.url = url;
        this.site = site;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.isIndexing = isIndexing;
        this.lemmasFinder = lemmasFinder;
        this.visitedUrls = visitedUrls;
    }

    @Override
    protected Void compute() {
        if (!isIndexing.get() || !visitedUrls.add(url)) {
            return null;
        }

        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("HeliontSearchBot")
                    .referrer("http://www.google.com")
                    .timeout(30000)
                    .ignoreContentType(true)
                    .execute();

            int statusCode = response.statusCode();

            if (statusCode != 200) {
                savePageWithStatus(statusCode, response.statusMessage());
                return null;
            }

            String textContent = TIKA_INSTANCE.parseToString(new ByteArrayInputStream(response.bodyAsBytes()));

            Document doc = null;
            String pageContentToSave;

            if (response.contentType() != null && response.contentType().toLowerCase().contains("text/html")) {
                doc = response.parse();
                pageContentToSave = doc.html();
            } else {
                pageContentToSave = textContent;
            }

            Page page = savePageWithStatus(statusCode, pageContentToSave);

            if (!textContent.isBlank()) {
                Map<String, Integer> pageLemmasMap = lemmasFinder.findLemmas(textContent);
                if (pageLemmasMap.isEmpty()) {
                } else {
                    Collection<String> lemmaNames = pageLemmasMap.keySet();
                    List<Lemma> existingLemmas = lemmaRepository.findAllBySiteAndLemmaIn(site, lemmaNames);

                    Map<String, Lemma> dbLemmasMap = existingLemmas.stream()
                            .collect(Collectors.toMap(Lemma::getLemma, Function.identity()));

                    List<Lemma> lemmasToSave = new ArrayList<>();
                    for (String lemmaName : lemmaNames) {
                        Lemma lemma = dbLemmasMap.computeIfAbsent(lemmaName, l -> new Lemma(l, site));
                        lemma.setFrequency(lemma.getFrequency() + 1);
                        lemmasToSave.add(lemma);
                    }
                    lemmaRepository.saveAll(lemmasToSave);

                    List<SearchIndex> indicesToSave = new ArrayList<>();
                    for (Lemma lemma : lemmasToSave) {
                        Integer rank = pageLemmasMap.get(lemma.getLemma());
                        indicesToSave.add(new SearchIndex(page, lemma, rank.floatValue()));
                    }
                    indexRepository.saveAll(indicesToSave);
                }
            }

            if (doc != null) {
                List<WebCrawler> subTasks = new ArrayList<>();
                Elements links = doc.select("a[href]");

                for (Element link : links) {
                    String absUrl = link.attr("abs:href");
                    if (isValidUrl(absUrl)) {
                        WebCrawler task = new WebCrawler(absUrl, site, siteRepository, pageRepository, lemmaRepository, indexRepository, isIndexing, this.lemmasFinder, this.visitedUrls);
                        subTasks.add(task);
                        task.fork();
                    }
                }
                for (WebCrawler task : subTasks) {
                    task.join();
                }
            }
        } catch (IOException | TikaException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return null;
            }
            savePageWithStatus(500, "Ошибка обработки страницы: " + e.getClass().getName() + " - " + e.getMessage());
        }
        return null;
    }

    private Page savePageWithStatus(int statusCode, String content) {
        Page page = new Page();
        page.setSite(site);
        page.setPath(url.replace(site.getUrl(), ""));
        page.setCode(statusCode);
        page.setContent(content);
        pageRepository.saveAndFlush(page);
        return page;
    }

    private boolean isValidUrl(String url) {
        return url.startsWith(site.getUrl()) &&
               !url.contains("#") &&
               !url.matches("(?i).*\\.(pdf|jpe?g|png|gif|zip|rar|exe|mp3|mp4|xml|doc|docx|xls|xlsx)$");
    }
}