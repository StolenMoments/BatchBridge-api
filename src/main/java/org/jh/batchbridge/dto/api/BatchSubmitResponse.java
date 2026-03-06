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
public class BatchSubmitResponse {
    private String jobId;
    private String status;
    private List<ChunkInfo> chunks;
    private LocalDateTime submittedAt;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkInfo {
        private String chunkId;
        private String model;
        private Integer rows;
        private String batchId;
    }
}
