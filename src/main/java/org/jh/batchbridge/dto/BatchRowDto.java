package org.jh.batchbridge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRowDto {
    private String id;
    private String prompt;
    private String model;
    private String systemPrompt;
    private Double temperature;
    @JsonProperty("max_tokens")
    private Integer maxTokens;
}
