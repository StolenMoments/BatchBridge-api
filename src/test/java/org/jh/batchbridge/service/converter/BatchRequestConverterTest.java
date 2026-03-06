package org.jh.batchbridge.service.converter;

import org.jh.batchbridge.config.AiConfig;
import org.jh.batchbridge.dto.BatchRequest;
import org.jh.batchbridge.dto.BatchRequest.Provider;
import org.jh.batchbridge.dto.BatchRowDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BatchRequestConverterTest {

    private BatchRequestConverter converter;

    @BeforeEach
    void setUp() {
        AiConfig aiConfig = new AiConfig();
        AiConfig.ProviderConfig anthropic = new AiConfig.ProviderConfig();
        anthropic.setModel("claude-3-5-sonnet-latest");
        AiConfig.ProviderConfig google = new AiConfig.ProviderConfig();
        google.setModel("gemini-1.5-flash");
        AiConfig.ProviderConfig xai = new AiConfig.ProviderConfig();
        xai.setModel("grok-2-latest");
        aiConfig.setProviders(Map.of("anthropic", anthropic, "google", google, "xai", xai));
        converter = new BatchRequestConverter(aiConfig);
    }

    @Test
    @DisplayName("row에 model이 있으면 해당 model과 provider가 설정된다")
    void convert_withRowModel() {
        BatchRowDto row = BatchRowDto.builder()
                .id("1")
                .prompt("Hello")
                .model("claude-3-opus")
                .systemPrompt("You are helpful")
                .temperature(0.7)
                .maxTokens(1000)
                .build();

        BatchRequest result = converter.convert(row, null);

        assertThat(result.getId()).isEqualTo("1");
        assertThat(result.getPrompt()).isEqualTo("Hello");
        assertThat(result.getModel()).isEqualTo("claude-3-opus");
        assertThat(result.getProvider()).isEqualTo(Provider.ANTHROPIC);
        assertThat(result.getSystemPrompt()).isEqualTo("You are helpful");
        assertThat(result.getTemperature()).isEqualTo(0.7);
        assertThat(result.getMaxTokens()).isEqualTo(1000);
    }

    @Test
    @DisplayName("row에 model이 없으면 defaultModel이 사용된다")
    void convert_fallbackToDefaultModel() {
        BatchRowDto row = BatchRowDto.builder()
                .id("2")
                .prompt("Hi")
                .build();

        BatchRequest result = converter.convert(row, "gemini-1.5-flash");

        assertThat(result.getModel()).isEqualTo("gemini-1.5-flash");
        assertThat(result.getProvider()).isEqualTo(Provider.GOOGLE);
    }

    @Test
    @DisplayName("grok 모델은 XAI provider로 매핑된다")
    void convert_grokMapsToXai() {
        BatchRowDto row = BatchRowDto.builder()
                .id("3")
                .prompt("Test")
                .model("grok-2-latest")
                .build();

        BatchRequest result = converter.convert(row, null);

        assertThat(result.getProvider()).isEqualTo(Provider.XAI);
    }

    @Test
    @DisplayName("model이 없고 defaultModel도 없으면 model과 provider가 null이다")
    void convert_noModel_noDefault() {
        BatchRowDto row = BatchRowDto.builder()
                .id("4")
                .prompt("Test")
                .build();

        BatchRequest result = converter.convert(row, null);

        assertThat(result.getModel()).isNull();
        assertThat(result.getProvider()).isNull();
    }

    @Test
    @DisplayName("convertAll은 모든 row를 변환한다")
    void convertAll() {
        List<BatchRowDto> rows = List.of(
                BatchRowDto.builder().id("1").prompt("A").model("claude-3-5-sonnet-latest").build(),
                BatchRowDto.builder().id("2").prompt("B").model("gemini-1.5-flash").build(),
                BatchRowDto.builder().id("3").prompt("C").model("grok-2-latest").build()
        );

        List<BatchRequest> results = converter.convertAll(rows, null);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).getProvider()).isEqualTo(Provider.ANTHROPIC);
        assertThat(results.get(1).getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(results.get(2).getProvider()).isEqualTo(Provider.XAI);
    }

    @Test
    @DisplayName("알 수 없는 model명은 provider가 null이다")
    void convert_unknownModel_nullProvider() {
        BatchRowDto row = BatchRowDto.builder()
                .id("5")
                .prompt("Test")
                .model("unknown-model-xyz")
                .build();

        BatchRequest result = converter.convert(row, null);

        assertThat(result.getModel()).isEqualTo("unknown-model-xyz");
        assertThat(result.getProvider()).isNull();
    }
}
