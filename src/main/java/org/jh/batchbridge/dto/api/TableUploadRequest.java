package org.jh.batchbridge.dto.api;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jh.batchbridge.dto.BatchRowDto;

@Getter
@NoArgsConstructor
public class TableUploadRequest {
    private String filename;
    private String defaultModel;
    private String systemPrompt;
    private List<BatchRowDto> rows;
}
