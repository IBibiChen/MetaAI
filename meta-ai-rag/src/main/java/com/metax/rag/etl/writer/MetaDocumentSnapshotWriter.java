package com.metax.rag.etl.writer;

import com.metax.rag.config.RagProperties;
import com.metax.rag.indexing.DocumentIndexingRequest;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.writer.FileDocumentWriter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * MetaDocumentSnapshotWriter .
 *
 * <p>
 * 基于 Spring AI 官方 FileDocumentWriter 的 ETL 快照 Writer
 * 它用于把 Reader 和 Transformer 处理后的 Document 写入本地文件，方便排查 chunk 切分、metadata 和格式化内容
 *
 * <p>
 * 注意：它不是生产 RAG 的最终写入端
 * 生产索引仍由 VectorStore 承担，快照文件只用于调试、排查和离线观察
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/1
 */
public class MetaDocumentSnapshotWriter implements DocumentWriter {

    private final DocumentIndexingRequest request;

    private final RagProperties.Snapshot properties;

    public MetaDocumentSnapshotWriter(DocumentIndexingRequest request, RagProperties.Snapshot properties) {
        Assert.notNull(request, "DocumentIndexingRequest must not be null");
        Assert.notNull(properties, "Snapshot properties must not be null");
        this.request = request;
        this.properties = properties;
    }

    /**
     * 写入 ETL 快照文件
     *
     * <p>
     * FileDocumentWriter 会写入 Document.getFormattedContent(metadataMode)
     * 因此快照内容能够反映 ContentFormatter 和 metadataMode 对最终文本的影响
     *
     * @param documents Transformer 处理后的 Document 列表
     */
    @Override
    public void accept(List<Document> documents) {
        Path snapshotFile = createSnapshotFile();
        FileDocumentWriter writer = new FileDocumentWriter(snapshotFile.toString(),
                properties.isWithDocumentMarkers(), properties.getMetadataMode(), properties.isAppend());
        writer.write(documents);
    }

    /**
     * 创建本次索引请求对应的快照文件路径
     *
     * @return 快照文件路径
     */
    public Path createSnapshotFile() {
        try {
            Path outputDir = Path.of(properties.getOutputDir()).toAbsolutePath().normalize();
            Files.createDirectories(outputDir);
            Path snapshotFile = outputDir.resolve(fileName()).normalize();
            if (!snapshotFile.startsWith(outputDir)) {
                throw new IllegalArgumentException("Snapshot file must stay inside output directory");
            }
            return snapshotFile;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create RAG ETL snapshot directory", ex);
        }
    }

    /**
     * 生成本次 ETL 快照文件名
     *
     * <p>
     * 文件名由 tenantId、knowledgeBaseId、documentId 组成
     * 当前运行环境只启用一套 EmbeddingModel 和一套 VectorStore，同一份文档重复索引时会覆盖同一个快照文件
     *
     * <p>
     * 示例
     * <pre>{@code
     * tenant-1_kb-1_doc-1.txt
     * }</pre>
     *
     * @return 快照文件名
     */
    private String fileName() {
        return String.join("_",
                safeValue(request.tenantId()),
                safeValue(request.knowledgeBaseId()),
                safeValue(request.documentId())) + ".txt";
    }

    /**
     * 清理文件名片段
     *
     * <p>
     * 业务字段可能包含斜杠、冒号、空格或路径跳转字符，不能直接作为文件名使用
     * 这里只保留字母、数字、点号、下划线和短横线，其余字符统一替换为下划线
     * 如果清理后仍以连续点号开头，则继续替换为下划线，避免出现类似路径跳转的文件名片段
     *
     * <p>
     * 示例
     * <pre>{@code
     * tenant/1  -> tenant_1
     * doc:001   -> doc_001
     * ../tenant -> _tenant
     * null      -> unknown
     * }</pre>
     *
     * @param value 原始业务字段值
     * @return 安全文件名片段
     */
    private String safeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_").replaceAll("^\\.+", "_");
    }
}
