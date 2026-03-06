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
 * Google Gemini Batch API 어댑터.
 * <p>
 * 참고: https://ai.google.dev/gemini-api/docs/batch
 */
@Component
public class GeminiBatchAdapter implements BaseBatchAdapter {

    private static final String PROVIDER = "google";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final int DEFAULT_MAX_TOKENS = 1024;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String defaultModel;

    @Autowired
    public GeminiBatchAdapter(AiConfig aiConfig, WebClient.Builder webClientBuilder,
                              ObjectMapper objectMapper) {
        AiConfig.ProviderConfig config = aiConfig.getProviders().get(PROVIDER);
        this.apiKey = config != null ? config.getApiKey() : "";
        this.defaultModel = config != null ? config.getModel() : "gemini-1.5-flash";
        this.objectMapper = objectMapper;
        this.webClient = buildWebClient(webClientBuilder, BASE_URL);
    }

    // 테스트에서 baseUrl을 오버라이드할 수 있도록 분리
    GeminiBatchAdapter(AiConfig aiConfig, WebClient.Builder webClientBuilder,
                       ObjectMapper objectMapper, String baseUrl) {
        AiConfig.ProviderConfig config = aiConfig.getProviders().get(PROVIDER);
        this.apiKey = config != null ? config.getApiKey() : "";
        this.defaultModel = config != null ? config.getModel() : "gemini-1.5-flash";
        this.objectMapper = objectMapper;
        this.webClient = buildWebClient(webClientBuilder, baseUrl);
    }

    private static WebClient buildWebClient(WebClient.Builder builder, String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("content-type", "application/json")
                .build();
    }

    /**
     * API 키 유효성을 사전 검증한다 (ping).
     * 401/403 응답 시 {@link ApiKeyValidator.InvalidApiKeyException}을 던진다.
     */
    public void validateApiKey() {
        ApiKeyValidator.validateNotEmpty(apiKey, PROVIDER);
        ApiKeyValidator.ping(webClient, "/v1beta/models?key=" + apiKey, PROVIDER);
    }

    @Override
    public String submitBatch(List<BatchRowDto> rows, String model) {
        validateApiKey();
        String resolvedModel = (model != null && !model.isBlank()) ? model : defaultModel;

        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode requests = body.putArray("requests");

        for (BatchRowDto row : rows) {
            ObjectNode req = requests.addObject();
            req.put("id", row.getId());

            ObjectNode request = req.putObject("request");
            ArrayNode contents = request.putArray("contents");
            ObjectNode userContent = contents.addObject();
            userContent.put("role", "user");
            ArrayNode parts = userContent.putArray("parts");
            ObjectNode textPart = parts.addObject();
            textPart.put("text", row.getPrompt());

            if (row.getSystemPrompt() != null && !row.getSystemPrompt().isBlank()) {
                ObjectNode systemInstruction = request.putObject("system_instruction");
                ArrayNode sysParts = systemInstruction.putArray("parts");
                ObjectNode sysPart = sysParts.addObject();
                sysPart.put("text", row.getSystemPrompt());
            }

            ObjectNode generationConfig = request.putObject("generation_config");
            generationConfig.put("max_output_tokens",
                    row.getMaxTokens() != null ? row.getMaxTokens() : DEFAULT_MAX_TOKENS);
            if (row.getTemperature() != null) {
                generationConfig.put("temperature", row.getTemperature());
            }
        }

        return RetryUtils.withRetry(() -> {
            JsonNode response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:batchGenerateContent")
                            .queryParam("key", apiKey)
                            .build(resolvedModel))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return response != null ? response.path("name").asText() : null;
        });
    }

    @Override
    public BatchStatus checkStatus(String externalBatchId) {
        JsonNode response = RetryUtils.withRetry(() -> webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/{batchId}")
                        .queryParam("key", apiKey)
                        .build(externalBatchId))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block());

        if (response == null) return BatchStatus.FAILED;

        String state = response.path("metadata").path("state").asText();
        return switch (state) {
            case "JOB_STATE_SUCCEEDED" -> BatchStatus.COMPLETED;
            case "JOB_STATE_FAILED", "JOB_STATE_CANCELLED" -> BatchStatus.FAILED;
            case "JOB_STATE_CANCELLING" -> BatchStatus.CANCELLED;
            default -> BatchStatus.IN_PROGRESS;
        };
    }

    @Override
    public List<BatchResultItem> collectResults(String externalBatchId) {
        JsonNode response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1beta/{batchId}/results")
                        .queryParam("key", apiKey)
                        .build(externalBatchId))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        List<BatchResultItem> items = new ArrayList<>();
        if (response == null) return items;

        JsonNode responses = response.path("responses");
        if (!responses.isArray()) return items;

        for (JsonNode node : responses) {
            String rowId = node.path("id").asText();
            JsonNode error = node.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                String errorMsg = error.path("message").asText("Unknown error");
                items.add(BatchResultItem.failure(rowId, errorMsg));
                continue;
            }

            JsonNode candidate = node.path("response").path("candidates").path(0);
            String text = candidate.path("content").path("parts").path(0).path("text").asText();
            JsonNode usageMetadata = node.path("response").path("usageMetadata");
            int inputTokens = usageMetadata.path("promptTokenCount").asInt(0);
            int outputTokens = usageMetadata.path("candidatesTokenCount").asInt(0);
            items.add(BatchResultItem.success(rowId, text, inputTokens, outputTokens));
        }
        return items;
    }

    @Override
    public String getProvider() {
        return PROVIDER;
    }
}
