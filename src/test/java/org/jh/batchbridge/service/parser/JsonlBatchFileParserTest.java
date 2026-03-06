package org.jh.batchbridge.service.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.exception.BatchParsingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonlBatchFileParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonlBatchFileParser parser = new JsonlBatchFileParser(objectMapper);

    @Test
    @DisplayName("정상적인 JSONL 파일을 파싱한다")
    void parseJsonl() {
        String jsonl = "{\"id\":\"1\",\"prompt\":\"Hello\",\"model\":\"gpt-4\",\"temperature\":0.7,\"max_tokens\":100}\n" +
                "{\"id\":\"2\",\"prompt\":\"World\",\"model\":\"gpt-3.5-turbo\"}";
        
        List<BatchRowDto> results = parser.parse(new ByteArrayInputStream(jsonl.getBytes(StandardCharsets.UTF_8)));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo("1");
        assertThat(results.get(0).getPrompt()).isEqualTo("Hello");
        assertThat(results.get(0).getModel()).isEqualTo("gpt-4");
        assertThat(results.get(0).getTemperature()).isEqualTo(0.7);
        assertThat(results.get(0).getMaxTokens()).isEqualTo(100);

        assertThat(results.get(1).getId()).isEqualTo("2");
        assertThat(results.get(1).getPrompt()).isEqualTo("World");
        assertThat(results.get(1).getModel()).isEqualTo("gpt-3.5-turbo");
    }

    @Test
    @DisplayName("빈 줄이 포함된 JSONL 파일을 파싱한다")
    void parseJsonlWithEmptyLines() {
        String jsonl = "\n{\"id\":\"1\",\"prompt\":\"Hello\"}\n\n  \n{\"id\":\"2\",\"prompt\":\"World\"}\n";
        
        List<BatchRowDto> results = parser.parse(new ByteArrayInputStream(jsonl.getBytes(StandardCharsets.UTF_8)));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo("1");
        assertThat(results.get(1).getId()).isEqualTo("2");
    }

    @Test
    @DisplayName("잘못된 형식의 JSON이 포함되면 예외가 발생한다")
    void throwExceptionWhenJsonIsInvalid() {
        String jsonl = "{\"id\":\"1\",\"prompt\":\"Hello\"}\n{invalid_json}";
        
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(jsonl.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BatchParsingException.class)
                .hasMessageContaining("line 2");
    }

    @Test
    @DisplayName("지원하는 확장자 확인")
    void supportCheck() {
        assertThat(parser.supports("test.jsonl")).isTrue();
        assertThat(parser.supports("TEST.JSONL")).isTrue();
        assertThat(parser.supports("test.csv")).isFalse();
    }

    @Test
    @DisplayName("필수 필드(id)가 누락되면 예외가 발생한다")
    void throwExceptionWhenIdMissing() {
        String jsonl = "{\"prompt\":\"Hello\"}";
        
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(jsonl.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BatchParsingException.class)
                .hasMessageContaining("Missing required field 'id'");
    }

    @Test
    @DisplayName("필수 필드(prompt)가 누락되면 예외가 발생한다")
    void throwExceptionWhenPromptMissing() {
        String jsonl = "{\"id\":\"1\"}";
        
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(jsonl.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BatchParsingException.class)
                .hasMessageContaining("Missing required field 'prompt'");
    }
}
