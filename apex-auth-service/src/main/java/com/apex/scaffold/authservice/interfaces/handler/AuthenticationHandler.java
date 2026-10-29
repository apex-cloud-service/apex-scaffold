package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.dto.LoginRequest;
import com.apex.scaffold.auth.application.dto.LoginResponse;
import com.apex.scaffold.auth.application.dto.RefreshTokenRequest;
import com.apex.scaffold.auth.application.service.AuthenticationApplicationService;
import com.apex.scaffold.common.annotation.OperationLog;
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
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 认证处理器（函数式端点）
 *
 * <p>处理用户认证相关的HTTP请求，提供完整的身份认证流程管理，包括用户登录验证、
 * 会话管理、令牌刷新和安全登出功能。支持多租户隔离和设备追踪。</p>
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>用户名密码登录 - 验证用户凭证并颁发JWT访问令牌和刷新令牌</li>
 *   <li>安全登出 - 撤销用户在当前租户下的所有有效令牌，防止令牌重放攻击</li>
 *   <li>令牌刷新 - 使用刷新令牌换取新的访问令牌，延长会话有效期</li>
 *   <li>多租户支持 - 根据租户ID隔离用户认证和令牌管理</li>
 *   <li>设备追踪 - 记录登录设备信息和客户端IP地址</li>
 * </ul>
 *
 * <h2>安全特性</h2>
 * <ul>
 *   <li>密码哈希验证 - 使用BCrypt算法验证密码</li>
 *   <li>令牌加密存储 - 数据库中的令牌使用AES-256-GCM加密</li>
 *   <li>刷新令牌轮换 - 每次刷新后自动撤销旧的刷新令牌</li>
 *   <li>IP地址绑定 - 记录并验证令牌颁发时的客户端IP</li>
 *   <li>防暴力破解 - 失败登录次数限制和账户锁定机制</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 * @see AuthenticationApplicationService
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "用户认证", description = "用户身份认证相关接口,包括登录、登出和令牌刷新")
public class AuthenticationHandler {
    private static final String USER_ID_PATH_VARIABLE = "userId";

    private final AuthenticationApplicationService authenticationService;

