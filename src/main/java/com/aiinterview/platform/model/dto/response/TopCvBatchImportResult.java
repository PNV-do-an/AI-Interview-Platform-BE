package com.aiinterview.platform.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TopCvBatchImportResult {

    private String sourceUrl;
    private int requested;
    private int imported;
    private int failed;
    private List<TopCvImportResult> items;
    private List<Failure> failures;

    @Data
    @Builder
    public static class Failure {
        private String url;
        private String message;
    }
}
