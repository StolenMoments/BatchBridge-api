package org.jh.batchbridge.service;

import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.exception.ErrorMessage;
import org.jh.batchbridge.exception.InvalidFileUploadException;
import org.jh.batchbridge.repository.JobRepository;
import org.jh.batchbridge.repository.ResultRepository;
import org.jh.batchbridge.service.parser.BatchFileParser;
import org.jh.batchbridge.service.TokenEstimator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchJobServiceTest {

    @Mock
    private BatchFileParser csvParser;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private TokenEstimator tokenEstimator;

    private BatchJobService batchJobService;

    @BeforeEach
    void setUp() {
        batchJobService = new BatchJobService(List.of(csvParser), jobRepository, resultRepository, tokenEstimator);
    }

    @Test
    @DisplayName("빈 파일을 업로드하면 InvalidFileUploadException이 발생한다")
    void throwExceptionWhenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> batchJobService.createJobFromUpload(emptyFile, "claude"))
                .isInstanceOf(InvalidFileUploadException.class)
                .hasMessage(ErrorMessage.FILE_EMPTY);
    }

    @Test
    @DisplayName("파일 이름이 없으면 InvalidFileUploadException이 발생한다")
    void throwExceptionWhenFileNameIsMissing() {
        MockMultipartFile noNameFile = new MockMultipartFile("file", "", "text/csv", "id,prompt\n1,hello".getBytes());

        assertThatThrownBy(() -> batchJobService.createJobFromUpload(noNameFile, "claude"))
                .isInstanceOf(InvalidFileUploadException.class)
                .hasMessage(ErrorMessage.FILE_NAME_MISSING);
    }

    @Test
    @DisplayName("지원하지 않는 파일 형식을 업로드하면 InvalidFileUploadException이 발생한다")
    void throwExceptionWhenFileFormatIsUnsupported() {
        when(csvParser.supports("test.txt")).thenReturn(false);
        MockMultipartFile txtFile = new MockMultipartFile("file", "test.txt", "text/plain", "some content".getBytes());

        assertThatThrownBy(() -> batchJobService.createJobFromUpload(txtFile, "claude"))
                .isInstanceOf(InvalidFileUploadException.class)
                .hasMessage(ErrorMessage.FILE_FORMAT_UNSUPPORTED);
    }

    @Test
    @DisplayName("model 컬럼이 있는 행은 해당 모델로 그룹화된다")
    void groupRowsByModelColumn() {
        List<BatchRowDto> rows = List.of(
                BatchRowDto.builder().id("1").prompt("p1").model("claude").build(),
                BatchRowDto.builder().id("2").prompt("p2").model("gemini").build(),
                BatchRowDto.builder().id("3").prompt("p3").model("claude").build()
        );

        Map<String, List<BatchRowDto>> result = batchJobService.groupRowsByModel(rows, "grok");

        assertThat(result).containsOnlyKeys("claude", "gemini");
        assertThat(result.get("claude")).hasSize(2);
        assertThat(result.get("gemini")).hasSize(1);
    }

    @Test
    @DisplayName("model 컬럼이 없는 행은 defaultModel로 그룹화된다")
    void groupRowsWithoutModelUsesDefaultModel() {
        List<BatchRowDto> rows = List.of(
                BatchRowDto.builder().id("1").prompt("p1").model(null).build(),
                BatchRowDto.builder().id("2").prompt("p2").model("").build(),
                BatchRowDto.builder().id("3").prompt("p3").model("gemini").build()
        );

        Map<String, List<BatchRowDto>> result = batchJobService.groupRowsByModel(rows, "claude");

        assertThat(result).containsOnlyKeys("claude", "gemini");
        assertThat(result.get("claude")).hasSize(2);
        assertThat(result.get("gemini")).hasSize(1);
    }

    @Test
    @DisplayName("모든 행에 model 컬럼이 없으면 전체가 defaultModel 그룹으로 묶인다")
    void groupAllRowsToDefaultModelWhenNoModelColumn() {
        List<BatchRowDto> rows = List.of(
                BatchRowDto.builder().id("1").prompt("p1").build(),
                BatchRowDto.builder().id("2").prompt("p2").build()
        );

        Map<String, List<BatchRowDto>> result = batchJobService.groupRowsByModel(rows, "grok");

        assertThat(result).containsOnlyKeys("grok");
        assertThat(result.get("grok")).hasSize(2);
    }

    @Test
    @DisplayName("혼합 배치: claude, gemini, grok 행이 각각 올바른 그룹으로 분리된다")
    void groupMixedModelRows() {
        List<BatchRowDto> rows = List.of(
                BatchRowDto.builder().id("1").prompt("p1").model("claude").build(),
                BatchRowDto.builder().id("2").prompt("p2").model("gemini").build(),
                BatchRowDto.builder().id("3").prompt("p3").model("grok").build(),
                BatchRowDto.builder().id("4").prompt("p4").model(null).build()
        );

        Map<String, List<BatchRowDto>> result = batchJobService.groupRowsByModel(rows, "claude");

        assertThat(result).containsOnlyKeys("claude", "gemini", "grok");
        assertThat(result.get("claude")).hasSize(2); // id=1 + id=4(fallback)
        assertThat(result.get("gemini")).hasSize(1);
        assertThat(result.get("grok")).hasSize(1);
    }
}
