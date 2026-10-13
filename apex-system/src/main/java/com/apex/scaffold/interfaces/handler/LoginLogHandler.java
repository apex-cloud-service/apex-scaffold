package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.LoginLogDTO;
import com.apex.scaffold.application.service.LoginLogService;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
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
 * 登录日志处理器（函数式端点）。
 *
 * <p>提供登录日志的分页回溯查询 API，要求 {@code system:login-log:list}
 * 权限。支持按登录账号模糊匹配与登录状态筛选，按登录时间倒序返回，
 * 支撑安全审计与异常登录排查。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>登录日志分页查询：支持按登录账号模糊匹配与登录状态筛选，按登录时间倒序返回</li>
 * </ul>
 *
 * <h2>接口列表</h2>
 * <ul>
 *   <li>GET /admin-api/v1/login-logs — 分页查询登录日志</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see LoginLogService
 */
@Component
@Tag(name = "后台-登录日志", description = "登录日志回溯与安全审计")
public class LoginLogHandler extends BaseHandler {

    /**
     * 登录日志应用服务。
     */
    private final LoginLogService loginLogService;

    /**
     * 创建登录日志处理器。
     *
     * @param loginLogService 登录日志应用服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public LoginLogHandler(LoginLogService loginLogService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.loginLogService = loginLogService;
    }

    /**
     * 分页查询登录日志。
     *
     * @param request HTTP 请求；{@code page} 缺省 1，{@code size} 缺省 20，
     *                {@code username} 与 {@code status} 可缺省
     * @return 带 traceId 的统一成功响应，data 为登录日志分页结果
     */
    @RequirePermission("system:login-log:list")
    @Operation(
            operationId = "adminListLoginLogs",
            summary = "分页查询登录日志",
            description = "支持按登录账号模糊匹配与登录状态筛选，按登录时间倒序返回，支撑安全审计与异常登录排查")
    @Parameters({
        @Parameter(
                name = "page",
                description = "页码，从1开始",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", defaultValue = "1")),
        @Parameter(
                name = "size",
                description = "每页大小",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", defaultValue = "20")),
        @Parameter(
                name = "username",
                description = "登录账号（模糊匹配）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "status",
                description = "登录状态（0-成功，1-失败）",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(mediaType = "application/json", schema = @Schema(implementation = LoginLogDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数错误"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:login-log:list 权限")
    })
    public Mono<ServerResponse> list(ServerRequest request) {
        int page = safeParseInt(request.queryParam("page").orElse(null), 1);
        int size = safeParseInt(request.queryParam("size").orElse(null), 20);
        Integer status = request.queryParam("status")
                .filter(value -> !value.isBlank())
                .map(value -> safeParseInt(value, -1))
                .orElse(null);
        return loginLogService
                .search(request.queryParam("username").orElse(null), status, page, size)
                .flatMap(this::buildSuccess);
    }
}
