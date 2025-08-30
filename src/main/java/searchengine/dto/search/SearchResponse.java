package searchengine.dto.search;

import lombok.Data;
import java.util.List;

@Data
public class SearchResponse {
    private boolean result;
    private int count;
    private String error;
    private List<SearchData> data;
}