package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.application.dto.LoginRequest;
import com.apex.scaffold.auth.application.dto.LoginResponse;
import com.apex.scaffold.auth.domain.model.vo.TokenPair;
import com.apex.scaffold.auth.domain.repository.UserRepository;
import com.apex.scaffold.auth.domain.service.TokenService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.event.TokenRevokedEvent;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 认证应用服务
 *
 * <p>编排用户认证流程，包括登录、登出和Token刷新。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>用户名密码登录：验证凭证、记录登录、签发Token</li>
 *   <li>登出：撤销用户所有Token</li>
 *   <li>Token刷新：验证刷新令牌、签发新访问令牌</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>密码使用BCrypt加密存储和验证</li>
 *   <li>登录失败自动记录，5次失败锁定30分钟</li>
 *   <li>账户锁定、禁用状态检查</li>
 *   <li>租户隔离验证</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationApplicationService {

    /** 登出撤销令牌的事件原因标识。 */
    private static final String LOGOUT_REASON = "LOGOUT";

    /** MFA 挑战凭据的 purpose 声明值（仅用于二步验证交换）。 */
    private static final String MFA_CHALLENGE_PURPOSE = "mfa_challenge";

    /** MFA 挑战凭据有效期（毫秒）：5 分钟，仅供一次验证码交换。 */
    private static final long MFA_CHALLENGE_TTL_MS = 5 * 60 * 1000L;

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    /** 领域事件发布器：登出成功后广播令牌撤销事件，通知各服务清理本地权限缓存。 */
    private final DomainEventPublisher domainEventPublisher;

    /** TOTP MFA 服务（登录二步挑战判定与验证码校验）。 */
    private final TotpService totpService;

    /** 会话服务（登录登记会话、登出终止会话，统一认证收口 A4 接线）。 */
    private final SessionService sessionService;

    /** JWT 密钥材料服务（签发/解析 MFA 挑战凭据，RS256）。 */
    private final com.apex.scaffold.auth.domain.service.JwtKeyMaterialService jwtKeyMaterialService;

    /** 租户认证期状态校验端口（统一认证收口 A6，登录前置；未配置时适配器跳过）。 */
    private final com.apex.scaffold.auth.domain.service.TenantStatusPort tenantStatusPort;

    /**
     * 用户名密码登录
     *
     * <p>验证用户凭证，登录成功后签发Token对并记录登录信息
     *
     * @param request  登录请求
     * @param clientIp 客户端IP
     * @return 登录响应（包含用户信息和Token）
     */
    public Mono<LoginResponse> login(LoginRequest request, String clientIp) {
        String username = request.getUsername();
        String password = request.getPassword();
        String tenantId = request.getTenantId() != null ? request.getTenantId() : "default";
        String deviceId = request.getDeviceId();
        String clientType = request.getClientType();

        log.debug("开始登录");

        return tenantStatusPort
                // 认证期租户校验（A6）：未知/停用/冻结/过期租户在用户查询前拒绝（fail-closed）
                .validateLoginTenant(tenantId)
                .then(Mono.defer(() -> userRepository
                        .findByUsername(username, tenantId)
                        .switchIfEmpty(Mono.defer(() -> {
                            log.debug("登录拒绝: 用户不存在");
                            return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误"));
                        }))
                        .flatMap(user -> {
                            if (user.isLocked()) {
                                log.debug("登录拒绝: 账户已锁定, userId={}", user.getId());
                                return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "账户已锁定，请稍后重试"));
                            }

                            if (!user.isActive()) {
                                log.debug("登录拒绝: 账户已禁用, userId={}", user.getId());
                                return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "账户已禁用"));
                            }

                            boolean passwordMatches = passwordEncoder.matches(password, user.getPasswordHash());
                            if (!passwordMatches) {
                                log.debug("登录拒绝: 凭证无效, userId={}", user.getId());
                                user.recordLoginFailure();
                                return userRepository
                                        .update(user)
                                        .then(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误")));
                            }

                            user.recordLoginSuccess(clientIp);
                            return userRepository
                                    .update(user)
                                    // MFA 已启用：不签发正式令牌，返回挑战凭据（统一认证收口 A4 二步登录）。
                                    // 登录端点无租户上下文（permitAll），此处以已验证的 (username, tenantId)
                                    // 匹配结果作为可信租户，向下游持久化链桥接 TenantContext。
                                    .then(Mono.defer(() -> totpService
                                            .isEnabled(user.getId(), tenantId)
                                            .flatMap(mfaEnabled -> Boolean.TRUE.equals(mfaEnabled)
                                                    ? issueMfaChallenge(user, tenantId, username, deviceId)
                                                    : issueTokensAndRegisterSession(
                                                            user, tenantId, username, deviceId, clientType, clientIp))
                                            .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant(
                                                    tenantId))));
                        })
                        .doOnNext(response -> log.debug("登录成功: userId={}", response.getUserId()))
                        .doOnError(e -> log.debug("登录失败: error={}", e.getClass().getSimpleName()))));
    }

    /**
     * 签发 MFA 挑战响应：短 TTL RS256 凭据（purpose=mfa_challenge），不签发正式令牌。
     *
     * @param user     已通过密码校验的用户
     * @param tenantId 租户
     * @param username 用户名
     * @param deviceId 设备 ID
     * @return mfaRequired=true 的登录响应
     */
    private Mono<LoginResponse> issueMfaChallenge(
            com.apex.scaffold.auth.domain.model.entity.UserEntity user,
            String tenantId,
            String username,
            String deviceId) {
        java.util.Map<String, Object> claims = new java.util.LinkedHashMap<>();
        claims.put("purpose", MFA_CHALLENGE_PURPOSE);
        claims.put("accountId", user.getId());
        claims.put("tenantId", tenantId);
        claims.put("username", username);
        claims.put("deviceId", deviceId == null ? "" : deviceId);
        return jwtKeyMaterialService
                .sign(String.valueOf(user.getId()), claims, MFA_CHALLENGE_TTL_MS)
                .map(challenge -> LoginResponse.builder()
                        .userId(user.getId())
                        .username(username)
                        .tenantId(tenantId)
                        .accountId(user.getId())
                        .mfaRequired(Boolean.TRUE)
                        .challengeToken(challenge)
                        .build());
    }

    /**
     * 签发正式令牌对并登记在线会话（统一认证收口 A4）。
     *
     * <p>会话登记关联访问令牌持久化 ID；会话数量上限由 {@link SessionService} 内部策略约束。
     */
    private Mono<LoginResponse> issueTokensAndRegisterSession(
            com.apex.scaffold.auth.domain.model.entity.UserEntity user,
            String tenantId,
            String username,
            String deviceId,
            String clientType,
            String clientIp) {
        Mono<com.apex.scaffold.auth.domain.model.vo.TokenPair> issuing = clientType == null || clientType.isBlank()
                ? tokenService.issueToken(user.getId(), tenantId, deviceId, clientIp)
                : tokenService.issueToken(user.getId(), tenantId, deviceId, clientIp, clientType);
        return issuing.flatMap(tokenPair -> registerSession(user, tenantId, tokenPair, clientType, deviceId, clientIp)
                .then(Mono.fromSupplier(() -> buildLoginResponse(user, tenantId, tokenPair))));
    }

    /** 登记登录会话（令牌缺失持久化 ID 时静默跳过，保持会话能力可降级）。 */
    private Mono<Void> registerSession(
            com.apex.scaffold.auth.domain.model.entity.UserEntity user,
            String tenantId,
            com.apex.scaffold.auth.domain.model.vo.TokenPair tokenPair,
            String clientType,
            String deviceId,
            String clientIp) {
        if (tokenPair.getAccessTokenId() == null) {
            return Mono.empty();
        }
        return sessionService
                .createSession(
                        user.getId(),
                        tenantId,
                        tokenPair.getAccessTokenId(),
                        clientType == null || clientType.isBlank() ? "UNKNOWN" : clientType,
                        deviceId == null || deviceId.isBlank() ? "unknown" : deviceId,
                        clientIp,
                        null)
                .then();
    }

    /**
     * MFA 二步验证：以挑战凭据交换正式令牌（统一认证收口 A4）。
     *
     * <p>校验挑战凭据签名、有效期与 purpose 声明后，验证 TOTP 验证码；通过即按登录链路
     * 签发令牌并登记会话。挑战凭据一次性语义由短 TTL 保证，验证失败按未授权处理。
     *
     * @param challengeToken 登录返回的挑战凭据
     * @param code           TOTP 验证码
     * @param clientIp       客户端 IP（会话登记）
     * @return 登录响应（正式令牌）
     */
    public Mono<LoginResponse> verifyMfaChallenge(String challengeToken, String code, String clientIp) {
        if (challengeToken == null || challengeToken.isBlank() || code == null || code.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "MFA 挑战凭据或验证码缺失"));
        }
        return jwtKeyMaterialService
                .parse(challengeToken)
                .flatMap(claims -> {
                    String purpose = claims.get("purpose", String.class);
                    Long accountId = claims.get("accountId", Long.class);
                    String tenantId = claims.get("tenantId", String.class);
                    String username = claims.get("username", String.class);
                    String deviceId = claims.get("deviceId", String.class);
                    if (!MFA_CHALLENGE_PURPOSE.equals(purpose)
                            || accountId == null
                            || tenantId == null
                            || tenantId.isBlank()) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "MFA 挑战凭据无效"));
                    }
                    return totpService.verifyCode(accountId, code, tenantId).flatMap(valid -> {
                        if (!Boolean.TRUE.equals(valid)) {
                            return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "MFA 验证码错误"));
                        }
                        return userRepository
                                .findById(accountId)
                                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "MFA 挑战凭据无效")))
                                .flatMap(user -> {
                                    if (!user.isActive()) {
                                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "账户已禁用"));
                                    }
                                    return issueTokensAndRegisterSession(
                                                    user, tenantId, username, deviceId, null, clientIp)
                                            .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant(
                                                    tenantId));
                                });
                    });
                })
                .onErrorResume(
                        io.jsonwebtoken.JwtException.class,
                        e -> Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "MFA 挑战凭据无效")))
                .doOnError(e -> log.debug("MFA 二步验证失败: error={}", e.getClass().getSimpleName()));
    }

    /**
     * 用户登出
     *
     * <p>撤销用户在指定租户下的所有Token，并发布 {@link TokenRevokedEvent}，
     * 通知各业务服务清理本地权限缓存与在线态副本（迁移方案 Phase 2.6 登出传播）。
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     */
    public Mono<Void> logout(Long userId, String tenantId) {
        log.debug("开始登出: userId={}", userId);

        return tokenService
                .revokeAllUserTokens(userId, tenantId)
                // 令牌撤销成功后再发布撤销事件：延迟构造事件，确保撤销失败时不产生事件
                .then(Mono.defer(
                        () -> domainEventPublisher.publish(new TokenRevokedEvent(userId, tenantId, LOGOUT_REASON))))
                // 会话登记接线（A4）：终止该用户全部在线会话，SessionRouter 端点从空壳变为可用
                .then(sessionService.terminateUserSessions(userId, tenantId))
                .doOnSuccess(v -> log.debug("登出成功: userId={}", userId))
                .doOnError(e -> log.debug(
                        "登出失败: userId={}, error={}", userId, e.getClass().getSimpleName()));
    }

    /**
     * 刷新Token
     *
     * <p>使用刷新令牌换取新的访问令牌，刷新令牌不轮换
     *
     * @param refreshToken 刷新令牌
     * @return 新的Token对
     */
    public Mono<TokenPair> refreshToken(String refreshToken) {
        log.debug("开始刷新Token");

        return tokenService
                .refreshToken(refreshToken)
                .doOnSuccess(pair -> log.debug("Token刷新成功"))
                .doOnError(e -> log.debug("Token刷新失败: error={}", e.getClass().getSimpleName()));
    }

    /**
     * 构建登录响应（统一账户模型）。
     *
     * @param user      登录用户实体（携带统一账户映射）
     * @param tenantId  租户ID
     * @param tokenPair Token对
     * @return 登录响应
     */
    private LoginResponse buildLoginResponse(
            com.apex.scaffold.auth.domain.model.entity.UserEntity user, String tenantId, TokenPair tokenPair) {
        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .tenantId(tenantId)
                .accountId(user.getAccountId())
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .tokenType(tokenPair.getTokenType())
                .expiresIn(tokenPair.getExpiresIn())
                .build();
    }
}
