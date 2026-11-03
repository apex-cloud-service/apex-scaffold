package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.GenConfigDTO;
import com.apex.scaffold.application.dto.admin.GenConfigRequest;
import com.apex.scaffold.application.dto.admin.TableInfoDTO;
import com.apex.scaffold.application.service.CodeGenerationService;
import com.apex.scaffold.application.service.GenConfigService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
 * 代码生成器配置处理器（函数式端点）。
 *
 * <p>提供生成配置的增删改查、表结构查询、字段信息查询和生成历史查询 API，
 * 所有端点要求 {@code system:generator:config:*} 或 {@code system:generator:table:*} 权限。
 * 生成配置用于定义代码生成的目标表、输出路径、模板选择等参数。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>GET /admin-api/v1/generator/configs - 查询生成配置列表</li>
 *   <li>POST /admin-api/v1/generator/configs - 新增生成配置</li>
 *   <li>PUT /admin-api/v1/generator/configs/{id} - 更新生成配置</li>
 *   <li>DELETE /admin-api/v1/generator/configs/{id} - 删除生成配置</li>
 *   <li>GET /admin-api/v1/generator/datasources/{id}/tables - 查询数据库表结构</li>
 *   <li>GET /admin-api/v1/generator/datasources/{id}/tables/{tableName}/columns - 查询表字段信息</li>
 *   <li>GET /admin-api/v1/generator/configs/{id}/history - 查询生成历史</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@Tag(name = "后台-代码生成器-配置", description = "生成配置管理")
public class GenConfigHandler extends BaseHandler {

    private final GenConfigService genConfigService;
    private final CodeGenerationService codeGenerationService;

