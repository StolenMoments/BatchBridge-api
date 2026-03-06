package org.jh.batchbridge.service.converter;

import lombok.RequiredArgsConstructor;
import org.jh.batchbridge.config.AiConfig;
import org.jh.batchbridge.dto.BatchRequest;
import org.jh.batchbridge.dto.BatchRequest.Provider;
import org.jh.batchbridge.dto.BatchRowDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BatchRequestConverter {

    private final AiConfig aiConfig;

    public BatchRequest convert(BatchRowDto row, String defaultModel) {
        String model = resolveModel(row.getModel(), defaultModel);
        Provider provider = Provider.fromModel(model);
        String resolvedModel = resolveProviderDefaultModel(model, provider);

        return BatchRequest.builder()
                .id(row.getId())
                .prompt(row.getPrompt())
                .systemPrompt(row.getSystemPrompt())
                .model(resolvedModel)
                .provider(provider)
                .temperature(row.getTemperature())
                .maxTokens(row.getMaxTokens())
                .build();
    }

    public List<BatchRequest> convertAll(List<BatchRowDto> rows, String defaultModel) {
        return rows.stream()
                .map(row -> convert(row, defaultModel))
                .collect(Collectors.toList());
    }

    private String resolveModel(String rowModel, String defaultModel) {
        if (rowModel != null && !rowModel.isBlank()) {
            return rowModel.trim();
        }
        if (defaultModel != null && !defaultModel.isBlank()) {
            return defaultModel.trim();
        }
        return null;
    }

    private String resolveProviderDefaultModel(String model, Provider provider) {
        if (model != null && !model.isBlank()) {
            return model;
        }
        if (provider == null || aiConfig.getProviders() == null) {
            return null;
        }
        String providerKey = provider.name().toLowerCase();
        AiConfig.ProviderConfig config = aiConfig.getProviders().get(providerKey);
        return config != null ? config.getModel() : null;
    }
}
