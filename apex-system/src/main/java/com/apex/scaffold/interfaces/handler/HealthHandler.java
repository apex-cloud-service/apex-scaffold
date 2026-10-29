package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 健康检查处理器（函数式端点）。
 *
 * <p>提供系统健康检查、根路径探活和系统信息查询接口，
 * 系统描述信息会根据客户端语言偏好自动切换。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>健康检查：返回服务运行状态、时间戳、应用名称和版本</li>
 *   <li>根路径探活：供负载均衡器和反向代理探测服务存活</li>
 *   <li>系统信息：返回应用描述、版本、Java 版本、操作系统等运行环境信息</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>GET /api/v1/health — 健康检查，返回服务运行状态</li>
 *   <li>GET / — 根路径探活，返回服务存活状态</li>
 *   <li>GET /api/v1/info — 系统信息，返回应用描述、版本、运行环境等</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see MessageUtils
 */
@Slf4j
@Component
@Tag(name = "系统监控", description = "系统健康检查和状态查询接口")
public class HealthHandler extends BaseHandler {

    /**
     * 构造方法，注入 MessageUtils
     *
     * @param messageUtils 国际化消息工具实例，由 Spring 容器注入
     */
    protected HealthHandler(MessageUtils messageUtils) {
        super(messageUtils);
    }

    /** 应用名称（读配置，兜底默认值与应用名 apex-scaffold 保持一致） */
    @Value("${spring.application.name:apex-scaffold}")
    private String applicationName;

    /** 应用版本（读配置，三档构建时由 apex-build.properties 注入） */
    @Value("${apex.version:1.2.0}")
    private String applicationVersion;

    /**
     * 健康检查接口
     *
     * <p>提供服务健康状态检查，用于负载均衡器、服务发现组件或监控系统检查服务可用性。
     * 返回包含状态、时间戳、应用名称和版本信息。
     *
     * <p><strong>响应结构</strong></p>
     * <ul>
     *   <li>status: 服务状态，"UP"表示正常运行</li>
     *   <li>timestamp: 当前时间戳</li>
     *   <li>application: 应用名称</li>
     *   <li>version: 应用版本</li>
     * </ul>
     *
     * @param request HTTP请求对象
     * @return 包含健康状态信息的响应
     */
    @Operation(operationId = "healthCheck", summary = "健康检查", description = "用于负载均衡和服务发现的健康检查，返回服务运行状态、时间戳、应用名称和版本信息")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "健康检查通过",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "503",
                description = "服务不可用",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "Health",
            type = OperationLog.OperationType.SELECT,
            description = "health",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> health(ServerRequest request) {
        Map<String, Object> healthInfo = new HashMap<>(4);
        healthInfo.put("status", "UP");
        healthInfo.put("timestamp", LocalDateTime.now());
        healthInfo.put("application", applicationName);
        healthInfo.put("version", applicationVersion);
        return buildOk(Result.success(healthInfo));
    }

    /**
     * 根路径探活接口
     *
     * <p>供负载均衡器、反向代理和探活脚本对根路径发起探测，返回 200 与统一 Result 结构，
     * 表明服务进程存活且可路由。根路径已纳入公开路径白名单，不再被认证过滤器误判为未授权。
     *
     * @param request HTTP请求对象
     * @return 包含服务存活状态的响应
     */
    @Operation(operationId = "rootProbe", summary = "根路径探活", description = "服务根路径探活，返回服务运行状态，用于负载均衡器和反向代理的健康检查")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "服务存活",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "Health",
            type = OperationLog.OperationType.SELECT,
            description = "root",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> root(ServerRequest request) {
        Map<String, Object> probeInfo = new HashMap<>(3);
        probeInfo.put("status", "UP");
        probeInfo.put("application", applicationName);
        probeInfo.put("timestamp", LocalDateTime.now());
        return buildOk(Result.success(probeInfo));
    }

    /**
     * 获取系统信息
     *
     * <p>返回系统基本信息，包括应用描述、版本、Java版本、操作系统等。
     * 主要用于运维人员了解当前运行环境信息。
     * 系统描述（description）会根据客户端 Accept-Language 请求头进行国际化。
     *
     * <p><strong>响应结构</strong></p>
     * <ul>
     *   <li>application: 应用名称</li>
     *   <li>description: 应用描述（国际化）</li>
     *   <li>version: 应用版本</li>
     *   <li>java: Java运行时版本</li>
     *   <li>os: 操作系统名称</li>
     *   <li>timestamp: 当前时间戳</li>
     * </ul>
     *
     * @param request HTTP请求对象
     * @return 包含系统信息的响应
     */
    @Operation(operationId = "getSystemInfo", summary = "系统信息", description = "获取系统基本信息，包括应用描述、版本、Java版本、操作系统等，支持国际化")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "获取成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "Health",
            type = OperationLog.OperationType.SELECT,
            description = "info",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> info(ServerRequest request) {
        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
            String description = messageUtils.get(locale, "sys.description");
            Map<String, Object> systemInfo = new HashMap<>(6);
            systemInfo.put("application", "Apex Scaffold");
            systemInfo.put("description", description);
            systemInfo.put("version", "1.0.0");
            systemInfo.put("java", System.getProperty("java.version"));
            systemInfo.put("os", System.getProperty("os.name"));
            systemInfo.put("timestamp", LocalDateTime.now());
            return buildOk(Result.success(systemInfo));
        });
    }
}
