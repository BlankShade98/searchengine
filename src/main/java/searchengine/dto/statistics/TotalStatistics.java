package searchengine.dto.statistics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TotalStatistics {
    private int sites;
    private long pages;
    private long lemmas;
    @JsonProperty("isIndexing")
    private boolean indexing;
}