    /**
     * 用户登录
     *
     * <p>验证用户名和密码，登录成功后返回JWT访问令牌和刷新令牌。
     * 支持多租户隔离，登录时需要指定租户ID或使用默认租户。
     * 系统会记录客户端IP地址和设备ID用于安全审计。</p>
     *
     * <h3>登录流程</h3>
     * <ol>
     *   <li>提取客户端IP地址</li>
     *   <li>验证用户名和密码（BCrypt哈希比对）</li>
     *   <li>检查账户状态（是否锁定、禁用）</li>
     *   <li>验证租户权限</li>
     *   <li>颁发访问令牌（有效期2小时）和刷新令牌（有效期7天）</li>
     *   <li>记录登录日志</li>
     * </ol>
     *
     * <h3>安全说明</h3>
     * <ul>
     *   <li>密码错误超过5次将锁定账户15分钟</li>
     *   <li>令牌使用JWT格式，包含用户ID、租户ID、权限等信息</li>
     *   <li>刷新令牌在数据库中加密存储</li>
     *   <li>登录成功后重置失败计数器</li>
     * </ul>
     *
     * @param request HTTP请求，包含LoginRequest请求体
     * @return 登录响应（包含用户信息、访问令牌和刷新令牌）
     */
    @Operation(
            operationId = "userLogin",
            summary = "用户登录",
            description = "使用用户名和密码进行身份认证,登录成功后返回JWT访问令牌和刷新令牌。支持多租户隔离和设备追踪。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "登录成功,返回用户信息和令牌对",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "认证失败 - 用户名或密码错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "403",
                description = "账户被锁定或禁用",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "系统内部错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    })
    @RequestBody(
            description = "登录请求参数,包含用户名、密码、租户ID（可选）和设备ID（可选）",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginRequest.class)))
    @OperationLog(
            module = "Authentication",
            type = OperationLog.OperationType.LOGIN,
            description = "login",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> login(ServerRequest request) {
        String clientIp = request.remoteAddress()
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");

        return request.bodyToMono(LoginRequest.class)
                .flatMap(loginRequest -> authenticationService.login(loginRequest, clientIp))
                .flatMap(loginResponse -> ServerResponse.ok().bodyValue(loginResponse))
                .onErrorResume(error -> {
                    if (error instanceof BusinessException) {
                        BusinessException be = (BusinessException) error;
                        return ServerResponse.status(be.getCode() == ResultCode.UNAUTHORIZED.getCode() ? 401 : 403)
                                .bodyValue(be.getMessage());
                    }
                    return ServerResponse.status(500).bodyValue("系统错误");
                });
    }

    /**
     * 用户登出
     *
     * <p>撤销用户在当前租户下的所有有效令牌，实现安全登出。
     * 登出后所有访问令牌和刷新令牌立即失效，防止令牌重放攻击。
     * 需要用户已认证并且只能登出自己的账户。</p>
     *
     * <h3>登出流程</h3>
     * <ol>
     *   <li>验证用户身份（从JWT中提取用户信息）</li>
     *   <li>校验路径参数中的userId与当前登录用户是否一致</li>
     *   <li>撤销该用户在指定租户下的所有令牌</li>
     *   <li>将令牌加入黑名单（Redis缓存）</li>
     *   <li>记录登出日志</li>
     * </ol>
     *
     * <h3>安全说明</h3>
     * <ul>
     *   <li>必须携带有效的访问令牌（Bearer Token）</li>
     *   <li>只能登出自己的账户，不能登出其他用户</li>
     *   <li>登出后该用户所有设备上的会话都将失效</li>
     *   <li>令牌黑名单会保留到原令牌过期时间</li>
     * </ul>
     *
     * @param request HTTP请求，路径参数中包含userId
     * @return 成功响应（204 No Content）或错误响应
     */
    @Operation(
            operationId = "userLogout",
            summary = "用户登出",
            description = "撤销用户在当前租户下的所有有效令牌,实现安全登出。登出后所有访问令牌和刷新令牌立即失效。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "登出成功,所有令牌已撤销"),
        @ApiResponse(
                responseCode = "401",
                description = "未认证 - 访问令牌无效或已过期",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "403",
                description = "权限不足 - 只能登出自己的账户",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    })
    @Parameter(
            name = "userId",
            description = "用户ID,必须与当前登录用户的ID一致",
            required = true,
            in = ParameterIn.PATH,
            example = "1001")
    @SecurityRequirement(name = "Bearer Authentication")
    @OperationLog(
            module = "Authentication",
            type = OperationLog.OperationType.LOGOUT,
            description = "logout",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> logout(ServerRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(authentication ->
                        authentication.isAuthenticated() && authentication.getDetails() instanceof String)
                .flatMap(authentication -> {
                    if (!authentication.getName().equals(request.pathVariable(USER_ID_PATH_VARIABLE))) {
                        return ServerResponse.status(403).build();
                    }
                    return authenticationService
                            .logout(Long.valueOf(authentication.getName()), (String) authentication.getDetails())
                            .then(ServerResponse.ok().build());
                })
                .switchIfEmpty(ServerResponse.status(401).build());
    }

    /**
     * 刷新访问令牌
     *
     * <p>使用刷新令牌换取新的访问令牌，延长会话有效期。
     * 支持刷新令牌轮换机制，每次刷新后旧的刷新令牌自动失效，
     * 新的访问令牌和刷新令牌会一起返回。</p>
     *
     * <h3>刷新流程</h3>
     * <ol>
     *   <li>验证刷新令牌的有效性（签名、过期时间）</li>
     *   <li>检查刷新令牌是否已被撤销（黑名单检查）</li>
     *   <li>验证令牌绑定的设备和IP信息（可选）</li>
     *   <li>撤销旧的刷新令牌（防止重放攻击）</li>
     *   <li>颁发新的访问令牌（有效期2小时）</li>
     *   <li>颁发新的刷新令牌（有效期7天）</li>
     *   <li>记录刷新日志</li>
     * </ol>
     *
     * <h3>安全说明</h3>
     * <ul>
     *   <li>刷新令牌只能使用一次，使用后立即失效</li>
     *   <li>检测到刷新令牌重用会撤销该用户所有令牌（可能的令牌泄露）</li>
     *   <li>刷新令牌有效期内可以无限次刷新</li>
     *   <li>建议在访问令牌过期前主动刷新，避免接口调用中断</li>
     * </ul>
     *
     * <h3>最佳实践</h3>
     * <ul>
     *   <li>客户端应在访问令牌过期前5分钟主动刷新</li>
     *   <li>刷新失败后应引导用户重新登录</li>
     *   <li>保存新的令牌对并更新本地存储</li>
     *   <li>避免频繁刷新，建议间隔至少1小时</li>
     * </ul>
     *
     * @param request HTTP请求，包含RefreshTokenRequest请求体
     * @return 新的令牌对（包含新的访问令牌和刷新令牌）
     */
    @Operation(
            operationId = "refreshToken",
            summary = "刷新访问令牌",
            description = "使用刷新令牌换取新的访问令牌,延长会话有效期。支持刷新令牌轮换机制,每次刷新后旧的刷新令牌自动失效。")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "刷新成功,返回新的访问令牌和刷新令牌",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "刷新令牌无效、已过期或已被撤销",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "403",
                description = "检测到刷新令牌重用,可能的安全威胁,所有令牌已被撤销",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "系统内部错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    })
    @RequestBody(
            description = "刷新令牌请求参数,包含刷新令牌字符串",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RefreshTokenRequest.class)))
    @OperationLog(
            module = "Authentication",
            type = OperationLog.OperationType.OTHER,
            description = "refreshToken",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> refreshToken(ServerRequest request) {
        return request.bodyToMono(RefreshTokenRequest.class)
                .flatMap(req -> authenticationService.refreshToken(req.getRefreshToken()))
                .flatMap(tokenPair -> ServerResponse.ok().bodyValue(tokenPair))
                .onErrorResume(error -> {
                    if (error instanceof BusinessException) {
                        return ServerResponse.status(401).bodyValue("刷新令牌无效或已过期");
                    }
                    return ServerResponse.status(500).bodyValue("刷新失败");
                });
    }
}
