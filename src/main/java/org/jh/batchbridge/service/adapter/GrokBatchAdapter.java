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
 * xAI Grok Batch API 어댑터.
 * <p>
 * xAI는 OpenAI 호환 Batch API를 제공한다.
 * 참고: https://docs.x.ai/docs/guides/batch
 */
@Component
public class GrokBatchAdapter implements BaseBatchAdapter {

    private static final String PROVIDER = "xai";
    private static final String BASE_URL = "https://api.x.ai";
    private static final String BATCH_ENDPOINT = "/v1/batch";
    private static final String FILES_ENDPOINT = "/v1/files";
    private static final int DEFAULT_MAX_TOKENS = 1024;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String defaultModel;

    @Autowired
    public GrokBatchAdapter(AiConfig aiConfig, WebClient.Builder webClientBuilder,
                            ObjectMapper objectMapper) {
        AiConfig.ProviderConfig config = aiConfig.getProviders().get(PROVIDER);
        String apiKey = config != null ? config.getApiKey() : "";
        this.defaultModel = config != null ? config.getModel() : "grok-2-latest";
        this.objectMapper = objectMapper;
        this.webClient = buildWebClient(webClientBuilder, BASE_URL, apiKey);
    }

    // 테스트에서 baseUrl을 오버라이드할 수 있도록 분리
    GrokBatchAdapter(AiConfig aiConfig, WebClient.Builder webClientBuilder,
                     ObjectMapper objectMapper, String baseUrl) {
        AiConfig.ProviderConfig config = aiConfig.getProviders().get(PROVIDER);
        String apiKey = config != null ? config.getApiKey() : "";
        this.defaultModel = config != null ? config.getModel() : "grok-2-latest";
        this.objectMapper = objectMapper;
        this.webClient = buildWebClient(webClientBuilder, baseUrl, apiKey);
    }

    private static WebClient buildWebClient(WebClient.Builder builder, String baseUrl, String apiKey) {
        return builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("content-type", "application/json")
                .build();
    }

    @Override
    public String submitBatch(List<BatchRowDto> rows, String model) {
        String resolvedModel = (model != null && !model.isBlank()) ? model : defaultModel;

        // 1단계: JSONL 파일 내용 생성
        StringBuilder jsonlBuilder = new StringBuilder();
        for (BatchRowDto row : rows) {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", resolvedModel);
            requestBody.put("max_tokens", row.getMaxTokens() != null ? row.getMaxTokens() : DEFAULT_MAX_TOKENS);
            if (row.getTemperature() != null) {
                requestBody.put("temperature", row.getTemperature());
            }

            ArrayNode messages = requestBody.putArray("messages");
            if (row.getSystemPrompt() != null && !row.getSystemPrompt().isBlank()) {
                ObjectNode sysMsg = messages.addObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", row.getSystemPrompt());
            }
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", row.getPrompt());

            ObjectNode line = objectMapper.createObjectNode();
            line.put("custom_id", row.getId());
            line.put("method", "POST");
            line.put("url", "/v1/chat/completions");
            line.set("body", requestBody);

            try {
                jsonlBuilder.append(objectMapper.writeValueAsString(line)).append("\n");
            } catch (Exception e) {
                throw new RuntimeException("JSONL 직렬화 실패: " + e.getMessage(), e);
            }
        }

        // 2단계: 파일 업로드 (multipart/form-data)
        String fileId = uploadBatchFile(jsonlBuilder.toString());

        // 3단계: 배치 작업 생성
        ObjectNode batchBody = objectMapper.createObjectNode();
        batchBody.put("input_file_id", fileId);
        batchBody.put("endpoint", "/v1/chat/completions");
        batchBody.put("completion_window", "24h");

        JsonNode response = webClient.post()
                .uri(BATCH_ENDPOINT)
                .bodyValue(batchBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        return response != null ? response.path("id").asText() : null;
    }

    private String uploadBatchFile(String jsonlContent) {
        // multipart/form-data 업로드
        org.springframework.http.client.MultipartBodyBuilder builder =
                new org.springframework.http.client.MultipartBodyBuilder();
        builder.part("purpose", "batch");
        builder.part("file", jsonlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .filename("batch_input.jsonl")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/jsonl"));

        JsonNode response = webClient.post()
                .uri(FILES_ENDPOINT)
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null) throw new RuntimeException("파일 업로드 응답이 없습니다.");
        return response.path("id").asText();
    }

    @Override
    public BatchStatus checkStatus(String externalBatchId) {
        JsonNode response = webClient.get()
                .uri(BATCH_ENDPOINT + "/" + externalBatchId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null) return BatchStatus.FAILED;

        String status = response.path("status").asText();
        return switch (status) {
            case "completed" -> BatchStatus.COMPLETED;
            case "failed", "expired" -> BatchStatus.FAILED;
            case "cancelling", "cancelled" -> BatchStatus.CANCELLED;
            default -> BatchStatus.IN_PROGRESS;
        };
    }

    @Override
    public List<BatchResultItem> collectResults(String externalBatchId) {
        // 배치 정보에서 output_file_id 조회
        JsonNode batchInfo = webClient.get()
                .uri(BATCH_ENDPOINT + "/" + externalBatchId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        List<BatchResultItem> items = new ArrayList<>();
        if (batchInfo == null) return items;

        String outputFileId = batchInfo.path("output_file_id").asText(null);
        if (outputFileId == null || outputFileId.isBlank()) return items;

        // 파일 내용 다운로드 (JSONL)
        String rawResults = webClient.get()
                .uri(FILES_ENDPOINT + "/" + outputFileId + "/content")
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
                JsonNode error = node.path("error");
                if (!error.isMissingNode() && !error.isNull()) {
                    String errorMsg = error.path("message").asText("Unknown error");
                    items.add(BatchResultItem.failure(rowId, errorMsg));
                    continue;
                }

                JsonNode choice = node.path("response").path("body").path("choices").path(0);
                String text = choice.path("message").path("content").asText();
                JsonNode usage = node.path("response").path("body").path("usage");
                int inputTokens = usage.path("prompt_tokens").asInt(0);
                int outputTokens = usage.path("completion_tokens").asInt(0);
                items.add(BatchResultItem.success(rowId, text, inputTokens, outputTokens));
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
