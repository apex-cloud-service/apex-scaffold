package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.OpenAppRequest;
import com.apex.scaffold.application.openplatform.OpenAppAdminService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 开放应用管理 Handler（D2，函数式端点）。
 *
 * <p>基于既有 OAUTH2_CLIENT 注册表管理开放平台第三方应用：注册（scope 断言 + 密钥
 * 一次性下发）、查询、更新（回调/scope/元数据/状态）与密钥重置。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>POST /api/v1/open-apps - 创建开放应用</li>
 *   <li>GET /api/v1/open-apps - 分页查询应用列表</li>
 *   <li>GET /api/v1/open-apps/{clientId} - 查询应用详情</li>
 *   <li>PUT /api/v1/open-apps/{clientId} - 更新应用配置</li>
 *   <li>POST /api/v1/open-apps/{clientId}/reset-secret - 重置应用密钥</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 * @see OpenAppAdminService
 */
@SuppressWarnings({"checkstyle:TypeName", "PMD.ClassNamingShouldBeCamelRule"})
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "开放应用管理", description = "开放平台应用的注册、查询、更新与密钥管理接口")
public class OpenAppAdminHandler {

    private final OpenAppAdminService openAppAdminService;

    /**
     * 创建开放应用.
     *
     * @param request 服务器请求，请求体为 {@link OpenAppRequest} JSON
     * @return 创建后的应用（含一次性明文密钥）
     */
    @RequirePermission("open:app:manage")
    @Operation(
            operationId = "createOpenApp",
            summary = "创建开放应用",
            description = "注册开放平台应用，scope 须在开放平台 scope 注册表内；明文密钥仅本次返回")
    @OperationLog(
            module = "OpenAppAdmin",
            type = OperationLog.OperationType.INSERT,
            description = "createOpenApp",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> createApp(ServerRequest request) {
        return request.bodyToMono(OpenAppRequest.class)
                .flatMap(openAppAdminService::createApp)
                .flatMap(response -> ok(Result.success(response)));
    }

    /**
     * 分页查询应用列表.
     *
     * @param request 服务器请求（query 参数 page/size）
     * @return 应用列表
     */
    @RequirePermission("open:app:read")
    @Operation(
            operationId = "listOpenApps",
            summary = "分页查询开放应用",
            description = "基于 OAUTH2_CLIENT 注册表的分页查询，含未开通开放元数据的普通客户端")
    public Mono<ServerResponse> listApps(ServerRequest request) {
        int page = intParam(request, "page", 0);
        int size = intParam(request, "size", 20);
        return openAppAdminService.listApps(page, size).collectList().flatMap(list -> ok(Result.success(list)));
    }

    /**
     * 查询应用详情.
     *
     * @param request 服务器请求（路径变量 clientId）
     * @return 应用详情（不含密钥）
     */
    @RequirePermission("open:app:read")
    @Operation(operationId = "getOpenApp", summary = "查询开放应用详情", description = "按客户端 ID 查询开放应用注册信息与 scope 清单")
    public Mono<ServerResponse> getApp(ServerRequest request) {
        return openAppAdminService
                .getApp(request.pathVariable("clientId"))
                .flatMap(response -> ok(Result.success(response)));
    }

    /**
     * 更新应用配置.
     *
     * @param request 服务器请求（路径变量 clientId，请求体为 {@link OpenAppRequest} JSON）
     * @return 更新后的应用
     */
    @RequirePermission("open:app:manage")
    @Operation(operationId = "updateOpenApp", summary = "更新开放应用", description = "更新回调、scope 清单与开放元数据；请求缺省字段保持原值")
    @OperationLog(
            module = "OpenAppAdmin",
            type = OperationLog.OperationType.UPDATE,
            description = "updateOpenApp",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> updateApp(ServerRequest request) {
        String clientId = request.pathVariable("clientId");
        return request.bodyToMono(OpenAppRequest.class)
                .flatMap(body -> openAppAdminService.updateApp(clientId, body))
                .flatMap(response -> ok(Result.success(response)));
    }

    /**
     * 重置应用密钥.
     *
     * @param request 服务器请求（路径变量 clientId）
     * @return 应用信息（{@code clientSecret} 为一次性明文）
     */
    @RequirePermission("open:app:manage")
    @Operation(operationId = "resetOpenAppSecret", summary = "重置开放应用密钥", description = "生成新的随机密钥并加密落库，明文仅本次返回")
    @OperationLog(
            module = "OpenAppAdmin",
            type = OperationLog.OperationType.UPDATE,
            description = "resetOpenAppSecret",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> resetSecret(ServerRequest request) {
        return openAppAdminService
                .resetSecret(request.pathVariable("clientId"))
                .flatMap(response -> ok(Result.success(response)));
    }

    private static Mono<ServerResponse> ok(Result<?> body) {
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(body);
    }

    private static int intParam(ServerRequest request, String name, int defaultValue) {
        String value = request.queryParam(name).orElse(null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
}
