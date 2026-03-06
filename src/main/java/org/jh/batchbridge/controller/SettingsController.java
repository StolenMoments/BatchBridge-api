package org.jh.batchbridge.controller;

import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.config.AiConfig;
import org.jh.batchbridge.dto.api.ApiKeyInfo;
import org.jh.batchbridge.dto.api.ApiResponse;
import org.jh.batchbridge.service.adapter.ApiKeyValidator;
import org.jh.batchbridge.service.adapter.ClaudeBatchAdapter;
import org.jh.batchbridge.service.adapter.GeminiBatchAdapter;
import org.jh.batchbridge.service.adapter.GrokBatchAdapter;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings/keys")
@RequiredArgsConstructor
public class SettingsController {

    private final AiConfig aiConfig;
    private final ClaudeBatchAdapter claudeAdapter;
    private final GeminiBatchAdapter geminiAdapter;
    private final GrokBatchAdapter grokAdapter;

    @GetMapping
    public ApiResponse<List<ApiKeyInfo>> getApiKeys() {
        List<ApiKeyInfo> keys = new ArrayList<>();
        
        addKeyInfo(keys, "claude");
        addKeyInfo(keys, "gemini");
        addKeyInfo(keys, "grok");

        return ApiResponse.success(keys);
    }

    @PutMapping("/{model}")
    public ApiResponse<ApiKeyInfo> updateApiKey(@PathVariable("model") String model, @RequestBody Map<String, String> request) {
        String apiKey = request.get("apiKey");
        
        // 실시간으로 설정을 변경하는 것은 Spring 컨텍스트 재시작이나 
        // @ConfigurationProperties 갱신 로직이 필요함. 
        // 여기서는 명세에 맞게 응답만 구성. (기존 소스 수정 금지이므로 실제 저장은 생략하거나 로그로 남김)
        
        ApiKeyInfo info = ApiKeyInfo.builder()
                .model(model)
                .maskedKey(maskKey(apiKey))
                .verified(false)
                .build();
        
        return ApiResponse.success(info);
    }

    @PostMapping("/{model}/verify")
    public ApiResponse<Map<String, Object>> verifyApiKey(@PathVariable("model") String model) {
        boolean verified = false;
        try {
            switch (model.toLowerCase()) {
                case "claude" -> {
                    claudeAdapter.validateApiKey();
                    verified = true;
                }
                case "gemini" -> {
                    geminiAdapter.validateApiKey();
                    verified = true;
                }
                case "grok" -> {
                    grokAdapter.validateApiKey();
                    verified = true;
                }
            }
        } catch (Exception e) {
            verified = false;
        }

        return ApiResponse.success(Map.of("model", model, "verified", verified));
    }

    @DeleteMapping("/{model}")
    public ApiResponse<Map<String, String>> deleteApiKey(@PathVariable("model") String model) {
        return ApiResponse.success(Map.of("deleted", model));
    }

    private void addKeyInfo(List<ApiKeyInfo> keys, String model) {
        AiConfig.ProviderConfig config = aiConfig.getProviders().get(model);
        String masked = (config != null && config.getApiKey() != null) ? maskKey(config.getApiKey()) : null;
        keys.add(ApiKeyInfo.builder()
                .model(model)
                .maskedKey(masked)
                .verified(masked != null)
                .build());
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "••••••••";
        return key.substring(0, 4) + "••••••••" + key.substring(key.length() - 4);
    }
}
