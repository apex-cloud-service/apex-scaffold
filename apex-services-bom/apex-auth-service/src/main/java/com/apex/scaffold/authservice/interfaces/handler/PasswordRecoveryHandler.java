package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.PasswordRecoveryConfirmResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.PasswordRecoveryInitiateResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.PasswordRecoveryVerifyResult;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryConfirmRequest;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryInitiateRequest;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryVerifyRequest;
import com.apex.scaffold.auth.application.service.PasswordRecoveryApplicationService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 自助密码找回 Handler。
 *
 * <p>三段式匿名链路：申请恢复单 → 校验验证码 → 确认重设。所有响应统一包装 {@link Result}；
 * 恢复凭证、联系方式与新密码不写入日志（saveRequest/saveResponse 均关闭）。
 * 对账号存在与否、无效凭证等场景返回统一的通用错误，不泄露账号信息。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "自助密码找回", description = "匿名密码找回链路：申请恢复单、校验验证码、确认重设密码")
public class PasswordRecoveryHandler {

    private final PasswordRecoveryApplicationService recoveryService;

    /**
     * 发起密码找回申请。
     *
     * <p>按 IP 与联系方式双维度限流；对账号存在与否保持同一响应形态。</p>
     */
    @Operation(operationId = "initiatePasswordRecovery", summary = "发起密码找回申请")
    @ApiResponse(
            responseCode = "200",
            description = "申请受理，返回恢复单ID与验证码有效期",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PasswordRecoveryInitiateResult.class)))
    @OperationLog(
            module = "Authentication",
            type = OperationLog.OperationType.INSERT,
            description = "password-recovery-request",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> initiate(ServerRequest request) {
        String clientIp = request.remoteAddress()
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");
        return request.bodyToMono(PasswordRecoveryInitiateRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_INVALID, "请求体不能为空")))
                .flatMap(req -> recoveryService.initiate(req, clientIp))
                .flatMap(resp -> ok(Result.success(resp)))
                .onErrorResume(this::toErrorResponse);
    }

    /**
     * 校验验证码并获取一次性确认令牌。
     */
    @Operation(operationId = "verifyPasswordRecovery", summary = "校验找回验证码")
    @ApiResponse(
            responseCode = "200",
            description = "校验通过，返回一次性确认令牌",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PasswordRecoveryVerifyResult.class)))
    @OperationLog(
            module = "Authentication",
            type = OperationLog.OperationType.SELECT,
            description = "password-recovery-verify",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> verify(ServerRequest request) {
        return request.bodyToMono(PasswordRecoveryVerifyRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_INVALID, "请求体不能为空")))
                .flatMap(recoveryService::verify)
                .flatMap(resp -> ok(Result.success(resp)))
                .onErrorResume(this::toErrorResponse);
    }

    /**
     * 确认重设密码并撤销全部会话。
     *
     * <p>撤销失败时响应中 sessionRevocation=PENDING，不伪称全端退出。</p>
     */
    @Operation(operationId = "confirmPasswordRecovery", summary = "确认重设密码")
    @ApiResponse(
            responseCode = "200",
            description = "密码已重设，返回会话撤销状态",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PasswordRecoveryConfirmResult.class)))
    @OperationLog(
            module = "Authentication",
            type = OperationLog.OperationType.UPDATE,
            description = "password-recovery-confirm",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> confirm(ServerRequest request) {
        return request.bodyToMono(PasswordRecoveryConfirmRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_INVALID, "请求体不能为空")))
                .flatMap(recoveryService::confirm)
                .flatMap(resp -> ok(Result.success(resp)))
                .onErrorResume(this::toErrorResponse);
    }

    private Mono<ServerResponse> ok(Result<?> body) {
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(body);
    }

    private Mono<ServerResponse> toErrorResponse(Throwable error) {
        if (error instanceof BusinessException be) {
            return ServerResponse.status(toHttpStatus(be.getCode()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Result.error(be.getCode(), be.getMessage()));
        }
        log.error("密码找回处理失败", error);
        return ServerResponse.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.error("系统错误"));
    }

    private int toHttpStatus(Integer code) {
        if (code == null) {
            return 500;
        }
        if (code.equals(ResultCode.UNAUTHORIZED.getCode())) {
            return 401;
        }
        if (code.equals(ResultCode.RATE_LIMIT.getCode())) {
            return 429;
        }
        if (code.equals(ResultCode.SMS_SERVICE_ERROR.getCode())) {
            return 503;
        }
        return 400;
    }
}
