package org.jh.batchbridge.service.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryUtilsTest {

    @Test
    @DisplayName("성공 시 결과를 즉시 반환한다")
    void successOnFirstAttempt() {
        String result = RetryUtils.withRetry(() -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("429 오류 후 재시도하여 성공한다")
    void retryOnRateLimit() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = RetryUtils.withRetry(() -> {
            if (attempts.incrementAndGet() < 2) {
                throw rateLimitException();
            }
            return "success";
        }, 3, 10L, 100L);

        assertThat(result).isEqualTo("success");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("503 오류 후 재시도하여 성공한다")
    void retryOnServerError() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = RetryUtils.withRetry(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw serverErrorException(503);
            }
            return "recovered";
        }, 3, 10L, 100L);

        assertThat(result).isEqualTo("recovered");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("최대 재시도 횟수 초과 시 예외를 던진다")
    void throwsAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> RetryUtils.withRetry(() -> {
            attempts.incrementAndGet();
            throw rateLimitException();
        }, 3, 10L, 100L))
                .isInstanceOf(WebClientResponseException.class);

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("400 오류는 재시도하지 않고 즉시 예외를 던진다")
    void noRetryOnClientError() {
        AtomicInteger attempts = new AtomicInteger(0);

        assertThatThrownBy(() -> RetryUtils.withRetry(() -> {
            attempts.incrementAndGet();
            throw serverErrorException(400);
        }, 3, 10L, 100L))
                .isInstanceOf(WebClientResponseException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("isRetryable()은 429, 500, 502, 503, 504를 재시도 가능으로 판단한다")
    void isRetryable() {
        assertThat(RetryUtils.isRetryable(429)).isTrue();
        assertThat(RetryUtils.isRetryable(500)).isTrue();
        assertThat(RetryUtils.isRetryable(502)).isTrue();
        assertThat(RetryUtils.isRetryable(503)).isTrue();
        assertThat(RetryUtils.isRetryable(504)).isTrue();
    }

    @Test
    @DisplayName("isRetryable()은 400, 401, 403, 404를 재시도 불가로 판단한다")
    void isNotRetryable() {
        assertThat(RetryUtils.isRetryable(400)).isFalse();
        assertThat(RetryUtils.isRetryable(401)).isFalse();
        assertThat(RetryUtils.isRetryable(403)).isFalse();
        assertThat(RetryUtils.isRetryable(404)).isFalse();
    }

    @Test
    @DisplayName("429 응답에 Retry-After 헤더가 있으면 해당 시간만큼 대기한다")
    void retryAfterHeaderIsRespected() {
        AtomicInteger attempts = new AtomicInteger(0);

        String result = RetryUtils.withRetry(() -> {
            if (attempts.incrementAndGet() < 2) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Retry-After", "0");
                throw WebClientResponseException.create(
                        429, "Too Many Requests", headers,
                        new byte[0], StandardCharsets.UTF_8);
            }
            return "done";
        }, 3, 10L, 100L);

        assertThat(result).isEqualTo("done");
        assertThat(attempts.get()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // 헬퍼
    // -------------------------------------------------------------------------

    private static WebClientResponseException rateLimitException() {
        return serverErrorException(429);
    }

    private static WebClientResponseException serverErrorException(int status) {
        return WebClientResponseException.create(
                status, HttpStatus.resolve(status) != null
                        ? HttpStatus.resolve(status).getReasonPhrase() : "Error",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
    }
}
