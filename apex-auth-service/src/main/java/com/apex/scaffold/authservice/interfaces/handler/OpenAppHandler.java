package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.domain.model.entity.AuthConfigEntity;
import com.apex.scaffold.auth.domain.repository.AuthConfigRepository;
import com.apex.scaffold.auth.openplatform.OpenAppScopes;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 开放平台应用管理 Handler（方案 M4 / 批次 D2）。
 *
 * <p>开放应用复用认证中心 OAUTH2_CLIENT 注册表（{@code auth_config}，configValue JSON），
 * 在其上扩展开放平台元数据：应用名、描述、scope 清单、限流档、状态。
 *
 * <p>端点：
 * <ul>
 *   <li>{@code GET  /admin-api/v1/open-apps}——列出本租户开放应用</li>
 *   <li>{@code GET  /admin-api/v1/open-apps/{clientId}}——应用详情（含已声明 scope）</li>
 *   <li>{@code GET  /admin-api/v1/open-apps/scopes}——平台 scope 目录（供注册时选择）</li>
 * </ul>
 *
 * <p>安全边界：管理端点由网关+认证中心安全链保护（需平台管理员令牌），
 * 本 Handler 不做二次鉴权；应用凭据的签发/轮换沿用既有 OAUTH2_CLIENT 管理端点。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "开放平台应用（认证中心）", description = "开放应用注册表查询与 scope 目录")
public class OpenAppHandler {

    /** 注册表类型：OAuth2 客户端。 */
    private static final String OAUTH2_CLIENT = "OAUTH2_CLIENT";

    private final AuthConfigRepository configRepository;

    private final ObjectMapper objectMapper;

