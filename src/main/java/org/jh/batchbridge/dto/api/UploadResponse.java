package org.jh.batchbridge.dto.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jh.batchbridge.dto.BatchRowDto;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private String uploadId;
    private String filename;
    private Integer totalRows;
    private List<String> columns;
    private List<BatchRowDto> preview;
    private Map<String, Integer> estimatedTokens;
    private Map<String, Double> estimatedCost;
}
