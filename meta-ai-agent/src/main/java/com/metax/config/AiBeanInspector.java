package com.metax.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * AiBeanInspector .
 *
 * <p>
 * AI 相关 bean 的诊断打印工具，仅当 metax.debug.print-ai-beans=true 时启用 (默认关闭，不影响正常启动)
 * 用于核实当前 ChatModel / EmbeddingModel / VectorStore / ChatMemory / ChatMemoryRepository 的实际装配情况 (bean 名与具体类型)
 * ChatMemory 预期包含 redisChatMemory 和 jdbcChatMemory，默认 ChatClient 显式绑定 redisChatMemory
 *
 * <p>
 * 启用方式：启动参数追加 --metax.debug.print-ai-beans=true
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/5/29
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "metax.debug.print-ai-beans", havingValue = "true")
public class AiBeanInspector implements CommandLineRunner {

    private final ApplicationContext ctx;

    public AiBeanInspector(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run(String... args) {
        printBeans(ChatModel.class);
        printBeans(EmbeddingModel.class);
        printBeans(VectorStore.class);
        printBeans(ChatMemory.class);
        printBeans(ChatMemoryRepository.class);
    }

    /**
     * 打印指定类型的全部 bean 名与具体实现类型
     *
     * @param type bean 类型
     */
    private void printBeans(Class<?> type) {
        String[] names = ctx.getBeanNamesForType(type);
        log.info("==== {} 共 {} 个 ====", type.getSimpleName(), names.length);
        for (String name : names) {
            log.info("  bean: name = {}, type = {}", name, ctx.getType(name).getName());
        }
    }

}
