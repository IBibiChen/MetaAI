/**
 * 文档资源适配包
 *
 * <p>
 * 本包负责把对象存储文件流和受控本地文件统一抽象为 Spring Resource
 * Reader 层只依赖 Resource、documentType 和 source，不感知 RustFS、OSS、上传文件或本地路径细节
 *
 * <p>
 * 1、对象存储来源使用 MetaObjectStorageResource 延迟打开对象流
 * 2、本地文件来源必须限制在 metax.ai.rag.storage.local-root 内
 * 3、上传文件先进入对象存储，再按对象存储来源进入 ETL 链路
 * 4、documentType 的最终值在资源创建阶段确定，后续 metadata 与 Reader 共用同一个值
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
package com.metax.rag.etl.resource;
