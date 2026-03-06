package org.jh.batchbridge.service.parser;

import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.exception.BatchParsingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaValidatorTest {

    @Test
    @DisplayName("id와 prompt 컬럼이 모두 있으면 예외가 발생하지 않는다")
    void validateRequiredColumns_success() {
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("id", 0);
        headerMap.put("prompt", 1);

        assertThatCode(() -> SchemaValidator.validateRequiredColumns(headerMap))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("id 컬럼이 없으면 예외가 발생한다")
    void validateRequiredColumns_missingId() {
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("prompt", 0);

        assertThatThrownBy(() -> SchemaValidator.validateRequiredColumns(headerMap))
                .isInstanceOf(BatchParsingException.class)
                .hasMessageContaining("Missing required column: id");
    }

    @Test
    @DisplayName("prompt 컬럼이 없으면 예외가 발생한다")
    void validateRequiredColumns_missingPrompt() {
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("id", 0);

        assertThatThrownBy(() -> SchemaValidator.validateRequiredColumns(headerMap))
                .isInstanceOf(BatchParsingException.class)
                .hasMessageContaining("Missing required column: prompt");
    }

    @Test
    @DisplayName("id와 prompt 컬럼이 모두 없으면 id 누락 예외가 발생한다")
    void validateRequiredColumns_missingBoth() {
        Map<String, Integer> headerMap = new HashMap<>();
        headerMap.put("model", 0);

        assertThatThrownBy(() -> SchemaValidator.validateRequiredColumns(headerMap))
                .isInstanceOf(BatchParsingException.class)
                .hasMessageContaining("Missing required column: id");
    }

    @Test
    @DisplayName("id와 prompt 필드가 모두 있으면 예외가 발생하지 않는다")
    void validateRequiredFields_success() {
        BatchRowDto row = BatchRowDto.builder()
                .id("1")
                .prompt("Hello")
                .build();

        assertThatCode(() -> SchemaValidator.validateRequiredFields(row, 1))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("id 필드가 없으면 예외가 발생한다")
    void validateRequiredFields_missingId() {
        BatchRowDto row = BatchRowDto.builder()
                .id(null)
                .prompt("Hello")
                .build();

        assertThatThrownBy(() -> SchemaValidator.validateRequiredFields(row, 3))
                .isInstanceOf(BatchParsingException.class)
                .hasMessageContaining("Missing required field 'id' at line 3");
    }

    @Test
    @DisplayName("id 필드가 빈 문자열이면 예외가 발생한다")
    void validateRequiredFields_emptyId() {
        BatchRowDto row = BatchRowDto.builder()
                .id("   ")
                .prompt("Hello")
                .build();

        assertThatThrownBy(() -> SchemaValidator.validateRequiredFields(row, 5))
                .isInstanceOf(BatchParsingException.class)
                .hasMessageContaining("Missing required field 'id' at line 5");
    }

    @Test
    @DisplayName("prompt 필드가 없으면 예외가 발생한다")
    void validateRequiredFields_missingPrompt() {
        BatchRowDto row = BatchRowDto.builder()
                .id("1")
                .prompt(null)
                .build();

        assertThatThrownBy(() -> SchemaValidator.validateRequiredFields(row, 2))
                .isInstanceOf(BatchParsingException.class)
                .hasMessageContaining("Missing required field 'prompt' at line 2");
    }

    @Test
    @DisplayName("prompt 필드가 빈 문자열이면 예외가 발생한다")
    void validateRequiredFields_emptyPrompt() {
        BatchRowDto row = BatchRowDto.builder()
                .id("1")
                .prompt("")
                .build();

        assertThatThrownBy(() -> SchemaValidator.validateRequiredFields(row, 4))
                .isInstanceOf(BatchParsingException.class)
                .hasMessageContaining("Missing required field 'prompt' at line 4");
    }
}
