package com.apex.scaffold.maskingservice.interfaces;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.masking.JsonFieldMasker;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.maskingservice.config.MaskingProperties;
import com.apex.scaffold.maskingservice.config.MaskingSecurityConfig;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

/**
 * 批量请求协议适配，异常只返回固定业务码，不记录请求或异常原文。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Component
@RequiredArgsConstructor
@Tag(name = "脱敏服务", description = "按当前 JWT 租户的固定版本规则批量脱敏 JSON 文档；不持久化请求内容")
public class MaskingHandler {
    private final MaskingProperties properties;
    private final Validator validator;

    /**
     * 使用当前 JWT 租户获准访问的固定版本规则，逐个脱敏 JSON 文档。
     * @param request 包含版本号与文档列表的 JSON 请求
     * @return 包含版本及脱敏文档列表的统一响应；请求非法、未授权或超限时返回相应错误
     */
    @Operation(
            operationId = "maskingBatch",
            summary = "按租户授权版本批量脱敏",
            description = "租户仅来自已验证 JWT 的 tenant_id；策略为启动配置，返回版本及脱敏后的 JSON 文档",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody =
                    @RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = MaskingRequest.class))),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "批量脱敏结果",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MaskingBatchResult.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "请求非法",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MaskingErrorResult.class))),
                @ApiResponse(responseCode = "401", description = "未认证"),
                @ApiResponse(
                        responseCode = "403",
                        description = "缺少权限、受信任租户或版本未授权",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MaskingErrorResult.class))),
                @ApiResponse(
                        responseCode = "413",
                        description = "请求体超过上限",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MaskingErrorResult.class)))
            })
    @OperationLog(
            module = "Masking",
            type = OperationLog.OperationType.OTHER,
            description = "batch",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> batch(ServerRequest request) {
        return requireCapability("masking:apply")
                .then(tenant().flatMap(tenant -> request.bodyToMono(MaskingRequest.class)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("请求不能为空")))
                        .flatMap(input -> {
                            if (!validator.validate(input).isEmpty()) {
                                return Mono.error(new IllegalArgumentException("参数错误"));
                            }
                            MaskingProperties.Policy policy =
                                    properties.versions().get(input.version());
                            if (policy == null || !policy.tenants().contains(tenant)) {
                                return Mono.error(new AccessDeniedException("禁止访问"));
                            }
                            var documents = input.documents().stream()
                                    .map(document -> JsonFieldMasker.mask(document, policy.fields()))
                                    .toList();
                            return ServerResponse.ok()
                                    .bodyValue(
                                            Result.success(Map.of("version", input.version(), "documents", documents)));
                        })))
                .onErrorResume(
                        AccessDeniedException.class, error -> failure(HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN))
                .onErrorResume(
                        IllegalArgumentException.class,
                        error -> failure(HttpStatus.BAD_REQUEST, ResultCode.PARAM_ERROR))
                .onErrorResume(
                        ServerWebInputException.class, error -> failure(HttpStatus.BAD_REQUEST, ResultCode.PARAM_ERROR))
                .onErrorResume(
                        DataBufferLimitException.class,
                        error -> failure(HttpStatus.PAYLOAD_TOO_LARGE, ResultCode.PARAM_ERROR));
    }

    /**
     * 列出当前已认证租户可以访问的版本及字段规则。
     * @param request 请求
     * @return 策略响应
     */
    @Operation(
            operationId = "maskingRules",
            summary = "列出当前租户可访问的固定版本规则",
            description = "仅返回 JWT tenant_id 获得授权的版本，data 按版本、字段路径映射至脱敏规则",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "按版本及字段路径组织的规则映射",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MaskingRulesResult.class))),
                @ApiResponse(responseCode = "401", description = "未认证"),
                @ApiResponse(
                        responseCode = "403",
                        description = "缺少权限或受信任租户",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = MaskingErrorResult.class)))
            })
    @OperationLog(
            module = "Masking",
            type = OperationLog.OperationType.SELECT,
            description = "rules",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> rules(ServerRequest request) {
        return requireCapability("masking:rules")
                .then(tenant().flatMap(tenant -> {
                    var versions = properties.versions().entrySet().stream()
                            .filter(entry -> entry.getValue().tenants().contains(tenant))
                            .collect(java.util.stream.Collectors.toMap(
                                    Map.Entry::getKey, entry -> entry.getValue().fields()));
                    return ServerResponse.ok().bodyValue(Result.success(versions));
                }))
                .onErrorResume(
                        AccessDeniedException.class, error -> failure(HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN));
    }

    /**
     * 从验证后的 JWT 提取租户，不接受请求头或输入覆盖。
     * @return 租户
     */
    private Mono<String> tenant() {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(context -> {
                    if (!(context.getAuthentication() instanceof JwtAuthenticationToken token)) {
                        return Mono.empty();
                    }
                    var claims = token.getToken().getClaims();
                    Object tenant = claims.get("tenant_id") != null ? claims.get("tenant_id") : claims.get("tenantId");
                    return tenant instanceof String value && !value.isBlank() ? Mono.just(value) : Mono.empty();
                })
                .switchIfEmpty(Mono.error(new AccessDeniedException("缺少受信任租户")));
    }

    /**
     * 按令牌家族校验操作能力：OAuth2 令牌要求 scope，登录令牌要求 ACCESS 声明。
     * @param scope OAuth2 令牌要求具备的 scope 值
     * @return 校验通过返回空 Mono
     */
    private Mono<Void> requireCapability(String scope) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(authentication -> {
                    if (!(authentication instanceof JwtAuthenticationToken token)) {
                        return Mono.error(new AccessDeniedException("Forbidden"));
                    }
                    var claims = token.getToken().getClaims();
                    if (claims.get(MaskingSecurityConfig.OAUTH2_TOKEN_USE_CLAIM) != null) {
                        Object raw = claims.get("scope");
                        java.util.List<String> scopes = raw instanceof String value
                                ? java.util.List.of(value.split("\\s+"))
                                : (raw instanceof java.util.Collection<?> collection
                                        ? collection.stream()
                                                .map(String::valueOf)
                                                .toList()
                                        : java.util.List.of());
                        return scopes.contains(scope)
                                ? Mono.<Void>empty()
                                : Mono.error(new AccessDeniedException("缺少 scope: " + scope));
                    }
                    return MaskingSecurityConfig.LOGIN_ACCESS_TYPE.equals(
                                    claims.get(MaskingSecurityConfig.LOGIN_TOKEN_TYPE_CLAIM))
                            ? Mono.<Void>empty()
                            : Mono.error(new AccessDeniedException("缺少 ACCESS 令牌声明"));
                });
    }

    /**
     * 输出固定错误码。
     * @param status HTTP 状态
     * @param code 业务码
     * @return 响应
     */
    private Mono<ServerResponse> failure(HttpStatus status, ResultCode code) {
        return ServerResponse.status(status).bodyValue(Result.error(code));
    }

    /**
     * 批量脱敏数据的文档模型。
     *
     * @param version 使用的规则版本
     * @param documents 脱敏后的 JSON 文档，保留原文档结构
     */
    @Schema(name = "MaskingBatchData")
    public record MaskingBatchData(
            @Schema(description = "使用的规则版本") String version,
            @Schema(description = "脱敏后的 JSON 文档，保留原文档结构") List<JsonNode> documents) {}

    /** 批量脱敏统一响应的文档模型。 */
    @Schema(name = "MaskingBatchResult")
    public static class MaskingBatchResult extends Result<MaskingBatchData> {}

    /** 授权规则统一响应的文档模型。 */
    @Schema(name = "MaskingRulesResult")
    public static class MaskingRulesResult extends Result<Map<String, Map<String, JsonFieldMasker.Rule>>> {}

    /** 固定错误码统一响应的文档模型。 */
    @Schema(name = "MaskingErrorResult")
    public static class MaskingErrorResult extends Result<Void> {}
}
