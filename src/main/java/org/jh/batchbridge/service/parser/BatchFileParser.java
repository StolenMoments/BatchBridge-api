package org.jh.batchbridge.service.parser;

import org.jh.batchbridge.dto.BatchRowDto;
import java.io.InputStream;
import java.util.List;

public interface BatchFileParser {
    List<BatchRowDto> parse(InputStream inputStream);
    boolean supports(String fileName);
}
