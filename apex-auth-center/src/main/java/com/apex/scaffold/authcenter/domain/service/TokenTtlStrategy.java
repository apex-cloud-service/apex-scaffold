package com.apex.scaffold.authcenter.domain.service;

import java.time.Duration;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Token动态生命周期策略服务
 *
 * <p>根据用户角色、租户配置、操作敏感度动态调整Token有效期：</p>
 * <ul>
 *   <li>超级管理员：Access Token 30分钟，Refresh Token 1天</li>
 *   <li>租户管理员：Access Token 1小时，Refresh Token 3天</li>
 *   <li>财务/HR角色：Access Token 1小时，Refresh Token 7天</li>
 *   <li>普通用户：Access Token 2小时，Refresh Token 14天</li>
 *   <li>只读用户：Access Token 4小时，Refresh Token 30天</li>
 * </ul>
 *
 * <p>敏感操作临时降级：</p>
 * <ul>
 *   <li>修改密码后：强制重新登录</li>
 *   <li>修改安全设置后：Access Token降级为15分钟</li>
 *   <li>敏感数据操作：每次验证Token</li>
 * </ul>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenTtlStrategy {

    /**
     * 默认Token有效期
     */
    private static final Duration DEFAULT_ACCESS_TOKEN_TTL = Duration.ofHours(2);

    private static final Duration DEFAULT_REFRESH_TOKEN_TTL = Duration.ofDays(7);

    /**
     * 角色对应的Token有效期
     */
    private static final Duration SUPER_ADMIN_ACCESS_TTL = Duration.ofMinutes(30);

    private static final Duration SUPER_ADMIN_REFRESH_TTL = Duration.ofDays(1);

    private static final Duration TENANT_ADMIN_ACCESS_TTL = Duration.ofHours(1);
    private static final Duration TENANT_ADMIN_REFRESH_TTL = Duration.ofDays(3);

    private static final Duration SENSITIVE_ROLE_ACCESS_TTL = Duration.ofHours(1);
    private static final Duration SENSITIVE_ROLE_REFRESH_TTL = Duration.ofDays(7);

    private static final Duration READONLY_ACCESS_TTL = Duration.ofHours(4);
    private static final Duration READONLY_REFRESH_TTL = Duration.ofDays(30);

    private static final Duration SECURITY_CHANGE_ACCESS_TTL = Duration.ofMinutes(15);

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";
    private static final String ROLE_TENANT_ADMIN = "TENANT_ADMIN";
    private static final String ROLE_ORG_ADMIN = "ORG_ADMIN";
    private static final String ROLE_READONLY = "READONLY";
    private static final String ROLE_VIEWER = "VIEWER";
    private static final String ROLE_FINANCE = "FINANCE";
    private static final String ROLE_HR = "HR";

    public Mono<Duration> getAccessTokenTtl(
            String userId, Set<String> roles, String tenantId, boolean isSecuritySensitive) {
        log.debug(
                "计算Access Token TTL: userId={}, roles={}, tenantId={}, securitySensitive={}",
                userId,
                roles,
                tenantId,
                isSecuritySensitive);

        if (isSecuritySensitive) {
            log.info("安全敏感操作后降级Token有效期: userId={}", userId);
            return Mono.just(SECURITY_CHANGE_ACCESS_TTL);
        }

        if (roles.contains(ROLE_SUPER_ADMIN) || roles.contains(ROLE_SYSTEM_ADMIN)) {
            return Mono.just(SUPER_ADMIN_ACCESS_TTL);
        }

        if (roles.contains(ROLE_TENANT_ADMIN) || roles.contains(ROLE_ORG_ADMIN)) {
            return Mono.just(TENANT_ADMIN_ACCESS_TTL);
        }

        if (roles.stream()
                .anyMatch(role -> role.contains(ROLE_FINANCE) || role.contains(ROLE_HR) || role.contains("AUDIT"))) {
            return Mono.just(SENSITIVE_ROLE_ACCESS_TTL);
        }

        if (roles.contains(ROLE_READONLY) || roles.contains(ROLE_VIEWER)) {
            return Mono.just(READONLY_ACCESS_TTL);
        }

        return Mono.just(DEFAULT_ACCESS_TOKEN_TTL);
    }

    public Mono<Duration> getRefreshTokenTtl(String userId, Set<String> roles, String tenantId) {
        log.debug("计算Refresh Token TTL: userId={}, roles={}, tenantId={}", userId, roles, tenantId);

        if (roles.contains(ROLE_SUPER_ADMIN) || roles.contains(ROLE_SYSTEM_ADMIN)) {
            return Mono.just(SUPER_ADMIN_REFRESH_TTL);
        }

        if (roles.contains(ROLE_TENANT_ADMIN) || roles.contains(ROLE_ORG_ADMIN)) {
            return Mono.just(TENANT_ADMIN_REFRESH_TTL);
        }

        if (roles.stream()
                .anyMatch(role -> role.contains(ROLE_FINANCE) || role.contains(ROLE_HR) || role.contains("AUDIT"))) {
            return Mono.just(SENSITIVE_ROLE_REFRESH_TTL);
        }

        if (roles.contains(ROLE_READONLY) || roles.contains(ROLE_VIEWER)) {
            return Mono.just(READONLY_REFRESH_TTL);
        }

        return Mono.just(DEFAULT_REFRESH_TOKEN_TTL);
    }

    /**
     * 获取OAuth2授权码有效期
     *
     * @param clientId 客户端ID
     * @return 授权码有效期
     */
    public Mono<Duration> getAuthorizationCodeTtl(String clientId) {
        // 授权码统一为5分钟（符合OAuth2规范）
        return Mono.just(Duration.ofMinutes(5));
    }

    /**
     * 判断操作是否需要Token降级
     *
     * @param operationType 操作类型
     * @return true表示需要降级
     */
    public boolean requiresTokenDowngrade(String operationType) {
        return Set.of(
                        "CHANGE_PASSWORD",
                        "CHANGE_EMAIL",
                        "CHANGE_PHONE",
                        "ENABLE_2FA",
                        "DISABLE_2FA",
                        "UPDATE_SECURITY_QUESTION",
                        "BIND_DEVICE",
                        "UNBIND_DEVICE")
                .contains(operationType);
    }

    /**
     * 根据租户配置获取自定义TTL
     *
     * @param tenantId 租户ID
     * @param defaultTtl 默认TTL
     * @return 最终TTL
     */
    public Mono<Duration> getCustomTtlByTenant(String tenantId, Duration defaultTtl) {
        return Mono.just(defaultTtl);
    }

    /**
     * 计算Token剩余有效时间百分比
     *
     * @param issuedAt Token颁发时间（毫秒时间戳）
     * @param expiresAt Token过期时间（毫秒时间戳）
     * @return 剩余有效时间百分比（0-100）
     */
    public int calculateRemainingTtlPercent(long issuedAt, long expiresAt) {
        long now = System.currentTimeMillis();
        long totalTtl = expiresAt - issuedAt;
        long remainingTtl = expiresAt - now;

        if (remainingTtl <= 0) {
            return 0;
        }

        if (totalTtl <= 0) {
            return 100;
        }

        return (int) ((remainingTtl * 100) / totalTtl);
    }

    /**
     * 判断Token是否应该主动刷新
     *
     * @param remainingPercent 剩余有效时间百分比
     * @return true表示应该刷新
     */
    public boolean shouldProactivelyRefresh(int remainingPercent) {
        // 剩余时间小于20%时主动刷新
        return remainingPercent < 20;
    }

    /**
     * Token TTL配置
     */
    public static class TokenTtlConfig {
        private final Duration accessTokenTtl;
        private final Duration refreshTokenTtl;
        private final boolean isDowngraded;
        private final String reason;

        /**
         * 构造Token TTL配置。
         *
         * @param accessTokenTtl 访问令牌TTL
         * @param refreshTokenTtl 刷新令牌TTL
         * @param isDowngraded 是否降级
         * @param reason 降级原因
         */
        public TokenTtlConfig(Duration accessTokenTtl, Duration refreshTokenTtl, boolean isDowngraded, String reason) {
            this.accessTokenTtl = accessTokenTtl;
            this.refreshTokenTtl = refreshTokenTtl;
            this.isDowngraded = isDowngraded;
            this.reason = reason;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public boolean isDowngraded() {
            return isDowngraded;
        }

        public String getReason() {
            return reason;
        }

        public long getAccessTokenTtlSeconds() {
            return accessTokenTtl.getSeconds();
        }

        public long getRefreshTokenTtlSeconds() {
            return refreshTokenTtl.getSeconds();
        }

        @Override
        public String toString() {
            return String.format(
                    "TokenTtlConfig{accessTtl=%s, refreshTtl=%s, downgraded=%s, reason='%s'}",
                    accessTokenTtl, refreshTokenTtl, isDowngraded, reason);
        }
    }

    /**
     * 获取完整的Token TTL配置
     *
     * @param userId 用户ID
     * @param roles 用户角色
     * @param tenantId 租户ID
     * @param isSecuritySensitive 是否安全敏感操作
     * @return Token TTL配置
     */
    public Mono<TokenTtlConfig> getTokenTtlConfig(
            String userId, Set<String> roles, String tenantId, boolean isSecuritySensitive) {
        return Mono.zip(
                        getAccessTokenTtl(userId, roles, tenantId, isSecuritySensitive),
                        getRefreshTokenTtl(userId, roles, tenantId))
                .map(tuple -> {
                    Duration accessTtl = tuple.getT1();
                    Duration refreshTtl = tuple.getT2();

                    String reason = isSecuritySensitive ? "安全敏感操作后降级" : determineReason(roles);

                    return new TokenTtlConfig(accessTtl, refreshTtl, isSecuritySensitive, reason);
                });
    }

    private String determineReason(Set<String> roles) {
        if (roles.contains(ROLE_SUPER_ADMIN)) {
            return "超级管理员（最短TTL）";
        }
        if (roles.contains(ROLE_TENANT_ADMIN)) {
            return "租户管理员";
        }
        if (roles.stream().anyMatch(r -> r.contains(ROLE_FINANCE) || r.contains(ROLE_HR))) {
            return "敏感角色（财务/HR）";
        }
        if (roles.contains(ROLE_READONLY)) {
            return "只读用户（最长TTL）";
        }
        return "普通用户";
    }
}
