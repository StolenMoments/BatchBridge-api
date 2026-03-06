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

class ClaudeBatchAdapterTest {

    private MockWebServer mockWebServer;
    private ClaudeBatchAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        // 테스트 속도를 위해 재시도 대기 시간을 0으로 설정
        RetryUtils.DEFAULT_INITIAL_DELAY_MS = 0L;

        mockWebServer = new MockWebServer();
        mockWebServer.start();

        AiConfig aiConfig = new AiConfig();
        AiConfig.ProviderConfig providerConfig = new AiConfig.ProviderConfig();
        providerConfig.setApiKey("test-api-key");
        providerConfig.setModel("claude-3-5-sonnet-latest");
        aiConfig.setProviders(Map.of("anthropic", providerConfig));

        adapter = new ClaudeBatchAdapter(aiConfig, WebClient.builder(),
                new ObjectMapper(), mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        // 재시도 대기 시간 원복
        RetryUtils.DEFAULT_INITIAL_DELAY_MS = 1_000L;

        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("getProvider()는 'anthropic'을 반환한다")
    void getProvider() {
        assertThat(adapter.getProvider()).isEqualTo("anthropic");
    }

    @Test
    @DisplayName("submitBatch()는 배치를 제출하고 외부 배치 ID를 반환한다")
    void submitBatch() throws InterruptedException {
        // validateApiKey()용 200 OK
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]")
                .addHeader("Content-Type", "application/json"));
        // 배치 제출용 응답
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_abc123\",\"processing_status\":\"in_progress\"}")
                .addHeader("Content-Type", "application/json"));

        List<BatchRowDto> rows = List.of(
                BatchRowDto.builder().id("row-1").prompt("Hello").model("claude-3-5-sonnet-latest").build()
        );

        String batchId = adapter.submitBatch(rows, "claude-3-5-sonnet-latest");

        assertThat(batchId).isEqualTo("batch_abc123");

        // 1. ping 요청 검증
        RecordedRequest pingReq = mockWebServer.takeRequest();
        assertThat(pingReq.getPath()).isEqualTo("/v1/models");

        // 2. 배치 제출 요청 검증
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/v1/messages/batches");

        String body = request.getBody().readUtf8();
        assertThat(body).contains("row-1");
        assertThat(body).contains("Hello");
    }

    @Test
    @DisplayName("submitBatch()는 systemPrompt가 있으면 system 필드를 포함한다")
    void submitBatchWithSystemPrompt() throws InterruptedException {
        // validateApiKey()용 200 OK
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]")
                .addHeader("Content-Type", "application/json"));
        // 배치 제출용 응답
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_sys123\"}")
                .addHeader("Content-Type", "application/json"));

        List<BatchRowDto> rows = List.of(
                BatchRowDto.builder().id("row-1").prompt("Hello").systemPrompt("You are helpful.").build()
        );

        adapter.submitBatch(rows, "claude-3-5-sonnet-latest");

        // 1. ping 요청 검증
        mockWebServer.takeRequest();

        // 2. 배치 제출 요청 검증
        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("You are helpful.");
        assertThat(body).contains("\"system\"");
    }

    @Test
    @DisplayName("checkStatus()는 'ended' 상태를 COMPLETED로 매핑한다")
    void checkStatusCompleted() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_abc123\",\"processing_status\":\"ended\"}")
                .addHeader("Content-Type", "application/json"));

        BaseBatchAdapter.BatchStatus status = adapter.checkStatus("batch_abc123");

        assertThat(status).isEqualTo(BaseBatchAdapter.BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("checkStatus()는 'in_progress' 상태를 IN_PROGRESS로 매핑한다")
    void checkStatusInProgress() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_abc123\",\"processing_status\":\"in_progress\"}")
                .addHeader("Content-Type", "application/json"));

        BaseBatchAdapter.BatchStatus status = adapter.checkStatus("batch_abc123");

        assertThat(status).isEqualTo(BaseBatchAdapter.BatchStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("checkStatus()는 'canceling' 상태를 CANCELLED로 매핑한다")
    void checkStatusCancelled() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_abc123\",\"processing_status\":\"canceling\"}")
                .addHeader("Content-Type", "application/json"));

        BaseBatchAdapter.BatchStatus status = adapter.checkStatus("batch_abc123");

        assertThat(status).isEqualTo(BaseBatchAdapter.BatchStatus.CANCELLED);
    }

    @Test
    @DisplayName("collectResults()는 성공한 결과를 올바르게 파싱한다")
    void collectResultsSuccess() {
        String resultsJsonl =
                "{\"custom_id\":\"row-1\",\"result\":{\"type\":\"succeeded\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"Hello!\"}],\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}}}\n";

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_abc123\",\"results_url\":\"/v1/messages/batches/batch_abc123/results\"}")
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse()
                .setBody(resultsJsonl)
                .addHeader("Content-Type", "application/x-ndjson"));

        List<BaseBatchAdapter.BatchResultItem> results = adapter.collectResults("batch_abc123");

        assertThat(results).hasSize(1);
        BaseBatchAdapter.BatchResultItem item = results.get(0);
        assertThat(item.rowId()).isEqualTo("row-1");
        assertThat(item.resultText()).isEqualTo("Hello!");
        assertThat(item.success()).isTrue();
        assertThat(item.inputTokens()).isEqualTo(10);
        assertThat(item.outputTokens()).isEqualTo(5);
    }

    @Test
    @DisplayName("collectResults()는 실패한 결과를 올바르게 파싱한다")
    void collectResultsFailure() {
        String resultsJsonl =
                "{\"custom_id\":\"row-2\",\"result\":{\"type\":\"errored\",\"error\":{\"type\":\"invalid_request\",\"message\":\"Bad request\"}}}\n";

        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_abc123\",\"results_url\":\"/v1/messages/batches/batch_abc123/results\"}")
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse()
                .setBody(resultsJsonl)
                .addHeader("Content-Type", "application/x-ndjson"));

        List<BaseBatchAdapter.BatchResultItem> results = adapter.collectResults("batch_abc123");

        assertThat(results).hasSize(1);
        BaseBatchAdapter.BatchResultItem item = results.get(0);
        assertThat(item.rowId()).isEqualTo("row-2");
        assertThat(item.success()).isFalse();
        assertThat(item.errorMessage()).isEqualTo("Bad request");
    }

    @Test
    @DisplayName("collectResults()는 results_url이 없으면 빈 목록을 반환한다")
    void collectResultsEmptyWhenNoResultsUrl() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_abc123\"}")
                .addHeader("Content-Type", "application/json"));

        List<BaseBatchAdapter.BatchResultItem> results = adapter.collectResults("batch_abc123");

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("validateApiKey()는 401 응답 시 InvalidApiKeyException을 던진다")
    void validateApiKeyThrowsOn401() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(401));

        assertThatThrownBy(() -> adapter.validateApiKey())
                .isInstanceOf(ApiKeyValidator.InvalidApiKeyException.class)
                .hasMessageContaining("anthropic");
    }

    @Test
    @DisplayName("validateApiKey()는 200 응답 시 예외를 던지지 않는다")
    void validateApiKeyPassesOn200() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]")
                .addHeader("Content-Type", "application/json"));

        adapter.validateApiKey();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v1/models");
    }

    @Test
    @DisplayName("submitBatch()는 429 응답 후 재시도하여 성공한다")
    void submitBatchRetriesOnRateLimit() throws InterruptedException {
        // 첫 번째 요청: ping 성공
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]")
                .addHeader("Content-Type", "application/json"));
        // 두 번째 요청: 429 rate limit
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .addHeader("Content-Type", "application/json"));
        // 세 번째 요청: 성공
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":\"batch_retry123\"}")
                .addHeader("Content-Type", "application/json"));

        List<BatchRowDto> rows = List.of(
                BatchRowDto.builder().id("row-1").prompt("Hello").build()
        );

        String batchId = adapter.submitBatch(rows, "claude-3-5-sonnet-latest");

        assertThat(batchId).isEqualTo("batch_retry123");

        // 1. ping 요청 검증
        RecordedRequest pingReq = mockWebServer.takeRequest();
        assertThat(pingReq.getPath()).isEqualTo("/v1/models");

        // 2. 429 에러 발생한 첫 번째 제출 요청 검증
        RecordedRequest failReq = mockWebServer.takeRequest();
        assertThat(failReq.getMethod()).isEqualTo("POST");
        assertThat(failReq.getPath()).isEqualTo("/v1/messages/batches");

        // 3. 재시도하여 성공한 두 번째 제출 요청 검증
        RecordedRequest successReq = mockWebServer.takeRequest();
        assertThat(successReq.getMethod()).isEqualTo("POST");
        assertThat(successReq.getPath()).isEqualTo("/v1/messages/batches");
    }

    @Test
    @DisplayName("submitBatch()는 API 키가 비어 있으면 IllegalStateException을 던진다")
    void submitBatchThrowsWhenApiKeyEmpty() {
        AiConfig emptyKeyConfig = new AiConfig();
        AiConfig.ProviderConfig providerConfig = new AiConfig.ProviderConfig();
        providerConfig.setApiKey("");
        providerConfig.setModel("claude-3-5-sonnet-latest");
        emptyKeyConfig.setProviders(Map.of("anthropic", providerConfig));

        ClaudeBatchAdapter emptyKeyAdapter = new ClaudeBatchAdapter(
                emptyKeyConfig, WebClient.builder(), new ObjectMapper(),
                mockWebServer.url("/").toString());

        assertThatThrownBy(() -> emptyKeyAdapter.submitBatch(List.of(), "claude-3-5-sonnet-latest"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("anthropic");
    }
}
