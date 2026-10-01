package com.apex.scaffold.authcenter.interfaces.handler;

import com.apex.scaffold.authcenter.application.dto.MfaSetupRequest;
import com.apex.scaffold.authcenter.application.dto.MfaSetupResponse;
import com.apex.scaffold.authcenter.application.dto.MfaVerifyRequest;
import com.apex.scaffold.authcenter.application.service.TotpService;
import com.apex.scaffold.authcenter.interfaces.util.RequestHandlerUtil;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.OperationLog.OperationType;
import com.apex.scaffold.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * MFA（多因素认证）Handler
 *
 * <p>提供基于TOTP（Time-based One-Time Password）的多因素认证功能，用于增强账号安全性。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li><b>TOTP设置</b>：生成密钥和二维码，用户扫码绑定认证器应用</li>
 *   <li><b>验证激活</b>：首次设置时验证TOTP验证码以激活MFA</li>
 *   <li><b>登录验证</b>：用户登录时进行二次验证</li>
 *   <li><b>备份码验证</b>：设备丢失时使用备份码应急登录</li>
 *   <li><b>禁用MFA</b>：关闭多因素认证功能</li>
 *   <li><b>状态查询</b>：查询用户是否已启用MFA</li>
 * </ul>
 *
 * <p><b>TOTP工作原理：</b>
 * <ol>
 *   <li>系统为用户生成唯一的密钥（Base32编码）</li>
 *   <li>密钥与当前时间（30秒为一个时间窗口）结合，使用HMAC-SHA1算法生成6位数字验证码</li>
 *   <li>认证器应用（如Google Authenticator）使用相同密钥和算法生成相同验证码</li>
 *   <li>验证时比对用户输入的验证码与系统计算的验证码是否一致</li>
 * </ol>
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li><b>时间同步</b>：验证码每30秒更新一次，防止重放攻击</li>
 *   <li><b>时间容差</b>：允许±1个时间窗口的偏移，应对时钟偏差</li>
 *   <li><b>备份码加密</b>：备份码使用BCrypt加密存储</li>
 *   <li><b>一次性备份码</b>：每个备份码只能使用一次</li>
 *   <li><b>失败锁定</b>：连续验证失败会触发账号临时锁定</li>
 * </ul>
 *
 * <p><b>支持的认证器应用：</b>
 * <ul>
 *   <li>Google Authenticator（推荐）</li>
 *   <li>Microsoft Authenticator</li>
 *   <li>Authy</li>
 *   <li>1Password</li>
 *   <li>其他支持TOTP协议的认证器</li>
 * </ul>
 *
 * <p><b>使用流程：</b>
 * <ol>
 *   <li><b>设置</b>：调用 setupMfa 生成密钥和二维码</li>
 *   <li><b>扫码</b>：用户使用认证器应用扫描二维码</li>
 *   <li><b>激活</b>：调用 verifyAndEnableMfa 验证验证码并激活</li>
 *   <li><b>登录</b>：正常登录后调用 verifyMfaCode 进行二次验证</li>
 * </ol>
 *
 * <p><b>多租户支持：</b>
 * <p>MFA配置按租户隔离，不同租户的用户使用独立的密钥和配置。
 *
 * @author Apex Scaffold Team
 * @version 1.0.0
 * @since 2026-09-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "MFA 多因素认证", description = "TOTP、备份码等 MFA 管理接口")
public class MfaHandler {

    private final TotpService totpService;
    private final RequestHandlerUtil requestHandlerUtil;

