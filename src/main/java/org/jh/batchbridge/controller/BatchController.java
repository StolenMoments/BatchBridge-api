package org.jh.batchbridge.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.domain.Job;
import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.dto.api.ApiResponse;
import org.jh.batchbridge.dto.api.BatchSubmitResponse;
import org.jh.batchbridge.dto.api.UploadResponse;
import org.jh.batchbridge.repository.JobRepository;
import org.jh.batchbridge.service.BatchJobService;
import org.jh.batchbridge.service.TokenEstimator;
import org.jh.batchbridge.service.parser.BatchFileParser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BatchController {

    private final BatchJobService batchJobService;
    private final TokenEstimator tokenEstimator;
    private final List<BatchFileParser> parsers;
    private final JobRepository jobRepository;

    @PostMapping("/upload")
    public ApiResponse<UploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("defaultModel") String defaultModel,
            @RequestParam(value = "systemPrompt", required = false) String systemPrompt) throws IOException {

        BatchFileParser parser = parsers.stream()
                .filter(p -> p.supports(file.getOriginalFilename()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported file format"));

        List<BatchRowDto> rows = parser.parse(file.getInputStream());

        // 업로드 시점에는 DB에 저장하지 않고 미리보기 정보만 반환하라고 되어 있으나,
        // /batches 에서 uploadId를 사용하므로 임시 저장이 필요함.
        // 여기서는 BatchJobService.createJobFromFile 를 사용하여 Job을 생성(PENDING 상태)하고 그 ID를 uploadId로 사용함.
        Job job = batchJobService.createJobFromFile(file.getOriginalFilename(), file.getInputStream(), defaultModel);

        Map<String, List<BatchRowDto>> grouped = batchJobService.groupRowsByModel(rows, defaultModel);

        Map<String, Integer> estimatedTokens = new HashMap<>();
        Map<String, Double> estimatedCosts = new HashMap<>();
        double totalCost = 0;

        for (Map.Entry<String, List<BatchRowDto>> entry : grouped.entrySet()) {
            String model = entry.getKey();
            int tokens = tokenEstimator.estimateTotalTokens(entry.getValue());
            double cost = tokenEstimator.estimateCost(tokens, model);
            estimatedTokens.put(model, tokens);
            estimatedCosts.put(model, cost);
            totalCost += cost;
        }
        estimatedCosts.put("total", totalCost);

        UploadResponse response = UploadResponse.builder()
                .uploadId(job.getId().toString())
                .filename(file.getOriginalFilename())
                .totalRows(rows.size())
                .columns(Arrays.asList("custom_id", "prompt", "model", "system_prompt"))
                .preview(rows.stream().limit(5).collect(Collectors.toList()))
                .estimatedTokens(estimatedTokens)
                .estimatedCost(estimatedCosts)
                .build();

        return ApiResponse.success(response);
    }

    @PostMapping("/batches")
    public ApiResponse<BatchSubmitResponse> submitBatch(@RequestBody Map<String, String> request) {
        Long jobId = Long.parseLong(request.get("uploadId"));
        // API 명세에는 defaultModel, systemPrompt도 있으나 이미 업로드 시점에 설정됨. 
        // 필요하다면 여기서 다시 업데이트 로직이 필요할 수 있으나 기존 BatchJobService는 submitJob(jobId)만 지원함.
        
        batchJobService.submitJob(jobId);
        
        Job job = jobRepository.findById(jobId).orElseThrow();
        
        List<BatchSubmitResponse.ChunkInfo> chunks = job.getChunks().stream()
                .map(c -> BatchSubmitResponse.ChunkInfo.builder()
                        .chunkId(c.getId().toString())
                        .model(c.getModel())
                        .rows(c.getRowCount())
                        .batchId(c.getExternalBatchId())
                        .build())
                .collect(Collectors.toList());

        BatchSubmitResponse response = BatchSubmitResponse.builder()
                .jobId(job.getId().toString())
                .status(job.getStatus().name().toLowerCase())
                .chunks(chunks)
                .submittedAt(job.getCreatedAt())
                .build();

        return ApiResponse.success(response);
    }

    @PostMapping("/batches/refresh")
    public ApiResponse<Map<String, Object>> refreshBatches() {
        batchJobService.syncUnfinishedJobs();
        
        // 갱신된 내역을 반환해야 함. 기존 syncUnfinishedJobs는 반환값이 없으므로 
        // 여기서는 간단히 성공 메시지만 혹은 최근 상태를 조회하여 반환. 
        // 명세에 맞추기 위해 모든 Job 목록을 조회하여 반환하는 식으로 구성.
        List<Job> jobs = jobRepository.findAll();
        List<Map<String, String>> jobStatuses = jobs.stream()
                .map(j -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("jobId", j.getId().toString());
                    map.put("status", j.getStatus().name().toLowerCase());
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("updated", jobStatuses.size());
        data.put("jobs", jobStatuses);
        
        return ApiResponse.success(data);
    }
}
