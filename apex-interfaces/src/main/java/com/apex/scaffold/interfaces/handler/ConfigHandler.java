package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.ConfigRequest;
import com.apex.scaffold.application.service.ConfigService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import org.springframework.http.MediaType;
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
        return configService.listConfigs().flatMap(this::ok);
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
        return configService.getConfig(configId(request)).flatMap(this::ok);
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
        return request.bodyToMono(ConfigRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(configService::createConfig)
                .flatMap(this::ok);
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
        Long id = configId(request);
        return request.bodyToMono(ConfigRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(body -> configService.updateConfig(id, body))
                .flatMap(this::ok);
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
                .deleteConfig(configId(request))
                .flatMap(deleted -> ok(java.util.Map.of("deleted", deleted)));
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
        Long id = configId(request);
        return request.bodyToMono(StatusRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(body -> configService.updateConfigStatus(id, body.isEnabled()))
                .flatMap(this::ok);
    }

    /**
     * 解析并校验路径参数 {@code id}。
     *
     * @param request HTTP 请求
     * @return 大于 0 的参数配置 ID
     * @throws BusinessException 当参数缺失、非数字或不大于 0 时抛出
     */
    private Long configId(ServerRequest request) {
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null || id <= 0) throw new BusinessException(ResultCode.PARAM_ERROR, "id格式错误");
        return id;
    }

    /**
     * 构造统一 JSON 成功响应。
     *
     * @param data 业务数据，可为 {@code null}
     * @return 携带当前 traceId 的 200 JSON 响应
     */
    private <T> Mono<ServerResponse> ok(T data) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.success(data, TraceIdUtil.getTraceId()));
    }

    /**
     * 状态更新请求体。
     *
     * <p>仅承载 {@code enabled} 布尔值，表示启用或停用目标对象。</p>
     */
    public static class StatusRequest {

        /** 是否启用：true-启用，false-停用 */
        private boolean enabled;

        /**
         * 构造状态更新请求体。
         */
        public StatusRequest() {}

        /**
         * 获取是否启用。
         *
         * @return true-启用，false-停用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用。
         *
         * @param enabled true-启用，false-停用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
