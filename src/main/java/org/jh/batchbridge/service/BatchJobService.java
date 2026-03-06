package org.jh.batchbridge.service;

import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.domain.Job;
import org.jh.batchbridge.domain.Result;
import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.repository.JobRepository;
import org.jh.batchbridge.repository.ResultRepository;
import org.jh.batchbridge.service.parser.BatchFileParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BatchJobService {

    private final List<BatchFileParser> parsers;
    private final JobRepository jobRepository;
    private final ResultRepository resultRepository;

    @Transactional
    public Job createJobFromFile(String fileName, InputStream inputStream, String defaultModel) {
        BatchFileParser parser = parsers.stream()
                .filter(p -> p.supports(fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported file format: " + fileName));

        List<BatchRowDto> rows = parser.parse(inputStream);

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
