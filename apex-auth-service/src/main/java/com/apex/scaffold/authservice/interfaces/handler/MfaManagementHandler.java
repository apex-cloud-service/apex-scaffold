package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses;
import com.apex.scaffold.auth.domain.model.MfaConfig;
import com.apex.scaffold.auth.domain.service.MfaService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.OperationLog.OperationType;
import com.apex.scaffold.common.annotation.RequireAdminRole;
import com.apex.scaffold.common.annotation.RequireResourcePermission;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
 * 多因素认证管理处理器。
 *
 * <p>当前用户与租户来自可信请求上下文。TOTP 初始化后需激活，短信与邮箱配置由服务直接启用。
 * 配置列表返回裸数组，其他成功响应使用 Result；服务异步失败返回 HTTP 500。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "多因素认证", description = "MFA/2FA管理 - 支持TOTP、SMS、Email三种认证方式")
public class MfaManagementHandler {
    private final MfaService mfaService;

    /**
     * 为当前用户初始化 TOTP，生成密钥与二维码信息，尚需激活。
     * @param request 提供可信 userId、tenantId 的请求
     * @return Result 包装的 secret、qrCodeUrl、manualEntryKey
     */
    @OperationLog(
            module = "MFA管理",
            type = OperationType.INSERT,
            description = "启用TOTP多因素认证",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "enableTotp", summary = "初始化TOTP", description = "生成TOTP密钥和二维码，添加到认证器后需验证激活")
    @ApiResponse(
            responseCode = "200",
            description = "初始化信息",
            content =
                    @Content(mediaType = "application/json", schema = @Schema(implementation = TotpSetupResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> enableTotp(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String tenantId = tenantId(request);
        return mfaService
                .enableTotp(userId, tenantId)
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(this::handleError);
    }

    /**
     * 校验当前用户的 TOTP 并激活配置。
     * @param request 包含 code 查询参数和可信用户、租户属性的请求
     * @return Result 包装的激活结论及备用码；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "MFA管理",
            type = OperationType.UPDATE,
            description = "验证并激活TOTP",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "activateTotp", summary = "验证并激活TOTP", description = "校验认证器验证码并返回激活结果及备用码")
    @Parameter(
            name = "code",
            in = ParameterIn.QUERY,
            description = "认证器验证码",
            required = true,
            schema = @Schema(implementation = String.class))
    @ApiResponse(
            responseCode = "200",
            description = "激活结果",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TotpActivationResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> activateTotp(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String code = request.queryParam("code").orElseThrow(() -> new IllegalArgumentException("code is required"));
        String tenantId = tenantId(request);
        return mfaService
                .verifyAndActivateTotp(userId, code, tenantId)
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(this::handleError);
    }

    /**
     * 绑定手机号并调用服务直接启用短信 MFA。
     * @param request 含 phoneNumber 查询参数的请求
     * @return Result 包装的 MFA 配置；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "MFA管理",
            type = OperationType.INSERT,
            description = "启用短信MFA",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "enableSms", summary = "启用短信MFA", description = "为当前可信用户绑定手机号并直接启用短信MFA")
    @Parameter(
            name = "phoneNumber",
            in = ParameterIn.QUERY,
            description = "手机号码",
            required = true,
            schema = @Schema(implementation = String.class))
    @ApiResponse(
            responseCode = "200",
            description = "MFA配置",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ManagedMfaConfigResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> enableSms(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String tenantId = tenantId(request);
        String phoneNumber = request.queryParam("phoneNumber")
                .orElseThrow(() -> new IllegalArgumentException("phoneNumber is required"));
        return mfaService
                .enableSms(userId, tenantId, phoneNumber)
                .flatMap(config -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(config)))
                .onErrorResume(this::handleError);
    }

    /**
     * 绑定邮箱并调用服务直接启用邮箱 MFA。
     * @param request 含 email 查询参数的请求
     * @return Result 包装的 MFA 配置；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "MFA管理",
            type = OperationType.INSERT,
            description = "启用邮箱MFA",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "enableEmail", summary = "启用邮箱MFA", description = "为当前可信用户绑定邮箱并直接启用邮箱MFA")
    @Parameter(
            name = "email",
            in = ParameterIn.QUERY,
            description = "邮箱地址",
            required = true,
            schema = @Schema(implementation = String.class))
    @ApiResponse(
            responseCode = "200",
            description = "MFA配置",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ManagedMfaConfigResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> enableEmail(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String tenantId = tenantId(request);
        String email = request.queryParam("email").orElseThrow(() -> new IllegalArgumentException("email is required"));
        return mfaService
                .enableEmail(userId, tenantId, email)
                .flatMap(config -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(config)))
                .onErrorResume(this::handleError);
    }

    /**
     * 禁用当前用户指定类型的 MFA。
     * @param request 含 type 路径参数的请求
     * @return data 为空的 Result；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "MFA管理",
            type = OperationType.DELETE,
            description = "禁用MFA",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "disableManagedMfa", summary = "禁用MFA", description = "禁用当前可信用户指定类型的MFA")
    @Parameter(
            name = "type",
            in = ParameterIn.PATH,
            description = "MFA类型",
            required = true,
            schema =
                    @Schema(
                            implementation = String.class,
                            allowableValues = {"TOTP", "SMS", "EMAIL"}))
    @ApiResponse(
            responseCode = "200",
            description = "禁用成功",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthCenterApiResponses.EmptyResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> disableMfa(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String typeStr = request.pathVariable("type");
        MfaConfig.MfaType type = MfaConfig.MfaType.valueOf(typeStr);
        String tenantId = tenantId(request);
        return mfaService
                .disableMfa(userId, type, tenantId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 校验当前用户指定类型的验证码。
     * @param request 含 code、type 查询参数的请求
     * @return Result 包装的布尔值；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "MFA管理",
            type = OperationType.SELECT,
            description = "验证MFA代码",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "verifyManagedMfaCode", summary = "验证MFA代码", description = "返回验证码是否通过校验的布尔值")
    @Parameter(
            name = "code",
            in = ParameterIn.QUERY,
            description = "验证码",
            required = true,
            schema = @Schema(implementation = String.class))
    @Parameter(
            name = "type",
            in = ParameterIn.QUERY,
            description = "MFA类型",
            required = true,
            schema =
                    @Schema(
                            implementation = String.class,
                            allowableValues = {"TOTP", "SMS", "EMAIL"}))
    @ApiResponse(
            responseCode = "200",
            description = "验证结论",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ManagedMfaBooleanResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> verifyMfaCode(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String code = request.queryParam("code").orElseThrow(() -> new IllegalArgumentException("code is required"));
        String typeStr = request.queryParam("type").orElseThrow(() -> new IllegalArgumentException("type is required"));
        MfaConfig.MfaType type = MfaConfig.MfaType.valueOf(typeStr);
        String tenantId = tenantId(request);
        return mfaService
                .verifyMfaCode(userId, code, type, tenantId)
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(this::handleError);
    }

    /**
     * 向当前用户绑定的手机号或邮箱发送验证码。
     * @param request 含 type 查询参数的请求
     * @return data 为空的 Result；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "MFA管理",
            type = OperationType.INSERT,
            description = "发送MFA验证码",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "sendMfaCode", summary = "发送MFA验证码", description = "调用服务向绑定的手机号或邮箱发送验证码")
    @Parameter(
            name = "type",
            in = ParameterIn.QUERY,
            description = "短信或邮箱类型",
            required = true,
            schema =
                    @Schema(
                            implementation = String.class,
                            allowableValues = {"SMS", "EMAIL"}))
    @ApiResponse(
            responseCode = "200",
            description = "发送成功",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthCenterApiResponses.EmptyResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> sendMfaCode(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String typeStr = request.queryParam("type").orElseThrow(() -> new IllegalArgumentException("type is required"));
        MfaConfig.MfaType type = MfaConfig.MfaType.valueOf(typeStr);
        String tenantId = tenantId(request);
        return mfaService
                .sendMfaCode(userId, type, tenantId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 查询当前用户的全部 MFA 配置，不在处理器中过滤启用状态。
     * @param request 提供可信用户和租户上下文的请求
     * @return 裸配置数组；服务异步失败返回 HTTP 500 Result
     */
    @OperationLog(
            module = "MFA管理",
            type = OperationType.SELECT,
            description = "查询用户MFA配置",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "getUserMfaConfigs", summary = "查询用户的MFA配置", description = "返回当前可信用户的全部配置，成功响应为裸 JSON 数组")
    @ApiResponse(
            responseCode = "200",
            description = "配置数组",
            content =
                    @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = MfaConfig.class))))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> getUserMfaConfigs(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        return mfaService
                .getUserMfaConfigs(userId, tenantId(request))
                .collectList()
                .flatMap(configs -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(configs))
                .onErrorResume(this::handleError);
    }

