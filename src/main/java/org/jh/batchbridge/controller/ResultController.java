package org.jh.batchbridge.controller;

import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.domain.Job;
import org.jh.batchbridge.domain.Result;
import org.jh.batchbridge.dto.api.ApiResponse;
import org.jh.batchbridge.dto.api.ReportResponse;
import org.jh.batchbridge.repository.JobRepository;
import org.jh.batchbridge.repository.ResultRepository;
import org.jh.batchbridge.service.BatchJobService;
import org.jh.batchbridge.service.TokenEstimator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/results")
@RequiredArgsConstructor
public class ResultController {

    private final BatchJobService batchJobService;
    private final JobRepository jobRepository;
    private final ResultRepository resultRepository;
    private final TokenEstimator tokenEstimator;

    @GetMapping("/{jobId}")
    public ResponseEntity<byte[]> downloadResults(@PathVariable("jobId") Long jobId) {
        byte[] csv = batchJobService.exportResultsToCsv(jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"result_" + jobId + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @GetMapping("/{jobId}/failed")
    public ResponseEntity<byte[]> downloadFailedResults(@PathVariable("jobId") Long jobId) {
        byte[] csv = batchJobService.exportFailedRowsToCsv(jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"failed_" + jobId + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @GetMapping("/{jobId}/report")
    public ApiResponse<ReportResponse> getReport(@PathVariable("jobId") Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        List<Result> results = resultRepository.findByJobId(jobId);
        
        int totalInputTokens = results.stream().mapToInt(Result::getInputTokens).sum();
        int totalOutputTokens = results.stream().mapToInt(Result::getOutputTokens).sum();
        double totalCost = tokenEstimator.estimateCost(totalInputTokens, job.getModel());

        ReportResponse.Summary summary = ReportResponse.Summary.builder()
                .totalRows(job.getTotalRows())
                .successRows(job.getCompletedRows())
                .failedRows(job.getFailedRows())
                .totalInputTokens(totalInputTokens)
                .totalOutputTokens(totalOutputTokens)
                .totalCost(totalCost)
                .build();

        // 모델별 집계 (Job 엔티티 구조상 단일 모델일 가능성이 높으나, Chunk별로 나뉠 수 있음)
        Map<String, List<Result>> byModel = results.stream()
                .collect(Collectors.groupingBy(r -> r.getChunk().getModel()));

        List<ReportResponse.ModelReport> modelReports = byModel.entrySet().stream()
                .map(entry -> {
                    String model = entry.getKey();
                    List<Result> modelResults = entry.getValue();
                    int input = modelResults.stream().mapToInt(Result::getInputTokens).sum();
                    int output = modelResults.stream().mapToInt(Result::getOutputTokens).sum();
                    return ReportResponse.ModelReport.builder()
                            .model(model)
                            .rows(modelResults.size())
                            .inputTokens(input)
                            .outputTokens(output)
                            .cost(tokenEstimator.estimateCost(input, model))
                            .build();
                })
                .collect(Collectors.toList());

        ReportResponse response = ReportResponse.builder()
                .jobId(jobId.toString())
                .summary(summary)
                .byModel(modelReports)
                .build();

        return ApiResponse.success(response);
    }
}
