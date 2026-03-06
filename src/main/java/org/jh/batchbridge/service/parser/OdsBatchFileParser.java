package org.jh.batchbridge.service.parser;

import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.exception.BatchParsingException;
import org.odftoolkit.odfdom.doc.OdfDocument;
import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument;
import org.odftoolkit.odfdom.doc.table.OdfTable;
import org.odftoolkit.odfdom.doc.table.OdfTableCell;
import org.odftoolkit.odfdom.doc.table.OdfTableRow;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Component
public class OdsBatchFileParser implements BatchFileParser {

    private static final String COL_ID = "id";
    private static final String COL_PROMPT = "prompt";
    private static final String COL_MODEL = "model";
    private static final String COL_SYSTEM_PROMPT = "system_prompt";
    private static final String COL_TEMPERATURE = "temperature";
    private static final String COL_MAX_TOKENS = "max_tokens";

    @Override
    public List<BatchRowDto> parse(InputStream inputStream) {
        List<BatchRowDto> rows = new ArrayList<>();
        try (OdfSpreadsheetDocument document = OdfSpreadsheetDocument.loadDocument(inputStream)) {
            OdfTable table = document.getTableList().get(0);
            if (table == null || table.getRowCount() == 0) {
                throw new BatchParsingException("ODS file is empty");
            }

            int rowCount = table.getRowCount();
            if (rowCount < 1) {
                throw new BatchParsingException("ODS file has no rows");
            }

            OdfTableRow headerRow = table.getRowByIndex(0);
            Map<String, Integer> headerMap = createHeaderMap(headerRow);

            validateRequiredColumns(headerMap);

            for (int i = 1; i < rowCount; i++) {
                OdfTableRow row = table.getRowByIndex(i);
                if (isRowEmpty(row, headerMap.size())) continue;
                rows.add(mapRowToDto(row, headerMap));
            }

        } catch (BatchParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new BatchParsingException("Failed to parse .ods file", e);
        }
        return rows;
    }

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".ods");
    }

    private Map<String, Integer> createHeaderMap(OdfTableRow headerRow) {
        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            OdfTableCell cell = headerRow.getCellByIndex(i);
            if (cell == null) break;
            String value = getCellValueAsString(cell).toLowerCase();
            if (!value.isEmpty()) {
                headerMap.put(value, i);
            }
        }
        return headerMap;
    }

    private void validateRequiredColumns(Map<String, Integer> headerMap) {
        SchemaValidator.validateRequiredColumns(headerMap);
    }

    private BatchRowDto mapRowToDto(OdfTableRow row, Map<String, Integer> headerMap) {
        return BatchRowDto.builder()
                .id(getCellValueAsString(row.getCellByIndex(headerMap.get(COL_ID))))
                .prompt(getCellValueAsString(row.getCellByIndex(headerMap.get(COL_PROMPT))))
                .model(getOptionalCellValueAsString(row, headerMap, COL_MODEL))
                .systemPrompt(getOptionalCellValueAsString(row, headerMap, COL_SYSTEM_PROMPT))
                .temperature(getOptionalCellValueAsDouble(row, headerMap, COL_TEMPERATURE))
                .maxTokens(getOptionalCellValueAsInteger(row, headerMap, COL_MAX_TOKENS))
                .build();
    }

    private String getCellValueAsString(OdfTableCell cell) {
        if (cell == null) return "";
        String value = cell.getDisplayText();
        return value != null ? value.trim() : "";
    }

    private String getOptionalCellValueAsString(OdfTableRow row, Map<String, Integer> headerMap, String colName) {
        Integer index = headerMap.get(colName);
        if (index == null) return null;
        String val = getCellValueAsString(row.getCellByIndex(index));
        return val.isEmpty() ? null : val;
    }

    private Double getOptionalCellValueAsDouble(OdfTableRow row, Map<String, Integer> headerMap, String colName) {
        Integer index = headerMap.get(colName);
        if (index == null) return null;
        OdfTableCell cell = row.getCellByIndex(index);
        if (cell == null) return null;
        
        Double val = cell.getDoubleValue();
        if (val != null) return val;

        String text = getCellValueAsString(cell);
        if (text.isEmpty()) return null;
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getOptionalCellValueAsInteger(OdfTableRow row, Map<String, Integer> headerMap, String colName) {
        Double val = getOptionalCellValueAsDouble(row, headerMap, colName);
        return val != null ? val.intValue() : null;
    }

    private boolean isRowEmpty(OdfTableRow row, int colLimit) {
        if (row == null) return true;
        for (int i = 0; i < colLimit; i++) {
            String val = getCellValueAsString(row.getCellByIndex(i));
            if (!val.isEmpty()) return false;
        }
        return true;
    }
}
