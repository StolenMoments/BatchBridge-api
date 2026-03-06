package org.jh.batchbridge.service.adapter;

import org.jh.batchbridge.dto.BatchRowDto;

import java.util.List;

/**
 * AI 서비스별 Batch API 어댑터 인터페이스.
 * 제출 / 상태조회 / 결과수집 세 가지 오퍼레이션을 정의한다.
 */
public interface BaseBatchAdapter {

    /**
     * 배치 요청을 AI 서비스에 제출하고 외부 배치 ID를 반환한다.
     *
     * @param rows  배치 행 목록
     * @param model 사용할 모델명
     * @return 외부 배치 ID
     */
    String submitBatch(List<BatchRowDto> rows, String model);

    /**
     * 외부 배치 ID로 현재 처리 상태를 조회한다.
     *
     * @param externalBatchId 외부 배치 ID
     * @return 배치 상태
     */
    BatchStatus checkStatus(String externalBatchId);

    /**
     * 완료된 배치의 결과를 수집한다.
     *
     * @param externalBatchId 외부 배치 ID
     * @return 행 ID → 응답 텍스트 매핑 결과 목록
     */
    List<BatchResultItem> collectResults(String externalBatchId);

    /**
     * 이 어댑터가 지원하는 프로바이더 식별자를 반환한다.
     */
    String getProvider();

    // -------------------------------------------------------------------------
    // 공통 값 객체
    // -------------------------------------------------------------------------

    enum BatchStatus {
        IN_PROGRESS, COMPLETED, FAILED, CANCELLED
    }

    record BatchResultItem(String rowId, String resultText, boolean success, String errorMessage,
                           int inputTokens, int outputTokens) {

        public static BatchResultItem success(String rowId, String resultText,
                                              int inputTokens, int outputTokens) {
            return new BatchResultItem(rowId, resultText, true, null, inputTokens, outputTokens);
        }

        public static BatchResultItem failure(String rowId, String errorMessage) {
            return new BatchResultItem(rowId, null, false, errorMessage, 0, 0);
        }
    }
}
