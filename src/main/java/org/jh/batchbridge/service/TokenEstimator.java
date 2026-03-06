package org.jh.batchbridge.service;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.jh.batchbridge.dto.BatchRowDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TokenEstimator {

    // 모델별 입력 토큰 단가 (달러 / 1M 토큰)
    private static final double PRICE_GEMINI_PRO_PREVIEW = 1.0;
    private static final double PRICE_CLAUDE_SONNET = 1.5;

    // 모델별 배치 한도 (행 수)
    public static final int BATCH_LIMIT_CLAUDE = 10_000;
    public static final int BATCH_LIMIT_GEMINI = 2_000;
    public static final int BATCH_LIMIT_GROK = 5_000;
    public static final int BATCH_LIMIT_DEFAULT = 1_000;

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

    /**
     * 모델명에 따른 배치 한도(행 수)를 반환한다.
     */
    public int resolveBatchLimit(String model) {
        if (model == null) return BATCH_LIMIT_DEFAULT;
        String lower = model.toLowerCase();
        if (lower.startsWith("claude")) return BATCH_LIMIT_CLAUDE;
        if (lower.startsWith("gemini")) return BATCH_LIMIT_GEMINI;
        if (lower.startsWith("grok")) return BATCH_LIMIT_GROK;
        return BATCH_LIMIT_DEFAULT;
    }

    /**
     * 행 목록을 모델별 배치 한도에 맞게 청크로 분할한다.
     * 한도를 초과하지 않으면 단일 청크로 반환한다.
     */
    public List<List<BatchRowDto>> splitIntoChunks(List<BatchRowDto> rows, String model) {
        int limit = resolveBatchLimit(model);
        List<List<BatchRowDto>> chunks = new ArrayList<>();
        for (int i = 0; i < rows.size(); i += limit) {
            chunks.add(rows.subList(i, Math.min(i + limit, rows.size())));
        }
        return chunks;
    }

    private double resolvePricePerMillion(String model) {
        if (model == null) return 0.0;
        String lower = model.toLowerCase();
        if (lower.startsWith("gemini")) return PRICE_GEMINI_PRO_PREVIEW;
        if (lower.startsWith("claude")) return PRICE_CLAUDE_SONNET;
        return 0.0;
    }
}
