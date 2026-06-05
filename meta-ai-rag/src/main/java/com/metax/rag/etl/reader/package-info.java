/**
 * DocumentReader 适配包
 *
 * <p>
 * 本包负责把 MetaAI 的文档资源委托给 Spring AI 官方 DocumentReader 实现
 * MetaDocumentReader 是项目统一 Reader 门面，不自己解析文件格式
 * MetaDocumentReaderStrategy 是无状态 Spring Bean，负责按文档类型创建请求级官方 Reader
 * MetaDocumentReaderFactory 负责选择策略并组装请求级 MetaDocumentReader
 *
 * <p>
 * 这里使用的是 Factory(工厂模式) + Strategy(策略模式) + Delegation(委托模式) 组合
 * Factory(工厂模式)：MetaDocumentReaderFactory 根据 documentType 选择合适的 Reader 策略
 * Strategy(策略模式)：MetaDocumentReaderStrategy 封装不同文档类型的官方 Reader 创建方式
 * Delegation(委托模式)：MetaDocumentReader 持有已创建的官方 Reader，并把 get / read 调用委托给它执行
 *
 * <p>
 * 这样调用方只依赖 Spring AI DocumentReader 接口，不需要关心 txt、markdown、json、pdf 等格式差异
 * 新增文档格式时只需要增加一个 Strategy，不需要改 Pipeline 主流程
 *
 * <p>
 * 1、documentType 已在 resource 阶段解析完成
 * 2、txt 使用 TextReader，并固定 UTF-8
 * 3、md / markdown 使用 MarkdownDocumentReader
 * 4、json 使用 JsonReader
 * 5、pdf 优先使用 PaddleOCR Reader，适合扫描件 PDF
 * 6、复杂或未知类型使用 TikaDocumentReader 兜底
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
