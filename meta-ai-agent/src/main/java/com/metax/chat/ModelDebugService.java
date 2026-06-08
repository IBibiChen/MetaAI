package com.metax.chat;

import com.metax.chat.support.ChatDefaults;
import com.metax.chat.request.ModelChatRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * ModelDebugService .
 *
 * <p>
 * 当前 ChatModel 直连调试服务
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@Service
@RequiredArgsConstructor
public class ModelDebugService {

    private final ChatModel chatModel;

    /**
     * 直连当前 ChatModel
     *
     * @param request 模型直连请求参数
     * @return 模型响应内容
     */
    public String chat(ModelChatRequest request) {
        return chatModel.call(messageOrDefault(request.getMsg(), ChatDefaults.CHAT_MESSAGE));
    }

    private String messageOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
