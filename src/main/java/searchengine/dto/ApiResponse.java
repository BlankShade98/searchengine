package searchengine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {
    private boolean result;
    private String error;

    public static ApiResponse ok() {
        ApiResponse response = new ApiResponse();
        response.setResult(true);
        return response;
    }
}