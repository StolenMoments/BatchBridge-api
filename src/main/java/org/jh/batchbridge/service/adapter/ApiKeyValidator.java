package org.jh.batchbridge.service.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * AI 서비스 API 키 유효성 사전 검증기.
 * <p>
 * 배치 제출 전 경량 ping 요청으로 API 키가 유효한지 확인한다.
 */
public final class ApiKeyValidator {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyValidator.class);

    private ApiKeyValidator() {
    }

    /**
     * API 키가 비어 있거나 null이면 즉시 예외를 던진다.
     *
     * @param apiKey   검증할 API 키
     * @param provider 프로바이더 이름 (로그용)
     */
    public static void validateNotEmpty(String apiKey, String provider) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "[" + provider + "] API 키가 설정되지 않았습니다. application.yaml을 확인하세요.");
        }
    }

    /**
     * WebClient로 ping 요청을 보내 API 키 유효성을 확인한다.
     * <p>
     * 401/403 응답이면 {@link InvalidApiKeyException}을 던진다.
     * 그 외 오류(네트워크, 5xx 등)는 경고 로그만 남기고 통과시킨다.
     *
     * @param webClient  사용할 WebClient
     * @param pingUri    ping 요청 URI (상대 경로)
     * @param provider   프로바이더 이름 (로그용)
     */
    public static void ping(WebClient webClient, String pingUri, String provider) {
        log.debug("[{}] API 키 유효성 ping: {}", provider, pingUri);
        try {
            webClient.get()
                    .uri(pingUri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.debug("[{}] API 키 유효성 확인 완료", provider);
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new InvalidApiKeyException(
                        "[" + provider + "] API 키가 유효하지 않습니다. (HTTP " + status + ")", e);
            }
            // 그 외 오류(404, 5xx 등)는 ping 실패로 간주하지 않고 경고만 남김
            log.warn("[{}] API 키 ping 중 비인증 오류 발생 (HTTP {}), 계속 진행합니다.", provider, status);
        } catch (Exception e) {
            log.warn("[{}] API 키 ping 중 오류 발생, 계속 진행합니다: {}", provider, e.getMessage());
        }
    }

    /**
     * API 키가 유효하지 않을 때 던지는 예외.
     */
    public static class InvalidApiKeyException extends RuntimeException {
        public InvalidApiKeyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
