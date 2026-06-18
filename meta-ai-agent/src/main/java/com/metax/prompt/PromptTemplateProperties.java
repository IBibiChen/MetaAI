package com.metax.prompt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PromptTemplateProperties .
 *
 * <p>
 * 管理 prompt 模板加载相关配置
 * locations 按顺序声明模板查找位置，Docker 外部目录优先，jar 内置 classpath 模板兜底
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/18
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "metax.ai.prompt")
public class PromptTemplateProperties {

    /**
     * prompt 模板查找位置
     */
    private List<String> locations = new ArrayList<>(List.of("file:/app/prompts/", "classpath:/prompts/"));
}
