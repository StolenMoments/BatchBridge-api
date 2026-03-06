package org.jh.batchbridge.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BatchRequest {

    private final String id;
    private final String prompt;
    private final String systemPrompt;
    private final String model;
    private final Provider provider;
    private final Double temperature;
    private final Integer maxTokens;

    public enum Provider {
        ANTHROPIC, GOOGLE, XAI;

        public static Provider fromModel(String model) {
            if (model == null) return null;
            String lower = model.toLowerCase();
            if (lower.startsWith("claude")) return ANTHROPIC;
            if (lower.startsWith("gemini")) return GOOGLE;
            if (lower.startsWith("grok")) return XAI;
            return null;
        }
    }
}
