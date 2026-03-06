package org.jh.batchbridge.dto.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyInfo {
    private String model;
    private String maskedKey;
    private boolean verified;
}
