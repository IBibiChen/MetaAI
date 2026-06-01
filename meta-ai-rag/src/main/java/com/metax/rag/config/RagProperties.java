package com.metax.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RagProperties .
 *
 * <p>
 * RAG 模块统一配置入口，集中管理文档切分、检索参数、异步入库任务和 RustFS 对象存储配置
 * 默认值只作为企业级保守起点，具体知识库可以在请求侧覆盖 topK、similarityThreshold 和 filter 条件
 *
 * <p>
 * 设计说明：RAG 参数不要硬编码在业务代码里
 * chunk 参数影响入库质量和 embedding 成本
 * retrieval 参数影响召回质量和模型上下文占用
 * ingestion 参数影响异步任务状态保存周期
 * storage 参数决定 RustFS 对象存储连接方式
 *
 * <p>
 * 配置示例
 * <pre>{@code
 * metax.ai.rag.chunk.size=800
 * metax.ai.rag.chunk.min-chars=350
 * metax.ai.rag.retrieval.top-k=5
 * metax.ai.rag.retrieval.similarity-threshold=0.50
 * metax.ai.rag.ingestion.redis-key-prefix=rag:ingestion:job:
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

    private final Storage storage = new Storage();

    public Chunk getChunk() {
        return chunk;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public Ingestion getIngestion() {
        return ingestion;
    }

    public Storage getStorage() {
        return storage;
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
         * RAG 入库任务 Redis key 前缀
         *
         * <p>
         * key 示例：rag:ingestion:job:{jobId}
         */
        private String redisKeyPrefix = "rag:ingestion:job:";

        /**
         * job 状态保留时间
         *
         * <p>
         * 第一版 job 状态放 Redis，适合短期查询进度，长期审计后续应落 JDBC
         */
        private long jobTtlSeconds = 86400;

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }

        public long getJobTtlSeconds() {
            return jobTtlSeconds;
        }

        public void setJobTtlSeconds(long jobTtlSeconds) {
            this.jobTtlSeconds = jobTtlSeconds;
        }
    }

    public static class Storage {

        /**
         * RustFS S3 endpoint
         *
         * <p>
         * 本地 Docker 常见值为 http://localhost:9000
         */
        private String endpoint = "http://localhost:9000";

        /**
         * 知识库原始文件默认 bucket
         */
        private String bucket = "meta-ai-knowledge";

        /**
         * RustFS access key
         *
         * <p>
         * 生产环境必须通过环境变量或密钥系统注入，不要写死真实密钥
         */
        private String accessKey = "rustfsadmin";

        /**
         * RustFS secret key
         *
         * <p>
         * 生产环境必须通过环境变量或密钥系统注入，不要写死真实密钥
         */
        private String secretKey = "rustfsadmin";

        /**
         * S3 region
         *
         * <p>
         * RustFS 私有化部署通常不强依赖 region，但 AWS SDK 仍要求提供一个值
         */
        private String region = "us-east-1";

        /**
         * 本地知识库文件根目录
         *
         * <p>
         * 本地导入只允许读取该目录下的相对路径，避免接口读取任意系统文件
         */
        private String localRoot = "D:/meta-ai/knowledge";

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
    }
}
