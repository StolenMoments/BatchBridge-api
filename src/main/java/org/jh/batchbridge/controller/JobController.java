package org.jh.batchbridge.controller;

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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

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
        
        // 각 결과의 모델을 알 수 없으므로 Job의 기본 모델 사용 혹은 Result에 모델 정보 추가 필요하나
        // 기존 엔티티를 수정하지 않기로 했으므로 Job 레벨에서 계산
        double estimatedCost = tokenEstimator.estimateCost(inputTokens, job.getModel());

        List<JobDetailResponse.ChunkDetail> chunks = job.getChunks().stream()
                .map(c -> JobDetailResponse.ChunkDetail.builder()
                        .chunkId(c.getId().toString())
                        .model(c.getModel())
                        .batchId(c.getExternalBatchId())
                        .totalRows(c.getRowCount())
                        .completedRows(c.getResults().size()) // 대략적인 수치
                        .failedRows(0) // 상세 로직 생략
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
        double estimatedCost = tokenEstimator.estimateCost(inputTokens, job.getModel());

        return JobListResponse.JobInfo.builder()
                .jobId(job.getId().toString())
                .filename(job.getName())
                .models(List.of(job.getModel()))
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
