package org.jh.batchbridge.controller;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.domain.Job;
import org.jh.batchbridge.domain.Result;
import org.jh.batchbridge.dto.api.ApiResponse;
import org.jh.batchbridge.dto.api.JobDetailResponse;
import org.jh.batchbridge.dto.api.JobListResponse;
import org.jh.batchbridge.repository.JobRepository;
import org.jh.batchbridge.repository.ResultRepository;
import org.jh.batchbridge.service.TokenEstimator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobRepository jobRepository;
    private final ResultRepository resultRepository;
    private final TokenEstimator tokenEstimator;

    @GetMapping
    public ApiResponse<JobListResponse> getJobs(
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        // 필터링 로직은 생략하거나 구현 가능하나, 여기서는 단순 페이징만 적용
        Page<Job> jobPage = jobRepository.findAll(PageRequest.of(page, size));

        List<JobListResponse.JobInfo> jobs = jobPage.getContent().stream()
                .map(this::convertToJobInfo)
                .collect(Collectors.toList());

        JobListResponse response = JobListResponse.builder()
                .total(jobPage.getTotalElements())
                .page(page)
                .size(size)
                .jobs(jobs)
                .build();

        return ApiResponse.success(response);
    }

    @GetMapping("/{jobId}")
    public ApiResponse<JobDetailResponse> getJob(@PathVariable("jobId") Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();

        List<Result> results = resultRepository.findByJobId(jobId);
        int inputTokens = results.stream().mapToInt(Result::getInputTokens).sum();
        int outputTokens = results.stream().mapToInt(Result::getOutputTokens).sum();

        // 모델별 비용 합산
        double estimatedCost = results.stream()
                .collect(Collectors.groupingBy(Result::getModel, Collectors.summingInt(Result::getInputTokens)))
                .entrySet().stream()
                .mapToDouble(e -> tokenEstimator.estimateCost(e.getValue(), e.getKey()))
                .sum();

        List<JobDetailResponse.ChunkDetail> chunks = job.getChunks().stream()
                .map(c -> JobDetailResponse.ChunkDetail.builder()
                        .chunkId(c.getId().toString())
                        .model(c.getModel())
                        .batchId(c.getExternalBatchId())
                        .totalRows(c.getRowCount())
                        .completedRows((int) c.getResults().stream().filter(r -> r.getStatus() == Result.ResultStatus.SUCCESS).count())
                        .failedRows((int) c.getResults().stream().filter(r -> r.getStatus() == Result.ResultStatus.FAIL).count())
                        .status(c.getStatus().name().toLowerCase())
                        .build())
                .collect(Collectors.toList());

        JobDetailResponse response = JobDetailResponse.builder()
                .jobId(job.getId().toString())
                .filename(job.getName())
                .status(job.getStatus().name().toLowerCase())
                .chunks(chunks)
                .submittedAt(job.getCreatedAt())
                .completedAt(job.getUpdatedAt())
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .estimatedCost(estimatedCost)
                .build();

        return ApiResponse.success(response);
    }

    @DeleteMapping("/{jobId}")
    public ApiResponse<java.util.Map<String, String>> deleteJob(@PathVariable("jobId") Long jobId) {
        jobRepository.deleteById(jobId);
        return ApiResponse.success(java.util.Map.of("deleted", jobId.toString()));
    }

    private JobListResponse.JobInfo convertToJobInfo(Job job) {
        List<Result> results = resultRepository.findByJobId(job.getId());
        int inputTokens = results.stream().mapToInt(Result::getInputTokens).sum();
        int outputTokens = results.stream().mapToInt(Result::getOutputTokens).sum();

        // 모델별 비용 합산
        double estimatedCost = results.stream()
                .collect(Collectors.groupingBy(Result::getModel, Collectors.summingInt(Result::getInputTokens)))
                .entrySet().stream()
                .mapToDouble(e -> tokenEstimator.estimateCost(e.getValue(), e.getKey()))
                .sum();

        List<String> models = results.stream()
                .map(Result::getModel)
                .distinct()
                .collect(Collectors.toList());

        if (models.isEmpty()) {
            models = List.of(job.getModel());
        }

        return JobListResponse.JobInfo.builder()
                .jobId(job.getId().toString())
                .filename(job.getName())
                .models(models)
                .totalRows(job.getTotalRows())
                .completedRows(job.getCompletedRows())
                .status(job.getStatus().name().toLowerCase())
                .submittedAt(job.getCreatedAt())
                .completedAt(job.getUpdatedAt())
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .estimatedCost(estimatedCost)
                .build();
    }
}
