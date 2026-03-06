package org.jh.batchbridge.service.parser;

import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.exception.BatchParsingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvBatchFileParserTest {

    private final CsvBatchFileParser parser = new CsvBatchFileParser();

    @Test
    @DisplayName("정상적인 UTF-8 CSV 파일을 파싱한다")
    void parseUtf8Csv() {
        String csv = "id,prompt,model,temperature,max_tokens\n" +
                "1,Hello,gpt-4,0.7,100\n" +
                "2,World,gpt-3.5-turbo,1.0,200";
        
        List<BatchRowDto> results = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo("1");
        assertThat(results.get(0).getPrompt()).isEqualTo("Hello");
        assertThat(results.get(0).getModel()).isEqualTo("gpt-4");
        assertThat(results.get(0).getTemperature()).isEqualTo(0.7);
        assertThat(results.get(0).getMaxTokens()).isEqualTo(100);

        assertThat(results.get(1).getId()).isEqualTo("2");
        assertThat(results.get(1).getPrompt()).isEqualTo("World");
    }

    @Test
    @DisplayName("CP949(EUC-KR) 인코딩된 CSV 파일을 자동으로 감지하여 파싱한다")
    void parseCp949Csv() {
        // "번호,프롬프트" in CP949
        // 인코딩 감지를 돕기 위해 데이터 양을 조금 더 늘림
        StringBuilder sb = new StringBuilder("id,prompt\n");
        for (int i = 1; i <= 20; i++) {
            sb.append(i).append(",한글 데이터 내용입니다 ").append(i).append("\n");
        }
        String csv = sb.toString();
        Charset cp949 = Charset.forName("CP949");
        byte[] bytes = csv.getBytes(cp949);

        List<BatchRowDto> results = parser.parse(new ByteArrayInputStream(bytes));

        assertThat(results).hasSize(20);
        assertThat(results.get(0).getPrompt()).contains("한글");
    }

    @Test
    @DisplayName("필수 컬럼(id)이 누락되면 예외가 발생한다")
    void throwExceptionWhenIdColumnMissing() {
        String csv = "prompt,model\nHello,gpt-4";
        
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(csv.getBytes())))
                .isInstanceOf(BatchParsingException.class)
                .hasMessageContaining("Missing required column: id");
    }

    @Test
    @DisplayName("빈 파일인 경우 예외가 발생한다")
    void throwExceptionWhenFileIsEmpty() {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(BatchParsingException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("지원하는 확장자 확인")
    void supportCheck() {
        assertThat(parser.supports("test.csv")).isTrue();
        assertThat(parser.supports("TEST.CSV")).isTrue();
        assertThat(parser.supports("test.xlsx")).isFalse();
    }
}
