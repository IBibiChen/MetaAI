/**
 * DocumentReader 适配包
 *
 * <p>
 * 本包负责把 MetaAI 的文档资源委托给 Spring AI 官方 DocumentReader 实现
 * MetaDocumentReader 是项目统一 Reader 门面，不自己解析文件格式
 * MetaDocumentReaderStrategy 是无状态 Spring Bean，负责按文档类型创建请求级官方 Reader
 *
 * <p>
 * 1、documentType 由调用方显式传入或由文件名后缀自动推断
 * 2、txt 使用 TextReader，并固定 UTF-8
 * 3、md / markdown 使用 MarkdownDocumentReader
 * 4、json 使用 JsonReader
 * 5、复杂或未知类型使用 TikaDocumentReader 兜底
 *
 * <p>
 * 注意：Spring AI 1.1.7 MarkdownDocumentReader 内部使用 JVM 默认字符集读取 Resource
 * Windows 本地开发建议固定 -Dfile.encoding=UTF-8，避免中文 markdown 内容乱码
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
package com.metax.rag.etl.reader;