    /**
     * 创建生成配置管理处理器。
     *
     * @param genConfigService 生成配置服务
     * @param codeGenerationService 代码生成服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public GenConfigHandler(
            GenConfigService genConfigService,
            CodeGenerationService codeGenerationService,
            MessageUtils messageUtils,
            Validator validator) {
        super(messageUtils, validator);
        this.genConfigService = genConfigService;
        this.codeGenerationService = codeGenerationService;
    }

    /**
     * 查询生成配置列表。
     *
     * @param request HTTP 请求，支持分页参数 page、size 和查询条件
     * @return 带 traceId 的统一成功响应，data 为生成配置分页列表
     */
    @RequirePermission("system:generator:config:list")
    @Operation(
            operationId = "adminListGenConfigs",
            summary = "查询生成配置列表",
            description = "分页查询代码生成器配置列表，返回包含配置名称、数据源、表名、模块名等信息")
    @Parameters({
        @Parameter(
                name = "page",
                description = "页码（从1开始）",
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", example = "1")),
        @Parameter(
                name = "size",
                description = "每页数量",
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", example = "20")),
        @Parameter(
                name = "configName",
                description = "配置名称（模糊查询）",
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "datasourceId",
                description = "数据源ID（精确过滤）",
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为生成配置分页列表",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:config:list 权限")
    })
    @OperationLog(
            module = "GenConfig",
            type = OperationLog.OperationType.SELECT,
            description = "list",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> list(ServerRequest request) {
        int page = safeParseInt(request.queryParam("page").orElse("1"), 1);
        int size = safeParseInt(request.queryParam("size").orElse("20"), 20);
        Long datasourceId = safeParseLong(request.queryParam("datasourceId").orElse(null));
        String configName = request.queryParam("configName").orElse(null);

        return genConfigService.list(configName, datasourceId, page, size).flatMap(this::buildSuccess);
    }

    /**
     * 新增生成配置。
     *
     * @param request HTTP 请求，请求体包含生成配置信息
     * @return 带 traceId 的统一成功响应，data 为已创建的生成配置实体
     */
    @RequirePermission("system:generator:config:add")
    @Operation(
            operationId = "adminCreateGenConfig",
            summary = "新增生成配置",
            description = "创建新的代码生成配置，指定数据源、表名、包名、模块名、输出路径和模板等参数")
    @RequestBody(
            description = "生成配置创建请求信息",
            required = true,
            content =
                    @Content(mediaType = "application/json", schema = @Schema(implementation = GenConfigRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功，data 为已创建的生成配置",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = GenConfigDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:config:add 权限")
    })
    @OperationLog(
            module = "代码生成器-配置",
            type = OperationLog.OperationType.INSERT,
            description = "新增生成配置",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> create(ServerRequest request) {
        return parseBody(request, GenConfigRequest.class)
                .flatMap(genConfigService::create)
                .flatMap(this::buildSuccess);
    }

    /**
     * 更新生成配置。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数，请求体包含更新信息
     * @return 带 traceId 的统一成功响应，data 为已更新的生成配置实体
     */
    @RequirePermission("system:generator:config:edit")
    @Operation(
            operationId = "adminUpdateGenConfig",
            summary = "更新生成配置",
            description = "更新指定生成配置的信息，支持修改表名、包名、输出路径、模板选择等参数")
    @Parameter(
            name = "id",
            description = "生成配置ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "生成配置更新请求信息",
            required = true,
            content =
                    @Content(mediaType = "application/json", schema = @Schema(implementation = GenConfigRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功，data 为已更新的生成配置",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = GenConfigDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:config:edit 权限"),
        @ApiResponse(responseCode = "404", description = "生成配置不存在")
    })
    @OperationLog(
            module = "代码生成器-配置",
            type = OperationLog.OperationType.UPDATE,
            description = "更新生成配置",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> update(ServerRequest request) {
        Long id = requirePositiveId(request);
        return parseBody(request, GenConfigRequest.class)
                .flatMap(body -> genConfigService.update(id, body))
                .flatMap(this::buildSuccess);
    }

    /**
     * 删除生成配置。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为 {@code {"deleted": 是否删除成功}}
     */
    @RequirePermission("system:generator:config:remove")
    @Operation(operationId = "adminDeleteGenConfig", summary = "删除生成配置", description = "删除指定的代码生成配置")
    @Parameter(
            name = "id",
            description = "生成配置ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除成功，data 为 {\"deleted\": 是否删除成功}",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                type = "object",
                                                additionalProperties = Schema.AdditionalPropertiesValue.TRUE))),
        @ApiResponse(responseCode = "400", description = "参数错误，ID必须为正整数"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:config:remove 权限"),
        @ApiResponse(responseCode = "404", description = "生成配置不存在")
    })
    @OperationLog(
            module = "代码生成器-配置",
            type = OperationLog.OperationType.DELETE,
            description = "删除生成配置",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> delete(ServerRequest request) {
        return genConfigService
                .delete(requirePositiveId(request))
                .flatMap(deleted -> buildSuccess(java.util.Map.of("deleted", deleted)));
    }

    /**
     * 查询数据库表结构。
     *
     * @param request HTTP 请求，路径参数 {@code id} 为数据源ID
     * @return 带 traceId 的统一成功响应，data 为表结构列表（表名、注释等）
     */
    @RequirePermission("system:generator:table:list")
    @Operation(operationId = "adminListGenTables", summary = "查询数据库表结构", description = "查询指定数据源下的所有表结构信息，包括表名、表注释等元数据")
    @Parameter(
            name = "id",
            description = "数据源ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为表名列表",
                content =
                        @Content(
                                mediaType = "application/json",
                                array = @ArraySchema(schema = @Schema(type = "string", example = "sys_user")))),
        @ApiResponse(responseCode = "400", description = "参数错误，ID必须为正整数"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:table:list 权限"),
        @ApiResponse(responseCode = "404", description = "数据源不存在")
    })
    @OperationLog(
            module = "GenConfig",
            type = OperationLog.OperationType.SELECT,
            description = "getTables",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getTables(ServerRequest request) {
        Long datasourceId = requirePositiveId(request);
        return codeGenerationService.getTables(datasourceId).collectList().flatMap(this::buildSuccess);
    }

    /**
     * 查询表字段信息。
     *
     * @param request HTTP 请求，路径参数 {@code id} 为数据源ID，{@code tableName} 为表名
     * @return 带 traceId 的统一成功响应，data 为字段列表（字段名、类型、长度、注释等）
     */
    @RequirePermission("system:generator:table:list")
    @Operation(
            operationId = "adminListGenColumns",
            summary = "查询表字段信息",
            description = "查询指定表的所有字段信息，包括字段名、数据类型、长度、是否主键、是否可空、字段注释等")
    @Parameter(
            name = "id",
            description = "数据源ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(
            name = "tableName",
            description = "表名",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "string", example = "sys_user"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为表字段元数据（字段名、类型、长度、主键、可空、注释）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TableInfoDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数错误"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:table:list 权限"),
        @ApiResponse(responseCode = "404", description = "数据源或表不存在")
    })
    @OperationLog(
            module = "GenConfig",
            type = OperationLog.OperationType.SELECT,
            description = "getColumns",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getColumns(ServerRequest request) {
        Long datasourceId = requirePositiveId(request);
        String tableName = request.pathVariable("tableName");
        if (tableName == null || tableName.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "表名不能为空"));
        }

        return codeGenerationService.getTableColumns(datasourceId, tableName).flatMap(this::buildSuccess);
    }

    /**
     * 查询生成历史。
     *
     * @param request HTTP 请求，路径参数 {@code id} 为生成配置ID，支持分页参数
     * @return 带 traceId 的统一成功响应，data 为生成历史分页列表
     */
    @RequirePermission("system:generator:history:list")
    @Operation(
            operationId = "adminListGenHistory",
            summary = "查询生成历史",
            description = "查询指定生成配置的代码生成历史记录，包括生成时间、生成文件列表、生成状态等")
    @Parameter(
            name = "id",
            description = "生成配置ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
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
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为生成历史分页列表",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "400", description = "参数错误，ID必须为正整数"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:history:list 权限"),
        @ApiResponse(responseCode = "404", description = "生成配置不存在")
    })
    @OperationLog(
            module = "GenConfig",
            type = OperationLog.OperationType.SELECT,
            description = "listHistory",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listHistory(ServerRequest request) {
        Long configId = requirePositiveId(request);
        int page = safeParseInt(request.queryParam("page").orElse("1"), 1);
        int size = safeParseInt(request.queryParam("size").orElse("20"), 20);

        return genConfigService.listHistory(configId, page, size).flatMap(this::buildSuccess);
    }
}
