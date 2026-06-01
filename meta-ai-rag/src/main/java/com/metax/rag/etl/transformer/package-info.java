/**
 * DocumentTransformer 适配包
 *
 * <p>
 * 本包负责实现 Spring AI DocumentTransformer 阶段的项目增强逻辑
 * Transformer 只处理 Document 内容和 metadata，不关心文件来源和向量库实现
 *
 * <p>
 * 1、MetaDocumentMetadataTransformer 在切分前补齐文档级 metadata
 * 2、TokenTextSplitter 负责按 token 粒度切分 chunk
 * 3、MetaChunkMetadataTransformer 在切分后补齐 chunk 级 metadata
 * 4、ContentFormatTransformer 在写入前设置 metadata 参与 EMBED / INFERENCE 的格式化规则
 * 5、写入 VectorStore 前必须保证 tenantId、knowledgeBaseId、documentId 等过滤字段完整
 *
 * <p>
 * ContentFormatTransformer 不修改 Document text，而是设置 Document 的 ContentFormatter
 * 技术 metadata 仍保存在 Document.metadata 中，但不参与 embedding 或推理文本格式化，避免污染语义
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
package com.metax.rag.etl.transformer;
