package org.jh.batchbridge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jh.batchbridge.domain.Job;
import org.jh.batchbridge.domain.Result;
import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.exception.ErrorMessage;
import org.jh.batchbridge.exception.InvalidFileUploadException;
import org.jh.batchbridge.repository.JobRepository;
import org.jh.batchbridge.repository.ResultRepository;
import org.jh.batchbridge.service.parser.BatchFileParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchJobService {

    private final List<BatchFileParser> parsers;
    private final JobRepository jobRepository;
    private final ResultRepository resultRepository;
    private final TokenEstimator tokenEstimator;

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

        List<Result> results = rows.stream()
                .map(row -> Result.builder()
                        .job(savedJob)
                        .rowIdentifier(row.getId())
                        .prompt(row.getPrompt())
                        .status(Result.ResultStatus.PENDING) // 초기 상태는 PENDING
                        .inputTokens(0)
                        .outputTokens(0)
                        .build())
                .collect(Collectors.toList());

        resultRepository.saveAll(results);

        return savedJob;
    }
}
