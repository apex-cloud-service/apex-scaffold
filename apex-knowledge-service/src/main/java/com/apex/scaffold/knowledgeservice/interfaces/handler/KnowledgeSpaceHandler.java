package com.apex.scaffold.knowledgeservice.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.knowledge.application.dto.KnowledgeSpaceSaveRequest;
import com.apex.scaffold.knowledge.application.service.KnowledgeSpaceAppService;
import com.apex.scaffold.knowledgeservice.interfaces.util.KnowledgeRequestHandlerUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 知识空间管理 Handler：管理端空间 CRUD。
 *
 * <p>权限码与 Flyway 权限种子（V91 kb:space:*）一一对应；写操作带审计注解。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSpaceHandler {

    private final KnowledgeSpaceAppService spaceAppService;

    private final KnowledgeRequestHandlerUtil requestUtil;

    /**
     * 分页查询空间。
     *
     * @param request 请求（keyword/page/size 查询参数）
     * @return 空间分页响应
     */
    @RequirePermission("kb:space:read")
    public Mono<ServerResponse> page(ServerRequest request) {
        String keyword = request.queryParam("keyword").orElse(null);
        int pageNum = KnowledgeRequestHandlerUtil.safeParseInt(
                request.queryParam("page").orElse(null), 1);
        int pageSize = KnowledgeRequestHandlerUtil.safeParseInt(
                request.queryParam("size").orElse(null), 20);
        return spaceAppService
                .findPage(keyword, pageNum, pageSize)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 查询空间详情。
     *
     * @param request 请求（spaceId 路径变量）
     * @return 空间详情响应
     */
    @RequirePermission("kb:space:read")
    public Mono<ServerResponse> detail(ServerRequest request) {
        Long spaceId = Long.valueOf(request.pathVariable("spaceId"));
        return spaceAppService
                .findById(spaceId)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 创建空间。
     *
     * @param request 请求（JSON body）
     * @return 创建后的空间
     */
    @OperationLog(
            module = "知识库",
            type = com.apex.scaffold.common.annotation.OperationLog.OperationType.INSERT,
            description = "创建知识空间")
    @RequirePermission("kb:space:create")
    public Mono<ServerResponse> create(ServerRequest request) {
        return requestUtil
                .handleRequestBody(request, KnowledgeSpaceSaveRequest.class)
                .flatMap(spaceAppService::create)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 更新空间。
     *
     * @param request 请求（spaceId 路径变量 + JSON body）
     * @return 更新后的空间
     */
    @OperationLog(
            module = "知识库",
            type = com.apex.scaffold.common.annotation.OperationLog.OperationType.UPDATE,
            description = "更新知识空间")
    @RequirePermission("kb:space:update")
    public Mono<ServerResponse> update(ServerRequest request) {
        Long spaceId = Long.valueOf(request.pathVariable("spaceId"));
        return requestUtil
                .handleRequestBody(request, KnowledgeSpaceSaveRequest.class)
                .flatMap(body -> spaceAppService.update(spaceId, body))
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 删除空间。
     *
     * @param request 请求（spaceId 路径变量）
     * @return 删除结果
     */
    @OperationLog(
            module = "知识库",
            type = com.apex.scaffold.common.annotation.OperationLog.OperationType.DELETE,
            description = "删除知识空间")
    @RequirePermission("kb:space:delete")
    public Mono<ServerResponse> delete(ServerRequest request) {
        Long spaceId = Long.valueOf(request.pathVariable("spaceId"));
        return spaceAppService
                .delete(spaceId)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }
}
