package org.jh.batchbridge.dto.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobListResponse {
    private Long total;
    private Integer page;
    private Integer size;
    private List<JobInfo> jobs;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobInfo {
        private String jobId;
        private String filename;
        private List<String> models;
        private Integer totalRows;
        private Integer completedRows;
        private String status;
        private LocalDateTime submittedAt;
        private LocalDateTime completedAt;
        private Integer inputTokens;
        private Integer outputTokens;
        private Double estimatedCost;
    }
}
