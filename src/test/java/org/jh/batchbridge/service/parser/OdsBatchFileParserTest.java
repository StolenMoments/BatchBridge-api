package org.jh.batchbridge.service.parser;

import org.jh.batchbridge.dto.BatchRowDto;
import org.jh.batchbridge.exception.BatchParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.odftoolkit.odfdom.doc.OdfSpreadsheetDocument;
import org.odftoolkit.odfdom.doc.table.OdfTable;
import org.odftoolkit.odfdom.doc.table.OdfTableRow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OdsBatchFileParserTest {

    private OdsBatchFileParser parser;

    @BeforeEach
    void setUp() {
        parser = new OdsBatchFileParser();
    }

    @Test
    @DisplayName("정상적인 ODS 파일을 파싱한다")
    void testParseValidOds() throws Exception {
        // Given
        byte[] odsContent = createOdsContent(new String[]{"id", "prompt", "model", "temperature"},
                new Object[][]{
                        {"1", "Hello ODS", "claude", 0.7},
                        {"2", "ODS Test", "gemini", 0.5}
                });

        // When
        List<BatchRowDto> results = parser.parse(new ByteArrayInputStream(odsContent));

        // Then
        assertEquals(2, results.size());
        assertEquals("1", results.get(0).getId());
        assertEquals("Hello ODS", results.get(0).getPrompt());
        assertEquals("claude", results.get(0).getModel());
        assertEquals(0.7, results.get(0).getTemperature());

        assertEquals("2", results.get(1).getId());
        assertEquals("ODS Test", results.get(1).getPrompt());
        assertEquals("gemini", results.get(1).getModel());
        assertEquals(0.5, results.get(1).getTemperature());
    }

    @Test
    @DisplayName("필수 컬럼(prompt)이 누락되면 예외가 발생한다")
    void testParseMissingRequiredColumn() throws Exception {
        // Given
        byte[] odsContent = createOdsContent(new String[]{"id", "not_prompt"},
                new Object[][]{{"1", "some prompt"}});

        // When & Then
        BatchParsingException exception = assertThrows(BatchParsingException.class, () -> {
            parser.parse(new ByteArrayInputStream(odsContent));
        });
        System.out.println("[DEBUG_LOG] Exception message: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("Missing required column: prompt"));
    }

    @Test
    @DisplayName("지원하는 확장자 확인")
    void testSupports() {
        assertTrue(parser.supports("test.ods"));
        assertTrue(parser.supports("TEST.ODS"));
        assertFalse(parser.supports("test.xlsx"));
        assertFalse(parser.supports(null));
    }

    private byte[] createOdsContent(String[] headers, Object[][] data) throws Exception {
        try (OdfSpreadsheetDocument document = OdfSpreadsheetDocument.newSpreadsheetDocument();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            OdfTable table = document.getTableList().get(0);
            
            // Header
            OdfTableRow headerRow = table.getRowByIndex(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.getCellByIndex(i).setStringValue(headers[i]);
            }

            // Data
            for (int i = 0; i < data.length; i++) {
                OdfTableRow row = table.getRowByIndex(i + 1);
                for (int j = 0; j < data[i].length; j++) {
                    if (data[i][j] instanceof String) {
                        row.getCellByIndex(j).setStringValue((String) data[i][j]);
                    } else if (data[i][j] instanceof Double) {
                        row.getCellByIndex(j).setDoubleValue((Double) data[i][j]);
                    } else if (data[i][j] instanceof Integer) {
                        row.getCellByIndex(j).setDoubleValue(((Integer) data[i][j]).doubleValue());
                    }
                }
            }
            document.save(bos);
            return bos.toByteArray();
        }
    }
}
