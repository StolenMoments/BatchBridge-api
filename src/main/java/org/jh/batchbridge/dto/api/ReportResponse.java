package org.jh.batchbridge.dto.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private String jobId;
    private Summary summary;
    private List<ModelReport> byModel;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private Integer totalRows;
        private Integer successRows;
        private Integer failedRows;
        private Integer totalInputTokens;
        private Integer totalOutputTokens;
        private Double totalCost;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelReport {
        private String model;
        private Integer rows;
        private Integer inputTokens;
        private Integer outputTokens;
        private Double cost;
    }
}
