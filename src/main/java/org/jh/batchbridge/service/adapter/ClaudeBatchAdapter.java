package org.jh.batchbridge.service.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jh.batchbridge.config.AiConfig;
import org.jh.batchbridge.dto.BatchRowDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Anthropic Claude Batch API 어댑터.
 * <p>
 * 참고: https://docs.anthropic.com/en/api/creating-message-batches
 */
@Component
public class ClaudeBatchAdapter implements BaseBatchAdapter {

    private static final String PROVIDER = "anthropic";
    private static final String BASE_URL = "https://api.anthropic.com";
    private static final String BATCH_ENDPOINT = "/v1/messages/batches";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String BETA_HEADER = "message-batches-2024-09-24";
    private static final int DEFAULT_MAX_TOKENS = 1024;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String defaultModel;

    @Autowired
    public ClaudeBatchAdapter(AiConfig aiConfig, WebClient.Builder webClientBuilder,
                              ObjectMapper objectMapper) {
        AiConfig.ProviderConfig config = aiConfig.getProviders().get(PROVIDER);
        this.apiKey = config != null ? config.getApiKey() : "";
        this.defaultModel = config != null ? config.getModel() : "claude-3-5-sonnet-latest";
        this.objectMapper = objectMapper;
        this.webClient = buildWebClient(webClientBuilder, BASE_URL, this.apiKey);
    }

    // 테스트에서 baseUrl을 오버라이드할 수 있도록 분리
    ClaudeBatchAdapter(AiConfig aiConfig, WebClient.Builder webClientBuilder,
                       ObjectMapper objectMapper, String baseUrl) {
        AiConfig.ProviderConfig config = aiConfig.getProviders().get(PROVIDER);
        this.apiKey = config != null ? config.getApiKey() : "";
        this.defaultModel = config != null ? config.getModel() : "claude-3-5-sonnet-latest";
        this.objectMapper = objectMapper;
        this.webClient = buildWebClient(webClientBuilder, baseUrl, this.apiKey);
    }

    private static WebClient buildWebClient(WebClient.Builder builder, String baseUrl, String apiKey) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .defaultHeader("anthropic-beta", BETA_HEADER)
                .defaultHeader("content-type", "application/json")
                .build();
    }

    @Override
    public String submitBatch(List<BatchRowDto> rows, String model) {
        String resolvedModel = (model != null && !model.isBlank()) ? model : defaultModel;
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode requests = body.putArray("requests");

        for (BatchRowDto row : rows) {
            ObjectNode req = requests.addObject();
            req.put("custom_id", row.getId());

            ObjectNode params = req.putObject("params");
            params.put("model", resolvedModel);
            params.put("max_tokens", row.getMaxTokens() != null ? row.getMaxTokens() : DEFAULT_MAX_TOKENS);

            if (row.getSystemPrompt() != null && !row.getSystemPrompt().isBlank()) {
                params.put("system", row.getSystemPrompt());
            }
            if (row.getTemperature() != null) {
                params.put("temperature", row.getTemperature());
            }

            ArrayNode messages = params.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            ArrayNode content = userMsg.putArray("content");
            ObjectNode textBlock = content.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", row.getPrompt());
        }

        JsonNode response = webClient.post()
                .uri(BATCH_ENDPOINT)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return response != null ? response.path("id").asText() : null;
    }

    @Override
    public BatchStatus checkStatus(String externalBatchId) {
        JsonNode response = webClient.get()
                .uri(BATCH_ENDPOINT + "/" + externalBatchId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null) return BatchStatus.FAILED;

        String processingStatus = response.path("processing_status").asText();
        return switch (processingStatus) {
            case "ended" -> BatchStatus.COMPLETED;
            case "canceling", "canceled" -> BatchStatus.CANCELLED;
            default -> BatchStatus.IN_PROGRESS;
        };
    }

    @Override
    public List<BatchResultItem> collectResults(String externalBatchId) {
        // 결과 스트림 URL 조회
        JsonNode batchInfo = webClient.get()
                .uri(BATCH_ENDPOINT + "/" + externalBatchId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        List<BatchResultItem> items = new ArrayList<>();
        if (batchInfo == null) return items;

        String resultsUrl = batchInfo.path("results_url").asText(null);
        if (resultsUrl == null || resultsUrl.isBlank()) return items;

        // 결과는 JSONL 형식으로 반환됨
        String rawResults = webClient.get()
                .uri(resultsUrl)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (rawResults == null || rawResults.isBlank()) return items;

        for (String line : rawResults.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                JsonNode node = objectMapper.readTree(line);
                String rowId = node.path("custom_id").asText();
                String type = node.path("result").path("type").asText();

                if ("succeeded".equals(type)) {
                    JsonNode message = node.path("result").path("message");
                    String text = message.path("content").path(0).path("text").asText();
                    int inputTokens = message.path("usage").path("input_tokens").asInt(0);
                    int outputTokens = message.path("usage").path("output_tokens").asInt(0);
                    items.add(BatchResultItem.success(rowId, text, inputTokens, outputTokens));
                } else {
                    String errorMsg = node.path("result").path("error").path("message").asText("Unknown error");
                    items.add(BatchResultItem.failure(rowId, errorMsg));
                }
            } catch (Exception e) {
                items.add(BatchResultItem.failure("unknown", e.getMessage()));
            }
        }
        return items;
    }

    @Override
    public String getProvider() {
        return PROVIDER;
    }
}
