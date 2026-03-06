package org.jh.batchbridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergedResultDto {
    private String rowId;
    private String customId;
    private String prompt;
    private String model;
    private String resultText;
    private String status;
    private Integer inputTokens;
    private Integer outputTokens;
    private String errorMessage;
}
