package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.GenDatasourceRequest;
import com.apex.scaffold.application.service.GenDatasourceService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * 代码生成器数据源处理器（函数式端点）。
 *
 * <p>提供数据源的增删改查、连接测试、表结构查询和字段信息查询 API，
 * 所有端点要求 {@code system:generator:datasource:*} 权限。数据源配置
 * 用于代码生成器连接目标数据库，支持 MySQL、PostgreSQL、Oracle、SQL Server 等。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /admin-api/v1/generator/datasources - 查询数据源列表（分页）</li>
 *   <li>POST /admin-api/v1/generator/datasources - 新增数据源</li>
 *   <li>PUT /admin-api/v1/generator/datasources/{id} - 更新数据源</li>
 *   <li>DELETE /admin-api/v1/generator/datasources/{id} - 删除数据源</li>
 *   <li>POST /admin-api/v1/generator/datasources/{id}/test - 测试数据源连接</li>
 *   <li>GET /admin-api/v1/generator/datasources/{id}/tables - 查询数据库表结构</li>
 *   <li>GET /admin-api/v1/generator/datasources/{id}/tables/{tableName}/columns - 查询表字段信息</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Tag(name = "后台-代码生成器-数据源", description = "数据源配置管理")
public class GenDatasourceHandler extends BaseHandler {

    private final GenDatasourceService genDatasourceService;

    /**
     * 创建数据源配置管理处理器。
     *
     * @param genDatasourceService 生成数据源服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public GenDatasourceHandler(
            GenDatasourceService genDatasourceService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.genDatasourceService = genDatasourceService;
    }

    /**
     * 查询数据源列表（分页）。
     *
     * @param request HTTP 请求，支持分页参数 page、size 和查询条件
     * @return 带 traceId 的统一成功响应，data 为数据源分页列表
     */
    @RequirePermission("system:generator:datasource:list")
    @Operation(
            operationId = "adminListGenDatasources",
            summary = "查询数据源列表",
            description = "分页查询代码生成器数据源配置列表，返回包含数据源名称、类型、连接信息等")
    @Parameter(
            name = "page",
            description = "页码（从1开始）",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "integer", example = "1"))
    @Parameter(
            name = "size",
            description = "每页数量",
            in = ParameterIn.QUERY,
            schema = @Schema(type = "integer", example = "20"))
    @Parameter(name = "name", description = "数据源名称（模糊查询）", in = ParameterIn.QUERY, schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:datasource:list 权限")
    })
    @OperationLog(
            module = "GenDatasource",
            type = OperationLog.OperationType.SELECT,
            description = "list",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> list(ServerRequest request) {
        int page = safeParseInt(request.queryParam("page").orElse("1"), 1);
        int size = safeParseInt(request.queryParam("size").orElse("20"), 20);
        String name = request.queryParam("name").orElse(null);

        return genDatasourceService.list(name, page, size).flatMap(this::buildSuccess);
    }

    /**
     * 新增数据源。
     *
     * @param request HTTP 请求，请求体包含数据源配置信息
     * @return 带 traceId 的统一成功响应，data 为已创建的数据源实体
     */
    @RequirePermission("system:generator:datasource:add")
    @Operation(
            operationId = "adminCreateGenDatasource",
            summary = "新增数据源",
            description = "创建新的数据源配置，支持 MySQL、PostgreSQL、Oracle、SQL Server 等数据库类型")
    @RequestBody(description = "数据源创建请求信息", required = true, content = @Content(mediaType = "application/json"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:datasource:add 权限")
    })
    @OperationLog(
            module = "代码生成器-数据源",
            type = OperationLog.OperationType.INSERT,
            description = "新增数据源",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> create(ServerRequest request) {
        return parseBody(request, GenDatasourceRequest.class)
                .flatMap(genDatasourceService::create)
                .flatMap(this::buildSuccess);
    }

    /**
     * 更新数据源。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数，请求体包含更新信息
     * @return 带 traceId 的统一成功响应，data 为已更新的数据源实体
     */
    @RequirePermission("system:generator:datasource:edit")
    @Operation(
            operationId = "adminUpdateGenDatasource",
            summary = "更新数据源",
            description = "更新指定数据源的配置信息，支持修改连接参数、用户名、密码等")
    @Parameter(
            name = "id",
            description = "数据源ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(description = "数据源更新请求信息", required = true, content = @Content(mediaType = "application/json"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:datasource:edit 权限"),
        @ApiResponse(responseCode = "404", description = "数据源不存在")
    })
    @OperationLog(
            module = "代码生成器-数据源",
            type = OperationLog.OperationType.UPDATE,
            description = "更新数据源",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> update(ServerRequest request) {
        Long id = requirePositiveId(request);
        return parseBody(request, GenDatasourceRequest.class)
                .flatMap(body -> genDatasourceService.update(id, body))
                .flatMap(this::buildSuccess);
    }

    /**
     * 删除数据源。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为 {@code {"deleted": 是否删除成功}}
     */
    @RequirePermission("system:generator:datasource:remove")
    @Operation(
            operationId = "adminDeleteGenDatasource",
            summary = "删除数据源",
            description = "删除指定的数据源配置，若该数据源被生成配置引用则无法删除")
    @Parameter(
            name = "id",
            description = "数据源ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "400", description = "参数错误，ID必须为正整数"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:datasource:remove 权限"),
        @ApiResponse(responseCode = "404", description = "数据源不存在"),
        @ApiResponse(responseCode = "409", description = "数据源被生成配置引用，无法删除")
    })
    @OperationLog(
            module = "代码生成器-数据源",
            type = OperationLog.OperationType.DELETE,
            description = "删除数据源",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> delete(ServerRequest request) {
        return genDatasourceService
                .delete(requirePositiveId(request))
                .flatMap(deleted -> buildSuccess(java.util.Map.of("deleted", deleted)));
    }

    /**
     * 测试数据源连接。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为连接测试结果（成功/失败及错误信息）
     */
    @RequirePermission("system:generator:datasource:test")
    @Operation(
            operationId = "adminTestGenDatasourceConnection",
            summary = "测试数据源连接",
            description = "测试指定数据源的数据库连接是否正常，返回连接状态和错误信息")
    @Parameter(
            name = "id",
            description = "数据源ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "测试完成（包含连接成功或失败结果）"),
        @ApiResponse(responseCode = "400", description = "参数错误，ID必须为正整数"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:datasource:test 权限"),
        @ApiResponse(responseCode = "404", description = "数据源不存在")
    })
    @OperationLog(
            module = "GenDatasource",
            type = OperationLog.OperationType.OTHER,
            description = "testConnection",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> testConnection(ServerRequest request) {
        return genDatasourceService.testConnection(requirePositiveId(request)).flatMap(this::buildSuccess);
    }
}
