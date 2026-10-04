package com.apex.scaffold.authcenter.interfaces.handler;

import com.apex.scaffold.authcenter.application.assembler.SessionAssembler;
import com.apex.scaffold.authcenter.application.service.SessionService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.OperationLog.OperationType;
import com.apex.scaffold.common.annotation.RequireAdminRole;
import com.apex.scaffold.common.annotation.RequireResourcePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import com.apex.scaffold.common.context.TenantContext;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

/**
 * 会话管理Handler
 *
 * <p>提供用户会话的全生命周期管理能力，支持会话查询、强制下线、在线统计等功能。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li><b>会话查询</b>：查询用户的所有活跃会话（包括设备、IP、登录时间等）</li>
 *   <li><b>会话终止</b>：强制下线单个会话或用户的所有会话</li>
 *   <li><b>在线监控</b>：实时查看在线用户列表和统计数据</li>
 *   <li><b>会话详情</b>：查看会话的详细信息（设备指纹、IP地理位置等）</li>
 * </ul>
 *
 * <p><b>会话存储：</b>
 * <ul>
 *   <li>会话数据存储在Redis中，支持分布式部署</li>
 *   <li>会话ID与JWT token绑定，确保安全性</li>
 *   <li>会话有效期与token有效期同步</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li><b>安全管理</b>：管理员强制下线异常会话</li>
 *   <li><b>账号保护</b>：用户查看并管理自己的登录设备</li>
 *   <li><b>并发控制</b>：限制同一账号的并发登录数</li>
 *   <li><b>运营监控</b>：实时查看在线用户数和活跃度</li>
 *   <li><b>审计追踪</b>：记录会话创建、终止等关键操作</li>
 * </ul>
 *
 * <p><b>多租户隔离：</b>
 * <p>会话数据按租户隔离存储，确保不同租户的会话信息互不可见。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Component
@RequiredArgsConstructor
@Tag(name = "会话管理", description = "会话生命周期管理 - 查询、终止、统计在线会话")
public class SessionHandler {

    private final SessionService sessionService;
    private final SessionAssembler sessionAssembler;

    /**
     * 查询用户会话列表
     *
     * <p>获取指定用户的所有活跃会话，包含设备信息、IP地址、登录时间等详细信息。
     *
     * <p><b>返回数据包含：</b>
     * <ul>
     *   <li><b>sessionId</b>：会话唯一标识</li>
     *   <li><b>userId</b>：用户ID</li>
     *   <li><b>deviceType</b>：设备类型（PC、Mobile、Tablet）</li>
     *   <li><b>deviceName</b>：设备名称/浏览器信息</li>
     *   <li><b>ipAddress</b>：登录IP地址</li>
     *   <li><b>location</b>：IP地理位置（国家、城市）</li>
     *   <li><b>loginTime</b>：登录时间</li>
     *   <li><b>lastActiveTime</b>：最后活跃时间</li>
     *   <li><b>expiresAt</b>：会话过期时间</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户查看自己的登录设备列表</li>
     *   <li>管理员查看用户的活跃会话</li>
     *   <li>检测异常登录（不同地区同时在线）</li>
     * </ul>
     *
     * @param request 服务器请求对象，路径参数包含userId，查询参数包含tenantId
     * @return 用户的会话列表数组
     */
    @OperationLog(module = "会话管理", type = OperationType.SELECT, description = "查询用户会话")
    @Operation(operationId = "getUserSessions", summary = "查询用户会话", description = "获取指定用户的所有活跃会话，包含设备、IP、登录时间等详细信息")
    @Parameter(name = "userId", description = "用户ID", required = true, example = "1001")
    @Parameter(name = "tenantId", description = "租户ID（可选，默认为default）", required = false, example = "tenant_001")
    @ApiResponse(
            responseCode = "200",
            description = "查询成功，返回会话列表数组",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "404",
            description = "用户不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务器内部错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getUserSessions(ServerRequest request) {
        String userId = request.pathVariable("userId");
        String tenantId = request.queryParam("tenantId").orElse("default");
        return sessionService
                .getUserSessions(Long.parseLong(userId), tenantId)
                .map(sessionAssembler::toDto)
                .collectList()
                .flatMap(this::buildSuccess)
                .onErrorResume(this::handleError);
    }

    /**
     * 终止指定会话
     *
     * <p>强制终止单个会话，用户该会话对应的token将立即失效，需要重新登录。
     *
     * <p><b>操作影响：</b>
     * <ul>
     *   <li>会话记录从Redis中删除</li>
     *   <li>对应的JWT token加入黑名单</li>
     *   <li>用户使用该token的后续请求将被拒绝（401 Unauthorized）</li>
     *   <li>不影响用户的其他会话</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户在"我的设备"页面主动下线某个设备</li>
     *   <li>管理员强制下线异常会话</li>
     *   <li>检测到账号盗用，立即终止可疑会话</li>
     *   <li>设备丢失后，远程下线该设备的会话</li>
     * </ul>
     *
     * <p><b>安全提示：</b>
     * <p>建议记录会话终止操作到审计日志，包括操作人、终止原因、目标会话信息。
     *
     * @param request 服务器请求对象，路径参数包含sessionId
     * @return 操作成功返回空响应体
     */
    @OperationLog(module = "会话管理", type = OperationType.DELETE, description = "终止会话")
    @RequireAdminRole
    @RequireResourcePermission(resourceType = "session", resourceIdExpression = "#request.pathVariable('sessionId')", permission = "MANAGE", errorMessage = "无权终止该会话")
    @Operation(operationId = "terminateSession", summary = "终止会话", description = "强制终止指定会话，token立即失效，用户需重新登录")
    @Parameter(name = "sessionId", description = "会话ID", required = true, example = "session_abc123xyz")
    @ApiResponse(
            responseCode = "200",
            description = "会话终止成功，对应token已失效",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "404",
            description = "会话不存在或已过期",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "403",
            description = "权限不足 - 需要管理员权限",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务器内部错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> terminateSession(ServerRequest request) {
        String sessionId = request.pathVariable("sessionId");
        return sessionService
                .terminateSession(sessionId)
                .then(buildSuccess(null))
                .onErrorResume(this::handleError);
    }

    /**
     * 批量终止用户所有会话
     *
     * <p>强制下线指定用户的所有活跃会话，所有设备需要重新登录。
     *
     * <p><b>操作影响：</b>
     * <ul>
     *   <li>用户的所有会话记录被清除</li>
     *   <li>所有相关JWT token加入黑名单</li>
     *   <li>用户在所有设备上将被强制下线</li>
     *   <li>用户需要重新登录才能继续使用系统</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li><b>密码重置</b>：修改密码后立即下线所有设备</li>
     *   <li><b>权限变更</b>：角色/权限变更后强制重新登录</li>
     *   <li><b>账号锁定</b>：检测到异常行为，立即锁定账号</li>
     *   <li><b>安全事件</b>：疑似账号泄露，紧急下线所有会话</li>
     *   <li><b>账号注销</b>：用户注销账号前清理所有会话</li>
     * </ul>
     *
     * <p><b>最佳实践：</b>
     * <ul>
     *   <li>操作前提示用户影响范围</li>
     *   <li>记录详细的操作日志（操作人、原因、影响会话数）</li>
     *   <li>通过消息通知用户会话被终止</li>
     *   <li>考虑保留当前操作者的会话（如果是用户自己操作）</li>
     * </ul>
     *
     * @param request 服务器请求对象，路径参数包含userId，查询参数包含tenantId
     * @return 操作成功返回空响应体
     */
    @OperationLog(module = "会话管理", type = OperationType.DELETE, description = "批量下线用户所有会话")
    @RequireAdminRole
    @RequireResourcePermission(resourceType = "user-sessions", resourceIdExpression = "#request.pathVariable('userId')", permission = "MANAGE", errorMessage = "无权终止该用户的会话")
    @Operation(
            operationId = "terminateUserSessions",
            summary = "批量下线用户所有会话",
            description = "强制终止指定用户的所有活跃会话，所有设备需重新登录，常用于密码重置或权限变更")
    @Parameter(name = "userId", description = "用户ID", required = true, example = "1001")
    @Parameter(name = "tenantId", description = "租户ID（可选，默认为default）", required = false, example = "tenant_001")
    @ApiResponse(
            responseCode = "200",
            description = "所有会话终止成功，用户已被强制下线",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "404",
            description = "用户不存在",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "403",
            description = "权限不足 - 需要管理员权限",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务器内部错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> terminateUserSessions(ServerRequest request) {
        String userId = request.pathVariable("userId");
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication().getDetails())
                .ofType(String.class)
                .switchIfEmpty(Mono.defer(() -> Mono.justOrEmpty(TenantContext.currentTenantId().orElse(null))))
                .switchIfEmpty(Mono.error(new IllegalStateException("tenant context is required")))
                .flatMap(tenantId -> sessionService
                        .terminateUserSessions(Long.parseLong(userId), tenantId))
                .then(buildSuccess(null))
                .onErrorResume(this::handleError);
    }

    /**
     * 获取在线用户列表
     *
     * <p>获取当前系统中所有在线用户的基本信息，用于实时监控和运营分析。
     *
     * <p><b>返回数据包含：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID</li>
     *   <li><b>username</b>：用户名</li>
     *   <li><b>sessionCount</b>：该用户的活跃会话数</li>
     *   <li><b>lastActiveTime</b>：最后活跃时间</li>
     *   <li><b>onlineDuration</b>：在线时长（秒）</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>运营大屏展示实时在线用户</li>
     *   <li>系统监控：观察在线用户数变化趋势</li>
     *   <li>客服系统：查看当前活跃用户</li>
     *   <li>即时通讯：显示好友在线状态</li>
     * </ul>
     *
     * <p><b>性能说明：</b>
     * <ul>
     *   <li>数据从Redis缓存读取，响应速度快</li>
     *   <li>建议配合分页使用（如果在线用户数超过1000）</li>
     *   <li>数据实时性：通常1-5秒延迟</li>
     * </ul>
     *
     * @param request 服务器请求对象
     * @return 在线用户列表数组
     */
    @OperationLog(module = "会话管理", type = OperationType.SELECT, description = "获取在线用户列表")
    @Operation(operationId = "getOnlineUsers", summary = "获取在线用户列表", description = "获取当前系统中所有在线用户的基本信息，用于实时监控和运营分析")
    @ApiResponse(
            responseCode = "200",
            description = "查询成功，返回在线用户列表数组",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务器内部错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getOnlineUsers(ServerRequest request) {
        return sessionService
                .getOnlineUsers()
                .collectList()
                .flatMap(this::buildSuccess)
                .onErrorResume(this::handleError);
    }

    /**
     * 获取在线用户统计
     *
     * <p>获取系统在线用户的统计数据，包括总数、时间戳等信息。
     *
     * <p><b>返回数据包含：</b>
     * <ul>
     *   <li><b>totalOnlineUsers</b>：当前在线用户总数</li>
     *   <li><b>timestamp</b>：统计时间戳</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>系统监控大屏：实时显示在线用户数</li>
     *   <li>容量规划：根据在线数评估系统负载</li>
     *   <li>运营分析：统计不同时段的在线用户数</li>
     *   <li>告警系统：在线数异常时触发告警</li>
     * </ul>
     *
     * <p><b>性能优化：</b>
     * <p>统计数据基于Redis缓存计算，性能优秀，适合高频调用（如每秒刷新）。
     *
     * @param request 服务器请求对象
     * @return 包含在线用户统计数据的响应体
     */
    @OperationLog(module = "会话管理", type = OperationType.SELECT, description = "获取在线用户统计")
    @Operation(operationId = "getOnlineStatistics", summary = "获取在线用户统计", description = "获取当前在线用户数量统计，包括总数和统计时间")
    @ApiResponse(
            responseCode = "200",
            description = "统计成功，返回在线用户数等统计信息",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务器内部错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getOnlineStatistics(ServerRequest request) {
        return sessionService
                .countOnlineUsers()
                .flatMap(count -> {
                    var statistics = new java.util.HashMap<String, Object>();
                    statistics.put("totalOnlineUsers", count);
                    statistics.put("timestamp", java.time.LocalDateTime.now());
                    return buildSuccess(statistics);
                })
                .onErrorResume(this::handleError);
    }

    /**
     * 根据会话ID查询详情
     *
     * <p>查询单个会话的完整详细信息，包括设备信息、IP地址、地理位置、登录时间等。
     *
     * <p><b>返回数据包含：</b>
     * <ul>
     *   <li><b>id</b>：会话唯一标识</li>
     *   <li><b>userId</b>：用户ID</li>
     *   <li><b>username</b>：用户名</li>
     *   <li><b>deviceType</b>：设备类型（PC、Mobile、Tablet）</li>
     *   <li><b>deviceName</b>：设备名称/浏览器信息</li>
     *   <li><b>deviceFingerprint</b>：设备指纹（用于识别设备）</li>
     *   <li><b>ipAddress</b>：登录IP地址</li>
     *   <li><b>location</b>：IP地理位置（国家、省份、城市）</li>
     *   <li><b>userAgent</b>：User-Agent字符串</li>
     *   <li><b>loginTime</b>：登录时间</li>
     *   <li><b>lastActiveTime</b>：最后活跃时间</li>
     *   <li><b>expiresAt</b>：会话过期时间</li>
     *   <li><b>tenantId</b>：租户ID</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>会话审计：查看会话的详细信息</li>
     *   <li>异常检测：分析可疑会话的详细特征</li>
     *   <li>用户支持：帮助用户识别自己的设备</li>
     *   <li>设备管理：展示设备的详细信息</li>
     * </ul>
     *
     * @param request 服务器请求对象，路径参数包含sessionId
     * @return 会话详细信息，如果会话不存在返回null
     */
    @OperationLog(module = "会话管理", type = OperationType.SELECT, description = "查询会话详情")
    @Operation(operationId = "getSessionById", summary = "查询会话详情", description = "根据ID查询单个会话的完整详细信息，包括设备、IP、地理位置等")
    @Parameter(name = "sessionId", description = "会话ID", required = true, example = "1001")
    @ApiResponse(
            responseCode = "200",
            description = "查询成功，返回会话详细信息",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "404",
            description = "会话不存在或已过期",
            content = @Content(schema = @Schema(implementation = Result.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务器内部错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getSessionById(ServerRequest request) {
        String sessionId = request.pathVariable("sessionId");
        return sessionService
                .getSessionById(Long.parseLong(sessionId))
                .map(sessionAssembler::toDto)
                .flatMap(this::buildSuccess)
                .switchIfEmpty(buildSuccess(null))
                .onErrorResume(this::handleError);
    }

    /**
     * 构建成功响应
     *
     * @param data 响应数据
     * @return ServerResponse
     */
    private Mono<ServerResponse> buildSuccess(Object data) {
        Result<Object> result = Result.success(data, TraceIdUtil.getTraceId());
        return ServerResponse.ok().bodyValue(result);
    }

    /**
     * 处理错误
     *
     * @param throwable 异常对象
     * @return ServerResponse
     */
    private Mono<ServerResponse> handleError(Throwable throwable) {
        Result<Object> result =
                Result.error(ResultCode.ERROR.getCode(), throwable.getMessage(), TraceIdUtil.getTraceId());
        return ServerResponse.ok().bodyValue(result);
    }
}
