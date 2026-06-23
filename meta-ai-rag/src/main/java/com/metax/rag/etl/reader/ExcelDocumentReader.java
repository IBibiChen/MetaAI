package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import lombok.extern.slf4j.Slf4j;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.event.AnalysisEventListener;
import org.apache.fesod.sheet.read.metadata.ReadSheet;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ExcelDocumentReader .
 *
 * <p>
 * Excel 文档 Reader，使用 Apache Fesod 按行流式抽取真实有值的单元格并输出稳定 TSV 文本
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/23
 */
@Slf4j
public class ExcelDocumentReader implements DocumentReader {

    private final MetaDocumentResource documentResource;

    /**
     * 创建 Excel 文档 Reader
     *
     * @param documentResource 文档资源
     */
    public ExcelDocumentReader(MetaDocumentResource documentResource) {
        this.documentResource = documentResource;
    }

    /**
     * 读取 Excel 文档
     *
     * <p>
     * 该方法完整遍历所有 sheet，只抽取真实有值的单元格，忽略纯样式空行
     *
     * @return Document 列表
     */
    @Override
    public List<Document> get() {
        try (InputStream inputStream = documentResource.resource().getInputStream()) {
            ExcelTextCollector collector = new ExcelTextCollector();
            // 先读取 sheet 元数据，再逐个 sheet 重新打开 Resource 读取内容
            // Fesod 的 doRead 会消费输入流，不能复用同一个 InputStream
            List<ReadSheet> sheets = FesodSheet.read(inputStream)
                    .headRowNumber(0)
                    .build()
                    .excelExecutor()
                    .sheetList();
            // Excel 专用 Reader 的职责是完整抽取原始表格文本
            // 行数、列数、字符数等容量治理应交给索引链路或部署资源策略
            for (ReadSheet sheet : sheets) {
                readSheet(documentResource.resource(), sheet, collector);
            }
            String text = collector.text();
            log.info("Excel 文档读取完成：source = {}，sheets = {}，rows = {}，chars = {}",
                    documentResource.source(), sheets.size(), collector.nonEmptyRows(), text.length());
            if (!StringUtils.hasText(text)) {
                return List.of();
            }
            // Excel 文件最终作为一个 Spring AI Document 进入后续 Transformer
            // sheet 和行结构通过 TSV 文本保留，不在 Reader 层拆 chunk
            return List.of(Document.builder()
                    .text(text)
                    .metadata(Map.of("source", documentResource.source()))
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Excel 文档读取失败：" + documentResource.source(), ex);
        }
    }

    /**
     * 读取单个 Excel sheet
     *
     * <p>
     * Fesod 读取流会在 sheet 维度消费输入流，因此每个 sheet 单独打开一次 Resource
     *
     * @param resource  原始文档 Resource
     * @param sheet     sheet 元数据
     * @param collector 文本收集器
     */
    private void readSheet(Resource resource, ReadSheet sheet, ExcelTextCollector collector) {
        try (InputStream inputStream = resource.getInputStream()) {
            SheetTextListener listener = new SheetTextListener(sheet.getSheetName(), collector);
            // 固定写入 sheet 边界，便于后续 chunk 和检索结果保留表格上下文
            collector.appendSheet(sheet.getSheetName());
            // headRowNumber(0) 表示第一行也是正文数据，不能被 Fesod 当作表头跳过
            // 不设置 numRows，保持 Fesod 默认完整读取 sheet 的行为
            FesodSheet.read(inputStream, listener)
                    .headRowNumber(0)
                    .sheet(sheet.getSheetNo())
                    .doRead();
        } catch (Exception ex) {
            throw new IllegalStateException("Excel sheet 读取失败：" + documentResource.source()
                    + "，sheet = " + sheet.getSheetName(), ex);
        }
    }

    /**
     * Excel sheet 行监听器
     *
     * <p>
     * 监听器只负责把 Fesod 行事件转换为稳定 TSV 行，不做容量截断和业务过滤
     */
    private static class SheetTextListener extends AnalysisEventListener<Map<Integer, String>> {

        private final String sheetName;

        private final ExcelTextCollector collector;

        private SheetTextListener(String sheetName, ExcelTextCollector collector) {
            this.sheetName = sheetName;
            this.collector = collector;
        }

        @Override
        public void invoke(Map<Integer, String> row, AnalysisContext context) {
            List<String> values = normalizeRow(row);
            if (values.isEmpty()) {
                // 纯空行和只有样式的行不进入文本，避免污染后续 embedding 内容
                return;
            }
            // 非空行按原 sheet 顺序追加，列结构由 TSV 的制表符保留
            collector.appendRow(sheetName, values);
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
        }

        /**
         * 规范化 Excel 行文本
         *
         * <p>
         * 保留中间空单元格，移除尾部空单元格，避免 TSV 输出无意义尾部分隔符
         *
         * @param row Fesod 行数据
         * @return 已清洗的单元格文本列表
         */
        private List<String> normalizeRow(Map<Integer, String> row) {
            if (row == null || row.isEmpty()) {
                return List.of();
            }
            TreeMap<Integer, String> sorted = new TreeMap<>(row);
            List<String> values = new ArrayList<>();
            // 按列号补齐中间空单元格，避免 B 列有值时被错误挤压到 A 列
            for (int index = 0; index <= sorted.lastKey(); index++) {
                String value = sorted.get(index);
                values.add(normalizeCell(value));
            }
            // 尾部空单元格不携带结构信息，移除后可以减少无意义 TSV 分隔符
            while (!values.isEmpty() && !StringUtils.hasText(values.get(values.size() - 1))) {
                values.remove(values.size() - 1);
            }
            boolean hasText = values.stream().anyMatch(StringUtils::hasText);
            return hasText ? values : List.of();
        }

        /**
         * 规范化 Excel 单元格文本
         *
         * <p>
         * TSV 使用制表符分隔列，因此单元格内部换行和制表符统一折叠为空格
         *
         * @param value 原始单元格文本
         * @return 已清洗的单元格文本
         */
        private String normalizeCell(String value) {
            if (!StringUtils.hasText(value)) {
                return "";
            }
            return value.strip().replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        }
    }

    /**
     * Excel 文本收集器
     *
     * <p>
     * 负责维护跨 sheet 的 TSV 文本、sheet 边界和真实非空行统计
     */
    private static class ExcelTextCollector {

        /**
         * 最终输出给 Spring AI Document 的完整 Excel 文本
         */
        private final StringBuilder text = new StringBuilder();

        /**
         * sheet 名称到非空行数的映射
         *
         * <p>
         * 同时用于判断 sheet 标题是否已经写入，避免重复输出同一个 sheet 边界
         */
        private final Map<String, Integer> sheetRows = new java.util.HashMap<>();

        /**
         * 全文件真实非空行数量
         *
         * <p>
         * 该值只用于读取完成日志，便于排查异常 Excel 的有效数据规模
         */
        private int nonEmptyRows;

        /**
         * 追加 sheet 标题
         *
         * @param sheetName sheet 名称
         */
        private void appendSheet(String sheetName) {
            if (!sheetRows.containsKey(sheetName)) {
                // sheet 标题是后续检索引用表格区域时最轻量的上下文边界
                text.append("# Sheet: ").append(sheetName).append("\n");
                sheetRows.put(sheetName, 0);
            }
        }

        /**
         * 追加非空行文本
         *
         * @param sheetName sheet 名称
         * @param values    行内单元格文本
         */
        private void appendRow(String sheetName, List<String> values) {
            // TSV 比自然语言拼接更稳定，能保留列边界并降低模型误读概率
            text.append(String.join("\t", values));
            text.append("\n");
            // 行数只统计真实非空行，用于读取完成日志排查异常表格规模
            sheetRows.merge(sheetName, 1, Integer::sum);
            nonEmptyRows++;
        }

        private int nonEmptyRows() {
            return nonEmptyRows;
        }

        private String text() {
            return text.toString();
        }
    }
}
