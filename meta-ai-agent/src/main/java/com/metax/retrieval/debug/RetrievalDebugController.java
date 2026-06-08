package com.metax.retrieval.debug;

import com.metax.retrieval.debug.request.RetrievalDetailsRequest;
import com.metax.rag.retrieval.model.RetrievalChatDetailsResponse;
import com.metax.rag.retrieval.model.RetrievalSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RetrievalDebugController .
 *
 * <p>
 * 知识库问答和向量检索调试接口
 *
 * @author IBibiChen
 * @version v1.0
 * @since 2026/6/8
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "知识库检索调试", description = "知识库问答详情和直接检索调试接口")
public class RetrievalDebugController {

    private final RetrievalDebugService retrievalDebugService;

    /**
     * 知识库问答调试详情
     *
     * <p>
     * 返回模型回答和本次检索命中的引用来源，便于排查 topK、metadata filter 和 chunk 命中质量
     *
     * @param request 知识库检索调试请求参数
     * @return 知识库问答调试详情
     */
    @PostMapping(value = "/v1/rag/details")
    @Operation(summary = "知识库问答调试详情", description = "返回 answer、references 和 trace，用于调试召回质量、过滤条件和后处理效果")
    public RetrievalChatDetailsResponse details(@Valid @ParameterObject RetrievalDetailsRequest request) {
        return retrievalDebugService.details(request);
    }

    /**
     * 知识库检索调试
     *
     * <p>
     * 绕过 ChatClient 和 ChatModel，直接查看 VectorStore 在当前过滤条件下召回的 chunk
     *
     * @param request 知识库检索调试请求参数
     * @return 直接检索响应
     */
    @PostMapping(value = "/v1/rag/search")
    @Operation(summary = "知识库检索调试", description = "绕过 ChatClient 和 ChatModel，直接返回向量库召回的 chunk")
    public RetrievalSearchResponse search(@Valid @ParameterObject RetrievalDetailsRequest request) {
        return retrievalDebugService.search(request);
    }
}
