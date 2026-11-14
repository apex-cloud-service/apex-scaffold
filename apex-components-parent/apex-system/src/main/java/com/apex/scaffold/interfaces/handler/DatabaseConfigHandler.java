package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.DataSourceRequest;
import com.apex.scaffold.application.dto.admin.DatabaseConfigRequest;
import com.apex.scaffold.application.service.DataSourceManagementService;
import com.apex.scaffold.application.service.DatabaseConfigService;
import com.apex.scaffold.common.annotation.OperationLog;
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
 * <p>提供数据库连接测试、部署配置生成、当前数据库信息查询，以及
 * 动态数据源的完整生命周期管理 API（列表/创建/更新/删除/监控指标），
 * 所有端点要求 {@code system:database:*} 系列权限。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>POST /admin-api/v1/database/test - 测试数据库连接</li>
 *   <li>POST /admin-api/v1/database/generate-config - 生成部署配置</li>
 *   <li>GET /admin-api/v1/database/current - 获取当前数据库信息</li>
 *   <li>GET /admin-api/v1/database/datasources - 分页查询数据源列表</li>
 *   <li>POST /admin-api/v1/database/datasources - 创建数据源</li>
 *   <li>PUT /admin-api/v1/database/datasources/{id} - 更新数据源</li>
 *   <li>DELETE /admin-api/v1/database/datasources/{id} - 删除数据源</li>
 *   <li>GET /admin-api/v1/database/datasources/{id}/metrics - 查询数据源监控指标</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>连接测试的密码仅用于一次性操作，不会被保存到系统中</li>
 *   <li>数据源管理的密码经加密持久化，响应中 URL 与用户名脱敏、密码不返回</li>
 *   <li>数据源写操作记录操作日志（{@code @OperationLog}）</li>
 *   <li>数据源操作限定租户上下文，禁止跨租户访问</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 * @see DatabaseConfigService
 * @see DataSourceManagementService
 */
@Slf4j
@Component
@Tag(name = "后台-数据库配置", description = "数据库配置采集、部署配置生成与数据源管理")
public class DatabaseConfigHandler extends BaseHandler {

    private final DatabaseConfigService databaseConfigService;

    private final DataSourceManagementService dataSourceManagementService;

