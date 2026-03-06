package org.jh.batchbridge.service.adapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyValidatorTest {

    private MockWebServer mockWebServer;
    private WebClient webClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("validateNotEmpty()는 null API 키에 대해 예외를 던진다")
    void validateNotEmptyThrowsOnNull() {
        assertThatThrownBy(() -> ApiKeyValidator.validateNotEmpty(null, "test-provider"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test-provider");
    }

    @Test
    @DisplayName("validateNotEmpty()는 빈 API 키에 대해 예외를 던진다")
    void validateNotEmptyThrowsOnBlank() {
        assertThatThrownBy(() -> ApiKeyValidator.validateNotEmpty("   ", "test-provider"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test-provider");
    }

    @Test
    @DisplayName("validateNotEmpty()는 유효한 API 키에 대해 예외를 던지지 않는다")
    void validateNotEmptyPassesOnValidKey() {
        assertThatCode(() -> ApiKeyValidator.validateNotEmpty("valid-key", "test-provider"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ping()은 200 응답 시 예외를 던지지 않는다")
    void pingSuccessOn200() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{}"));

        assertThatCode(() -> ApiKeyValidator.ping(webClient, "/ping", "test-provider"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ping()은 401 응답 시 InvalidApiKeyException을 던진다")
    void pingThrowsOn401() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(401));

        assertThatThrownBy(() -> ApiKeyValidator.ping(webClient, "/ping", "test-provider"))
                .isInstanceOf(ApiKeyValidator.InvalidApiKeyException.class)
                .hasMessageContaining("test-provider")
                .hasMessageContaining("401");
    }

    @Test
    @DisplayName("ping()은 403 응답 시 InvalidApiKeyException을 던진다")
    void pingThrowsOn403() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(403));

        assertThatThrownBy(() -> ApiKeyValidator.ping(webClient, "/ping", "test-provider"))
                .isInstanceOf(ApiKeyValidator.InvalidApiKeyException.class)
                .hasMessageContaining("403");
    }

    @Test
    @DisplayName("ping()은 404 응답 시 예외를 던지지 않는다 (ping 실패로 간주하지 않음)")
    void pingPassesOn404() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        assertThatCode(() -> ApiKeyValidator.ping(webClient, "/ping", "test-provider"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ping()은 500 응답 시 예외를 던지지 않는다 (서버 오류는 통과)")
    void pingPassesOn500() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertThatCode(() -> ApiKeyValidator.ping(webClient, "/ping", "test-provider"))
                .doesNotThrowAnyException();
    }
}
