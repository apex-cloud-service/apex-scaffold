package com.apex.scaffold.knowledgeservice.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.knowledge.application.dto.KnowledgeSpaceDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeSpaceSaveRequest;
import com.apex.scaffold.knowledge.application.service.KnowledgeSpaceAppService;
import com.apex.scaffold.knowledgeservice.interfaces.util.KnowledgeRequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
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
@Tag(name = "知识库-空间管理", description = "知识空间的分页查询、详情、创建、更新与删除；权限码 kb:space:*")
public class KnowledgeSpaceHandler {

    /** 空间分页的统一响应结构，仅用于接口文档。 */
    @Schema(name = "KbSpacePageResult")
    public static class KnowledgeSpacePageResult extends Result<java.util.List<KnowledgeSpaceDTO>> {}

    /** 单个空间的统一响应结构，仅用于接口文档。 */
    @Schema(name = "KbSpaceResult")
    public static class KnowledgeSpaceResult extends Result<KnowledgeSpaceDTO> {}

    /** 空间删除的布尔结果，仅用于接口文档。 */
    @Schema(name = "KbSpaceBooleanResult")
    public static class KnowledgeBooleanResult extends Result<Boolean> {}

    private final KnowledgeSpaceAppService spaceAppService;

    private final KnowledgeRequestHandlerUtil requestUtil;

    /**
     * 分页查询空间。
     *
     * @param request 请求（keyword/page/size 查询参数）
     * @return 空间分页响应
     */
    @RequirePermission("kb:space:read")
    @Operation(
            operationId = "kbPageSpaces",
            summary = "分页查询知识空间",
            description = "按关键字模糊匹配空间名称分页返回；keyword 为空时返回全部空间。需 kb:space:read 权限")
    @Parameter(
            name = "keyword",
            in = ParameterIn.QUERY,
            description = "空间名称检索关键字，可空",
            schema = @Schema(type = "string"))
    @Parameter(
            name = "page",
            in = ParameterIn.QUERY,
            description = "页码，从 1 开始，缺省 1",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "1", minimum = "1"))
    @Parameter(
            name = "size",
            in = ParameterIn.QUERY,
            description = "每页数量，缺省 20",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "20", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "分页查询成功，data 为空间分页结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KnowledgeSpacePageResult.class))),
        @ApiResponse(responseCode = "400", description = "分页参数格式非法"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:space:read 权限")
    })
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
    @Operation(
            operationId = "kbGetSpace",
            summary = "查询知识空间详情",
            description = "按空间 ID 返回空间详情；空间不存在时返回业务错误。需 kb:space:read 权限")
    @Parameter(
            name = "spaceId",
            in = ParameterIn.PATH,
            required = true,
            description = "空间 ID",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为空间详情",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KnowledgeSpaceResult.class))),
        @ApiResponse(responseCode = "400", description = "空间 ID 格式非法"),
        @ApiResponse(responseCode = "404", description = "空间不存在"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:space:read 权限")
    })
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
    @Operation(
            operationId = "kbCreateSpace",
            summary = "创建知识空间",
            description = "创建新的知识空间；name 不能为空白且最长 64 字符，description 最长 512 字符。需 kb:space:create 权限")
    @RequestBody(
            required = true,
            description = "空间创建请求",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = KnowledgeSpaceSaveRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功，data 为创建后的空间",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KnowledgeSpaceResult.class))),
        @ApiResponse(responseCode = "400", description = "名称空白、超长或请求体缺失"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:space:create 权限")
    })
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
    @Operation(
            operationId = "kbUpdateSpace",
            summary = "更新知识空间",
            description = "整体更新指定空间；status 为 0 表示停用、1 表示正常。需 kb:space:update 权限")
    @Parameter(
            name = "spaceId",
            in = ParameterIn.PATH,
            required = true,
            description = "空间 ID",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    @RequestBody(
            required = true,
            description = "空间更新请求",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = KnowledgeSpaceSaveRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功，data 为更新后的空间",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KnowledgeSpaceResult.class))),
        @ApiResponse(responseCode = "400", description = "参数非法、空间 ID 格式错误或请求体缺失"),
        @ApiResponse(responseCode = "404", description = "空间不存在"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:space:update 权限")
    })
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
    @Operation(operationId = "kbDeleteSpace", summary = "删除知识空间", description = "删除指定空间及其索引；不可恢复。需 kb:space:delete 权限")
    @Parameter(
            name = "spaceId",
            in = ParameterIn.PATH,
            required = true,
            description = "空间 ID",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KnowledgeBooleanResult.class))),
        @ApiResponse(responseCode = "400", description = "空间 ID 格式非法"),
        @ApiResponse(responseCode = "404", description = "空间不存在"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:space:delete 权限")
    })
    public Mono<ServerResponse> delete(ServerRequest request) {
        Long spaceId = Long.valueOf(request.pathVariable("spaceId"));
        return spaceAppService
                .delete(spaceId)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }
}
