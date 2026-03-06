package org.jh.batchbridge.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BatchRowDto {
    private String id;
    private String prompt;
    private String model;
    private String systemPrompt;
    private Double temperature;
    private Integer maxTokens;
}
