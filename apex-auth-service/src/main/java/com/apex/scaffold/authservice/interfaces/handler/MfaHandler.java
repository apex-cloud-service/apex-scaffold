package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses;
import com.apex.scaffold.auth.application.dto.MfaSetupRequest;
import com.apex.scaffold.auth.application.dto.MfaSetupResponse;
import com.apex.scaffold.auth.application.dto.MfaVerifyRequest;
import com.apex.scaffold.auth.application.service.TotpService;
import com.apex.scaffold.authservice.interfaces.util.RequestHandlerUtil;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.OperationLog.OperationType;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * TOTP 设置、验证、禁用与状态查询处理器，响应使用 Result 包装。
 *
 * <p>请求体中的用户和租户字段沿用现有 DTO 契约，但仅用于与认证主体比对；实际执行
 * 一律使用已验证令牌中的用户与租户，客户端不能通过请求体或查询参数操作他人配置。
 *
 * @author Apex Scaffold Team
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "MFA 多因素认证", description = "TOTP、备份码等 MFA 管理接口")
public class MfaHandler {
    private final TotpService totpService;
    private final RequestHandlerUtil requestHandlerUtil;

    /** 认证主体及其可信租户。 */
    private record TrustedIdentity(Long userId, String tenantId) {}

    /**
     * 从已验证的安全上下文解析当前用户与租户；缺少认证主体时按未授权拒绝。
     *
     * @return 可信身份
     */
    private Mono<TrustedIdentity> trustedIdentity() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(authentication -> authentication != null
                        && authentication.isAuthenticated()
                        && authentication.getName() != null
                        && !authentication.getName().isBlank())
                .flatMap(authentication -> {
                    Long userId;
                    try {
                        userId = Long.valueOf(authentication.getName());
                    } catch (NumberFormatException exception) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED));
                    }
                    Object details = authentication.getDetails();
                    String tenantId = details instanceof String value ? value : null;
                    if (tenantId == null || tenantId.isBlank()) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "缺少可信租户上下文"));
                    }
                    return Mono.just(new TrustedIdentity(userId, tenantId));
                })
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED)));
    }

    /**
     * 校验请求声明的用户与租户是否与认证主体一致，防止越权操作他人 MFA。
     *
     * @param identity 可信身份
     * @param userId   请求声明的用户 ID
     * @param tenantId 请求声明的租户 ID
     * @return 校验通过时完成
     */
    private Mono<Void> requireSelf(TrustedIdentity identity, Long userId, String tenantId) {
        if (userId == null || !userId.equals(identity.userId())) {
            return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "只能操作本人的 MFA 配置"));
        }
        if (tenantId == null || !tenantId.equals(identity.tenantId())) {
            return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "租户与认证主体不一致"));
        }
        return Mono.empty();
    }

    /**
     * 根据 JSON 请求中的用户、租户、颁发者和账号名称生成 TOTP 设置数据。
     *
     * @param request 包含 MfaSetupRequest 的请求
     * @return 包装密钥、OTP Auth URL 和备份码的响应；异常由请求工具处理
     */
    @OperationLog(
            module = "多因素认证",
            type = OperationType.INSERT,
            description = "设置 TOTP MFA",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "setupMfa", summary = "设置 TOTP MFA", description = "生成 TOTP 密钥、OTP Auth URL 和备份码；尚需验证码激活")
    @RequestBody(
            required = true,
            description = "MFA 设置请求",
            content = @Content(schema = @Schema(implementation = MfaSetupRequest.class)))
    @ApiResponse(
            responseCode = "200",
            description = "设置成功",
            content = @Content(schema = @Schema(implementation = AuthCenterApiResponses.MfaSetupResult.class)))
    @ApiResponse(
            responseCode = "400",
            description = "请求参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> setupMfa(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, MfaSetupRequest.class)
                .flatMap(req -> trustedIdentity().flatMap(identity -> requireSelf(
                                identity, req.getUserId(), req.getTenantId())
                        .then(Mono.defer(() -> totpService.generateSecret(
                                identity.userId(), identity.tenantId(), req.getIssuer(), req.getAccountName())))))
                .map(result -> MfaSetupResponse.builder()
                        .secret(result.secret())
                        .otpAuthUrl(result.otpAuthUrl())
                        .backupCodes(result.backupCodes())
                        .build())
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(response)))
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 校验 JSON 请求中的 TOTP 验证码并激活配置。
     *
     * @param request 包含用户、租户和 code 的请求
     * @return HTTP 200；成功时 data.enabled=true，校验未通过时返回业务错误
     */
    @OperationLog(
            module = "多因素认证",
            type = OperationType.UPDATE,
            description = "验证并启用 MFA",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "verifyAndEnableMfa",
            summary = "验证 TOTP 并启用 MFA",
            description = "校验通过后启用 MFA；校验未通过仍返回 HTTP 200，使用 Result 业务码表示失败")
    @RequestBody(
            required = true,
            description = "MFA 验证请求",
            content = @Content(schema = @Schema(implementation = MfaVerifyRequest.class)))
    @ApiResponse(
            responseCode = "200",
            description = "激活成功或业务校验失败",
            content = @Content(schema = @Schema(implementation = AuthCenterApiResponses.MfaEnabledResult.class)))
    @ApiResponse(
            responseCode = "400",
            description = "请求参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> verifyAndEnableMfa(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, MfaVerifyRequest.class)
                .flatMap(req -> trustedIdentity()
                        .flatMap(identity -> requireSelf(identity, req.getUserId(), req.getTenantId())
                                .then(Mono.defer(() -> totpService.verifyAndEnable(
                                        identity.userId(), req.getCode(), identity.tenantId())))))
                .flatMap(success -> {
                    if (success) {
                        return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Result.success(Map.of("enabled", true)));
                    }
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.error("验证码错误"));
                })
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 验证指定用户、租户的 TOTP 验证码。
     *
     * @param request 包含用户、租户和 code 的 JSON 请求
     * @return HTTP 200；成功时 data.verified=true，验证未通过时返回业务错误
     */
    @OperationLog(
            module = "多因素认证",
            type = OperationType.SELECT,
            description = "验证 TOTP 验证码",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "verifyMfaCode",
            summary = "验证 TOTP 验证码",
            description = "验证 TOTP；未通过仍返回 HTTP 200，使用 Result 业务码表示失败")
    @RequestBody(
            required = true,
            description = "MFA 验证请求",
            content = @Content(schema = @Schema(implementation = MfaVerifyRequest.class)))
    @ApiResponse(
            responseCode = "200",
            description = "验证成功或业务校验失败",
            content = @Content(schema = @Schema(implementation = AuthCenterApiResponses.MfaVerifiedResult.class)))
    @ApiResponse(
            responseCode = "400",
            description = "请求参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> verifyMfaCode(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, MfaVerifyRequest.class)
                .flatMap(req -> trustedIdentity()
                        .flatMap(identity -> requireSelf(identity, req.getUserId(), req.getTenantId())
                                .then(Mono.defer(() -> totpService.verifyCode(
                                        identity.userId(), req.getCode(), identity.tenantId())))))
                .flatMap(success -> {
                    if (success) {
                        return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Result.success(Map.of("verified", true)));
                    }
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.error("验证码错误"));
                })
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 使用请求体中的 backupCode 验证备份码。
     *
     * @param request 包含用户、租户和 backupCode 的 JSON 请求
     * @return HTTP 200；成功时 data.verified=true，无效或已使用时返回业务错误
     */
    @OperationLog(
            module = "多因素认证",
            type = OperationType.SELECT,
            description = "验证备份码",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "verifyBackupCode",
            summary = "验证备份码",
            description = "使用 backupCode 验证；无效或已使用仍返回 HTTP 200 业务错误")
    @RequestBody(
            required = true,
            description = "MFA 验证请求，使用 backupCode 字段",
            content = @Content(schema = @Schema(implementation = MfaVerifyRequest.class)))
    @ApiResponse(
            responseCode = "200",
            description = "验证成功或业务校验失败",
            content = @Content(schema = @Schema(implementation = AuthCenterApiResponses.MfaVerifiedResult.class)))
    @ApiResponse(
            responseCode = "400",
            description = "请求参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> verifyBackupCode(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, MfaVerifyRequest.class)
                .flatMap(req -> trustedIdentity()
                        .flatMap(identity -> requireSelf(identity, req.getUserId(), req.getTenantId())
                                .then(Mono.defer(() -> totpService.verifyBackupCode(
                                        identity.userId(), req.getBackupCode(), identity.tenantId())))))
                .flatMap(success -> {
                    if (success) {
                        return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Result.success(Map.of("verified", true)));
                    }
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.error("备份码无效或已使用"));
                })
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 禁用 JSON 请求中指定用户、租户的 TOTP 配置。
     *
     * @param request 包含用户和租户的 MfaVerifyRequest
     * @return HTTP 200；成功时 data.disabled=true，未配置时返回业务错误
     */
    @OperationLog(
            module = "多因素认证",
            type = OperationType.DELETE,
            description = "禁用 MFA",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "disableMfa", summary = "禁用 MFA", description = "禁用 TOTP；未配置时仍返回 HTTP 200 业务错误")
    @RequestBody(
            required = true,
            description = "MFA 禁用请求，使用用户与租户字段",
            content = @Content(schema = @Schema(implementation = MfaVerifyRequest.class)))
    @ApiResponse(
            responseCode = "200",
            description = "禁用成功或未配置",
            content = @Content(schema = @Schema(implementation = AuthCenterApiResponses.MfaDisabledResult.class)))
    @ApiResponse(
            responseCode = "400",
            description = "请求参数错误",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> disableMfa(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, MfaVerifyRequest.class)
                .flatMap(req -> trustedIdentity()
                        .flatMap(identity -> requireSelf(identity, req.getUserId(), req.getTenantId())
                                .then(Mono.defer(() -> totpService.disable(identity.userId(), identity.tenantId())))))
                .flatMap(success -> {
                    if (success) {
                        return ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Result.success(Map.of("disabled", true)));
                    }
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.error("禁用失败，MFA 未配置"));
                })
                .onErrorResume(requestHandlerUtil::handleError);
    }

    /**
     * 根据查询参数 userId 和 tenantId 查询 TOTP 状态。
     *
     * @param request 带用户和租户查询参数的请求
     * @return Result 包装的 enabled 与 mfaType；用户 ID 格式错误或异步查询失败以 HTTP 200 业务错误返回
     */
    @OperationLog(
            module = "多因素认证",
            type = OperationType.SELECT,
            description = "查询 MFA 状态",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "getMfaStatus",
            summary = "查询 MFA 状态",
            description = "返回 enabled 与 mfaType（TOTP/NONE）；用户 ID 格式错误或异步查询失败仍返回 HTTP 200 业务错误")
    @Parameter(
            name = "userId",
            in = ParameterIn.QUERY,
            description = "用户 ID",
            required = true,
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(
            name = "tenantId",
            in = ParameterIn.QUERY,
            description = "租户 ID",
            required = true,
            schema = @Schema(type = "string"))
    @ApiResponse(
            responseCode = "200",
            description = "查询成功或业务失败",
            content = @Content(schema = @Schema(implementation = AuthCenterApiResponses.MfaStatusResult.class)))
    @ApiResponse(
            responseCode = "400",
            description = "缺少必填查询参数",
            content = @Content(schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getMfaStatus(ServerRequest request) {
        String userIdStr =
                request.queryParam("userId").orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String tenantId =
                request.queryParam("tenantId").orElseThrow(() -> new IllegalArgumentException("tenantId is required"));
        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(Result.error("userId 格式错误"));
        }
        Long requestedUserId = userId;
        return trustedIdentity()
                .flatMap(identity -> requireSelf(identity, requestedUserId, tenantId)
                        .then(Mono.defer(() -> totpService.isEnabled(identity.userId(), identity.tenantId()))))
                .flatMap(enabled -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(Map.of("enabled", enabled, "mfaType", enabled ? "TOTP" : "NONE"))))
                .onErrorResume(e -> {
                    if (e instanceof BusinessException businessException) {
                        return requestHandlerUtil.handleError(businessException);
                    }
                    log.error("查询 MFA 状态失败", e);
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.error("查询失败: " + e.getMessage()));
                });
    }
}
