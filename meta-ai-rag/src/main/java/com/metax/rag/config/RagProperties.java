package com.metax.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.ai.document.MetadataMode;

import java.time.Duration;

/**
 * RagProperties .
 *
 * <p>
 * RAG 模块统一配置入口，集中管理文档切分、检索参数、异步索引执行和文档存储配置
 * 默认值只作为企业级保守起点，具体知识库可以在请求侧覆盖 topK、similarityThreshold 和 filter 条件
 *
 * <p>
 * 设计说明：RAG 参数不要硬编码在业务代码里
 * chunk 参数影响入库质量和 embedding 成本
 * retrieval 参数影响召回质量和模型上下文占用
 * ingestion 参数影响异步执行状态保存周期
 * storage 参数决定文档存储实现和连接方式
 *
 * <p>
 * 配置示例
 * <pre>{@code
 * metax.ai.rag.chunk.size=800
 * metax.ai.rag.chunk.min-chars=350
 * metax.ai.rag.retrieval.top-k=5
 * metax.ai.rag.retrieval.similarity-threshold=0.50
 * metax.ai.rag.ingestion.redis-key-prefix=rag:ingestion:run:
 * metax.ai.rag.snapshot.enabled=false
 * metax.ai.rag.snapshot.output-dir=D:/meta-ai/rag-snapshots
 * metax.ai.rag.storage.provider=object
 * metax.ai.rag.storage.endpoint=http://localhost:9000
 * metax.ai.rag.storage.local-root=D:/meta-ai/knowledge
 * }</pre>
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@ConfigurationProperties(prefix = "metax.ai.rag")
public class RagProperties {

    private final Chunk chunk = new Chunk();

    private final Retrieval retrieval = new Retrieval();

    private final Ingestion ingestion = new Ingestion();

    private final Snapshot snapshot = new Snapshot();

    private final Storage storage = new Storage();

    private final Ocr ocr = new Ocr();

    public Chunk getChunk() {
        return chunk;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public Ingestion getIngestion() {
        return ingestion;
    }

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public Storage getStorage() {
        return storage;
    }

    public Ocr getOcr() {
        return ocr;
    }

    public static class Chunk {

        /**
         * chunk 目标 token 数
         *
         * <p>
         * 值越大，单个 chunk 保留上下文越完整，但召回后占用模型上下文越多
         */
        private int size = 800;

        /**
         * 最小 chunk 字符数
         *
         * <p>
         * TokenTextSplitter 会尽量在标点处分割，但不会为了标点生成过短 chunk
         */
        private int minChars = 350;

        /**
         * 低于该长度的 chunk 不进入 embedding
         *
         * <p>
         * 过滤过短碎片可以减少无意义向量，避免目录、页眉、页脚污染召回结果
         */
        private int minLengthToEmbed = 20;

        /**
         * 单个文档最多切分 chunk 数
         *
         * <p>
         * 这是防御性参数，避免异常大文件一次入库产生过多 embedding 请求
         */
        private int maxNumChunks = 10000;

        /**
         * 是否保留换行和分隔符
         *
         * <p>
         * markdown、代码和列表类文档建议保留分隔符，便于模型理解结构
         */
        private boolean keepSeparator = true;

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getMinChars() {
            return minChars;
        }

        public void setMinChars(int minChars) {
            this.minChars = minChars;
        }

        public int getMinLengthToEmbed() {
            return minLengthToEmbed;
        }

        public void setMinLengthToEmbed(int minLengthToEmbed) {
            this.minLengthToEmbed = minLengthToEmbed;
        }

        public int getMaxNumChunks() {
            return maxNumChunks;
        }

        public void setMaxNumChunks(int maxNumChunks) {
            this.maxNumChunks = maxNumChunks;
        }

        public boolean isKeepSeparator() {
            return keepSeparator;
        }

        public void setKeepSeparator(boolean keepSeparator) {
            this.keepSeparator = keepSeparator;
        }
    }

    public static class Retrieval {

        /**
         * 默认召回 chunk 数
         *
         * <p>
         * topK 不是越大越好，过大会把弱相关 chunk 塞进上下文，降低回答质量并增加 token 成本
         */
        private int topK = 5;

        /**
         * 默认相似度阈值
         *
         * <p>
         * 阈值过低容易召回噪音，阈值过高可能导致没有上下文可用
         */
        private double similarityThreshold = 0.5;

        /**
         * 空上下文时是否允许模型继续回答
         *
         * <p>
         * true 表示知识库优先，检索不到上下文时允许模型基于通用能力、系统提示词和会话上下文回答
         * false 表示严格 RAG，检索不到上下文时由 ContextualQueryAugmenter 引导模型拒答
         */
        private boolean allowEmptyContext = true;

        /**
         * 是否启用权限过滤
         *
         * <p>
         * 关闭时只使用 tenantId、kbId、documentId 和 documentType 做范围过滤
         * 开启后追加 visibility、deptId 和 userId 权限过滤
         */
        private boolean permissionFilterEnabled = false;

        private final Decision decision = new Decision();

        private final QueryTransformer queryTransformer = new QueryTransformer();

        private final PostProcessor postProcessor = new PostProcessor();

        private final Observability observability = new Observability();

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public boolean isAllowEmptyContext() {
            return allowEmptyContext;
        }

        public void setAllowEmptyContext(boolean allowEmptyContext) {
            this.allowEmptyContext = allowEmptyContext;
        }

        public boolean isPermissionFilterEnabled() {
            return permissionFilterEnabled;
        }

        public void setPermissionFilterEnabled(boolean permissionFilterEnabled) {
            this.permissionFilterEnabled = permissionFilterEnabled;
        }

        public Decision getDecision() {
            return decision;
        }

        public QueryTransformer getQueryTransformer() {
            return queryTransformer;
        }

        public PostProcessor getPostProcessor() {
            return postProcessor;
        }

        public Observability getObservability() {
            return observability;
        }
    }

    public static class Decision {

        /**
         * 检索决策模式
         *
         * <p>
         * rule 表示只使用规则判断，hybrid 表示规则未知时调用 ChatModel 判断，llm 表示全部交给 ChatModel 判断
         */
        private String mode = "hybrid";

        /**
         * 检索决策模型温度
         *
         * <p>
         * 意图分类需要稳定输出，默认使用 0.0
         */
        private double temperature = 0.0;

        /**
         * 检索决策模型最大输出 token 数
         */
        private int maxTokens = 16;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    public static class QueryTransformer {

        /**
         * 是否启用检索前 query 转换
         *
         * <p>
         * 生产默认关闭，避免每次检索都额外调用一次模型
         */
        private boolean enabled = false;

        /**
         * query 转换模式
         *
         * <p>
         * none 表示不转换，compression 适合多轮追问，rewrite 适合单轮检索优化
         */
        private String mode = "none";

        /**
         * 转换 query 使用的低温度
         *
         * <p>
         * query 转换追求稳定和可重复，通常使用 0.0
         */
        private double temperature = 0.0;

        /**
         * query 转换输出的最大 token 数
         */
        private int maxTokens = 512;

        /**
         * rewrite 模式面向的目标检索系统
         */
        private String targetSearchSystem = "vector store";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public String getTargetSearchSystem() {
            return targetSearchSystem;
        }

        public void setTargetSearchSystem(String targetSearchSystem) {
            this.targetSearchSystem = targetSearchSystem;
        }
    }

    public static class PostProcessor {

        /**
         * 是否启用检索后文档处理
         */
        private boolean enabled = true;

        /**
         * 是否按 chunkId 或 contentHash 去重
         */
        private boolean deduplicateEnabled = true;

        /**
         * 是否启用 rerank 扩展
         *
         * <p>
         * 第一版保留开关，不强依赖外部 rerank 服务
         */
        private boolean rerankEnabled = false;

        /**
         * 最终进入上下文的最大文档数
         */
        private int maxContextDocuments = 5;

        /**
         * 最终进入上下文的最大字符数
         */
        private int maxContextChars = 12000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isDeduplicateEnabled() {
            return deduplicateEnabled;
        }

        public void setDeduplicateEnabled(boolean deduplicateEnabled) {
            this.deduplicateEnabled = deduplicateEnabled;
        }

        public boolean isRerankEnabled() {
            return rerankEnabled;
        }

        public void setRerankEnabled(boolean rerankEnabled) {
            this.rerankEnabled = rerankEnabled;
        }

        public int getMaxContextDocuments() {
            return maxContextDocuments;
        }

        public void setMaxContextDocuments(int maxContextDocuments) {
            this.maxContextDocuments = maxContextDocuments;
        }

        public int getMaxContextChars() {
            return maxContextChars;
        }

        public void setMaxContextChars(int maxContextChars) {
            this.maxContextChars = maxContextChars;
        }
    }

    public static class Observability {

        /**
         * 是否启用 RAG 检索链路 trace
         *
         * <p>
         * trace 只在 details 响应中返回，不写完整 prompt 到日志
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Ingestion {

        /**
         * RAG 入库执行 Redis key 前缀
         *
         * <p>
         * key 示例：rag:ingestion:run:{runId}
         */
        private String redisKeyPrefix = "rag:ingestion:run:";

        /**
         * run 状态保留时间
         *
         * <p>
         * 第一版 run 状态放 Redis，适合短期查询进度，长期审计后续应落 JDBC
         */
        private long runTtlSeconds = 86400;

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }

        public long getRunTtlSeconds() {
            return runTtlSeconds;
        }

        public void setRunTtlSeconds(long runTtlSeconds) {
            this.runTtlSeconds = runTtlSeconds;
        }
    }

    public static class Snapshot {

        /**
         * 是否启用 ETL 文档快照导出
         *
         * <p>
         * 快照用于排查 Reader 和 Transformer 处理后的 chunk 内容，不作为向量库写入成功凭证
         */
        private boolean enabled = false;

        /**
         * ETL 快照文件输出目录
         *
         * <p>
         * 生产环境默认关闭该能力，避免知识库内容被无意落盘
         */
        private String outputDir = "D:/meta-ai/rag-snapshots";

        /**
         * 是否写入 Spring AI FileDocumentWriter 的文档分隔标记
         *
         * <p>
         * 开启后便于观察 chunk 边界，但标记中的页码依赖 Reader 是否提供 page_number metadata
         */
        private boolean withDocumentMarkers = true;

        /**
         * 快照内容使用的 metadata 模式
         *
         * <p>
         * ALL 适合排查 metadata 和 ContentFormatter，EMBED 更接近 embedding 输入，NONE 只查看纯文本
         */
        private MetadataMode metadataMode = MetadataMode.ALL;

        /**
         * 是否追加写入同一个快照文件
         *
         * <p>
         * 默认覆盖同一文档的旧快照，方便查看最近一次索引结果
         */
        private boolean append = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getOutputDir() {
            return outputDir;
        }

        public void setOutputDir(String outputDir) {
            this.outputDir = outputDir;
        }

        public boolean isWithDocumentMarkers() {
            return withDocumentMarkers;
        }

        public void setWithDocumentMarkers(boolean withDocumentMarkers) {
            this.withDocumentMarkers = withDocumentMarkers;
        }

        public MetadataMode getMetadataMode() {
            return metadataMode;
        }

        public void setMetadataMode(MetadataMode metadataMode) {
            this.metadataMode = metadataMode;
        }

        public boolean isAppend() {
            return append;
        }

        public void setAppend(boolean append) {
            this.append = append;
        }
    }

    public static class Storage {

        /**
         * 文档存储 provider
         *
         * <p>
         * object 表示对象存储，当前默认使用 RustFS，兼容 MinIO 等 S3 协议对象存储
         * legacy 预留给老系统文件服务适配器
         */
        private String provider = "object";

        /**
         * 对象存储 endpoint
         *
         * <p>
         * RustFS / MinIO 本地 Docker 常见值为 http://localhost:9000
         */
        private String endpoint = "http://localhost:9000";

        /**
         * 知识库原始文件默认 bucket
         */
        private String bucket = "meta-ai-knowledge";

        /**
         * 对象存储 access key
         *
         * <p>
         * 生产环境必须通过环境变量或密钥系统注入，不要写死真实密钥
         */
        private String accessKey = "rustfsadmin";

        /**
         * 对象存储 secret key
         *
         * <p>
         * 生产环境必须通过环境变量或密钥系统注入，不要写死真实密钥
         */
        private String secretKey = "rustfsadmin";

        /**
         * 对象存储 region
         *
         * <p>
         * RustFS / MinIO 私有化部署通常不强依赖 region，但 AWS SDK 仍要求提供一个值
         */
        private String region = "us-east-1";

        /**
         * 本地知识库文件根目录
         *
         * <p>
         * 本地导入只允许读取该目录下的相对路径，避免接口读取任意系统文件
         */
        private String localRoot = "D:/meta-ai/knowledge";

        /**
         * 是否在对象存储服务启动时初始化默认 bucket
         *
         * <p>
         * 本地开发可开启，生产环境建议由部署脚本或运维流程创建 bucket
         */
        private boolean initializeBucket = false;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getLocalRoot() {
            return localRoot;
        }

        public void setLocalRoot(String localRoot) {
            this.localRoot = localRoot;
        }

        public boolean isInitializeBucket() {
            return initializeBucket;
        }

        public void setInitializeBucket(boolean initializeBucket) {
            this.initializeBucket = initializeBucket;
        }
    }

    public static class Ocr {

        /**
         * 是否启用 OCR Reader
         *
         * <p>
         * 开启后 pdf 文档会优先交给 PaddleOCR 解析，适合扫描件 PDF
         */
        private boolean enabled = true;

        /**
         * OCR provider
         *
         * <p>
         * 当前只支持 paddle，后续可扩展其他 OCR 服务
         */
        private String provider = "paddle";

        /**
         * PaddleOCR 服务基础地址
         *
         * <p>
         * 本地 Docker Basic Serving 常见值为 http://localhost:8080
         */
        private String baseUrl = "http://localhost:8080";

        /**
         * PaddleOCR OCR 接口路径
         */
        private String endpoint = "/ocr";

        /**
         * OCR 请求超时时间
         *
         * <p>
         * 扫描 PDF 识别通常比普通 Reader 慢，默认给本地 OCR 服务更长等待时间
         */
        private Duration timeout = Duration.ofSeconds(300);

        /**
         * 是否要求 OCR 服务返回可视化结果
         *
         * <p>
         * RAG 入库只需要文本，默认关闭可视化以减少响应体大小
         */
        private boolean visualize = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public boolean isVisualize() {
            return visualize;
        }

        public void setVisualize(boolean visualize) {
            this.visualize = visualize;
        }

    }

}
