package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SiteRepository;
import org.jsoup.Jsoup;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    private final LemmasFinder lemmasFinder;
    private final IndexingService indexingService;

    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        if (indexingService.isIndexing()) {
            SearchResponse response = new SearchResponse();
            response.setResult(false);
            response.setError("Индексация не завершена");
            return response;
        }

        if (query == null || query.isEmpty()) {
            SearchResponse response = new SearchResponse();
            response.setResult(false);
            response.setError("Задан пустой поисковый запрос");
            return response;
        }

        List<Site> sites;
        if (siteUrl != null && !siteUrl.isEmpty()) {
            Optional<Site> siteOptional = siteRepository.findByUrl(siteUrl);
            sites = siteOptional.stream().collect(Collectors.toList());
        } else {
            sites = siteRepository.findAll();
        }

        Map<String, Integer> queryLemmas = lemmasFinder.findLemmas(query);
        if (queryLemmas.isEmpty()) {
            SearchResponse response = new SearchResponse();
            response.setResult(true);
            response.setCount(0);
            response.setData(new ArrayList<>());
            return response;
        }

        List<String> sortedLemmaStrings = queryLemmas.keySet().stream()
                .sorted(Comparator.comparingInt(lemmaText -> lemmaRepository.countByLemmaAndSiteIn(lemmaText, sites)))
                .collect(Collectors.toList());

        List<Page> foundPages = findPagesByLemmas(sortedLemmaStrings, sites);

        List<SearchData> searchResults = new ArrayList<>();
        if (!foundPages.isEmpty()) {
            searchResults = calculateRelevanceAndBuildSnippets(foundPages, queryLemmas);
        }

        SearchResponse response = new SearchResponse();
        response.setResult(true);
        response.setCount(searchResults.size());
        response.setData(searchResults.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList()));

        return response;
    }

    private List<Page> findPagesByLemmas(List<String> sortedLemmas, List<Site> sites) {
        if (sortedLemmas.isEmpty()) {
            return new ArrayList<>();
        }

        String rarestLemmaText = sortedLemmas.get(0);
        List<Page> pagesWithRarestLemma = indexRepository.findPagesByLemmaAndSiteIn(rarestLemmaText, sites);

        List<Page> finalPages = new ArrayList<>(pagesWithRarestLemma);
        for (int i = 1; i < sortedLemmas.size(); i++) {
            String currentLemmaText = sortedLemmas.get(i);
            List<Page> pagesWithCurrentLemma = indexRepository.findPagesByLemmaAndSiteIn(currentLemmaText, sites);
            finalPages.retainAll(pagesWithCurrentLemma);
        }

        return finalPages;
    }

    private List<SearchData> calculateRelevanceAndBuildSnippets(List<Page> foundPages, Map<String, Integer> queryLemmas) {
        List<SearchData> searchResults = new ArrayList<>();
        Map<Page, Float> pageAbsoluteRelevanceMap = new HashMap<>();

        for (Page page : foundPages) {
            float absoluteRelevance = 0;
            for (String lemmaText : queryLemmas.keySet()) {
                Optional<Lemma> lemmaOptional = lemmaRepository.findByLemmaAndSite(lemmaText, page.getSite());
                if (lemmaOptional.isPresent()) {
                    Lemma lemma = lemmaOptional.get();
                    Float rank = indexRepository.findRankByPageAndLemma(page.getId(), lemma.getId());
                    if (rank != null) {
                        absoluteRelevance += rank;
                    }
                }
            }
            pageAbsoluteRelevanceMap.put(page, absoluteRelevance);
        }

        if (pageAbsoluteRelevanceMap.isEmpty()) {
            return new ArrayList<>();
        }

        float maxRelevance = Collections.max(pageAbsoluteRelevanceMap.values());

        for (Page page : foundPages) {
            float relativeRelevance = pageAbsoluteRelevanceMap.get(page) / maxRelevance;

            SearchData data = new SearchData();
            data.setSite(page.getSite().getUrl());
            data.setSiteName(page.getSite().getName());
            data.setUri(page.getPath());
            data.setTitle(Jsoup.parse(page.getContent()).title());
            data.setRelevance(relativeRelevance);
            data.setSnippet(buildSnippet(page.getContent(), queryLemmas.keySet()));

            searchResults.add(data);
        }

        searchResults.sort(Comparator.comparing(SearchData::getRelevance).reversed());

        return searchResults;
    }

    private static final int MAX_SNIPPET_LENGTH = 300;

    private String buildSnippet(String pageContent, Set<String> queryLemmas) {
        String pageText = Jsoup.parse(pageContent).text();
        List<String> textWords = Arrays.asList(pageText.toLowerCase().split("\\s+"));
        Set<Integer> lemmaPositions = new HashSet<>();

        for (int i = 0; i < textWords.size(); i++) {
            String word = textWords.get(i).replaceAll("[^а-яё]", "");
            if (word.isEmpty()) {
                continue;
            }
            Set<String> wordLemmas = lemmasFinder.findLemmas(word).keySet();
            if (wordLemmas.stream().anyMatch(queryLemmas::contains)) {
                lemmaPositions.add(i);
            }
        }

        StringBuilder snippet = new StringBuilder();
        List<Integer> sortedPositions = new ArrayList<>(lemmaPositions);
        Collections.sort(sortedPositions);

        int i = 0;
        while (i < sortedPositions.size() && snippet.length() < MAX_SNIPPET_LENGTH) {
            int position = sortedPositions.get(i);
            int start = Math.max(0, position - 5);
            int end = Math.min(textWords.size(), position + 5);

            String fragment = String.join(" ", textWords.subList(start, end))
                    .replaceAll("\\s+", " ").trim();

            snippet.append(fragment).append("... ");
            i++;
        }

        return highlightLemmas(snippet.toString(), queryLemmas);
    }

    private String highlightLemmas(String text, Set<String> queryLemmas) {
        String[] words = text.split("(?=[.,!?;:]|\\s)");
        return Arrays.stream(words).map(word -> {
            String cleanWord = word.toLowerCase().replaceAll("[^а-яё]", "");
            if (cleanWord.isEmpty()) {
                return word;
            }
            return lemmasFinder.findLemmas(cleanWord).keySet().stream().anyMatch(queryLemmas::contains) ? "<b>" + word + "</b>" : word;
        }).collect(Collectors.joining());
    }
}
