package org.jh.batchbridge.exception;

import org.jh.batchbridge.dto.api.ApiResponse;
import org.jh.batchbridge.service.adapter.ApiKeyValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidFileUploadException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidFileUpload(InvalidFileUploadException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("INVALID_FILE_FORMAT", ex.getMessage()));
    }

    @ExceptionHandler(BatchParsingException.class)
    public ResponseEntity<ApiResponse<Void>> handleBatchParsing(BatchParsingException ex) {
        String code = "INVALID_FILE_FORMAT";
        if (ex.getMessage().contains("Missing required column")) {
            code = "MISSING_REQUIRED_COLUMN";
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.fail(code, ex.getMessage()));
    }

    @ExceptionHandler(ApiKeyValidator.InvalidApiKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidApiKey(ApiKeyValidator.InvalidApiKeyException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("INVALID_API_KEY", ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("JOB_NOT_FOUND", "존재하지 않는 리소스입니다."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("INVALID_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.fail("INVALID_FILE_FORMAT", ErrorMessage.FILE_SIZE_EXCEEDED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("INTERNAL_SERVER_ERROR", ex.getMessage()));
    }
}
