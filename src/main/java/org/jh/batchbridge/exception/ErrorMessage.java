package org.jh.batchbridge.exception;

public final class ErrorMessage {

    private ErrorMessage() {}

    // 파일 업로드 관련
    public static final String FILE_EMPTY = "업로드된 파일이 비어 있습니다.";
    public static final String FILE_NAME_MISSING = "파일 이름이 없습니다.";
    public static final String FILE_FORMAT_UNSUPPORTED =
            "지원하지 않는 파일 형식입니다. 지원 형식: .xlsx, .ods, .csv, .jsonl";
    public static final String FILE_SIZE_EXCEEDED = "파일 크기가 허용 한도를 초과했습니다.";

    // 파싱 관련
    public static final String PARSE_FAILED = "파일 파싱 중 오류가 발생했습니다.";
}
