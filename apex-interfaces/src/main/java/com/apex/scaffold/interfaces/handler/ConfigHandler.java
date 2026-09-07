package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.ConfigDTO;
import com.apex.scaffold.application.dto.admin.ConfigRequest;
import com.apex.scaffold.application.dto.admin.StatusRequest;
import com.apex.scaffold.application.service.ConfigService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
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
 * 系统参数配置处理器（函数式端点）。
 *
 * <p>提供系统参数配置的查询、创建、更新、删除与启停管理 API，
 * 所有端点要求 {@code system:config:*} 权限。参数采用键值对模型，
 * 是管理系统运行时参数的基础能力。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /admin-api/v1/configs - 查询参数配置列表</li>
 *   <li>GET /admin-api/v1/configs/{id} - 查询参数配置详情</li>
 *   <li>POST /admin-api/v1/configs - 创建参数配置</li>
 *   <li>PUT /admin-api/v1/configs/{id} - 更新参数配置</li>
 *   <li>DELETE /admin-api/v1/configs/{id} - 删除参数配置</li>
 *   <li>PUT /admin-api/v1/configs/{id}/status - 启用/停用参数配置</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ConfigService
 */
@Component
@Tag(name = "后台-参数配置", description = "系统参数配置管理")
public class ConfigHandler extends BaseHandler {

    /**
     * 参数配置应用服务。
     */
    private final ConfigService configService;

    /**
     * 创建系统参数配置处理器。
     *
     * @param configService 参数配置应用服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public ConfigHandler(ConfigService configService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.configService = configService;
    }

    /**
     * 查询参数配置列表。
     *
     * <p>返回所有系统参数配置的列表，包括内置参数和用户自定义参数。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为参数配置 DTO 列表
     */
    @RequirePermission("system:config:list")
    @Operation(
            operationId = "adminListConfigs",
            summary = "查询参数配置列表",
            description = "返回所有系统参数配置的列表，包括内置参数和用户自定义参数"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConfigDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "未授权，需要登录"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "权限不足，需要 system:config:list 权限"
            )
    })
    public Mono<ServerResponse> list(ServerRequest request) {
        return configService.listConfigs().flatMap(this::buildSuccess);
    }

    /**
     * 查询参数配置详情。
     *
     * <p>根据参数配置ID查询详细信息。</p>
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为参数配置实体
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:config:query")
    @Operation(
            operationId = "adminGetConfig",
            summary = "查询参数配置详情",
            description = "根据参数配置ID查询详细信息"
    )
    @Parameter(
            name = "id",
            description = "参数配置ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConfigDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "参数错误，id 必须为正整数"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "未授权，需要登录"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "权限不足，需要 system:config:query 权限"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "参数配置不存在"
            )
    })
    public Mono<ServerResponse> detail(ServerRequest request) {
        return configService.getConfig(requirePositiveId(request)).flatMap(this::buildSuccess);
    }

    /**
     * 创建参数配置。
     *
     * <p>创建新的系统参数配置，参数键名必须全局唯一。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为已创建的参数配置实体
     * @throws BusinessException 当请求体为空时抛出
     */
    @RequirePermission("system:config:add")
    @Operation(
            operationId = "adminCreateConfig",
            summary = "创建参数配置",
            description = "创建新的系统参数配置，参数键名必须全局唯一"
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "参数配置创建请求信息",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ConfigRequest.class)
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "创建成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConfigDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "参数校验失败或参数键名已存在"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "未授权，需要登录"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "权限不足，需要 system:config:add 权限"
            )
    })
    @OperationLog(module = "参数配置", type = OperationLog.OperationType.INSERT, description = "创建参数配置")
    public Mono<ServerResponse> create(ServerRequest request) {
        return parseBody(request, ConfigRequest.class)
                .flatMap(configService::createConfig)
                .flatMap(this::buildSuccess);
    }

    /**
     * 更新参数配置。
     *
     * <p>更新指定参数配置的信息，支持部分字段更新。</p>
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为已更新的参数配置实体
     * @throws BusinessException 当 {@code id} 非法或请求体为空时抛出
     */
    @RequirePermission("system:config:edit")
    @Operation(
            operationId = "adminUpdateConfig",
            summary = "更新参数配置",
            description = "更新指定参数配置的信息，支持部分字段更新"
    )
    @Parameter(
            name = "id",
            description = "参数配置ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64")
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "参数配置更新请求信息",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ConfigRequest.class)
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "更新成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConfigDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "参数校验失败或 id 非法"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "未授权，需要登录"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "权限不足，需要 system:config:edit 权限"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "参数配置不存在"
            )
    })
    @OperationLog(module = "参数配置", type = OperationLog.OperationType.UPDATE, description = "更新参数配置")
    public Mono<ServerResponse> update(ServerRequest request) {
        Long id = requirePositiveId(request);
        return parseBody(request, ConfigRequest.class)
                .flatMap(body -> configService.updateConfig(id, body))
                .flatMap(this::buildSuccess);
    }

    /**
     * 删除参数配置。
     *
     * <p>内置参数将被拒绝删除。</p>
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为 {@code {"deleted": 是否删除成功}}
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:config:remove")
    @Operation(
            operationId = "adminDeleteConfig",
            summary = "删除参数配置",
            description = "删除指定参数配置，内置参数将被拒绝删除"
    )
    @Parameter(
            name = "id",
            description = "参数配置ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "删除成功"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "参数错误，id 必须为正整数，或尝试删除内置参数"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "未授权，需要登录"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "权限不足，需要 system:config:remove 权限"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "参数配置不存在"
            )
    })
    @OperationLog(module = "参数配置", type = OperationLog.OperationType.DELETE, description = "删除参数配置")
    public Mono<ServerResponse> delete(ServerRequest request) {
        return configService
                .deleteConfig(requirePositiveId(request))
                .flatMap(deleted -> buildSuccess(java.util.Map.of("deleted", deleted)));
    }

    /**
     * 启用或停用参数配置。
     *
     * <p>切换参数配置的启用状态。</p>
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为更新后的参数配置实体
     * @throws BusinessException 当 {@code id} 非法或请求体为空时抛出
     */
    @RequirePermission("system:config:edit")
    @Operation(
            operationId = "adminUpdateConfigStatus",
            summary = "启用或停用参数配置",
            description = "切换参数配置的启用状态"
    )
    @Parameter(
            name = "id",
            description = "参数配置ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64")
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "状态更新请求信息",
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = StatusRequest.class)
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "更新成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConfigDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "参数校验失败或 id 非法"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "未授权，需要登录"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "权限不足，需要 system:config:edit 权限"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "参数配置不存在"
            )
    })
    @OperationLog(module = "参数配置", type = OperationLog.OperationType.UPDATE, description = "更新参数配置状态")
    public Mono<ServerResponse> updateStatus(ServerRequest request) {
        Long id = requirePositiveId(request);
        return parseBody(request, StatusRequest.class)
                .flatMap(body -> configService.updateConfigStatus(id, body.isEnabled()))
                .flatMap(this::buildSuccess);
    }
}
