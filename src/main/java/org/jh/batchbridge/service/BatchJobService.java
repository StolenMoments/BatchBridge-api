package org.jh.batchbridge.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchJobService {

    private final List<BatchFileParser> parsers;
    private final JobRepository jobRepository;
    private final ResultRepository resultRepository;
    private final ChunkRepository chunkRepository;
    private final TokenEstimator tokenEstimator;
    private final List<BaseBatchAdapter> adapters;

    @Transactional
    public Job createJobFromUpload(MultipartFile file, String defaultModel) {
        validateUploadedFile(file);
        String fileName = file.getOriginalFilename();
        try (InputStream inputStream = file.getInputStream()) {
            return createJobFromFile(fileName, inputStream, defaultModel);
        } catch (IOException e) {
            throw new InvalidFileUploadException(ErrorMessage.FILE_EMPTY, e);
        }
    }

    private void validateUploadedFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileUploadException(ErrorMessage.FILE_EMPTY);
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidFileUploadException(ErrorMessage.FILE_NAME_MISSING);
        }
        boolean supported = parsers.stream().anyMatch(p -> p.supports(fileName));
        if (!supported) {
            throw new InvalidFileUploadException(ErrorMessage.FILE_FORMAT_UNSUPPORTED);
        }
    }

    @Transactional
    public Job createJobFromFile(String fileName, InputStream inputStream, String defaultModel) {
        BatchFileParser parser = parsers.stream()
                .filter(p -> p.supports(fileName))
                .findFirst()
                .orElseThrow(() -> new InvalidFileUploadException(ErrorMessage.FILE_FORMAT_UNSUPPORTED));

        List<BatchRowDto> rows = parser.parse(inputStream);

        int totalTokens = tokenEstimator.estimateTotalTokens(rows);
        double estimatedCost = tokenEstimator.estimateCost(totalTokens, defaultModel);
        log.info("[TokenEstimate] file={}, rows={}, estimatedInputTokens={}, model={}, estimatedCost=${}",
                fileName, rows.size(), totalTokens, defaultModel, String.format("%.6f", estimatedCost));

        Job job = Job.builder()
                .name(fileName)
                .status(Job.JobStatus.PENDING)
                .model(defaultModel)
                .totalRows(rows.size())
                .completedRows(0)
                .failedRows(0)
                .build();

        Job savedJob = jobRepository.save(job);

        Map<String, List<BatchRowDto>> rowsByModel = groupRowsByModel(rows, defaultModel);
        rowsByModel.forEach((model, modelRows) -> {
            int modelTokens = tokenEstimator.estimateTotalTokens(modelRows);
            double modelCost = tokenEstimator.estimateCost(modelTokens, model);
            int batchLimit = tokenEstimator.resolveBatchLimit(model);
            List<List<BatchRowDto>> chunks = tokenEstimator.splitIntoChunks(modelRows, model);
            log.info("[ModelGroup] model={}, rows={}, estimatedInputTokens={}, estimatedCost=${}, batchLimit={}, chunks={}",
                    model, modelRows.size(), modelTokens, String.format("%.6f", modelCost), batchLimit, chunks.size());
            saveChunks(savedJob, model, chunks);
        });

        return savedJob;
    }

    /**
     * 청크 목록을 Chunk 엔티티로 저장하고, 각 행을 Result로 저장한다.
     */
    private void saveChunks(Job job, String model, List<List<BatchRowDto>> chunkGroups) {
        String provider = resolveProvider(model);
        for (int i = 0; i < chunkGroups.size(); i++) {
            List<BatchRowDto> chunkRows = chunkGroups.get(i);

            Chunk chunk = Chunk.builder()
                    .job(job)
                    .provider(provider)
                    .model(model)
                    .status(Chunk.ChunkStatus.CREATED)
                    .rowCount(chunkRows.size())
                    .build();

            Chunk savedChunk = chunkRepository.save(chunk);

            log.info("[ChunkCreated] jobId={}, chunkId={}, model={}, chunkIndex={}/{}, rows={}",
                    job.getId(), savedChunk.getId(), model, i + 1, chunkGroups.size(), chunkRows.size());

            List<Result> results = chunkRows.stream()
                    .map(row -> Result.builder()
                            .job(job)
                            .chunk(savedChunk)
                            .rowIdentifier(row.getId())
                            .prompt(row.getPrompt())
                            .model(model)
                            .status(Result.ResultStatus.PENDING)
                            .inputTokens(0)
                            .outputTokens(0)
                            .build())
                    .collect(Collectors.toList());

            resultRepository.saveAll(results);
        }
    }

    /**
     * 특정 작업을 실행(API 제출)한다.
     */
    @Transactional
    public void submitJob(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        if (job.getStatus() != Job.JobStatus.PENDING) {
            log.warn("[SubmitJob] Job is not in PENDING status. jobId={}, status={}", jobId, job.getStatus());
            return;
        }

        List<Chunk> chunks = chunkRepository.findByJobId(jobId);
        boolean allSubmitted = true;

        for (Chunk chunk : chunks) {
            if (chunk.getStatus() != Chunk.ChunkStatus.CREATED) continue;

            try {
                submitChunk(chunk);
            } catch (Exception e) {
                log.error("[SubmitChunk] Failed to submit chunk. chunkId={}, error={}", chunk.getId(), e.getMessage());
                chunk.setStatus(Chunk.ChunkStatus.FAILED);
                allSubmitted = false;
            }
        }

        job.setStatus(allSubmitted ? Job.JobStatus.PROCESSING : Job.JobStatus.FAILED);
        jobRepository.save(job);
    }

    private void submitChunk(Chunk chunk) {
        BaseBatchAdapter adapter = adapters.stream()
                .filter(a -> a.getProvider().equalsIgnoreCase(chunk.getProvider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No adapter found for provider: " + chunk.getProvider()));

        List<Result> results = resultRepository.findByChunkId(chunk.getId());
        List<BatchRowDto> rows = results.stream()
                .map(r -> BatchRowDto.builder()
                        .id(r.getRowIdentifier())
                        .prompt(r.getPrompt())
                        .model(chunk.getModel())
                        .build())
                .collect(Collectors.toList());

        String externalBatchId = adapter.submitBatch(rows, chunk.getModel());

        chunk.setExternalBatchId(externalBatchId);
        chunk.setStatus(Chunk.ChunkStatus.SUBMITTED);
        chunkRepository.save(chunk);

        log.info("[ChunkSubmitted] jobId={}, chunkId={}, externalBatchId={}",
                chunk.getJob().getId(), chunk.getId(), externalBatchId);
    }

    /**
     * 앱 시작 시 미완료된(SUBMITTED 상태) 청크들의 상태를 동기화한다.
     */
    @Transactional
    public void syncUnfinishedJobs() {
        log.info("[SyncUnfinishedJobs] Starting synchronization of unfinished chunks...");
        List<Chunk> submittedChunks = chunkRepository.findByStatus(Chunk.ChunkStatus.SUBMITTED);

        if (submittedChunks.isEmpty()) {
            log.info("[SyncUnfinishedJobs] No unfinished chunks found.");
            return;
        }

        for (Chunk chunk : submittedChunks) {
            try {
                syncChunkStatus(chunk);
            } catch (Exception e) {
                log.error("[SyncUnfinishedJobs] Failed to sync chunk status. chunkId={}, error={}", chunk.getId(), e.getMessage());
            }
        }
        log.info("[SyncUnfinishedJobs] Synchronization completed.");
    }

    private void syncChunkStatus(Chunk chunk) {
        BaseBatchAdapter adapter = adapters.stream()
                .filter(a -> a.getProvider().equalsIgnoreCase(chunk.getProvider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No adapter found for provider: " + chunk.getProvider()));

        BaseBatchAdapter.BatchStatus status = adapter.checkStatus(chunk.getExternalBatchId());
        log.info("[SyncChunkStatus] chunkId={}, externalId={}, status={}", chunk.getId(), chunk.getExternalBatchId(), status);

        if (status == BaseBatchAdapter.BatchStatus.COMPLETED) {
            processCompletedChunk(chunk, adapter);
        } else if (status == BaseBatchAdapter.BatchStatus.FAILED || status == BaseBatchAdapter.BatchStatus.CANCELLED) {
            chunk.setStatus(Chunk.ChunkStatus.FAILED);
            chunkRepository.save(chunk);
            updateJobStatusAfterChunkUpdate(chunk.getJob());
        }
    }

    private void processCompletedChunk(Chunk chunk, BaseBatchAdapter adapter) {
        List<BaseBatchAdapter.BatchResultItem> results = adapter.collectResults(chunk.getExternalBatchId());

        int completedCount = 0;
        int failedCount = 0;

        for (BaseBatchAdapter.BatchResultItem item : results) {
            Result result = resultRepository.findByChunkIdAndRowIdentifier(chunk.getId(), item.rowId())
                    .orElse(null);

            if (result == null) {
                log.warn("[ProcessCompletedChunk] Result record not found. chunkId={}, rowId={}", chunk.getId(), item.rowId());
                continue;
            }

            if (item.success()) {
                result.setResultText(item.resultText());
                result.setStatus(Result.ResultStatus.SUCCESS);
                result.setInputTokens(item.inputTokens());
                result.setOutputTokens(item.outputTokens());
                completedCount++;
            } else {
                result.setErrorMessage(item.errorMessage());
                result.setStatus(Result.ResultStatus.FAIL);
                failedCount++;
            }
            resultRepository.save(result);
        }

        chunk.setStatus(Chunk.ChunkStatus.COMPLETED);
        chunkRepository.save(chunk);

        Job job = chunk.getJob();
        job.setCompletedRows(job.getCompletedRows() + completedCount);
        job.setFailedRows(job.getFailedRows() + failedCount);
        updateJobStatusAfterChunkUpdate(job);

        log.info("[ChunkCompleted] chunkId={}, completed={}, failed={}", chunk.getId(), completedCount, failedCount);
    }

    private void updateJobStatusAfterChunkUpdate(Job job) {
        List<Chunk> chunks = chunkRepository.findByJobId(job.getId());
        boolean allFinished = chunks.stream()
                .allMatch(c -> c.getStatus() == Chunk.ChunkStatus.COMPLETED || c.getStatus() == Chunk.ChunkStatus.FAILED);

        if (allFinished) {
            boolean anyFailed = chunks.stream().anyMatch(c -> c.getStatus() == Chunk.ChunkStatus.FAILED);
            job.setStatus(anyFailed ? Job.JobStatus.FAILED : Job.JobStatus.COMPLETED);
        } else {
            job.setStatus(Job.JobStatus.PROCESSING);
        }
        jobRepository.save(job);
    }

    /**
     * 모델명으로 provider(API 제공사)를 결정한다.
     */
    public String resolveProvider(String model) {
        if (model == null) return "unknown";
        String lower = model.toLowerCase();
        if (lower.startsWith("claude")) return "anthropic";
        if (lower.startsWith("gemini")) return "google";
        if (lower.startsWith("grok")) return "xai";
        return "unknown";
    }

    /**
     * 행 목록을 model 컬럼 기준으로 그룹화한다.
     * 행에 model이 없으면 defaultModel을 사용한다 (혼합 배치 지원).
     */
    public Map<String, List<BatchRowDto>> groupRowsByModel(List<BatchRowDto> rows, String defaultModel) {
        return rows.stream()
                .collect(Collectors.groupingBy(row ->
                        (row.getModel() != null && !row.getModel().isBlank())
                                ? row.getModel()
                                : defaultModel
                ));
    }

    /**
     * Job ID를 기반으로 모든 모델의 결과를 수집하고 원본 입력 정보와 병합한다.
     */
    @Transactional(readOnly = true)
    public List<MergedResultDto> getMergedResults(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        List<Result> results = resultRepository.findByJobId(jobId);

        return results.stream()
                .map(r -> MergedResultDto.builder()
                        .rowId(r.getId().toString())
                        .customId(r.getRowIdentifier())
                        .prompt(r.getPrompt())
                        .model(r.getModel())
                        .resultText(r.getResultText())
                        .status(r.getStatus().name())
                        .inputTokens(r.getInputTokens())
                        .outputTokens(r.getOutputTokens())
                        .errorMessage(r.getErrorMessage())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Job ID를 기반으로 모든 모델의 결과를 수집하고 CSV 형식의 byte[]로 변환한다.
     */
    @Transactional(readOnly = true)
    public byte[] exportResultsToCsv(Long jobId) {
        List<MergedResultDto> results = getMergedResults(jobId);
        return convertToCsv(results);
    }

    /**
     * Job ID를 기반으로 실패한 행만 수집하고 CSV 형식의 byte[]로 변환한다.
     */
    @Transactional(readOnly = true)
    public byte[] exportFailedRowsToCsv(Long jobId) {
        List<MergedResultDto> results = getMergedResults(jobId).stream()
                .filter(r -> "FAIL".equals(r.getStatus()))
                .collect(Collectors.toList());
        return convertToCsv(results);
    }

    private byte[] convertToCsv(List<MergedResultDto> results) {
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
             java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8);
             com.opencsv.CSVWriter csvWriter = new com.opencsv.CSVWriter(writer)) {

            // Header: row_id, custom_id, prompt, result, model, input_tokens, output_tokens, status
            csvWriter.writeNext(new String[]{"row_id", "custom_id", "prompt", "result", "model", "input_tokens", "output_tokens", "status"});

            for (MergedResultDto result : results) {
                csvWriter.writeNext(new String[]{
                        result.getRowId(),
                        result.getCustomId(),
                        result.getPrompt(),
                        result.getResultText(),
                        result.getModel(),
                        String.valueOf(result.getInputTokens() != null ? result.getInputTokens() : 0),
                        String.valueOf(result.getOutputTokens() != null ? result.getOutputTokens() : 0),
                        result.getStatus()
                });
            }
            csvWriter.flush();
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to generate CSV file", e);
        }
    }
}
