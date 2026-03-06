package org.jh.batchbridge.service.parser;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.exception.BatchParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XlsxBatchFileParserTest {

    private XlsxBatchFileParser parser;

    @BeforeEach
    void setUp() {
        parser = new XlsxBatchFileParser();
    }

    @Test
    @DisplayName("정상적인 XLSX 파일을 파싱한다")
    void testParseValidXlsx() throws IOException {
        // Given
        byte[] excelContent = createExcelContent(new String[]{"id", "prompt", "model", "temperature"},
                new Object[][]{
                        {"1", "Hello AI", "claude", 0.7},
                        {"2", "Explain quantum physics", "gemini", 0.5}
                });

        // When
        List<BatchRowDto> results = parser.parse(new ByteArrayInputStream(excelContent));

        // Then
        assertEquals(2, results.size());
        assertEquals("1", results.get(0).getId());
        assertEquals("Hello AI", results.get(0).getPrompt());
        assertEquals("claude", results.get(0).getModel());
        assertEquals(0.7, results.get(0).getTemperature());

        assertEquals("2", results.get(1).getId());
        assertEquals("Explain quantum physics", results.get(1).getPrompt());
        assertEquals("gemini", results.get(1).getModel());
        assertEquals(0.5, results.get(1).getTemperature());
    }

    @Test
    @DisplayName("필수 컬럼(prompt)이 누락되면 예외가 발생한다")
    void testParseMissingRequiredColumn() throws IOException {
        // Given
        byte[] excelContent = createExcelContent(new String[]{"id", "not_prompt"},
                new Object[][]{{"1", "some prompt"}});

        // When & Then
        BatchParsingException exception = assertThrows(BatchParsingException.class, () -> {
            parser.parse(new ByteArrayInputStream(excelContent));
        });
        assertTrue(exception.getMessage().contains("Missing required column: prompt"));
    }

    @Test
    @DisplayName("빈 파일인 경우 예외가 발생한다")
    void testParseEmptyFile() throws IOException {
        // Given
        byte[] excelContent = createExcelContent(new String[]{}, new Object[][]{});

        // When & Then
        assertThrows(BatchParsingException.class, () -> {
            parser.parse(new ByteArrayInputStream(excelContent));
        });
    }

    @Test
    @DisplayName("지원하는 확장자 확인")
    void testSupports() {
        assertTrue(parser.supports("test.xlsx"));
        assertTrue(parser.supports("TEST.XLSX"));
        assertFalse(parser.supports("test.csv"));
        assertFalse(parser.supports(null));
    }

    private byte[] createExcelContent(String[] headers, Object[][] data) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet();
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            for (int i = 0; i < data.length; i++) {
                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < data[i].length; j++) {
                    Cell cell = row.createCell(j);
                    if (data[i][j] instanceof String) {
                        cell.setCellValue((String) data[i][j]);
                    } else if (data[i][j] instanceof Double) {
                        cell.setCellValue((Double) data[i][j]);
                    } else if (data[i][j] instanceof Integer) {
                        cell.setCellValue((Integer) data[i][j]);
                    }
                }
            }
            workbook.write(bos);
            return bos.toByteArray();
        }
    }
}
