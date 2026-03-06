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

class GeminiBatchAdapterTest {

    private MockWebServer mockWebServer;
    private GeminiBatchAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        AiConfig aiConfig = new AiConfig();
        AiConfig.ProviderConfig providerConfig = new AiConfig.ProviderConfig();
        providerConfig.setApiKey("test-google-key");
        providerConfig.setModel("gemini-1.5-flash");
        aiConfig.setProviders(Map.of("google", providerConfig));

        adapter = new GeminiBatchAdapter(aiConfig, WebClient.builder(),
                new ObjectMapper(), mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("getProvider()는 'google'을 반환한다")
    void getProvider() {
        assertThat(adapter.getProvider()).isEqualTo("google");
    }

    @Test
    @DisplayName("submitBatch()는 배치를 제출하고 외부 배치 ID(name)를 반환한다")
    void submitBatch() throws InterruptedException {
        // validateApiKey()용 200 OK
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"models\":[]}")
                .addHeader("Content-Type", "application/json"));
        // 배치 제출용 응답
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"name\":\"operations/batch_xyz789\"}")
                .addHeader("Content-Type", "application/json"));

        List<BatchRowDto> rows = List.of(
                BatchRowDto.builder().id("row-1").prompt("Translate this").model("gemini-1.5-flash").build()
        );

        String batchId = adapter.submitBatch(rows, "gemini-1.5-flash");

        assertThat(batchId).isEqualTo("operations/batch_xyz789");

        // 1. ping 요청 검증
        RecordedRequest pingReq = mockWebServer.takeRequest();
        assertThat(pingReq.getPath()).contains("/v1beta/models");

        // 2. 배치 제출 요청 검증
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).contains("batchGenerateContent");

        String body = request.getBody().readUtf8();
        assertThat(body).contains("row-1");
        assertThat(body).contains("Translate this");
    }

    @Test
    @DisplayName("submitBatch()는 systemPrompt가 있으면 system_instruction 필드를 포함한다")
    void submitBatchWithSystemPrompt() throws InterruptedException {
        // validateApiKey()용 200 OK
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"models\":[]}")
                .addHeader("Content-Type", "application/json"));
        // 배치 제출용 응답
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"name\":\"operations/batch_sys\"}")
                .addHeader("Content-Type", "application/json"));

        List<BatchRowDto> rows = List.of(
                BatchRowDto.builder().id("row-1").prompt("Hello").systemPrompt("Be concise.").build()
        );

        adapter.submitBatch(rows, "gemini-1.5-flash");

        // 1. ping 요청 검증
        mockWebServer.takeRequest();

        // 2. 배치 제출 요청 검증
        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("Be concise.");
        assertThat(body).contains("system_instruction");
    }

    @Test
    @DisplayName("checkStatus()는 JOB_STATE_SUCCEEDED 상태를 COMPLETED로 매핑한다")
    void checkStatusCompleted() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"name\":\"operations/batch_xyz789\",\"metadata\":{\"state\":\"JOB_STATE_SUCCEEDED\"}}")
                .addHeader("Content-Type", "application/json"));

        BaseBatchAdapter.BatchStatus status = adapter.checkStatus("operations/batch_xyz789");

        assertThat(status).isEqualTo(BaseBatchAdapter.BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("checkStatus()는 JOB_STATE_RUNNING 상태를 IN_PROGRESS로 매핑한다")
    void checkStatusInProgress() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"name\":\"operations/batch_xyz789\",\"metadata\":{\"state\":\"JOB_STATE_RUNNING\"}}")
                .addHeader("Content-Type", "application/json"));

        BaseBatchAdapter.BatchStatus status = adapter.checkStatus("operations/batch_xyz789");

        assertThat(status).isEqualTo(BaseBatchAdapter.BatchStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("checkStatus()는 JOB_STATE_FAILED 상태를 FAILED로 매핑한다")
    void checkStatusFailed() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"name\":\"operations/batch_xyz789\",\"metadata\":{\"state\":\"JOB_STATE_FAILED\"}}")
                .addHeader("Content-Type", "application/json"));

        BaseBatchAdapter.BatchStatus status = adapter.checkStatus("operations/batch_xyz789");

        assertThat(status).isEqualTo(BaseBatchAdapter.BatchStatus.FAILED);
    }

    @Test
    @DisplayName("collectResults()는 성공한 결과를 올바르게 파싱한다")
    void collectResultsSuccess() {
        String responseBody = "{\"responses\":[{\"id\":\"row-1\",\"response\":{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Bonjour\"}]}}],\"usageMetadata\":{\"promptTokenCount\":5,\"candidatesTokenCount\":3}}}]}";

        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader("Content-Type", "application/json"));

        List<BaseBatchAdapter.BatchResultItem> results = adapter.collectResults("operations/batch_xyz789");

        assertThat(results).hasSize(1);
        BaseBatchAdapter.BatchResultItem item = results.get(0);
        assertThat(item.rowId()).isEqualTo("row-1");
        assertThat(item.resultText()).isEqualTo("Bonjour");
        assertThat(item.success()).isTrue();
        assertThat(item.inputTokens()).isEqualTo(5);
        assertThat(item.outputTokens()).isEqualTo(3);
    }

    @Test
    @DisplayName("collectResults()는 에러가 있는 결과를 실패로 파싱한다")
    void collectResultsFailure() {
        String responseBody = "{\"responses\":[{\"id\":\"row-2\",\"error\":{\"code\":400,\"message\":\"Invalid request\"}}]}";

        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader("Content-Type", "application/json"));

        List<BaseBatchAdapter.BatchResultItem> results = adapter.collectResults("operations/batch_xyz789");

        assertThat(results).hasSize(1);
        BaseBatchAdapter.BatchResultItem item = results.get(0);
        assertThat(item.rowId()).isEqualTo("row-2");
        assertThat(item.success()).isFalse();
        assertThat(item.errorMessage()).isEqualTo("Invalid request");
    }

    @Test
    @DisplayName("collectResults()는 응답이 비어있으면 빈 목록을 반환한다")
    void collectResultsEmpty() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"responses\":[]}")
                .addHeader("Content-Type", "application/json"));

        List<BaseBatchAdapter.BatchResultItem> results = adapter.collectResults("operations/batch_xyz789");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("validateApiKey()는 401 응답 시 InvalidApiKeyException을 던진다")
    void validateApiKeyThrowsOn401() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(401));

        assertThatThrownBy(() -> adapter.validateApiKey())
                .isInstanceOf(ApiKeyValidator.InvalidApiKeyException.class)
                .hasMessageContaining("google");
    }

    @Test
    @DisplayName("validateApiKey()는 200 응답 시 예외를 던지지 않는다")
    void validateApiKeyPassesOn200() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"models\":[]}")
                .addHeader("Content-Type", "application/json"));

        adapter.validateApiKey();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("/v1beta/models");
    }

    @Test
    @DisplayName("submitBatch()는 429 응답 후 재시도하여 성공한다")
    void submitBatchRetriesOnRateLimit() {
        // 첫 번째 요청: ping 성공
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"models\":[]}")
                .addHeader("Content-Type", "application/json"));
        // 두 번째 요청: 429 rate limit
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .addHeader("Content-Type", "application/json"));
        // 세 번째 요청: 성공
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"name\":\"operations/batch_retry\"}")
                .addHeader("Content-Type", "application/json"));

        List<BatchRowDto> rows = List.of(
                BatchRowDto.builder().id("row-1").prompt("Hello").build()
        );

        String batchId = adapter.submitBatch(rows, "gemini-1.5-flash");

        assertThat(batchId).isEqualTo("operations/batch_retry");
    }

    @Test
    @DisplayName("submitBatch()는 API 키가 비어 있으면 IllegalStateException을 던진다")
    void submitBatchThrowsWhenApiKeyEmpty() {
        AiConfig emptyKeyConfig = new AiConfig();
        AiConfig.ProviderConfig providerConfig = new AiConfig.ProviderConfig();
        providerConfig.setApiKey("");
        providerConfig.setModel("gemini-1.5-flash");
        emptyKeyConfig.setProviders(Map.of("google", providerConfig));

        GeminiBatchAdapter emptyKeyAdapter = new GeminiBatchAdapter(
                emptyKeyConfig, WebClient.builder(), new ObjectMapper(),
                mockWebServer.url("/").toString());

        assertThatThrownBy(() -> emptyKeyAdapter.submitBatch(List.of(), "gemini-1.5-flash"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("google");
    }
}
