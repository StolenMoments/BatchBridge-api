package org.jh.batchbridge.service;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.jh.batchbridge.dto.BatchRowDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TokenEstimator {

    // 모델별 입력 토큰 단가 (달러 / 1M 토큰)
    private static final double PRICE_GEMINI_PRO_PREVIEW = 1.0;
    private static final double PRICE_CLAUDE_SONNET = 1.5;

    private final Encoding encoding;

    public TokenEstimator() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    public int estimateTokens(BatchRowDto row) {
        StringBuilder sb = new StringBuilder();
        if (row.getSystemPrompt() != null) {
            sb.append(row.getSystemPrompt()).append("\n");
        }
        if (row.getPrompt() != null) {
            sb.append(row.getPrompt());
        }
        return encoding.countTokens(sb.toString());
    }

    public int estimateTotalTokens(List<BatchRowDto> rows) {
        return rows.stream().mapToInt(this::estimateTokens).sum();
    }

    public double estimateCost(int totalTokens, String model) {
        double pricePerMillion = resolvePricePerMillion(model);
        return totalTokens / 1_000_000.0 * pricePerMillion;
    }

    private double resolvePricePerMillion(String model) {
        if (model == null) return 0.0;
        String lower = model.toLowerCase();
        if (lower.startsWith("gemini")) return PRICE_GEMINI_PRO_PREVIEW;
        if (lower.startsWith("claude")) return PRICE_CLAUDE_SONNET;
        return 0.0;
    }
}
