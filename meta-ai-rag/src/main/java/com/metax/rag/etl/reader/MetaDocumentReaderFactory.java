package com.metax.rag.etl.reader;

import com.metax.rag.etl.resource.MetaDocumentResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.DocumentReader;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MetaDocumentReaderFactory .
 *
 * <p>
 * MetaDocumentReader 工厂，负责选择无状态 Reader 策略，并创建请求级 MetaDocumentReader
 * 官方 Reader 本身绑定 Resource，是有状态对象，不注册为 Spring 单例 Bean
 *
 * <p>
 * 当前类是 Factory(工厂模式) + Strategy(策略模式) 的组合入口
 * Factory(工厂模式) 负责接收 MetaDocumentResource 并创建统一的 DocumentReader
 * Strategy(策略模式) 负责封装不同 documentType 的官方 Reader 创建细节
 * 最终返回的 MetaDocumentReader 再通过 Delegation(委托模式) 把读取动作交给官方 Reader
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/31
 */
@Component
public class MetaDocumentReaderFactory {

    private static final Logger log = LoggerFactory.getLogger(MetaDocumentReaderFactory.class);

    private final List<MetaDocumentReaderStrategy> strategies;

    private final TikaDocumentReaderStrategy fallbackStrategy;

    public MetaDocumentReaderFactory(List<MetaDocumentReaderStrategy> strategies,
                                     TikaDocumentReaderStrategy fallbackStrategy) {
        this.strategies = strategies;
        this.fallbackStrategy = fallbackStrategy;
    }

    /**
     * 创建文档 Reader
     *
     * <p>
     * documentResource 已经完成来源抽象和 documentType 解析
     * 工厂只负责选择策略，具体官方 Reader 创建参数由策略 Bean 管理
     *
     * @param documentResource 文档资源
     * @return DocumentReader
     */
    public DocumentReader create(MetaDocumentResource documentResource) {
        MetaDocumentReaderStrategy strategy = strategies.stream()
                .filter(candidate -> candidate.supports(documentResource.documentType()))
                .findFirst()
                .orElse(fallbackStrategy);
        log.info("选择文档 Reader 策略：documentType = {}，source = {}，strategy = {}",
                documentResource.documentType(), documentResource.source(), strategy.getClass().getSimpleName());
        return new MetaDocumentReader(documentResource, strategy.create(documentResource));
    }
}

