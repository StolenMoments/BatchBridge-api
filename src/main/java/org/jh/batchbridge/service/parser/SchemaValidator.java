package org.jh.batchbridge.service.parser;

import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.exception.BatchParsingException;

import java.util.Map;

public class SchemaValidator {

    private static final String COL_ID = "id";
    private static final String COL_PROMPT = "prompt";

    private SchemaValidator() {
    }

    public static void validateRequiredColumns(Map<String, Integer> headerMap) {
        if (!headerMap.containsKey(COL_ID)) {
            throw new BatchParsingException("Missing required column: " + COL_ID);
        }
        if (!headerMap.containsKey(COL_PROMPT)) {
            throw new BatchParsingException("Missing required column: " + COL_PROMPT);
        }
    }

    public static void validateRequiredFields(BatchRowDto row, int lineNumber) {
        if (row.getId() == null || row.getId().trim().isEmpty()) {
            throw new BatchParsingException("Missing required field 'id' at line " + lineNumber);
        }
        if (row.getPrompt() == null || row.getPrompt().trim().isEmpty()) {
            throw new BatchParsingException("Missing required field 'prompt' at line " + lineNumber);
        }
    }
}
