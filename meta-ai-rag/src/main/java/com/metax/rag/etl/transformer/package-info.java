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
 * 4、ContentFormatTransformer 在写入前设置 metadata 的 EMBED / INFERENCE 格式化规则
 * 5、写入 VectorStore 前必须保证 tenantId、knowledgeBaseId、documentId 等过滤字段完整
 *
 * <p>
 * 创建内容格式化 Transformer
 *
 * <p>
 * ContentFormatTransformer 不会修改 Document 的原始 text
 * 它只为 Document 设置 ContentFormatter，用来控制哪些 metadata 会拼接进模型可见的文本
 *
 * <p>
 * tenantId、knowledgeBaseId、documentId、chunkId 等技术 metadata 仍然保留在 Document.metadata 中
 * 但会被排除在 EMBED / INFERENCE 文本格式化之外，避免干扰语义向量和模型回答
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
package com.metax.rag.etl.transformer;
