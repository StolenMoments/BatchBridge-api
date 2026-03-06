package org.jh.batchbridge.service.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jh.batchbridge.config.AiConfig;
import org.jh.batchbridge.dto.BatchRowDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrokBatchAdapterTest {

    private MockWebServer mockWebServer;
    private GrokBatchAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        AiConfig aiConfig = new AiConfig();
        AiConfig.ProviderConfig providerConfig = new AiConfig.ProviderConfig();
        providerConfig.setApiKey("test-xai-key");
        providerConfig.setModel("grok-2-latest");
        aiConfig.setProviders(Map.of("xai", providerConfig));

        adapter = new GrokBatchAdapter(aiConfig, WebClient.builder(),
                new ObjectMapper(), mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("getProvider()는 'xai'를 반환한다")
    void getProvider() {
        assertThat(adapter.getProvider()).isEqualTo("xai");
    }

    @Test
    @DisplayName("submitBatch()는 파일 업로드 후 배치를 생성하고 배치 ID를 반환한다")
    void submitBatch() throws InterruptedException {
        // 1단계: 파일 업로드 응답
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"file_abc\",\"object\":\"file\"}")
                .addHeader("Content-Type", "application/json"));
        // 2단계: 배치 생성 응답
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_grok123\",\"status\":\"validating\"}")
                .addHeader("Content-Type", "application/json"));

        List<BatchRowDto> rows = List.of(
                BatchRowDto.builder().id("row-1").prompt("Hello Grok").model("grok-2-latest").build()
        );

        String batchId = adapter.submitBatch(rows, "grok-2-latest");

        assertThat(batchId).isEqualTo("batch_grok123");

        // 파일 업로드 요청 확인
        RecordedRequest fileRequest = mockWebServer.takeRequest();
        assertThat(fileRequest.getMethod()).isEqualTo("POST");
        assertThat(fileRequest.getPath()).isEqualTo("/v1/files");

        // 배치 생성 요청 확인
        RecordedRequest batchRequest = mockWebServer.takeRequest();
        assertThat(batchRequest.getMethod()).isEqualTo("POST");
        assertThat(batchRequest.getPath()).isEqualTo("/v1/batch");

        String batchBody = batchRequest.getBody().readUtf8();
        assertThat(batchBody).contains("file_abc");
        assertThat(batchBody).contains("/v1/chat/completions");
    }

    @Test
    @DisplayName("submitBatch()는 systemPrompt가 있으면 system 메시지를 포함한다")
    void submitBatchWithSystemPrompt() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"file_sys\",\"object\":\"file\"}")
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_sys\"}")
                .addHeader("Content-Type", "application/json"));

        List<BatchRowDto> rows = List.of(
                BatchRowDto.builder().id("row-1").prompt("Hello").systemPrompt("You are Grok.").build()
        );

        adapter.submitBatch(rows, "grok-2-latest");

        RecordedRequest fileRequest = mockWebServer.takeRequest();
        String fileBody = fileRequest.getBody().readUtf8();
        assertThat(fileBody).contains("You are Grok.");
        assertThat(fileBody).contains("\"system\"");
    }

    @Test
    @DisplayName("checkStatus()는 'completed' 상태를 COMPLETED로 매핑한다")
    void checkStatusCompleted() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_grok123\",\"status\":\"completed\"}")
                .addHeader("Content-Type", "application/json"));

        BaseBatchAdapter.BatchStatus status = adapter.checkStatus("batch_grok123");

        assertThat(status).isEqualTo(BaseBatchAdapter.BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("checkStatus()는 'in_progress' 상태를 IN_PROGRESS로 매핑한다")
    void checkStatusInProgress() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_grok123\",\"status\":\"in_progress\"}")
                .addHeader("Content-Type", "application/json"));

        BaseBatchAdapter.BatchStatus status = adapter.checkStatus("batch_grok123");

        assertThat(status).isEqualTo(BaseBatchAdapter.BatchStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("checkStatus()는 'failed' 상태를 FAILED로 매핑한다")
    void checkStatusFailed() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_grok123\",\"status\":\"failed\"}")
                .addHeader("Content-Type", "application/json"));

        BaseBatchAdapter.BatchStatus status = adapter.checkStatus("batch_grok123");

        assertThat(status).isEqualTo(BaseBatchAdapter.BatchStatus.FAILED);
    }

    @Test
    @DisplayName("checkStatus()는 'cancelled' 상태를 CANCELLED로 매핑한다")
    void checkStatusCancelled() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_grok123\",\"status\":\"cancelled\"}")
                .addHeader("Content-Type", "application/json"));

        BaseBatchAdapter.BatchStatus status = adapter.checkStatus("batch_grok123");

        assertThat(status).isEqualTo(BaseBatchAdapter.BatchStatus.CANCELLED);
    }

    @Test
    @DisplayName("collectResults()는 성공한 결과를 올바르게 파싱한다")
    void collectResultsSuccess() {
        String resultsJsonl =
                "{\"custom_id\":\"row-1\",\"response\":{\"body\":{\"choices\":[{\"message\":{\"content\":\"Hi there!\"}}],\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":4}}}}\n";

        // 배치 정보 조회
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_grok123\",\"output_file_id\":\"file_out_abc\"}")
                .addHeader("Content-Type", "application/json"));
        // 파일 내용 다운로드
        mockWebServer.enqueue(new MockResponse()
                .setBody(resultsJsonl)
                .addHeader("Content-Type", "application/x-ndjson"));

        List<BaseBatchAdapter.BatchResultItem> results = adapter.collectResults("batch_grok123");

        assertThat(results).hasSize(1);
        BaseBatchAdapter.BatchResultItem item = results.get(0);
        assertThat(item.rowId()).isEqualTo("row-1");
        assertThat(item.resultText()).isEqualTo("Hi there!");
        assertThat(item.success()).isTrue();
        assertThat(item.inputTokens()).isEqualTo(8);
        assertThat(item.outputTokens()).isEqualTo(4);
    }

    @Test
    @DisplayName("collectResults()는 에러가 있는 결과를 실패로 파싱한다")
    void collectResultsFailure() {
        String resultsJsonl =
                "{\"custom_id\":\"row-2\",\"error\":{\"code\":\"invalid_request\",\"message\":\"Token limit exceeded\"}}\n";

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_grok123\",\"output_file_id\":\"file_out_abc\"}")
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse()
                .setBody(resultsJsonl)
                .addHeader("Content-Type", "application/x-ndjson"));

        List<BaseBatchAdapter.BatchResultItem> results = adapter.collectResults("batch_grok123");

        assertThat(results).hasSize(1);
        BaseBatchAdapter.BatchResultItem item = results.get(0);
        assertThat(item.rowId()).isEqualTo("row-2");
        assertThat(item.success()).isFalse();
        assertThat(item.errorMessage()).isEqualTo("Token limit exceeded");
    }

    @Test
    @DisplayName("collectResults()는 output_file_id가 없으면 빈 목록을 반환한다")
    void collectResultsEmptyWhenNoOutputFileId() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_grok123\"}")
                .addHeader("Content-Type", "application/json"));

        List<BaseBatchAdapter.BatchResultItem> results = adapter.collectResults("batch_grok123");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("validateApiKey()는 401 응답 시 InvalidApiKeyException을 던진다")
    void validateApiKeyThrowsOn401() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(401));

        assertThatThrownBy(() -> adapter.validateApiKey())
                .isInstanceOf(ApiKeyValidator.InvalidApiKeyException.class)
                .hasMessageContaining("xai");
    }

    @Test
    @DisplayName("validateApiKey()는 200 응답 시 예외를 던지지 않는다")
    void validateApiKeyPassesOn200() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"data\":[]}")
                .addHeader("Content-Type", "application/json"));

        adapter.validateApiKey();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/models");
    }

    @Test
    @DisplayName("submitBatch()는 배치 생성 시 429 응답 후 재시도하여 성공한다")
    void submitBatchRetriesOnRateLimit() {
        // ping 성공
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"data\":[]}")
                .addHeader("Content-Type", "application/json"));
        // 파일 업로드 성공
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"file_retry\",\"object\":\"file\"}")
                .addHeader("Content-Type", "application/json"));
        // 배치 생성 429
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .addHeader("Content-Type", "application/json"));
        // 배치 생성 성공
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_retry_grok\",\"status\":\"validating\"}")
                .addHeader("Content-Type", "application/json"));

        List<BatchRowDto> rows = List.of(
                BatchRowDto.builder().id("row-1").prompt("Hello").build()
        );

        String batchId = adapter.submitBatch(rows, "grok-2-latest");

        assertThat(batchId).isEqualTo("batch_retry_grok");
    }

    @Test
    @DisplayName("submitBatch()는 API 키가 비어 있으면 IllegalStateException을 던진다")
    void submitBatchThrowsWhenApiKeyEmpty() {
        AiConfig emptyKeyConfig = new AiConfig();
        AiConfig.ProviderConfig providerConfig = new AiConfig.ProviderConfig();
        providerConfig.setApiKey("");
        providerConfig.setModel("grok-2-latest");
        emptyKeyConfig.setProviders(Map.of("xai", providerConfig));

        GrokBatchAdapter emptyKeyAdapter = new GrokBatchAdapter(
                emptyKeyConfig, WebClient.builder(), new ObjectMapper(),
                mockWebServer.url("/").toString());

        assertThatThrownBy(() -> emptyKeyAdapter.submitBatch(List.of(), "grok-2-latest"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("xai");
    }
}
