package org.jh.batchbridge.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiConfig {

    private Map<String, ProviderConfig> providers;

    @Getter
    @Setter
    public static class ProviderConfig {
        private String apiKey;
        private String model;
    }
}
