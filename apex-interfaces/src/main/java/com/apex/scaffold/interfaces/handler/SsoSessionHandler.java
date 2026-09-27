package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.SysSsoSessionService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * SSO 会话管理处理器(函数式端点)
 *
 * <p>提供 SSO 单点登录会话的管理 API,包括会话查询、会话撤销和会话统计功能。
 * 支持按用户、提供商、会话状态等维度查询和管理 SSO 会话。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>会话查询:分页查询 SSO 会话列表,支持多维度筛选</li>
 *   <li>会话撤销:撤销指定会话或用户的所有会话,使会话失效</li>
 *   <li>会话统计:获取会话统计信息,包括活跃会话数、按提供商分组等</li>
 * </ul>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /admin-api/v1/sso/sessions - 查询会话列表(分页)</li>
 *   <li>POST /admin-api/v1/sso/sessions/{id}/revoke - 撤销指定会话</li>
 *   <li>POST /admin-api/v1/sso/sessions/users/{userId}/revoke - 撤销用户的所有会话</li>
 *   <li>GET /admin-api/v1/sso/sessions/statistics - 获取会话统计信息</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Tag(name = "后台-SSO 会话管理", description = "SSO 单点登录会话管理")
public class SsoSessionHandler extends BaseHandler {

    private final SysSsoSessionService sysSsoSessionService;

    public SsoSessionHandler(
            SysSsoSessionService sysSsoSessionService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.sysSsoSessionService = sysSsoSessionService;
    }

    /**
     * 分页查询 SSO 会话列表。
     *
     * <p>分页查询当前租户下的 SSO 会话，支持按提供商 ID、用户 ID、会话状态（ACTIVE/EXPIRED/REVOKED）
     * 组合筛选，页码默认 1，每页默认 20 条。</p>
     *
     * @param request 服务器请求，查询参数 page（页码）、size（每页数量）、userId（用户 ID）、
     *                providerId（SSO 提供商 ID）、status（会话状态）
     * @return 包含会话分页数据的 {@code Result} 响应（Mono）
     */
    @RequirePermission("system:sso:session:list")
    @Operation(
            operationId = "adminListSsoSessions",
            summary = "查询 SSO 会话列表",
            description = "分页查询当前租户下的 SSO 会话列表,支持按用户名、提供商、会话状态筛选")
    @Parameter(
            name = "page",
            description = "页码(从1开始)",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", example = "1"))
    @Parameter(
            name = "size",
            description = "每页数量",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", example = "20"))
    @Parameter(
            name = "username",
            description = "用户名(模糊查询)",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
    @Parameter(
            name = "providerId",
            description = "SSO 提供商 ID",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64"))
    @Parameter(
            name = "status",
            description = "会话状态(ACTIVE/EXPIRED/REVOKED)",
            in = ParameterIn.QUERY,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:sso:session:list 权限")
    })
    public Mono<ServerResponse> list(ServerRequest request) {
        int page = safeParseInt(request.queryParam("page").orElse("1"), 1);
        int size = safeParseInt(request.queryParam("size").orElse("20"), 20);
        Long userId = request.queryParam("userId").map(Long::parseLong).orElse(null);
        Long providerId = request.queryParam("providerId").map(Long::parseLong).orElse(null);
        String status = request.queryParam("status").orElse(null);

        return sysSsoSessionService.list(providerId, userId, status, page, size).flatMap(this::buildSuccess);
    }

    /**
     * 撤销指定的 SSO 会话。
     *
     * <p>根据路径中的会话 ID 撤销单个 SSO 会话，使其立即失效，用户需重新登录；
     * 响应中返回 {@code revoked} 标识撤销结果。</p>
     *
     * @param request 服务器请求，路径变量 id 为会话 ID（必须为正整数）
     * @return 包含 {@code revoked} 撤销结果的 {@code Result} 响应（Mono，ID 非法或会话不存在时发出错误信号）
     */
    @RequirePermission("system:sso:session:revoke")
    @Operation(
            operationId = "adminRevokeSsoSession",
            summary = "撤销指定 SSO 会话",
            description = "撤销指定的 SSO 会话,使该会话失效,用户需要重新登录")
    @Parameter(
            name = "id",
            description = "会话 ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64"))
    @RequestBody(description = "撤销原因(可选)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "撤销成功"),
        @ApiResponse(responseCode = "400", description = "参数错误,ID 必须为正整数"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:sso:session:revoke 权限"),
        @ApiResponse(responseCode = "404", description = "SSO 会话不存在")
    })
    @OperationLog(module = "SSO 会话管理", type = OperationLog.OperationType.UPDATE, description = "撤销 SSO 会话")
    public Mono<ServerResponse> revoke(ServerRequest request) {
        Long id = requirePositiveId(request);
        return sysSsoSessionService
                .revokeSession(id)
                .flatMap(result -> buildSuccess(java.util.Map.of("revoked", result)));
    }

    /**
     * 撤销指定用户的所有 SSO 会话。
     *
     * <p>根据路径中的用户 ID 撤销该用户的全部 SSO 会话，使其全部失效，用户需重新登录；
     * 响应中返回实际撤销的会话数量。</p>
     *
     * @param request 服务器请求，路径变量 userId 为用户 ID（必须为正整数）
     * @return 包含 {@code revokedCount}（撤销会话数）的 {@code Result} 响应（Mono，ID 非法或用户不存在时发出错误信号）
     */
    @RequirePermission("system:sso:session:revoke")
    @Operation(
            operationId = "adminRevokeUserSsoSessions",
            summary = "撤销用户的所有 SSO 会话",
            description = "撤销指定用户的所有 SSO 会话,使所有会话失效,用户需要重新登录")
    @Parameter(
            name = "userId",
            description = "用户 ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "integer", format = "int64"))
    @RequestBody(description = "撤销原因(可选)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "撤销成功"),
        @ApiResponse(responseCode = "400", description = "参数错误,ID 必须为正整数"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:sso:session:revoke 权限"),
        @ApiResponse(responseCode = "404", description = "用户不存在")
    })
    @OperationLog(module = "SSO 会话管理", type = OperationLog.OperationType.UPDATE, description = "撤销用户所有 SSO 会话")
    public Mono<ServerResponse> revokeUserSessions(ServerRequest request) {
        Long userId = requirePositiveId(request);
        return sysSsoSessionService
                .revokeUserSessions(userId)
                .flatMap(count -> buildSuccess(java.util.Map.of("revokedCount", count)));
    }

    /**
     * 获取 SSO 会话统计信息。
     *
     * <p>统计当前租户下的 SSO 会话情况，包括活跃会话数、过期会话数以及按提供商分组统计等。</p>
     *
     * @param request 服务器请求，无业务参数
     * @return 包含会话统计数据的 {@code Result} 响应（Mono）
     */
    @RequirePermission("system:sso:session:stat")
    @Operation(
            operationId = "adminGetSsoSessionStatistics",
            summary = "获取 SSO 会话统计信息",
            description = "获取当前租户下的 SSO 会话统计信息,包括活跃会话数、过期会话数、按提供商分组等")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "401", description = "未授权,需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足,需要 system:sso:session:stat 权限")
    })
    public Mono<ServerResponse> getStatistics(ServerRequest request) {
        return sysSsoSessionService.getStatistics().flatMap(this::buildSuccess);
    }
}
