package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.OnlineUserQueryService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 在线用户处理器（函数式端点）。
 *
 * <p>提供在线用户列表查询 API（C4 在线用户/会话管理），在线判定基于
 * {@code sys_user.last_heartbeat_time}（登录与活跃请求更新，最近 5 分钟
 * 有心跳视为在线）。强制下线由 {@code AdminUserManagementHandler} 的
 * 会话撤销端点承担。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>在线用户分页查询：查询最近 5 分钟内有心跳的在线用户列表，按最后心跳时间倒序返回</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>GET /admin-api/v1/online-users — 分页查询在线用户</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see OnlineUserQueryService
 */
@Component
@Tag(name = "后台-在线用户", description = "在线用户/会话管理")
public class OnlineUserHandler extends BaseHandler {

    /** 在线用户查询服务 */
    private final OnlineUserQueryService onlineUserQueryService;

    /**
     * 创建在线用户处理器。
     *
     * @param onlineUserQueryService 在线用户查询服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public OnlineUserHandler(
            OnlineUserQueryService onlineUserQueryService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.onlineUserQueryService = onlineUserQueryService;
    }

    /**
     * 分页查询在线用户。
     *
     * <p>查询参数：{@code page}（页码，默认 1）、{@code size}（每页大小，默认 20）。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为 {@code PageResponse<OnlineUserDTO>}
     */
    @RequirePermission("system:online-user:list")
    @Operation(
            operationId = "adminListOnlineUsers",
            summary = "分页查询在线用户",
            description = "查询最近5分钟内有心跳的在线用户列表，支持分页查询，按最后心跳时间倒序返回")
    @Parameter(
            name = "page",
            description = "页码，从1开始",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "integer", defaultValue = "1"))
    @Parameter(
            name = "size",
            description = "每页大小",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "integer", defaultValue = "20"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:online-user:list权限")
    })
    @OperationLog(
            module = "在线用户",
            type = OperationLog.OperationType.SELECT,
            description = "分页查询在线用户",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> list(ServerRequest request) {
        int page = safeParseInt(request.queryParam("page").orElse(null), 1);
        int size = safeParseInt(request.queryParam("size").orElse(null), 20);
        return onlineUserQueryService.findOnlineUsers(page, size).flatMap(this::buildSuccess);
    }
}
