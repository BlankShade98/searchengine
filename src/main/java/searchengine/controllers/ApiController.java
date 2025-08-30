package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final IndexingService indexingService;
    private final StatisticsService statisticsService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        if (indexingService.isIndexing()) {
            Map<String, Object> response = new HashMap<>();
            response.put("result", false);
            response.put("error", "Индексация уже запущена");
            return ResponseEntity.ok(response);
        }

        try {
            indexingService.startIndexing();
            Map<String, Object> response = new HashMap<>();
            response.put("result", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("result", false);
            response.put("error", "Произошла ошибка при запуске индексации: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        try {
            boolean indexingStopped = indexingService.stopIndexing();
            if (indexingStopped) {
                Map<String, Object> response = new HashMap<>();
                response.put("result", true);
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("result", false);
                response.put("error", "Индексация не запущена");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("result", false);
            response.put("error", "Произошла ошибка при остановке индексации: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam(name = "query", required = false) String query,
                                                 @RequestParam(name = "site", required = false) String site,
                                                 @RequestParam(name = "offset", defaultValue = "0") int offset,
                                                 @RequestParam(name = "limit", defaultValue = "20") int limit) {
        if (query == null || query.isEmpty()) {
            SearchResponse response = new SearchResponse();
            response.setResult(false);
            response.setError("Задан пустой поисковый запрос");
            return ResponseEntity.ok(response);
        }

        if (indexingService.isIndexing()) {
            SearchResponse response = new SearchResponse();
            response.setResult(false);
            response.setError("Индексация не завершена");
            return ResponseEntity.ok(response);
        }

        SearchResponse response = searchService.search(query, site, offset, limit);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Map<String, Object>> indexPage(@RequestParam(name = "url") String url) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (indexingService.indexPage(url)) {
                response.put("result", true);
                return ResponseEntity.ok(response);
            } else {
                response.put("result", false);
                response.put("error", "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            response.put("result", false);
            response.put("error", "Ошибка индексации страницы: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}