    /**
     * 列出当前租户的开放应用。
     *
     * @param request 当前请求（租户取自可信上下文）
     * @return 应用列表
     */
    @Operation(
            operationId = "authListOpenApps",
            summary = "列出当前租户开放应用",
            description = "基于 OAUTH2_CLIENT 注册表列出本租户开放应用摘要（应用名、scope、限流档、启停）；" + "租户取自可信上下文，缺失以 500 失败")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为应用摘要列表（clientId/appName/scopes/rateLimitTier/enabled）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                type = "object",
                                                additionalProperties = Schema.AdditionalPropertiesValue.TRUE))),
        @ApiResponse(responseCode = "500", description = "缺少可信租户上下文或查询失败")
    })
    public Mono<ServerResponse> listApps(ServerRequest request) {
        return Mono.deferContextual(context -> {
                    String tenant = context.getOrDefault(
                            com.apex.scaffold.common.context.TenantContext.REACTOR_CONTEXT_KEY, null);
                    if (tenant == null || tenant.isBlank()) {
                        return Mono.error(new IllegalStateException("缺少可信租户上下文"));
                    }
                    return configRepository
                            .findEnabledByType(OAUTH2_CLIENT, tenant)
                            .collectList();
                })
                .flatMap(configs -> ServerResponse.ok()
                        .bodyValue(Result.success(
                                configs.stream().map(this::toSummary).toList())))
                .onErrorResume(this::asError);
    }

    /**
     * 查询开放应用详情。
     *
     * @param request 当前请求（路径参数 clientId）
     * @return 应用详情
     */
    @Operation(
            operationId = "authGetOpenApp",
            summary = "查询开放应用详情",
            description = "按客户端 ID 查询本租户开放应用详情（含已声明 scope 与非法 scope 校验结果）；不存在返回 404")
    @Parameter(
            name = "clientId",
            in = ParameterIn.PATH,
            required = true,
            description = "开放应用客户端 ID",
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 含 clientId/appName/description/scopes/invalidScopes/"
                        + "rateLimitTier/redirectUris/enabled",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                type = "object",
                                                additionalProperties = Schema.AdditionalPropertiesValue.TRUE))),
        @ApiResponse(responseCode = "404", description = "开放应用不存在"),
        @ApiResponse(responseCode = "500", description = "缺少可信租户上下文或查询失败")
    })
    public Mono<ServerResponse> getApp(ServerRequest request) {
        String clientId = request.pathVariable("clientId");
        return Mono.deferContextual(context -> {
                    String tenant = context.getOrDefault(
                            com.apex.scaffold.common.context.TenantContext.REACTOR_CONTEXT_KEY, null);
                    if (tenant == null || tenant.isBlank()) {
                        return Mono.error(new IllegalStateException("缺少可信租户上下文"));
                    }
                    return configRepository
                            .findEnabledByType(OAUTH2_CLIENT, tenant)
                            .filter(config -> clientId.equals(config.getConfigKey()))
                            .next();
                })
                .flatMap(config -> ServerResponse.ok().bodyValue(Result.success(toDetail(config))))
                .switchIfEmpty(ServerResponse.status(404).bodyValue(Result.error("开放应用不存在")))
                .onErrorResume(this::asError);
    }

    /**
     * 平台 scope 目录。
     *
     * @param request 当前请求
     * @return scope 列表与权限码映射
     */
    @Operation(
            operationId = "authListOpenAppScopes",
            summary = "平台 scope 目录",
            description = "返回开放平台 scope 注册表与权限码映射（scope/permission），供注册应用时选择")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为 scope 目录（元素含 scope/permission）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(
                                                type = "object",
                                                additionalProperties = Schema.AdditionalPropertiesValue.TRUE)))
    })
    public Mono<ServerResponse> listScopes(ServerRequest request) {
        java.util.List<java.util.Map<String, String>> scopes = OpenAppScopes.catalog().stream()
                .map(scope -> java.util.Map.of(
                        "scope", scope, "permission", String.valueOf(OpenAppScopes.permissionFor(scope))))
                .toList();
        return ServerResponse.ok().bodyValue(Result.success(scopes));
    }

    /**
     * 应用摘要（列表用）。
     *
     * @param config 注册配置
     * @return 摘要
     */
    private java.util.Map<String, Object> toSummary(AuthConfigEntity config) {
        JsonNode node = parse(config.getConfigValue());
        return java.util.Map.of(
                "clientId", config.getConfigKey(),
                "appName", node.path("appName").asText(config.getDescription() == null ? "" : config.getDescription()),
                "scopes", node.path("scopes").toString(),
                "rateLimitTier", node.path("rateLimitTier").asText("default"),
                "enabled", Boolean.TRUE.equals(config.getEnabled()));
    }

    /**
     * 应用详情（含 scope 合法性校验结果）。
     *
     * @param config 注册配置
     * @return 详情
     */
    private java.util.Map<String, Object> toDetail(AuthConfigEntity config) {
        JsonNode node = parse(config.getConfigValue());
        java.util.List<String> declared = new java.util.ArrayList<>();
        node.path("scopes").forEach(scope -> declared.add(scope.asText()));
        java.util.Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("clientId", config.getConfigKey());
        detail.put("appName", node.path("appName").asText(""));
        detail.put("description", node.path("description").asText(""));
        detail.put("scopes", declared);
        detail.put("invalidScopes", OpenAppScopes.invalidScopes(declared));
        detail.put("rateLimitTier", node.path("rateLimitTier").asText("default"));
        detail.put("redirectUris", node.path("redirectUris").toString());
        detail.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        return detail;
    }

    /**
     * 解析注册配置 JSON（损坏时返回空对象，不抛出）。
     *
     * @param value 配置原文
     * @return JSON 节点
     */
    private JsonNode parse(String value) {
        try {
            JsonNode node = objectMapper.readTree(value == null ? "{}" : value);
            return node == null ? objectMapper.createObjectNode() : node;
        } catch (Exception invalid) {
            log.warn("开放应用配置 JSON 解析失败: {}", invalid.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    /**
     * 统一错误响应。
     *
     * @param error 异常
     * @return 错误响应
     */
    private Mono<ServerResponse> asError(Throwable error) {
        log.error("开放应用管理处理异常", error);
        return ServerResponse.status(500).bodyValue(Result.error(ResultCode.ERROR.getMsg()));
    }
}
