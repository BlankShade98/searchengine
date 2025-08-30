package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexingService indexingService;

    @Override
    public StatisticsResponse getStatistics() {
        List<Site> sites = siteRepository.findAll();
        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        long totalPages = 0;
        long totalLemmas = 0;

        for (Site site : sites) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            int pages = pageRepository.countBySiteId(site.getId());
            long lemmas = lemmaRepository.countBySite_Id(site.getId());

            item.setUrl(site.getUrl());
            item.setName(site.getName());
            item.setStatus(site.getStatus().toString());
            item.setStatusTime(site.getStatusTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            item.setError(site.getLastError());
            item.setPages(pages);
            item.setLemmas((int) lemmas);

            detailed.add(item);
            totalPages += pages;
            totalLemmas += lemmas;
        }

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.size());
        total.setPages(totalPages);
        total.setLemmas(totalLemmas);
        total.setIndexing(indexingService.isIndexing());

        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);

        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(data);

        return response;
    }
}