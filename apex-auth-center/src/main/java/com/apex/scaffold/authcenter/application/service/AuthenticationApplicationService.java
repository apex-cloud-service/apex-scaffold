package com.apex.scaffold.authcenter.application.service;

import com.apex.scaffold.authcenter.application.dto.LoginRequest;
import com.apex.scaffold.authcenter.application.dto.LoginResponse;
import com.apex.scaffold.authcenter.domain.model.vo.TokenPair;
import com.apex.scaffold.authcenter.domain.repository.UserRepository;
import com.apex.scaffold.authcenter.domain.service.TokenService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
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
 * <h2>核心职责</h2>
 * <ul>
 *   <li>用户名密码登录：验证凭证、记录登录、签发Token</li>
 *   <li>登出：撤销用户所有Token</li>
 *   <li>Token刷新：验证刷新令牌、签发新访问令牌</li>
 * </ul>
 *
 * <h2>安全保障</h2>
 * <ul>
 *   <li>密码使用BCrypt加密存储和验证</li>
 *   <li>登录失败自动记录，5次失败锁定30分钟</li>
 *   <li>账户锁定、禁用状态检查</li>
 *   <li>租户隔离验证</li>
 * </ul>
 *
 * @author Apex Team
 * @since 2026-09-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationApplicationService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

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

        log.debug("开始登录: username={}, tenantId={}", username, tenantId);

        return userRepository
                .findByUsername(username, tenantId)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("用户不存在: username={}, tenantId={}", username, tenantId);
                    return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误"));
                }))
                .flatMap(user -> {
                    if (user.isLocked()) {
                        log.warn("账户已锁定: userId={}, username={}", user.getId(), username);
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "账户已锁定，请稍后重试"));
                    }

                    if (!user.isActive()) {
                        log.warn("账户已禁用: userId={}, username={}", user.getId(), username);
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "账户已禁用"));
                    }

                    boolean passwordMatches = passwordEncoder.matches(password, user.getPasswordHash());
                    if (!passwordMatches) {
                        log.warn("密码错误: userId={}, username={}", user.getId(), username);
                        user.recordLoginFailure();
                        return userRepository
                                .update(user)
                                .then(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误")));
                    }

                    user.recordLoginSuccess(clientIp);
                    return userRepository
                            .update(user)
                            .then(tokenService.issueToken(user.getId(), tenantId, deviceId, clientIp))
                            .map(tokenPair -> buildLoginResponse(user.getId(), username, tenantId, tokenPair));
                })
                .doOnSuccess(response ->
                        log.info("登录成功: userId={}, username={}, tenantId={}", response.getUserId(), username, tenantId))
                .doOnError(
                        e -> log.error("登录失败: username={}, tenantId={}, error={}", username, tenantId, e.getMessage()));
    }

    /**
     * 用户登出
     *
     * <p>撤销用户在指定租户下的所有Token
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @return 空结果
     */
    public Mono<Void> logout(Long userId, String tenantId) {
        log.debug("开始登出: userId={}, tenantId={}", userId, tenantId);

        return tokenService
                .revokeAllUserTokens(userId, tenantId)
                .doOnSuccess(v -> log.info("登出成功: userId={}, tenantId={}", userId, tenantId))
                .doOnError(e -> log.error("登出失败: userId={}, tenantId={}, error={}", userId, tenantId, e.getMessage()));
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
                .doOnSuccess(pair -> log.info("Token刷新成功"))
                .doOnError(e -> log.error("Token刷新失败: error={}", e.getMessage()));
    }

    /**
     * 构建登录响应
     *
     * @param userId    用户ID
     * @param username  用户名
     * @param tenantId  租户ID
     * @param tokenPair Token对
     * @return 登录响应
     */
    private LoginResponse buildLoginResponse(Long userId, String username, String tenantId, TokenPair tokenPair) {
        return LoginResponse.builder()
                .userId(userId)
                .username(username)
                .tenantId(tenantId)
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .tokenType(tokenPair.getTokenType())
                .expiresIn(tokenPair.getExpiresIn())
                .build();
    }
}
