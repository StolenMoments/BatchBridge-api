package org.jh.batchbridge.service.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Exponential backoff 재시도 유틸리티.
 * <p>
 * Rate limit(429) 또는 일시적 서버 오류(5xx) 발생 시 지수 백오프로 재시도한다.
 */
public final class RetryUtils {

    private static final Logger log = LoggerFactory.getLogger(RetryUtils.class);

    /** 기본 최대 재시도 횟수 */
    public static int DEFAULT_MAX_ATTEMPTS = 3;

    /** 기본 초기 대기 시간(ms) */
    public static long DEFAULT_INITIAL_DELAY_MS = 1_000L;

    /** 기본 최대 대기 시간(ms) */
    public static long DEFAULT_MAX_DELAY_MS = 30_000L;

    /** 지수 배율 */
    private static final double BACKOFF_MULTIPLIER = 2.0;

    private RetryUtils() {
    }

    /**
     * 기본 설정으로 재시도를 수행한다.
     *
     * @param operation 실행할 작업
     * @param <T>       반환 타입
     * @return 작업 결과
     */
    public static <T> T withRetry(Supplier<T> operation) {
        return withRetry(operation, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_DELAY_MS, DEFAULT_MAX_DELAY_MS);
    }

    /**
     * 지정된 설정으로 재시도를 수행한다.
     *
     * @param operation      실행할 작업
     * @param maxAttempts    최대 시도 횟수
     * @param initialDelayMs 초기 대기 시간(ms)
     * @param maxDelayMs     최대 대기 시간(ms)
     * @param <T>            반환 타입
     * @return 작업 결과
     */
    public static <T> T withRetry(Supplier<T> operation, int maxAttempts,
                                   long initialDelayMs, long maxDelayMs) {
        int attempt = 0;
        long delayMs = initialDelayMs;

        while (attempt < maxAttempts) {
            try {
                attempt++;
                return operation.get();
            } catch (WebClientResponseException e) {
                int statusCode = e.getStatusCode().value();

                if (!isRetryable(statusCode) || attempt >= maxAttempts) {
                    log.error("재시도 불가 오류 또는 최대 재시도 횟수 초과 (시도: {}/{}, 상태코드: {})",
                            attempt, maxAttempts, statusCode);
                    throw e;
                }

                long waitMs = delayMs;
                if (statusCode == 429) {
                    // Retry-After 헤더가 있으면 우선 사용
                    String retryAfter = e.getHeaders().getFirst("Retry-After");
                    if (retryAfter != null) {
                        try {
                            waitMs = Long.parseLong(retryAfter.trim()) * 1_000L;
                            log.warn("Rate limit 감지 (429). Retry-After: {}초 대기 후 재시도 ({}/{})",
                                    retryAfter, attempt, maxAttempts);
                        } catch (NumberFormatException ignored) {
                            log.warn("Rate limit 감지 (429). {}ms 대기 후 재시도 ({}/{})",
                                    waitMs, attempt, maxAttempts);
                        }
                    } else {
                        log.warn("Rate limit 감지 (429). {}ms 대기 후 재시도 ({}/{})",
                                waitMs, attempt, maxAttempts);
                    }
                } else {
                    log.warn("일시적 오류 (상태코드: {}). {}ms 대기 후 재시도 ({}/{})",
                            statusCode, waitMs, attempt, maxAttempts);
                }

                sleep(waitMs);
                delayMs = Math.min((long) (delayMs * BACKOFF_MULTIPLIER), maxDelayMs);

            } catch (Exception e) {
                if (attempt >= maxAttempts) {
                    log.error("최대 재시도 횟수 초과 (시도: {}/{})", attempt, maxAttempts);
                    throw e;
                }
                log.warn("오류 발생. {}ms 대기 후 재시도 ({}/{}): {}", delayMs, attempt, maxAttempts, e.getMessage());
                sleep(delayMs);
                delayMs = Math.min((long) (delayMs * BACKOFF_MULTIPLIER), maxDelayMs);
            }
        }
        throw new RuntimeException("최대 재시도 횟수 도달");
    }

    /**
     * 재시도 가능한 HTTP 상태코드인지 확인한다.
     * 429(Rate Limit), 500, 502, 503, 504는 재시도 대상이다.
     */
    static boolean isRetryable(int statusCode) {
        return statusCode == 429
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(Duration.ofMillis(ms));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("재시도 대기 중 인터럽트 발생", ie);
        }
    }
}
