package searchengine.services;

import searchengine.dto.ApiResponse;

public interface IndexingService {
    ApiResponse startIndexing();
    ApiResponse stopIndexing();
    boolean isIndexing();
    boolean isIndexingComplete();
    ApiResponse indexPage(String url);
}