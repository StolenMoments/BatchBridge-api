package org.jh.batchbridge.service.parser;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.exception.BatchParsingException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Component
public class XlsxBatchFileParser implements BatchFileParser {

    private static final String COL_ID = "id";
    private static final String COL_PROMPT = "prompt";
    private static final String COL_MODEL = "model";
    private static final String COL_SYSTEM_PROMPT = "system_prompt";
    private static final String COL_TEMPERATURE = "temperature";
    private static final String COL_MAX_TOKENS = "max_tokens";

    @Override
    public List<BatchRowDto> parse(InputStream inputStream) {
        List<BatchRowDto> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            if (!rowIterator.hasNext()) {
                throw new BatchParsingException("Excel file is empty");
            }

            Row headerRow = rowIterator.next();
            Map<String, Integer> headerMap = createHeaderMap(headerRow);

            validateRequiredColumns(headerMap);

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if (isRowEmpty(row)) continue;
                rows.add(mapRowToDto(row, headerMap));
            }

        } catch (IOException e) {
            throw new BatchParsingException("Failed to parse .xlsx file", e);
        }
        return rows;
    }

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".xlsx");
    }

    private Map<String, Integer> createHeaderMap(Row headerRow) {
        Map<String, Integer> headerMap = new HashMap<>();
        for (Cell cell : headerRow) {
            headerMap.put(getCellValueAsString(cell).toLowerCase(), cell.getColumnIndex());
        }
        return headerMap;
    }

    private void validateRequiredColumns(Map<String, Integer> headerMap) {
        SchemaValidator.validateRequiredColumns(headerMap);
    }

    private BatchRowDto mapRowToDto(Row row, Map<String, Integer> headerMap) {
        return BatchRowDto.builder()
                .id(getCellValueAsString(row.getCell(headerMap.get(COL_ID))))
                .prompt(getCellValueAsString(row.getCell(headerMap.get(COL_PROMPT))))
                .model(getOptionalCellValueAsString(row, headerMap, COL_MODEL))
                .systemPrompt(getOptionalCellValueAsString(row, headerMap, COL_SYSTEM_PROMPT))
                .temperature(getOptionalCellValueAsDouble(row, headerMap, COL_TEMPERATURE))
                .maxTokens(getOptionalCellValueAsInteger(row, headerMap, COL_MAX_TOKENS))
                .build();
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                }
                yield String.valueOf((long) cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private String getOptionalCellValueAsString(Row row, Map<String, Integer> headerMap, String colName) {
        Integer index = headerMap.get(colName);
        if (index == null) return null;
        String val = getCellValueAsString(row.getCell(index));
        return val.isEmpty() ? null : val;
    }

    private Double getOptionalCellValueAsDouble(Row row, Map<String, Integer> headerMap, String colName) {
        Integer index = headerMap.get(colName);
        if (index == null) return null;
        Cell cell = row.getCell(index);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        try {
            return cell.getNumericCellValue();
        } catch (Exception e) {
            String val = getCellValueAsString(cell);
            try {
                return Double.parseDouble(val);
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
    }

    private Integer getOptionalCellValueAsInteger(Row row, Map<String, Integer> headerMap, String colName) {
        Double val = getOptionalCellValueAsDouble(row, headerMap, colName);
        return val != null ? val.intValue() : null;
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}
