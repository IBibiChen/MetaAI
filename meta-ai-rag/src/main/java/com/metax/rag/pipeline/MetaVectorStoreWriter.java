package com.metax.rag.pipeline;

import com.metax.rag.config.MetaRetrievalProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MetaVectorStoreWriter .
 *
 * <p>
 * Meta Retrieval 向量库写入器，统一封装项目级 VectorStore 写入策略
 * Spring AI 的 BatchingStrategy 继续负责 token 维度分批，这里额外控制单次写入的 Document 数量
 *
 * <p>
 * 这个组件同时服务知识库文档入库和聊天文件临时索引
 * 目标是屏蔽 DashScope、OpenAI、Ollama 等不同 embedding provider 的单次输入条数差异
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/10
 */
@Component
@RequiredArgsConstructor
public class MetaVectorStoreWriter {

    /**
     * provider 通用保守默认批次大小
     */
    private static final int DEFAULT_WRITE_BATCH_SIZE = 10;

    private final VectorStore vectorStore;

    private final MetaRetrievalProperties properties;

    /**
     * 按过滤条件删除向量库文档
     *
     * <p>
     * 删除动作保持单次执行，避免分批写入时重复删除刚写入的新 chunk
     *
     * @param filterExpression 删除过滤表达式
     */
    public void delete(Filter.Expression filterExpression) {
        vectorStore.delete(filterExpression);
    }

    /**
     * 按 Document 数量分批写入向量库
     *
     * <p>
     * Spring AI VectorStore 内部仍会使用官方 BatchingStrategy 控制 token 维度
     * 本方法只补齐 provider 输入数组长度限制，避免单次 documents 过多触发 embedding 服务拒绝
     *
     * @param documents 待写入的 Document 列表，空列表会直接跳过
     */
    public void write(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        int batchSize = writeBatchSize();
        for (int start = 0; start < documents.size(); start += batchSize) {
            int end = Math.min(start + batchSize, documents.size());
            // 复制 subList，避免下游 VectorStore 持有原始列表视图造成意外联动
            vectorStore.write(List.copyOf(documents.subList(start, end)));
        }
    }

    /**
     * 解析向量库写入批次大小
     *
     * <p>
     * 配置值小于等于 0 时回退保守默认值，避免错误配置导致死循环或一次性大批量写入
     *
     * @return 有效的写入批次大小
     */
    private int writeBatchSize() {
        int configuredBatchSize = properties.getVectorStore().getWriteBatchSize();
        if (configuredBatchSize <= 0) {
            return DEFAULT_WRITE_BATCH_SIZE;
        }
        return configuredBatchSize;
    }
}
