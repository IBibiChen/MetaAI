/**
 * 文档资源适配包
 *
 * <p>
 * 本包负责把对象存储文件流抽象为 Spring Resource
 * Reader 层只依赖 Resource、documentType 和 source，不感知 RustFS、MinIO 或 OSS 细节
 * documentType 的显式值和后缀推断都在本包完成，保证 Reader 和 metadata 使用同一份类型值
 *
 * <p>
 * 1、对象存储来源使用 MetaObjectStorageResource 延迟打开对象流
 * 2、MetaDocumentTypeResolver 负责把显式类型或文件后缀解析为标准 documentType
 * 3、documentType 的最终值在资源创建阶段确定，后续 metadata 与 Reader 共用同一个值
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
package com.metax.rag.etl.resource;
