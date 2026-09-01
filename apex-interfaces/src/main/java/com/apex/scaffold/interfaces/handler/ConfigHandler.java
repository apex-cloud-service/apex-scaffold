package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.ConfigRequest;
import com.apex.scaffold.application.dto.admin.StatusRequest;
import com.apex.scaffold.application.service.ConfigService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
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
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为参数配置 DTO 列表
     */
    @RequirePermission("system:config:list")
    @Operation(operationId = "adminListConfigs", summary = "查询参数配置列表")
    public Mono<ServerResponse> list(ServerRequest request) {
        return configService.listConfigs().flatMap(this::buildSuccess);
    }

    /**
     * 查询参数配置详情。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为参数配置实体
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:config:query")
    @Operation(operationId = "adminGetConfig", summary = "查询参数配置详情")
    public Mono<ServerResponse> detail(ServerRequest request) {
        return configService.getConfig(requirePositiveId(request)).flatMap(this::buildSuccess);
    }

    /**
     * 创建参数配置。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为已创建的参数配置实体
     * @throws BusinessException 当请求体为空时抛出
     */
    @RequirePermission("system:config:add")
    @Operation(operationId = "adminCreateConfig", summary = "创建参数配置")
    @OperationLog(module = "参数配置", type = OperationLog.OperationType.INSERT, description = "创建参数配置")
    public Mono<ServerResponse> create(ServerRequest request) {
        return parseBody(request, ConfigRequest.class)
                .flatMap(configService::createConfig)
                .flatMap(this::buildSuccess);
    }

    /**
     * 更新参数配置。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为已更新的参数配置实体
     * @throws BusinessException 当 {@code id} 非法或请求体为空时抛出
     */
    @RequirePermission("system:config:edit")
    @Operation(operationId = "adminUpdateConfig", summary = "更新参数配置")
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
    @Operation(operationId = "adminDeleteConfig", summary = "删除参数配置")
    @OperationLog(module = "参数配置", type = OperationLog.OperationType.DELETE, description = "删除参数配置")
    public Mono<ServerResponse> delete(ServerRequest request) {
        return configService
                .deleteConfig(requirePositiveId(request))
                .flatMap(deleted -> buildSuccess(java.util.Map.of("deleted", deleted)));
    }

    /**
     * 启用或停用参数配置。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为更新后的参数配置实体
     * @throws BusinessException 当 {@code id} 非法或请求体为空时抛出
     */
    @RequirePermission("system:config:edit")
    @Operation(operationId = "adminUpdateConfigStatus", summary = "启用或停用参数配置")
    @OperationLog(module = "参数配置", type = OperationLog.OperationType.UPDATE, description = "更新参数配置状态")
    public Mono<ServerResponse> updateStatus(ServerRequest request) {
        Long id = requirePositiveId(request);
        return parseBody(request, StatusRequest.class)
                .flatMap(body -> configService.updateConfigStatus(id, body.isEnabled()))
                .flatMap(this::buildSuccess);
    }
}