    /**
     * 设置 TOTP MFA
     *
     * <p>为用户生成新的 TOTP 密钥和二维码，返回密钥、OTP Auth URL 和备份码。
     * 用户需要使用认证器应用（如 Google Authenticator）扫描二维码，
     * 然后调用验证接口完成设置。
     *
     * <p><b>请求参数：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID（必填）</li>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     *   <li><b>issuer</b>：颁发者名称（可选，默认为"Apex Scaffold"）</li>
     *   <li><b>accountName</b>：账号名称（可选，通常为用户邮箱或用户名）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <ul>
     *   <li><b>secret</b>：Base32编码的密钥（32字符）</li>
     *   <li><b>otpAuthUrl</b>：OTP Auth URL，用于生成二维码（格式：otpauth://totp/...）</li>
     *   <li><b>backupCodes</b>：10个备份验证码（8位字母数字组合）</li>
     * </ul>
     *
     * <p><b>使用流程：</b>
     * <ol>
     *   <li>调用此接口获取密钥和二维码URL</li>
     *   <li>前端使用otpAuthUrl生成二维码图片</li>
     *   <li>用户使用认证器应用扫描二维码</li>
     *   <li>保存返回的备份验证码（只显示一次）</li>
     *   <li>调用 verifyAndEnableMfa 接口验证并激活</li>
     * </ol>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>调用此接口不会立即启用MFA，需要验证后才会激活</li>
     *   <li>如果用户已有未激活的配置，会被新配置覆盖</li>
     *   <li>备份码只在此接口返回一次，请提醒用户妥善保存</li>
     *   <li>密钥和备份码都会加密存储在数据库中</li>
     * </ul>
     *
     * <p><b>示例请求：</b>
     * <pre>
     * {
     *   "userId": 1001,
     *   "tenantId": "default",
     *   "issuer": "Apex Scaffold",
     *   "accountName": "user@example.com"
     * }
     * </pre>
     *
     * @param request HTTP 请求
     * @return MFA 设置响应
     */
    @OperationLog(module = "多因素认证", type = OperationType.INSERT, description = "设置 TOTP MFA")
    @Operation(operationId = "setupMfa", summary = "设置 TOTP MFA", description = "为用户生成 TOTP 密钥和备份码，返回二维码 URL")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "MFA设置请求",
            required = true,
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema =
                                    @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = MfaSetupRequest.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "设置成功，返回密钥、二维码URL和备份码",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class)))
    public Mono<ServerResponse> setupMfa(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, MfaSetupRequest.class)
                .flatMap(req -> totpService.generateSecret(
                        req.getUserId(), req.getTenantId(), req.getIssuer(), req.getAccountName()))
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
     * 验证 TOTP 验证码并启用 MFA
     *
     * <p>用户首次设置 TOTP 后，需要输入认证器应用中显示的 6 位验证码进行验证。
     * 验证通过后，MFA 将被正式启用。
     *
     * <p><b>请求参数：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID（必填）</li>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     *   <li><b>code</b>：认证器应用显示的6位数字验证码（必填）</li>
     * </ul>
     *
     * <p><b>验证流程：</b>
     * <ol>
     *   <li>从数据库获取用户的临时TOTP密钥（setupMfa时生成）</li>
     *   <li>使用密钥和当前时间计算期望的验证码</li>
     *   <li>比对用户输入的验证码与期望值（允许±1个时间窗口偏移）</li>
     *   <li>验证通过则将MFA状态设置为已启用</li>
     *   <li>将临时密钥转为正式密钥</li>
     * </ol>
     *
     * <p><b>返回数据：</b>
     * <ul>
     *   <li><b>enabled</b>：MFA是否已启用（true/false）</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户首次配置MFA后的激活验证</li>
     *   <li>确认用户已正确扫码并能生成验证码</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>必须先调用 setupMfa 生成密钥，才能调用此接口</li>
     *   <li>验证码有效期为30秒，输入时需注意时效性</li>
     *   <li>验证失败会计入失败次数，多次失败会触发锁定</li>
     *   <li>激活后，用户下次登录时将需要进行MFA验证</li>
     * </ul>
     *
     * <p><b>示例请求：</b>
     * <pre>
     * {
     *   "userId": 1001,
     *   "tenantId": "default",
     *   "code": "123456"
     * }
     * </pre>
     *
     * @param request HTTP 请求
     * @return 验证结果
     */
    @OperationLog(module = "多因素认证", type = OperationType.UPDATE, description = "验证并启用 MFA")
    @Operation(
            operationId = "verifyAndEnableMfa",
            summary = "验证 TOTP 并启用 MFA",
            description = "首次设置时验证 TOTP 验证码，验证通过后启用 MFA")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "MFA验证请求",
            required = true,
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema =
                                    @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = MfaVerifyRequest.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "验证完成，返回是否启用成功",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "验证码错误或参数错误",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class)))
    public Mono<ServerResponse> verifyAndEnableMfa(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, MfaVerifyRequest.class)
                .flatMap(req -> totpService.verifyAndEnable(req.getUserId(), req.getCode(), req.getTenantId()))
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
     * 验证 TOTP 验证码（登录时使用）
     *
     * <p>用户登录时，如果已启用 MFA，需要输入 TOTP 验证码进行二次验证。
     *
     * <p><b>请求参数：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID（必填）</li>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     *   <li><b>code</b>：认证器应用显示的6位数字验证码（必填）</li>
     * </ul>
     *
     * <p><b>验证流程：</b>
     * <ol>
     *   <li>检查用户是否已启用MFA</li>
     *   <li>从数据库获取用户的TOTP密钥</li>
     *   <li>使用密钥和当前时间计算期望的验证码</li>
     *   <li>比对用户输入的验证码与期望值（允许±1个时间窗口偏移）</li>
     *   <li>验证通过则允许登录，失败则拒绝并记录</li>
     * </ol>
     *
     * <p><b>返回数据：</b>
     * <ul>
     *   <li><b>verified</b>：验证是否通过（true/false）</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户名密码验证通过后的二次验证</li>
     *   <li>敏感操作（如修改密码、绑定邮箱）的二次确认</li>
     *   <li>高风险操作的额外安全保护</li>
     * </ul>
     *
     * <p><b>安全特性：</b>
     * <ul>
     *   <li><b>失败限制</b>：连续5次验证失败，账号临时锁定30分钟</li>
     *   <li><b>防重放</b>：同一验证码在时间窗口内不可重复使用</li>
     *   <li><b>时间容差</b>：允许±30秒的时钟偏差</li>
     *   <li><b>审计日志</b>：记录所有验证尝试（成功和失败）</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>用户必须已启用MFA，否则返回错误</li>
     *   <li>验证码有30秒有效期，过期需等待新验证码</li>
     *   <li>多次验证失败会触发账号锁定，需管理员解锁或等待锁定时间到期</li>
     * </ul>
     *
     * <p><b>示例请求：</b>
     * <pre>
     * {
     *   "userId": 1001,
     *   "tenantId": "default",
     *   "code": "654321"
     * }
     * </pre>
     *
     * @param request HTTP 请求
     * @return 验证结果
     */
    @OperationLog(module = "多因素认证", type = OperationType.SELECT, description = "验证 TOTP 验证码")
    @Operation(operationId = "verifyMfaCode", summary = "验证 TOTP 验证码", description = "登录时验证 TOTP 验证码")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "MFA验证请求",
            required = true,
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema =
                                    @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = MfaVerifyRequest.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "验证完成，返回是否验证通过",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "验证码错误或MFA未启用",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "423",
            description = "账号已锁定 - 验证失败次数过多",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class)))
    public Mono<ServerResponse> verifyMfaCode(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, MfaVerifyRequest.class)
                .flatMap(req -> totpService.verifyCode(req.getUserId(), req.getCode(), req.getTenantId()))
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
     * 验证备份码
     *
     * <p>当用户无法访问认证器应用时，可以使用备份码进行验证。
     * 每个备份码只能使用一次。
     *
     * <p><b>请求参数：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID（必填）</li>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     *   <li><b>backupCode</b>：8位备份验证码（必填）</li>
     * </ul>
     *
     * <p><b>验证流程：</b>
     * <ol>
     *   <li>检查用户是否已启用MFA</li>
     *   <li>从数据库获取用户的所有备份码（BCrypt加密存储）</li>
     *   <li>逐个使用BCrypt验证输入的备份码</li>
     *   <li>如果匹配成功，标记该备份码为已使用（不可再次使用）</li>
     *   <li>返回验证结果</li>
     * </ol>
     *
     * <p><b>返回数据：</b>
     * <ul>
     *   <li><b>verified</b>：验证是否通过（true/false）</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户的认证器设备丢失或损坏</li>
     *   <li>无法访问认证器应用（如手机没电、遗失等）</li>
     *   <li>更换手机尚未迁移认证器数据</li>
     *   <li>应急情况下需要登录账号</li>
     * </ul>
     *
     * <p><b>安全特性：</b>
     * <ul>
     *   <li><b>一次性使用</b>：每个备份码使用后立即失效</li>
     *   <li><b>BCrypt加密</b>：备份码以BCrypt方式加密存储</li>
     *   <li><b>失败限制</b>：连续验证失败会触发锁定</li>
     *   <li><b>使用提醒</b>：当剩余备份码少于3个时，提醒用户重新生成</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>备份码是首次设置MFA时生成的（setupMfa返回）</li>
     *   <li>每个备份码只能使用一次，使用后立即失效</li>
     *   <li>建议使用备份码登录后，立即检查认证器配置或重新生成备份码</li>
     *   <li>备份码用完后，如无法访问认证器，需联系管理员解锁</li>
     * </ul>
     *
     * <p><b>示例请求：</b>
     * <pre>
     * {
     *   "userId": 1001,
     *   "tenantId": "default",
     *   "backupCode": "ABCD1234"
     * }
     * </pre>
     *
     * @param request HTTP 请求
     * @return 验证结果
     */
    @OperationLog(module = "多因素认证", type = OperationType.SELECT, description = "验证备份码")
    @Operation(operationId = "verifyBackupCode", summary = "验证备份码", description = "使用备份码进行 MFA 验证")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "MFA验证请求（包含备份码）",
            required = true,
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema =
                                    @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = MfaVerifyRequest.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "验证完成，返回是否验证通过",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "备份码无效或已使用",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "423",
            description = "账号已锁定 - 验证失败次数过多",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class)))
    public Mono<ServerResponse> verifyBackupCode(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, MfaVerifyRequest.class)
                .flatMap(req -> totpService.verifyBackupCode(req.getUserId(), req.getBackupCode(), req.getTenantId()))
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
     * 禁用 MFA
     *
     * <p>禁用用户的 TOTP MFA，清除密钥和配置。
     *
     * <p><b>请求参数：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID（必填）</li>
     *   <li><b>tenantId</b>：租户ID（必填）</li>
     * </ul>
     *
     * <p><b>操作流程：</b>
     * <ol>
     *   <li>验证用户身份和权限</li>
     *   <li>检查用户是否已启用MFA</li>
     *   <li>删除用户的TOTP密钥</li>
     *   <li>删除所有备份验证码</li>
     *   <li>清除MFA相关配置和状态</li>
     *   <li>记录禁用操作的审计日志</li>
     * </ol>
     *
     * <p><b>返回数据：</b>
     * <ul>
     *   <li><b>disabled</b>：MFA是否已禁用（true/false）</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>用户主动关闭MFA功能</li>
     *   <li>用户更换认证器设备前临时禁用</li>
     *   <li>管理员强制禁用用户的MFA</li>
     * </ul>
     *
     * <p><b>安全考虑：</b>
     * <ul>
     *   <li><b>身份验证</b>：建议要求用户输入当前密码或MFA验证码确认</li>
     *   <li><b>审计日志</b>：记录禁用操作的时间、操作人、原因等信息</li>
     *   <li><b>通知机制</b>：禁用后发送邮件或短信通知用户</li>
     *   <li><b>冷却期</b>：可设置禁用后24小时内无法重新启用，防止攻击者频繁切换</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>禁用MFA会降低账号安全性，建议谨慎操作</li>
     *   <li>禁用后，用户下次登录将不再需要MFA验证</li>
     *   <li>所有密钥和备份码会被永久删除，无法恢复</li>
     *   <li>重新启用需要重新配置（生成新密钥、扫码等）</li>
     * </ul>
     *
     * <p><b>示例请求：</b>
     * <pre>
     * {
     *   "userId": 1001,
     *   "tenantId": "default"
     * }
     * </pre>
     *
     * @param request HTTP 请求
     * @return 操作结果
     */
    @OperationLog(module = "多因素认证", type = OperationType.DELETE, description = "禁用 MFA")
    @Operation(operationId = "disableMfa", summary = "禁用 MFA", description = "禁用用户的 TOTP 多因素认证")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "MFA禁用请求",
            required = true,
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema =
                                    @io.swagger.v3.oas.annotations.media.Schema(
                                            implementation = MfaVerifyRequest.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "禁用成功",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "禁用失败，MFA 未配置",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class)))
    public Mono<ServerResponse> disableMfa(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, MfaVerifyRequest.class)
                .flatMap(req -> totpService.disable(req.getUserId(), req.getTenantId()))
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
     * 查询 MFA 状态
     *
     * <p>查询用户是否已启用 TOTP MFA。
     *
     * <p><b>查询参数：</b>
     * <ul>
     *   <li><b>userId</b>：用户ID（必填，查询参数）</li>
     *   <li><b>tenantId</b>：租户ID（必填，查询参数）</li>
     * </ul>
     *
     * <p><b>返回数据：</b>
     * <ul>
     *   <li><b>enabled</b>：MFA是否已启用（true/false）</li>
     *   <li><b>mfaType</b>：MFA类型（"TOTP"或"NONE"）</li>
     * </ul>
     *
     * <p><b>使用场景：</b>
     * <ul>
     *   <li>登录前检查用户是否需要MFA验证</li>
     *   <li>前端页面判断是否显示MFA相关UI</li>
     *   <li>安全设置页面显示MFA当前状态</li>
     *   <li>接口网关决策是否需要MFA拦截</li>
     * </ul>
     *
     * <p><b>性能优化：</b>
     * <ul>
     *   <li>查询结果可缓存在Redis中，有效期5分钟</li>
     *   <li>适合高频调用，响应速度极快</li>
     * </ul>
     *
     * <p><b>注意事项：</b>
     * <ul>
     *   <li>只返回启用状态，不返回密钥等敏感信息</li>
     *   <li>未启用时返回 enabled=false, mfaType="NONE"</li>
     *   <li>已启用时返回 enabled=true, mfaType="TOTP"</li>
     * </ul>
     *
     * <p><b>示例URL：</b>
     * <pre>
     * GET /api/mfa/status?userId=1001&tenantId=default
     * </pre>
     *
     * <p><b>示例响应：</b>
     * <pre>
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "enabled": true,
     *     "mfaType": "TOTP"
     *   }
     * }
     * </pre>
     *
     * @param request HTTP 请求
     * @return MFA 状态
     */
    @OperationLog(module = "多因素认证", type = OperationType.SELECT, description = "查询 MFA 状态")
    @Operation(operationId = "getMfaStatus", summary = "查询 MFA 状态", description = "查询用户是否已启用 TOTP MFA")
    @Parameter(name = "userId", description = "用户 ID", required = true, example = "1001")
    @Parameter(name = "tenantId", description = "租户 ID", required = true, example = "default")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "查询成功，返回MFA状态",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误",
            content =
                    @io.swagger.v3.oas.annotations.media.Content(
                            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class)))
    public Mono<ServerResponse> getMfaStatus(@Parameter(description = "用户 ID") ServerRequest request) {
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

        return totpService
                .isEnabled(userId, tenantId)
                .flatMap(enabled -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(Map.of("enabled", enabled, "mfaType", enabled ? "TOTP" : "NONE"))))
                .onErrorResume(e -> {
                    log.error("查询 MFA 状态失败", e);
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.error("查询失败: " + e.getMessage()));
                });
    }
}
