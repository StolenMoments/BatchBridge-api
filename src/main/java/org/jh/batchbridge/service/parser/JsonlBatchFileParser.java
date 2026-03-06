package org.jh.batchbridge.service.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.exception.BatchParsingException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsonlBatchFileParser implements BatchFileParser {

    private final ObjectMapper objectMapper;

    @Override
    public List<BatchRowDto> parse(InputStream inputStream) {
        List<BatchRowDto> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    BatchRowDto row = objectMapper.readValue(line, BatchRowDto.class);
                    validateRequiredFields(row, lineNumber);
                    rows.add(row);
                } catch (BatchParsingException e) {
                    log.error("Validation error at line {}: {}", lineNumber, e.getMessage());
                    throw e;
                } catch (Exception e) {
                    log.error("Error parsing JSONL at line {}: {}", lineNumber, line, e);
                    throw new BatchParsingException("Error parsing JSONL at line " + lineNumber, e);
                }
            }
        } catch (BatchParsingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error reading JSONL input stream", e);
            throw new BatchParsingException("Error reading JSONL input stream", e);
        }
        return rows;
    }

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".jsonl");
    }

    private void validateRequiredFields(BatchRowDto row, int lineNumber) {
        if (row.getId() == null || row.getId().trim().isEmpty()) {
            throw new BatchParsingException("Missing required field 'id' at line " + lineNumber);
        }
        if (row.getPrompt() == null || row.getPrompt().trim().isEmpty()) {
            throw new BatchParsingException("Missing required field 'prompt' at line " + lineNumber);
        }
    }
}
