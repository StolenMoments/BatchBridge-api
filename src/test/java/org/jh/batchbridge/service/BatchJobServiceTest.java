package org.jh.batchbridge.service;

import org.jh.batchbridge.domain.Chunk;
import org.jh.batchbridge.domain.Job;
import org.jh.batchbridge.domain.Result;
import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.dto.MergedResultDto;
import org.jh.batchbridge.exception.ErrorMessage;
import org.jh.batchbridge.exception.InvalidFileUploadException;
import org.jh.batchbridge.repository.ChunkRepository;
import org.jh.batchbridge.repository.JobRepository;
import org.jh.batchbridge.repository.ResultRepository;
import org.jh.batchbridge.service.adapter.BaseBatchAdapter;
import org.jh.batchbridge.service.parser.BatchFileParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
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
    private ChunkRepository chunkRepository;

    @Mock
    private TokenEstimator tokenEstimator;

    @Mock
    private BaseBatchAdapter adapter;

    private BatchJobService batchJobService;

    @BeforeEach
    void setUp() {
        batchJobService = new BatchJobService(List.of(csvParser), jobRepository, resultRepository, chunkRepository, tokenEstimator, List.of(adapter));
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
    @DisplayName("혼합 배치: claude, gemini, grok 행이 각각 올바른 그룹으로 분류된다")
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

    // ── resolveProvider 테스트 ──────────────────────────────────────────────

    @Test
    @DisplayName("claude 모델은 provider가 anthropic이다")
    void resolveProviderForClaude() {
        assertThat(batchJobService.resolveProvider("claude-3-5-sonnet")).isEqualTo("anthropic");
    }

    @Test
    @DisplayName("gemini 모델은 provider가 google이다")
    void resolveProviderForGemini() {
        assertThat(batchJobService.resolveProvider("gemini-pro")).isEqualTo("google");
    }

    @Test
    @DisplayName("grok 모델은 provider가 xai이다")
    void resolveProviderForGrok() {
        assertThat(batchJobService.resolveProvider("grok-2")).isEqualTo("xai");
    }

    @Test
    @DisplayName("알 수 없는 모델은 provider가 unknown이다")
    void resolveProviderForUnknown() {
        assertThat(batchJobService.resolveProvider("unknown-model")).isEqualTo("unknown");
        assertThat(batchJobService.resolveProvider(null)).isEqualTo("unknown");
    }

    @Test
    @DisplayName("submitJob()은 PENDING 상태인 Job의 Chunk를 제출하고 상태를 업데이트한다")
    void submitJobSuccess() {
        // ... (이전과 동일한 내용은 생략하고 전체 메서드를 유지하기 위해 그대로 작성)
        Long jobId = 1L;
        Job job = Job.builder().id(jobId).status(Job.JobStatus.PENDING).build();
        Chunk chunk = Chunk.builder().id(10L).job(job).provider("anthropic").model("claude-3").status(Chunk.ChunkStatus.CREATED).build();
        Result result = Result.builder().id(100L).chunk(chunk).job(job).rowIdentifier("row-1").prompt("hello").build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(chunkRepository.findByJobId(jobId)).thenReturn(List.of(chunk));
        when(resultRepository.findByChunkId(chunk.getId())).thenReturn(List.of(result));
        when(adapter.getProvider()).thenReturn("anthropic");
        when(adapter.submitBatch(anyList(), anyString())).thenReturn("ext-batch-123");

        // When
        batchJobService.submitJob(jobId);

        // Then
        assertThat(chunk.getExternalBatchId()).isEqualTo("ext-batch-123");
        assertThat(chunk.getStatus()).isEqualTo(Chunk.ChunkStatus.SUBMITTED);
        assertThat(job.getStatus()).isEqualTo(Job.JobStatus.PROCESSING);
        verify(chunkRepository).save(chunk);
        verify(jobRepository).save(job);
    }

    @Test
    @DisplayName("syncUnfinishedJobs()는 SUBMITTED 상태인 청크의 상태를 조회하고 완료 시 결과를 저장한다")
    void syncUnfinishedJobsSuccess() {
        // Given
        Job job = Job.builder().id(1L).status(Job.JobStatus.PROCESSING).completedRows(0).failedRows(0).build();
        Chunk chunk = Chunk.builder()
                .id(10L)
                .job(job)
                .provider("anthropic")
                .externalBatchId("ext-id-123")
                .status(Chunk.ChunkStatus.SUBMITTED)
                .build();
        Result result = Result.builder().id(100L).chunk(chunk).job(job).rowIdentifier("row-1").status(Result.ResultStatus.PENDING).build();

        when(chunkRepository.findByStatus(Chunk.ChunkStatus.SUBMITTED)).thenReturn(List.of(chunk));
        when(adapter.getProvider()).thenReturn("anthropic");
        when(adapter.checkStatus("ext-id-123")).thenReturn(BaseBatchAdapter.BatchStatus.COMPLETED);
        when(adapter.collectResults("ext-id-123")).thenReturn(List.of(
                BaseBatchAdapter.BatchResultItem.success("row-1", "response text", 10, 20)
        ));
        when(resultRepository.findByChunkIdAndRowIdentifier(10L, "row-1")).thenReturn(Optional.of(result));
        when(chunkRepository.findByJobId(1L)).thenReturn(List.of(chunk));

        // When
        batchJobService.syncUnfinishedJobs();

        // Then
        assertThat(chunk.getStatus()).isEqualTo(Chunk.ChunkStatus.COMPLETED);
        assertThat(result.getStatus()).isEqualTo(Result.ResultStatus.SUCCESS);
        assertThat(result.getResultText()).isEqualTo("response text");
        assertThat(job.getCompletedRows()).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(Job.JobStatus.COMPLETED);

        verify(resultRepository).save(result);
        verify(chunkRepository).save(chunk);
        verify(jobRepository).save(job);
    }

    @Test
    @DisplayName("syncUnfinishedJobs()는 외부 배치 상태가 FAILED이면 청크 상태를 FAILED로 변경한다")
    void syncUnfinishedJobsFailed() {
        // Given
        Job job = Job.builder().id(1L).status(Job.JobStatus.PROCESSING).build();
        Chunk chunk = Chunk.builder()
                .id(10L)
                .job(job)
                .provider("anthropic")
                .externalBatchId("ext-id-failed")
                .status(Chunk.ChunkStatus.SUBMITTED)
                .build();

        when(chunkRepository.findByStatus(Chunk.ChunkStatus.SUBMITTED)).thenReturn(List.of(chunk));
        when(adapter.getProvider()).thenReturn("anthropic");
        when(adapter.checkStatus("ext-id-failed")).thenReturn(BaseBatchAdapter.BatchStatus.FAILED);
        when(chunkRepository.findByJobId(1L)).thenReturn(List.of(chunk));

        // When
        batchJobService.syncUnfinishedJobs();

        // Then
        assertThat(chunk.getStatus()).isEqualTo(Chunk.ChunkStatus.FAILED);
        assertThat(job.getStatus()).isEqualTo(Job.JobStatus.FAILED);
        verify(chunkRepository).save(chunk);
        verify(jobRepository).save(job);
    }

    @Test
    @DisplayName("Job ID로 결과를 조회하면 병합된 결과 리스트가 반환된다")
    void getMergedResultsSuccess() {
        // given
        Long jobId = 1L;
        Job job = Job.builder().id(jobId).name("test-job").build();
        Chunk chunk = Chunk.builder().id(10L).job(job).model("claude-3-5-sonnet").build();
        Result result = Result.builder()
                .rowIdentifier("row-1")
                .prompt("test prompt")
                .chunk(chunk)
                .resultText("result text")
                .status(Result.ResultStatus.SUCCESS)
                .inputTokens(10)
                .outputTokens(20)
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(resultRepository.findByJobId(jobId)).thenReturn(List.of(result));

        // when
        List<MergedResultDto> mergedResults = batchJobService.getMergedResults(jobId);

        // then
        assertThat(mergedResults).hasSize(1);
        MergedResultDto dto = mergedResults.get(0);
        assertThat(dto.getId()).isEqualTo("row-1");
        assertThat(dto.getPrompt()).isEqualTo("test prompt");
        assertThat(dto.getModel()).isEqualTo("claude-3-5-sonnet");
        assertThat(dto.getResultText()).isEqualTo("result text");
        assertThat(dto.getStatus()).isEqualTo("SUCCESS");
        assertThat(dto.getInputTokens()).isEqualTo(10);
        assertThat(dto.getOutputTokens()).isEqualTo(20);
    }

    @Test
    @DisplayName("실패한 행만 CSV로 내보내면 FAIL 상태인 데이터만 포함된다")
    void exportFailedRowsToCsvSuccess() throws Exception {
        // given
        Long jobId = 1L;
        Job job = Job.builder().id(jobId).name("test-job").build();
        Chunk chunk = Chunk.builder().id(10L).job(job).model("claude-3-5-sonnet").build();
        Result successResult = Result.builder()
                .rowIdentifier("row-success")
                .prompt("success prompt")
                .chunk(chunk)
                .resultText("result text")
                .status(Result.ResultStatus.SUCCESS)
                .build();
        Result failResult = Result.builder()
                .rowIdentifier("row-fail")
                .prompt("fail prompt")
                .chunk(chunk)
                .resultText(null)
                .status(Result.ResultStatus.FAIL)
                .errorMessage("some error")
                .build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(resultRepository.findByJobId(jobId)).thenReturn(List.of(successResult, failResult));

        // when
        byte[] csvBytes = batchJobService.exportFailedRowsToCsv(jobId);
        String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        // then
        String[] lines = csvContent.split("\n");
        assertThat(lines).hasSize(2); // Header + 1 Fail Row
        assertThat(lines[0]).contains("id", "prompt", "result", "model", "input_tokens", "output_tokens", "status");
        assertThat(lines[1]).contains("row-fail", "fail prompt", "claude-3-5-sonnet", "FAIL");
        assertThat(csvContent).doesNotContain("row-success");
    }
}