    /**
     * 检查当前用户是否启用任意 MFA。
     * @param request 提供可信用户和租户上下文的请求
     * @return Result 包装的布尔值；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "MFA管理",
            type = OperationType.SELECT,
            description = "检查MFA状态",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "checkMfaStatus", summary = "检查MFA状态", description = "检查当前可信用户是否启用至少一种MFA")
    @ApiResponse(
            responseCode = "200",
            description = "启用状态",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ManagedMfaBooleanResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> checkMfaStatus(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        return mfaService
                .isMfaEnabled(userId, tenantId(request))
                .flatMap(enabled -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(enabled)))
                .onErrorResume(this::handleError);
    }

    /**
     * 为当前用户重新生成备用验证码。
     * @param request 提供可信用户和租户上下文的请求
     * @return Result 包装的 codes 数组；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "MFA管理",
            type = OperationType.UPDATE,
            description = "重新生成备用验证码",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "regenerateBackupCodes", summary = "重新生成备用验证码", description = "生成新的备用码并使旧码失效，请保存本次返回的明文码")
    @ApiResponse(
            responseCode = "200",
            description = "备用码",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ManagedBackupCodesResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> regenerateBackupCodes(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        return mfaService
                .regenerateBackupCodes(userId, tenantId(request))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(this::handleError);
    }

    /**
     * 校验当前用户的备用验证码。
     * @param request 含 code 查询参数的请求
     * @return Result 包装的布尔值，不返回剩余码数量；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "MFA管理",
            type = OperationType.SELECT,
            description = "验证备用验证码",
            saveRequest = false,
            saveResponse = false)
    @Operation(operationId = "verifyManagedBackupCode", summary = "验证备用验证码", description = "校验一次性备用码，返回布尔结论")
    @Parameter(
            name = "code",
            in = ParameterIn.QUERY,
            description = "备用验证码",
            required = true,
            schema = @Schema(implementation = String.class))
    @ApiResponse(
            responseCode = "200",
            description = "验证结论",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ManagedMfaBooleanResult.class)))
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> verifyBackupCode(ServerRequest request) {
        String userId = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String code = request.queryParam("code").orElseThrow(() -> new IllegalArgumentException("code is required"));
        return mfaService
                .verifyBackupCode(userId, code, tenantId(request))
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(this::handleError);
    }

    /**
     * 管理员解锁当前可信租户内指定用户的 MFA。
     * @param request 含 userId、type 查询参数及可信操作人属性的请求
     * @return data 为空的 Result；服务异步失败返回 HTTP 500
     */
    @OperationLog(
            module = "MFA管理",
            type = OperationType.UPDATE,
            description = "解锁MFA",
            saveRequest = false,
            saveResponse = false)
    @RequireAdminRole
    @RequireResourcePermission(
            resourceType = "mfa",
            resourceIdExpression = "#request.queryParam('userId').orElse('')",
            permission = "MANAGE",
            errorMessage = "无权解锁该用户MFA")
    @Operation(operationId = "unlockMfa", summary = "解锁MFA（管理员）", description = "在现有管理员角色和mfa资源MANAGE权限检查下解锁指定用户")
    @Parameter(
            name = "userId",
            in = ParameterIn.QUERY,
            description = "目标用户标识",
            required = true,
            schema = @Schema(implementation = String.class))
    @Parameter(
            name = "type",
            in = ParameterIn.QUERY,
            description = "MFA类型",
            required = true,
            schema =
                    @Schema(
                            implementation = String.class,
                            allowableValues = {"TOTP", "SMS", "EMAIL"}))
    @ApiResponse(
            responseCode = "200",
            description = "解锁成功",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthCenterApiResponses.EmptyResult.class)))
    @ApiResponse(responseCode = "403", description = "管理员或资源权限检查失败")
    @ApiResponse(
            responseCode = "500",
            description = "服务处理失败",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    public Mono<ServerResponse> unlockMfa(ServerRequest request) {
        String userId =
                request.queryParam("userId").orElseThrow(() -> new IllegalArgumentException("userId is required"));
        String typeStr = request.queryParam("type").orElseThrow(() -> new IllegalArgumentException("type is required"));
        MfaConfig.MfaType type = MfaConfig.MfaType.valueOf(typeStr);
        String operator = request.attribute("userId")
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("operator is required"));
        String tenantId = tenantId(request);
        return mfaService
                .unlockMfa(userId, type, operator, tenantId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(null)))
                .onErrorResume(this::handleError);
    }

    /**
     * 从请求属性或租户上下文解析可信租户，不接受查询参数覆盖。
     * @param request 当前请求
     * @return 非空白租户标识
     */
    private String tenantId(ServerRequest request) {
        String tenantId = request.attribute("tenantId")
                .map(Object::toString)
                .orElseGet(() -> TenantContext.currentTenantId()
                        .orElseThrow(() -> new IllegalArgumentException("tenantId is required")));
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return tenantId;
    }

    /**
     * 将服务异步失败转换为现有 HTTP 500 错误响应。
     * @param error 服务异常
     * @return Result 错误响应
     */
    private Mono<ServerResponse> handleError(Throwable error) {
        log.error("MFA操作失败", error);
        return ServerResponse.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.error(error.getMessage()));
    }

    /** OpenAPI 专用 TOTP 初始化包装。 */
    public static class TotpSetupResult extends Result<MfaService.TotpSetupInfo> {}

    /** OpenAPI 专用 TOTP 激活包装。 */
    public static class TotpActivationResult extends Result<MfaService.MfaActivationResult> {}

    /** OpenAPI 专用 MFA 配置包装。 */
    public static class ManagedMfaConfigResult extends Result<MfaConfig> {}

    /** OpenAPI 专用布尔结论包装。 */
    public static class ManagedMfaBooleanResult extends Result<Boolean> {}

    /** OpenAPI 专用备用码包装。 */
    public static class ManagedBackupCodesResult extends Result<MfaService.BackupCodesResult> {}
}
