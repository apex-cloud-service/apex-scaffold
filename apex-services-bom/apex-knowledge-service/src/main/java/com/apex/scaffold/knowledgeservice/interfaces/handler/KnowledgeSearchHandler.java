package com.apex.scaffold.knowledgeservice.interfaces.handler;

import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.knowledge.application.dto.KbSearchRequest;
import com.apex.scaffold.knowledge.application.service.KnowledgeSearchAppService;
import com.apex.scaffold.knowledge.application.service.KnowledgeSpaceAppService;
import com.apex.scaffold.knowledgeservice.interfaces.util.KnowledgeRequestHandlerUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 知识检索 Handler：业务端混合检索与可用空间列表。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchHandler {

    private final KnowledgeSearchAppService searchAppService;

    private final KnowledgeSpaceAppService spaceAppService;

    private final KnowledgeRequestHandlerUtil requestUtil;

    /**
     * 知识检索（GET 参数：keyword/spaceIds(逗号分隔)/page/size）。
     *
     * @param request 请求
     * @return 检索结果响应
     */
    @RequirePermission("kb:search:query")
    public Mono<ServerResponse> search(ServerRequest request) {
        String keyword = request.queryParam("keyword").orElse(null);
        if (keyword == null || keyword.isBlank()) {
            return requestUtil.handleError(new com.apex.scaffold.common.exception.BusinessException(
                    com.apex.scaffold.common.result.ResultCode.PARAM_ERROR, "检索关键词不能为空"));
        }
        int page = KnowledgeRequestHandlerUtil.safeParseInt(
                request.queryParam("page").orElse(null), 1);
        int size = KnowledgeRequestHandlerUtil.safeParseInt(
                request.queryParam("size").orElse(null), 10);
        java.util.List<Long> spaceIds =
                parseSpaceIds(request.queryParam("spaceIds").orElse(null));
        return searchAppService
                .search(new KbSearchRequest(keyword, spaceIds, page, size))
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 查询租户内可用空间列表（检索与问答的空间选择器）。
     *
     * @param request 请求
     * @return 空间列表响应
     */
    public Mono<ServerResponse> spaces(ServerRequest request) {
        return spaceAppService
                .listActive()
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 解析逗号分隔的空间 ID 参数。
     *
     * @param raw 原始参数
     * @return 空间 ID 列表（无有效项时为 null）
     */
    private java.util.List<Long> parseSpaceIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        java.util.List<Long> ids = java.util.Arrays.stream(raw.split(","))
                .map(KnowledgeRequestHandlerUtil::safeParseLong)
                .filter(java.util.Objects::nonNull)
                .toList();
        return ids.isEmpty() ? null : ids;
    }
}
