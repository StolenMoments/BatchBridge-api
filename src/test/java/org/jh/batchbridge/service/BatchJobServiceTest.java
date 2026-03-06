package org.jh.batchbridge.service;

import org.jh.batchbridge.exception.ErrorMessage;
import org.jh.batchbridge.exception.InvalidFileUploadException;
import org.jh.batchbridge.repository.JobRepository;
import org.jh.batchbridge.repository.ResultRepository;
import org.jh.batchbridge.service.parser.BatchFileParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

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

    private BatchJobService batchJobService;

    @BeforeEach
    void setUp() {
        batchJobService = new BatchJobService(List.of(csvParser), jobRepository, resultRepository);
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
}
