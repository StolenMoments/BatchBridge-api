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
public class JobDetailResponse {
    private String jobId;
    private String filename;
    private String status;
    private List<ChunkDetail> chunks;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private Integer inputTokens;
    private Integer outputTokens;
    private Double estimatedCost;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkDetail {
        private String chunkId;
        private String model;
        private String batchId;
        private Integer totalRows;
        private Integer completedRows;
        private Integer failedRows;
        private String status;
    }
}
