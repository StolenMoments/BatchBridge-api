package org.jh.batchbridge.service;

import org.jh.batchbridge.dto.BatchRowDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenEstimatorTest {

    private TokenEstimator tokenEstimator;

    @BeforeEach
    void setUp() {
        tokenEstimator = new TokenEstimator();
    }

    // ── resolveBatchLimit 테스트 ────────────────────────────────────────────

    @Test
    @DisplayName("claude 모델의 배치 한도는 10,000이다")
    void batchLimitForClaude() {
        assertThat(tokenEstimator.resolveBatchLimit("claude-3-5-sonnet")).isEqualTo(10_000);
        assertThat(tokenEstimator.resolveBatchLimit("claude")).isEqualTo(10_000);
    }

    @Test
    @DisplayName("gemini 모델의 배치 한도는 2,000이다")
    void batchLimitForGemini() {
        assertThat(tokenEstimator.resolveBatchLimit("gemini-pro")).isEqualTo(2_000);
        assertThat(tokenEstimator.resolveBatchLimit("gemini")).isEqualTo(2_000);
    }

    @Test
    @DisplayName("grok 모델의 배치 한도는 5,000이다")
    void batchLimitForGrok() {
        assertThat(tokenEstimator.resolveBatchLimit("grok-2")).isEqualTo(5_000);
        assertThat(tokenEstimator.resolveBatchLimit("grok")).isEqualTo(5_000);
    }

    @Test
    @DisplayName("알 수 없는 모델의 배치 한도는 기본값 1,000이다")
    void batchLimitForUnknownModel() {
        assertThat(tokenEstimator.resolveBatchLimit("unknown-model")).isEqualTo(1_000);
        assertThat(tokenEstimator.resolveBatchLimit(null)).isEqualTo(1_000);
    }

    // ── splitIntoChunks 테스트 ──────────────────────────────────────────────

    @Test
    @DisplayName("행 수가 한도 이하이면 단일 청크로 반환된다")
    void splitIntoSingleChunkWhenBelowLimit() {
        List<BatchRowDto> rows = makeRows(5);
        List<List<BatchRowDto>> chunks = tokenEstimator.splitIntoChunks(rows, "gemini"); // 한도 2000
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).hasSize(5);
    }

    @Test
    @DisplayName("행 수가 한도와 정확히 같으면 단일 청크로 반환된다")
    void splitIntoSingleChunkWhenExactlyAtLimit() {
        List<BatchRowDto> rows = makeRows(TokenEstimator.BATCH_LIMIT_GEMINI);
        List<List<BatchRowDto>> chunks = tokenEstimator.splitIntoChunks(rows, "gemini");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).hasSize(TokenEstimator.BATCH_LIMIT_GEMINI);
    }

    @Test
    @DisplayName("행 수가 한도를 초과하면 자동으로 청크가 분할된다")
    void splitIntoMultipleChunksWhenExceedsLimit() {
        int limit = TokenEstimator.BATCH_LIMIT_GEMINI; // 2000
        List<BatchRowDto> rows = makeRows(limit + 1); // 2001행
        List<List<BatchRowDto>> chunks = tokenEstimator.splitIntoChunks(rows, "gemini");
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).hasSize(limit);
        assertThat(chunks.get(1)).hasSize(1);
    }

    @Test
    @DisplayName("행 수가 한도의 배수이면 균등하게 청크가 분할된다")
    void splitIntoEvenChunksWhenMultipleOfLimit() {
        int limit = TokenEstimator.BATCH_LIMIT_GEMINI; // 2000
        List<BatchRowDto> rows = makeRows(limit * 3); // 6000행
        List<List<BatchRowDto>> chunks = tokenEstimator.splitIntoChunks(rows, "gemini");
        assertThat(chunks).hasSize(3);
        chunks.forEach(chunk -> assertThat(chunk).hasSize(limit));
    }

    @Test
    @DisplayName("나머지가 있는 경우 마지막 청크에 나머지 행이 담긴다")
    void splitWithRemainderInLastChunk() {
        int limit = TokenEstimator.BATCH_LIMIT_GROK; // 5000
        int total = limit * 2 + 300; // 10300행
        List<BatchRowDto> rows = makeRows(total);
        List<List<BatchRowDto>> chunks = tokenEstimator.splitIntoChunks(rows, "grok");
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).hasSize(limit);
        assertThat(chunks.get(1)).hasSize(limit);
        assertThat(chunks.get(2)).hasSize(300);
    }

    @Test
    @DisplayName("빈 행 목록은 빈 청크 목록을 반환한다")
    void splitEmptyRowsReturnsEmptyChunks() {
        List<List<BatchRowDto>> chunks = tokenEstimator.splitIntoChunks(List.of(), "claude");
        assertThat(chunks).isEmpty();
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    private List<BatchRowDto> makeRows(int count) {
        List<BatchRowDto> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(BatchRowDto.builder().id(String.valueOf(i)).prompt("prompt " + i).build());
        }
        return rows;
    }
}
