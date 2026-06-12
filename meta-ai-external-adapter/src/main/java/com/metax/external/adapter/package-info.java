/**
 * ExternalAdapter .
 *
 * <p>
 * 第三方系统资料库文件学习适配器
 *
 * <p>
 * 本包用于把第三方系统资料库文件接入 MetaAI 的对象存储、ETL、OCR 和向量化链路
 * 设计目标是保持对 meta-ai-agent 和 meta-ai-rag 模块 0 入侵
 * 适配器只通过数据库源表、内部 HTTP API 和对象存储文档状态完成同步闭环
 *
 * <p>
 * 阶段 1：学习通知入队
 *
 * <p>
 * 第三方系统在文件上传并保存元数据后调用 /internal/external/documents/sync
 * 接口只接收文件 ID 列表，逐条校验 pikb_file_info 中可学习文件，并写入 meta_external_document_sync 持久队列
 * 该阶段会把第三方源表 status 回写为 0，表示推送成功、学习中
 * 接口线程不执行文件下载、对象存储上传、OCR、ETL 或向量化，避免批量上传时阻塞第三方业务流程
 *
 * <p>
 * 阶段 2：兜底补偿扫描
 *
 * <p>
 * ExternalDocumentReconciler 定时扫描第三方源表中 libraryType 有值且未删除的文件
 * 该阶段用于处理历史存量文件、第三方系统漏调通知接口、文件 hash 变化后的重新学习
 * 补偿扫描只负责发现候选文件并重新入队，不直接执行重型学习任务
 * 单轮扫描数量和最大扫描页数受配置及代码保护，避免初始部署时一次性扫穿几千个历史文件
 *
 * <p>
 * 阶段 3：队列抢占与串行消费
 *
 * <p>
 * ExternalAdapterSyncWorker 通过 SmartLifecycle 启动一个独立后台线程
 * Worker 每次只从 meta_external_document_sync 抢占一条可执行任务，并等待该文件进入终态后才处理下一条
 * 这是单机离线部署下的资源保护策略，用于限制 OCR、PDF 解析、GPU 和 CPU 消耗
 * 队列抢占 SQL 使用 PostgreSQL 的 FOR UPDATE SKIP LOCKED 和 UPDATE RETURNING
 * 如果运行环境改成 MySQL 或其他数据库，必须重新实现 ExternalDocumentSyncMapper 中的抢占 SQL
 *
 * <p>
 * 阶段 4：文件下载、对象存储归档和索引提交
 *
 * <p>
 * Worker 抢占任务后先回查第三方源表，确认文件仍然存在且仍然需要学习
 * ExternalFileDownloadClient 根据 filePath 调用第三方文件服务下载原始文件流
 * ExternalStorageDocumentClient 通过本应用 /v1/storage/documents/upload 接口写入 StorageDocument 并提交索引
 * 上传成功后记录 documentId，后续重试优先复用该 documentId 重新提交索引，避免重复上传同一份大文件
 *
 * <p>
 * 阶段 5：索引等待与第三方状态回写
 *
 * <p>
 * 文件提交索引后不会立即完成向量化，Worker 会按指数退避轮询 meta_storage_document 的 indexStatus
 * indexStatus 进入 INDEXED 时，适配器内部状态写为 INDEXED，并把第三方源表 status 回写为 2
 * indexStatus 进入 INDEX_FAILED 或等待超过 indexTimeout 时，任务进入重试或终态失败
 * 终态失败时第三方源表 status 回写为 3
 *
 * <p>
 * 阶段 6：异常、重试与停机恢复
 *
 * <p>
 * 下载失败、上传失败、索引失败或索引等待超时都会写入 lastError，并按 attemptCount 进入 RETRY_WAIT 或 FAILED
 * RETRY_WAIT 会释放 lockedBy 和 lockedAt，并通过 nextAttemptAt 控制下一次可抢占时间
 * 应用关闭导致 Worker 中断时，只释放当前任务并允许下次启动重新接管，不把文件直接判定为学习失败
 * lockTimeout 用于处理应用异常退出后遗留的运行中任务，超过锁超时时间后可被新的 Worker 抢占
 *
 * <p>
 * 关键约束
 *
 * <p>
 * 第三方源表 status 和适配器内部 syncStatus 是两套状态机，不能混用
 * 当前版本按 PostgreSQL 设计队列抢占，数据库方言不是可随意切换的实现细节
 * Worker 单线程不是性能缺陷，而是为了保护离线单机环境下的 OCR、PDF 解析和向量化资源
 * 下载日志中打印 Authorization 是当前离线排查场景的显式取舍，日志一旦外发或集中采集必须重新评估
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/12
 */
package com.metax.external.adapter;
