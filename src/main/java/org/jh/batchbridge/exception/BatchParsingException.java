package org.jh.batchbridge.exception;

public class BatchParsingException extends RuntimeException {
    public BatchParsingException(String message) {
        super(message);
    }

    public BatchParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