    /**
     * 创建数据库配置处理器。
     *
     * @param databaseConfigService 数据库配置应用服务
     * @param dataSourceManagementService 数据源管理应用服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public DatabaseConfigHandler(
            DatabaseConfigService databaseConfigService,
            DataSourceManagementService dataSourceManagementService,
            MessageUtils messageUtils,
            Validator validator) {
        super(messageUtils, validator);
        this.databaseConfigService = databaseConfigService;
        this.dataSourceManagementService = dataSourceManagementService;
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
    @OperationLog(
            module = "DatabaseConfig",
            type = OperationLog.OperationType.OTHER,
            description = "testConnection",
            saveRequest = false,
            saveResponse = false)
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
    @OperationLog(
            module = "DatabaseConfig",
            type = OperationLog.OperationType.OTHER,
            description = "generateConfig",
            saveRequest = false,
            saveResponse = false)
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
    @OperationLog(
            module = "DatabaseConfig",
            type = OperationLog.OperationType.SELECT,
            description = "getCurrentInfo",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getCurrentInfo(ServerRequest request) {
        return databaseConfigService.getCurrentDatabaseInfo().flatMap(this::buildSuccess);
    }

    /**
     * 分页查询数据源列表。
     *
     * <p>分页查询当前租户下注册的所有数据源配置，按创建时间倒序排列，
     * 返回结果中连接 URL 与用户名已脱敏，包含连接池运行时状态。
     *
     * @param request HTTP 请求，查询参数 pageNum（默认 1）、pageSize（默认 10）
     * @return 带 traceId 的统一成功响应，data 为分页的数据源列表
     */
    @RequirePermission("system:database:datasource:list")
    @Operation(
            operationId = "listDataSources",
            summary = "分页查询数据源列表",
            description = "分页查询当前租户下注册的数据源配置，按创建时间倒序排列，URL 与用户名已脱敏")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，返回分页数据源列表",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:database:datasource:list 权限")
    })
    @OperationLog(
            module = "DatabaseConfig",
            type = OperationLog.OperationType.SELECT,
            description = "listDataSources",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listDataSources(ServerRequest request) {
        int pageNum = safeParseInt(request.queryParam("pageNum").orElse("1"), 1);
        int pageSize = safeParseInt(request.queryParam("pageSize").orElse("10"), 10);
        log.info("收到数据源列表查询请求: pageNum={}, pageSize={}", pageNum, pageSize);

        return dataSourceManagementService.listDataSources(pageNum, pageSize).flatMap(this::buildSuccess);
    }

    /**
     * 创建数据源。
     *
     * <p>校验名称在租户内唯一后持久化数据源配置，密码经加密存储，
     * 并尽力建立 HikariCP 连接池（建立失败不影响配置保存，状态为 OFFLINE）。
     *
     * @param request HTTP 请求，请求体为 {@link DataSourceRequest}
     * @return 带 traceId 的统一成功响应，data 为创建成功的数据源（脱敏）
     */
    @RequirePermission("system:database:datasource:create")
    @Operation(operationId = "createDataSource", summary = "创建数据源", description = "创建新的数据源配置，密码加密存储，名称在租户内唯一")
    @RequestBody(
            description = "数据源配置信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DataSourceRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功，返回脱敏后的数据源信息",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "400", description = "请求参数校验失败或数据源名称已存在"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:database:datasource:create 权限")
    })
    @OperationLog(
            module = "数据库配置",
            type = OperationLog.OperationType.INSERT,
            description = "创建数据源",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> createDataSource(ServerRequest request) {
        log.info("收到创建数据源请求");
        return parseBody(request, DataSourceRequest.class)
                .flatMap(dataSourceManagementService::createDataSource)
                .flatMap(this::buildSuccess)
                .doOnError(e -> log.error("创建数据源失败", e));
    }

    /**
     * 更新数据源。
     *
     * <p>校验数据源归属当前租户且名称不与其他数据源冲突后，以乐观锁方式
     * 更新配置，并关闭旧连接池、尽力重建新连接池使新参数生效。
     *
     * @param request HTTP 请求，路径变量 id 为数据源 ID，请求体为 {@link DataSourceRequest}
     * @return 带 traceId 的统一成功响应，data 为更新后的数据源（脱敏）
     */
    @RequirePermission("system:database:datasource:update")
    @Operation(operationId = "updateDataSource", summary = "更新数据源", description = "更新指定的数据源配置，连接参数变更后重建连接池")
    @RequestBody(
            description = "数据源配置信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DataSourceRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功，返回脱敏后的数据源信息",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "400", description = "请求参数校验失败或数据源名称已存在"),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:database:datasource:update 权限"),
        @ApiResponse(responseCode = "404", description = "数据源不存在")
    })
    @OperationLog(
            module = "数据库配置",
            type = OperationLog.OperationType.UPDATE,
            description = "更新数据源",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> updateDataSource(ServerRequest request) {
        Long id = requirePositiveId(request);
        log.info("收到更新数据源请求: id={}", id);
        return parseBody(request, DataSourceRequest.class)
                .flatMap(req -> dataSourceManagementService.updateDataSource(id, req))
                .flatMap(this::buildSuccess)
                .doOnError(e -> log.error("更新数据源失败: id={}", id, e));
    }

    /**
     * 删除数据源。
     *
     * <p>校验数据源归属当前租户后执行逻辑删除，并关闭对应的 HikariCP 连接池。
     *
     * @param request HTTP 请求，路径变量 id 为数据源 ID
     * @return 带 traceId 的统一成功响应
     */
    @RequirePermission("system:database:datasource:delete")
    @Operation(operationId = "deleteDataSource", summary = "删除数据源", description = "逻辑删除指定的数据源配置，并关闭其连接池")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功", content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:database:datasource:delete 权限"),
        @ApiResponse(responseCode = "404", description = "数据源不存在")
    })
    @OperationLog(
            module = "数据库配置",
            type = OperationLog.OperationType.DELETE,
            description = "删除数据源",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> deleteDataSource(ServerRequest request) {
        Long id = requirePositiveId(request);
        log.info("收到删除数据源请求: id={}", id);
        return dataSourceManagementService
                .deleteDataSource(id)
                .then(buildSuccess("数据源删除成功"))
                .doOnError(e -> log.error("删除数据源失败: id={}", id, e));
    }

    /**
     * 查询数据源监控指标。
     *
     * <p>返回指定数据源连接池的运行时指标（总连接数、活跃连接数、空闲连接数、
     * 等待线程数等）；连接池未建立时状态为 OFFLINE、指标为空值。
     *
     * @param request HTTP 请求，路径变量 id 为数据源 ID
     * @return 带 traceId 的统一成功响应，data 为连接池监控指标
     */
    @RequirePermission("system:database:datasource:metrics")
    @Operation(
            operationId = "getDataSourceMetrics",
            summary = "查询数据源监控指标",
            description = "查询指定数据源连接池的运行时指标，连接池未建立时返回 OFFLINE 空指标")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，返回连接池监控指标",
                content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "401", description = "未授权，需要登录"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:database:datasource:metrics 权限"),
        @ApiResponse(responseCode = "404", description = "数据源不存在")
    })
    @OperationLog(
            module = "DatabaseConfig",
            type = OperationLog.OperationType.SELECT,
            description = "getDataSourceMetrics",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getDataSourceMetrics(ServerRequest request) {
        Long id = requirePositiveId(request);
        log.info("收到数据源监控指标查询请求: id={}", id);
        return dataSourceManagementService
                .getDataSourceMetrics(id)
                .flatMap(this::buildSuccess)
                .doOnError(e -> log.error("查询数据源监控指标失败: id={}", id, e));
    }
}
