package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import org.apache.fesod.sheet.FesodSheet;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExcelDocumentReaderTest .
 *
 * <p>
 * Excel 文档流式 Reader 测试
 */
class ExcelDocumentReaderTest {

    @Test
    void shouldReadOnlyNonBlankRowsAsTsv() {
        ExcelDocumentReader reader = new ExcelDocumentReader(resource(List.of(
                List.of("区县", "问题", "影响"),
                List.of("市中区", "排口直排", "影响岷江干流水质"),
                List.of("", "", ""),
                List.of("高新区", "雨污混接", "")
        )));

        List<Document> documents = reader.get();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText())
                .contains("# Sheet: Sheet1")
                .contains("区县\t问题\t影响")
                .contains("市中区\t排口直排\t影响岷江干流水质")
                .contains("高新区\t雨污混接")
                .doesNotContain("\t\t\n");
    }

    @Test
    void shouldKeepLongCellText() {
        ExcelDocumentReader reader = new ExcelDocumentReader(resource(List.of(
                List.of("标题", "很长很长很长的单元格"),
                List.of("第二行", "仍然很长很长很长")
        )));

        List<Document> documents = reader.get();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText())
                .contains("很长很长很长的单元格")
                .contains("仍然很长很长很长")
                .doesNotContain("...");
    }

    @Test
    void shouldKeepAllNonEmptyRows() {
        ExcelDocumentReader reader = new ExcelDocumentReader(resource(List.of(
                List.of("第一行"),
                List.of("第二行"),
                List.of("第三行")
        )));

        List<Document> documents = reader.get();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText())
                .contains("第一行")
                .contains("第二行")
                .contains("第三行");
    }

    @Test
    void shouldKeepAllColumns() {
        List<String> row = java.util.stream.IntStream.rangeClosed(1, 260)
                .mapToObj(index -> "列" + index)
                .toList();
        ExcelDocumentReader reader = new ExcelDocumentReader(resource(List.of(
                row
        )));

        List<Document> documents = reader.get();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText())
                .contains("列1")
                .contains("列200")
                .contains("列260");
    }

    private MetaDocumentResource resource(List<List<String>> rows) {
        byte[] bytes = workbook(rows);
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "demo.xlsx";
            }
        };
        return new MetaDocumentResource(resource, "xlsx", "demo.xlsx");
    }

    private byte[] workbook(List<List<String>> rows) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            FesodSheet.write(outputStream)
                    .sheet("Sheet1")
                    .doWrite(rows);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("测试 Excel 创建失败", ex);
        }
    }
}
