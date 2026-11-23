package com.apex.scaffold.knowledgeservice.interfaces.handler;

import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.knowledge.application.dto.KbSearchRequest;
import com.apex.scaffold.knowledge.application.dto.KbSearchResultDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeSpaceDTO;
import com.apex.scaffold.knowledge.application.service.KnowledgeSearchAppService;
import com.apex.scaffold.knowledge.application.service.KnowledgeSpaceAppService;
import com.apex.scaffold.knowledgeservice.interfaces.util.KnowledgeRequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "知识库-检索与问答", description = "知识混合检索与可用空间选择；权限码 kb:search:query")
public class KnowledgeSearchHandler {

    /** 检索结果的统一响应结构，仅用于接口文档。 */
    @Schema(name = "KbSearchResultWrapper")
    public static class KbSearchResultWrapper extends Result<KbSearchResultDTO> {}

    /** 可用空间列表的统一响应结构，仅用于接口文档。 */
    @Schema(name = "KbActiveSpaceListResult")
    public static class KnowledgeSpaceListResult extends Result<java.util.List<KnowledgeSpaceDTO>> {}

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
    @Operation(
            operationId = "kbSearch",
            summary = "知识混合检索",
            description = "按关键词执行词法检索，向量化能力启用时叠加语义检索；hybridUsed 反映本次是否真正启用语义检索。需 kb:search:query 权限")
    @Parameter(
            name = "keyword",
            in = ParameterIn.QUERY,
            required = true,
            description = "检索关键词，不能为空白",
            schema = @Schema(type = "string"))
    @Parameter(
            name = "spaceIds",
            in = ParameterIn.QUERY,
            description = "限定检索的空间 ID，逗号分隔；为空表示租户内全部正常空间",
            schema = @Schema(type = "string", example = "1,2"))
    @Parameter(
            name = "page",
            in = ParameterIn.QUERY,
            description = "页码，从 1 开始，缺省 1",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "1", minimum = "1"))
    @Parameter(
            name = "size",
            in = ParameterIn.QUERY,
            description = "每页数量，缺省 10",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "10", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "检索成功，data 为分页命中结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KbSearchResultWrapper.class))),
        @ApiResponse(responseCode = "400", description = "关键词为空或分页参数非法"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:search:query 权限")
    })
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
    @Operation(
            operationId = "kbListActiveSpaces",
            summary = "查询可用知识空间列表",
            description = "返回当前租户内全部正常状态的空间，供检索与问答的空间选择器使用")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为可用空间列表",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KnowledgeSpaceListResult.class))),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足")
    })
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
