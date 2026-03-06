package org.jh.batchbridge.service.parser;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.exception.BatchParsingException;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class CsvBatchFileParser implements BatchFileParser {

    private static final String COL_ID = "id";
    private static final String COL_PROMPT = "prompt";
    private static final String COL_MODEL = "model";
    private static final String COL_SYSTEM_PROMPT = "system_prompt";
    private static final String COL_TEMPERATURE = "temperature";
    private static final String COL_MAX_TOKENS = "max_tokens";

    @Override
    public List<BatchRowDto> parse(InputStream inputStream) {
        byte[] content;
        try {
            content = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new BatchParsingException("Failed to read CSV input stream", e);
        }

        if (content.length == 0) {
            throw new BatchParsingException("CSV file is empty");
        }

        Charset charset = detectCharset(content);
        
        try (CSVReader reader = new CSVReader(new InputStreamReader(new ByteArrayInputStream(content), charset))) {
            List<String[]> allRows = reader.readAll();
            if (allRows.isEmpty()) {
                throw new BatchParsingException("CSV file is empty");
            }

            String[] header = allRows.get(0);
            Map<String, Integer> headerMap = createHeaderMap(header);
            validateRequiredColumns(headerMap);

            List<BatchRowDto> rows = new ArrayList<>();
            for (int i = 1; i < allRows.size(); i++) {
                String[] row = allRows.get(i);
                if (isRowEmpty(row)) continue;
                rows.add(mapRowToDto(row, headerMap));
            }
            return rows;

        } catch (IOException | CsvException e) {
            if (e instanceof BatchParsingException) {
                throw (BatchParsingException) e;
            }
            throw new BatchParsingException("Failed to parse .csv file", e);
        }
    }

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".csv");
    }

    private Charset detectCharset(byte[] content) {
        CharsetDetector detector = new CharsetDetector();
        detector.setText(content);
        CharsetMatch match = detector.detect();
        if (match != null && match.getConfidence() > 50) {
            try {
                return Charset.forName(match.getName());
            } catch (Exception e) {
                return StandardCharsets.UTF_8;
            }
        }
        return StandardCharsets.UTF_8;
    }

    private Map<String, Integer> createHeaderMap(String[] header) {
        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            if (header[i] != null) {
                headerMap.put(header[i].trim().toLowerCase(), i);
            }
        }
        return headerMap;
    }

    private void validateRequiredColumns(Map<String, Integer> headerMap) {
        if (!headerMap.containsKey(COL_ID)) {
            throw new BatchParsingException("Missing required column: " + COL_ID);
        }
        if (!headerMap.containsKey(COL_PROMPT)) {
            throw new BatchParsingException("Missing required column: " + COL_PROMPT);
        }
    }

    private BatchRowDto mapRowToDto(String[] row, Map<String, Integer> headerMap) {
        return BatchRowDto.builder()
                .id(getValue(row, headerMap, COL_ID))
                .prompt(getValue(row, headerMap, COL_PROMPT))
                .model(getOptionalValue(row, headerMap, COL_MODEL))
                .systemPrompt(getOptionalValue(row, headerMap, COL_SYSTEM_PROMPT))
                .temperature(getOptionalDoubleValue(row, headerMap, COL_TEMPERATURE))
                .maxTokens(getOptionalIntegerValue(row, headerMap, COL_MAX_TOKENS))
                .build();
    }

    private String getValue(String[] row, Map<String, Integer> headerMap, String colName) {
        Integer index = headerMap.get(colName);
        if (index == null || index >= row.length) return "";
        return row[index] != null ? row[index].trim() : "";
    }

    private String getOptionalValue(String[] row, Map<String, Integer> headerMap, String colName) {
        String val = getValue(row, headerMap, colName);
        return val.isEmpty() ? null : val;
    }

    private Double getOptionalDoubleValue(String[] row, Map<String, Integer> headerMap, String colName) {
        String val = getOptionalValue(row, headerMap, colName);
        if (val == null) return null;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getOptionalIntegerValue(String[] row, Map<String, Integer> headerMap, String colName) {
        String val = getOptionalValue(row, headerMap, colName);
        if (val == null) return null;
        try {
            return (int) Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isRowEmpty(String[] row) {
        if (row == null || row.length == 0) return true;
        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
