package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.DatabaseConfigRequest;
import com.apex.scaffold.application.service.DatabaseConfigService;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 数据库配置处理器（函数式端点）。
 *
 * <p>提供数据库连接测试、部署配置生成和当前数据库信息查询 API，
 * 所有端点要求 {@code system:database:*} 权限。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>POST /admin-api/v1/database/test - 测试数据库连接</li>
 *   <li>POST /admin-api/v1/database/generate-config - 生成部署配置</li>
 *   <li>GET /admin-api/v1/database/current - 获取当前数据库信息</li>
 * </ul>
 *
 * <h2>安全边界</h2>
 * <p>密码仅用于一次性操作，不会被保存到系统中。</p>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see DatabaseConfigService
 */
@Slf4j
@Component
@Tag(name = "后台-数据库配置", description = "数据库配置采集与部署配置生成")
public class DatabaseConfigHandler extends BaseHandler {

    private final DatabaseConfigService databaseConfigService;

    /**
     * 创建数据库配置处理器。
     *
     * @param databaseConfigService 数据库配置应用服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public DatabaseConfigHandler(
            DatabaseConfigService databaseConfigService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.databaseConfigService = databaseConfigService;
    }

    /**
     * 测试数据库连接。
     *
     * <p>验证提供的数据库配置是否可以成功建立连接，支持MySQL和PostgreSQL。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为连接测试结果
     */
    @RequirePermission("system:database:test")
    @Operation(
            operationId = "testDatabaseConnection",
            summary = "测试数据库连接",
            description = "测试指定的数据库配置是否可以成功连接，支持MySQL和PostgreSQL数据库")
    @RequestBody(
            description = "数据库配置信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DatabaseConfigRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "连接测试成功，返回测试结果",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "400", description = "请求参数校验失败"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:database:test权限")
    })
    public Mono<ServerResponse> testConnection(ServerRequest request) {
        log.info("收到数据库连接测试请求");
        return parseBody(request, DatabaseConfigRequest.class)
                .doOnNext(req -> log.info("请求参数解析成功: dbType={}, host={}", req.getDbType(), req.getHost()))
                .flatMap(databaseConfigService::testConnection)
                .doOnNext(
                        result -> log.info("连接测试完成: success={}, message={}", result.getSuccess(), result.getMessage()))
                .flatMap(this::buildSuccess)
                .doOnError(e -> log.error("处理连接测试请求时发生错误", e));
    }

    /**
     * 生成部署配置。
     *
     * <p>根据提供的数据库配置生成用于部署的配置文件（YAML/env/docker-compose格式）。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为部署配置（YAML/env/docker-compose）
     */
    @RequirePermission("system:database:generate")
    @Operation(operationId = "generateDeploymentConfig", summary = "生成部署配置", description = "根据数据库配置生成部署所需的配置文件")
    @RequestBody(
            description = "数据库配置信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DatabaseConfigRequest.class)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "生成配置成功", content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "400", description = "请求参数校验失败"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:database:generate权限")
    })
    public Mono<ServerResponse> generateConfig(ServerRequest request) {
        return parseBody(request, DatabaseConfigRequest.class)
                .flatMap(databaseConfigService::generateDeploymentConfig)
                .flatMap(this::buildSuccess);
    }

    /**
     * 获取当前数据库信息。
     *
     * <p>返回当前系统正在使用的数据库类型、版本等信息。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为当前数据库信息
     */
    @RequirePermission("system:database:info")
    @Operation(operationId = "getCurrentDatabaseInfo", summary = "获取当前数据库信息", description = "查询当前系统连接的数据库类型、版本等信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:database:info权限")
    })
    public Mono<ServerResponse> getCurrentInfo(ServerRequest request) {
        return databaseConfigService.getCurrentDatabaseInfo().flatMap(this::buildSuccess);
    }
}
