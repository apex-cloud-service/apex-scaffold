package com.apex.scaffold.authcenter.domain.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@DisplayName("Token动态生命周期策略服务测试")
class TokenTtlStrategyTest {

    private TokenTtlStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new TokenTtlStrategy();
    }

    @Test
    @DisplayName("获取Access Token TTL - 超级管理员")
    void testGetAccessTokenTtl_SuperAdmin() {
        Set<String> roles = Set.of("SUPER_ADMIN");

        StepVerifier.create(strategy.getAccessTokenTtl("user-1", roles, "tenant-1", false))
                .expectNext(Duration.ofMinutes(30))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Access Token TTL - 系统管理员")
    void testGetAccessTokenTtl_SystemAdmin() {
        Set<String> roles = Set.of("SYSTEM_ADMIN");

        StepVerifier.create(strategy.getAccessTokenTtl("user-2", roles, "tenant-1", false))
                .expectNext(Duration.ofMinutes(30))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Access Token TTL - 租户管理员")
    void testGetAccessTokenTtl_TenantAdmin() {
        Set<String> roles = Set.of("TENANT_ADMIN");

        StepVerifier.create(strategy.getAccessTokenTtl("user-3", roles, "tenant-1", false))
                .expectNext(Duration.ofHours(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Access Token TTL - 组织管理员")
    void testGetAccessTokenTtl_OrgAdmin() {
        Set<String> roles = Set.of("ORG_ADMIN");

        StepVerifier.create(strategy.getAccessTokenTtl("user-4", roles, "tenant-1", false))
                .expectNext(Duration.ofHours(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Access Token TTL - 财务角色")
    void testGetAccessTokenTtl_FinanceRole() {
        Set<String> roles = Set.of("FINANCE_MANAGER");

        StepVerifier.create(strategy.getAccessTokenTtl("user-5", roles, "tenant-1", false))
                .expectNext(Duration.ofHours(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Access Token TTL - HR角色")
    void testGetAccessTokenTtl_HrRole() {
        Set<String> roles = Set.of("HR_ADMIN");

        StepVerifier.create(strategy.getAccessTokenTtl("user-6", roles, "tenant-1", false))
                .expectNext(Duration.ofHours(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Access Token TTL - 审计角色")
    void testGetAccessTokenTtl_AuditRole() {
        Set<String> roles = Set.of("AUDIT_OFFICER");

        StepVerifier.create(strategy.getAccessTokenTtl("user-7", roles, "tenant-1", false))
                .expectNext(Duration.ofHours(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Access Token TTL - 只读用户")
    void testGetAccessTokenTtl_ReadonlyUser() {
        Set<String> roles = Set.of("READONLY");

        StepVerifier.create(strategy.getAccessTokenTtl("user-8", roles, "tenant-1", false))
                .expectNext(Duration.ofHours(4))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Access Token TTL - 查看者")
    void testGetAccessTokenTtl_Viewer() {
        Set<String> roles = Set.of("VIEWER");

        StepVerifier.create(strategy.getAccessTokenTtl("user-9", roles, "tenant-1", false))
                .expectNext(Duration.ofHours(4))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Access Token TTL - 普通用户")
    void testGetAccessTokenTtl_RegularUser() {
        Set<String> roles = Set.of("USER");

        StepVerifier.create(strategy.getAccessTokenTtl("user-10", roles, "tenant-1", false))
                .expectNext(Duration.ofHours(2))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Access Token TTL - 安全敏感操作后降级")
    void testGetAccessTokenTtl_SecuritySensitive() {
        Set<String> roles = Set.of("TENANT_ADMIN");

        StepVerifier.create(strategy.getAccessTokenTtl("user-11", roles, "tenant-1", true))
                .expectNext(Duration.ofMinutes(15))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Access Token TTL - 多角色取最高权限")
    void testGetAccessTokenTtl_MultipleRoles() {
        Set<String> roles = Set.of("USER", "TENANT_ADMIN", "FINANCE_MANAGER");

        StepVerifier.create(strategy.getAccessTokenTtl("user-12", roles, "tenant-1", false))
                .expectNext(Duration.ofHours(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Refresh Token TTL - 超级管理员")
    void testGetRefreshTokenTtl_SuperAdmin() {
        Set<String> roles = Set.of("SUPER_ADMIN");

        StepVerifier.create(strategy.getRefreshTokenTtl("user-1", roles, "tenant-1"))
                .expectNext(Duration.ofDays(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Refresh Token TTL - 租户管理员")
    void testGetRefreshTokenTtl_TenantAdmin() {
        Set<String> roles = Set.of("TENANT_ADMIN");

        StepVerifier.create(strategy.getRefreshTokenTtl("user-2", roles, "tenant-1"))
                .expectNext(Duration.ofDays(3))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Refresh Token TTL - 财务角色")
    void testGetRefreshTokenTtl_FinanceRole() {
        Set<String> roles = Set.of("FINANCE_ADMIN");

        StepVerifier.create(strategy.getRefreshTokenTtl("user-3", roles, "tenant-1"))
                .expectNext(Duration.ofDays(7))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Refresh Token TTL - 只读用户")
    void testGetRefreshTokenTtl_ReadonlyUser() {
        Set<String> roles = Set.of("READONLY");

        StepVerifier.create(strategy.getRefreshTokenTtl("user-4", roles, "tenant-1"))
                .expectNext(Duration.ofDays(30))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Refresh Token TTL - 普通用户")
    void testGetRefreshTokenTtl_RegularUser() {
        Set<String> roles = Set.of("USER");

        StepVerifier.create(strategy.getRefreshTokenTtl("user-5", roles, "tenant-1"))
                .expectNext(Duration.ofDays(7))
                .verifyComplete();
    }

    @Test
    @DisplayName("获取授权码TTL - 固定5分钟")
    void testGetAuthorizationCodeTtl() {
        StepVerifier.create(strategy.getAuthorizationCodeTtl("client-123"))
                .expectNext(Duration.ofMinutes(5))
                .verifyComplete();
    }

    @Test
    @DisplayName("判断操作是否需要Token降级 - 修改密码")
    void testRequiresTokenDowngrade_ChangePassword() {
        assertTrue(strategy.requiresTokenDowngrade("CHANGE_PASSWORD"));
    }

    @Test
    @DisplayName("判断操作是否需要Token降级 - 修改邮箱")
    void testRequiresTokenDowngrade_ChangeEmail() {
        assertTrue(strategy.requiresTokenDowngrade("CHANGE_EMAIL"));
    }

    @Test
    @DisplayName("判断操作是否需要Token降级 - 修改手机")
    void testRequiresTokenDowngrade_ChangePhone() {
        assertTrue(strategy.requiresTokenDowngrade("CHANGE_PHONE"));
    }

    @Test
    @DisplayName("判断操作是否需要Token降级 - 启用2FA")
    void testRequiresTokenDowngrade_Enable2FA() {
        assertTrue(strategy.requiresTokenDowngrade("ENABLE_2FA"));
    }

    @Test
    @DisplayName("判断操作是否需要Token降级 - 禁用2FA")
    void testRequiresTokenDowngrade_Disable2FA() {
        assertTrue(strategy.requiresTokenDowngrade("DISABLE_2FA"));
    }

    @Test
    @DisplayName("判断操作是否需要Token降级 - 更新安全问题")
    void testRequiresTokenDowngrade_UpdateSecurityQuestion() {
        assertTrue(strategy.requiresTokenDowngrade("UPDATE_SECURITY_QUESTION"));
    }

    @Test
    @DisplayName("判断操作是否需要Token降级 - 绑定设备")
    void testRequiresTokenDowngrade_BindDevice() {
        assertTrue(strategy.requiresTokenDowngrade("BIND_DEVICE"));
    }

    @Test
    @DisplayName("判断操作是否需要Token降级 - 解绑设备")
    void testRequiresTokenDowngrade_UnbindDevice() {
        assertTrue(strategy.requiresTokenDowngrade("UNBIND_DEVICE"));
    }

    @Test
    @DisplayName("判断操作是否需要Token降级 - 普通操作不需要")
    void testRequiresTokenDowngrade_RegularOperation() {
        assertFalse(strategy.requiresTokenDowngrade("VIEW_PROFILE"));
        assertFalse(strategy.requiresTokenDowngrade("UPDATE_AVATAR"));
        assertFalse(strategy.requiresTokenDowngrade("READ_DATA"));
    }

    @Test
    @DisplayName("获取租户自定义TTL - 返回默认值")
    void testGetCustomTtlByTenant() {
        Duration defaultTtl = Duration.ofHours(3);

        StepVerifier.create(strategy.getCustomTtlByTenant("tenant-1", defaultTtl))
                .expectNext(defaultTtl)
                .verifyComplete();
    }

    @Test
    @DisplayName("计算Token剩余有效时间百分比 - 正常情况")
    void testCalculateRemainingTtlPercent_Normal() {
        long issuedAt = System.currentTimeMillis() - 1000 * 60 * 60;
        long expiresAt = System.currentTimeMillis() + 1000 * 60 * 60;

        int percent = strategy.calculateRemainingTtlPercent(issuedAt, expiresAt);

        assertTrue(percent >= 45 && percent <= 55);
    }

    @Test
    @DisplayName("计算Token剩余有效时间百分比 - 已过期")
    void testCalculateRemainingTtlPercent_Expired() {
        long issuedAt = System.currentTimeMillis() - 1000 * 60 * 120;
        long expiresAt = System.currentTimeMillis() - 1000 * 60 * 60;

        int percent = strategy.calculateRemainingTtlPercent(issuedAt, expiresAt);

        assertEquals(0, percent);
    }

    @Test
    @DisplayName("计算Token剩余有效时间百分比 - 刚颁发")
    void testCalculateRemainingTtlPercent_JustIssued() {
        long issuedAt = System.currentTimeMillis();
        long expiresAt = System.currentTimeMillis() + 1000 * 60 * 60;

        int percent = strategy.calculateRemainingTtlPercent(issuedAt, expiresAt);

        assertTrue(percent >= 95 && percent <= 100);
    }

    @Test
    @DisplayName("计算Token剩余有效时间百分比 - 即将过期")
    void testCalculateRemainingTtlPercent_AboutToExpire() {
        long issuedAt = System.currentTimeMillis() - 1000 * 60 * 55;
        long expiresAt = System.currentTimeMillis() + 1000 * 60 * 5;

        int percent = strategy.calculateRemainingTtlPercent(issuedAt, expiresAt);

        assertTrue(percent >= 5 && percent <= 10);
    }

    @Test
    @DisplayName("计算Token剩余有效时间百分比 - 总TTL为0")
    void testCalculateRemainingTtlPercent_ZeroTotalTtl() {
        long issuedAt = System.currentTimeMillis();
        long expiresAt = System.currentTimeMillis();

        int percent = strategy.calculateRemainingTtlPercent(issuedAt, expiresAt);

        assertEquals(0, percent);
    }

    @Test
    @DisplayName("判断是否应该主动刷新 - 剩余10%")
    void testShouldProactivelyRefresh_10Percent() {
        assertTrue(strategy.shouldProactivelyRefresh(10));
    }

    @Test
    @DisplayName("判断是否应该主动刷新 - 剩余19%")
    void testShouldProactivelyRefresh_19Percent() {
        assertTrue(strategy.shouldProactivelyRefresh(19));
    }

    @Test
    @DisplayName("判断是否应该主动刷新 - 剩余20%")
    void testShouldProactivelyRefresh_20Percent() {
        assertFalse(strategy.shouldProactivelyRefresh(20));
    }

    @Test
    @DisplayName("判断是否应该主动刷新 - 剩余50%")
    void testShouldProactivelyRefresh_50Percent() {
        assertFalse(strategy.shouldProactivelyRefresh(50));
    }

    @Test
    @DisplayName("判断是否应该主动刷新 - 边界值")
    void testShouldProactivelyRefresh_Boundaries() {
        assertTrue(strategy.shouldProactivelyRefresh(0));
        assertTrue(strategy.shouldProactivelyRefresh(1));
        assertFalse(strategy.shouldProactivelyRefresh(100));
    }

    @Test
    @DisplayName("获取Token TTL配置 - 普通用户")
    void testGetTokenTtlConfig_RegularUser() {
        Set<String> roles = Set.of("USER");

        StepVerifier.create(strategy.getTokenTtlConfig("user-1", roles, "tenant-1", false))
                .assertNext(config -> {
                    assertEquals(Duration.ofHours(2), config.getAccessTokenTtl());
                    assertEquals(Duration.ofDays(7), config.getRefreshTokenTtl());
                    assertFalse(config.isDowngraded());
                    assertEquals("普通用户", config.getReason());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Token TTL配置 - 超级管理员")
    void testGetTokenTtlConfig_SuperAdmin() {
        Set<String> roles = Set.of("SUPER_ADMIN");

        StepVerifier.create(strategy.getTokenTtlConfig("user-2", roles, "tenant-1", false))
                .assertNext(config -> {
                    assertEquals(Duration.ofMinutes(30), config.getAccessTokenTtl());
                    assertEquals(Duration.ofDays(1), config.getRefreshTokenTtl());
                    assertFalse(config.isDowngraded());
                    assertEquals("超级管理员（最短TTL）", config.getReason());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Token TTL配置 - 只读用户")
    void testGetTokenTtlConfig_ReadonlyUser() {
        Set<String> roles = Set.of("READONLY");

        StepVerifier.create(strategy.getTokenTtlConfig("user-3", roles, "tenant-1", false))
                .assertNext(config -> {
                    assertEquals(Duration.ofHours(4), config.getAccessTokenTtl());
                    assertEquals(Duration.ofDays(30), config.getRefreshTokenTtl());
                    assertFalse(config.isDowngraded());
                    assertEquals("只读用户（最长TTL）", config.getReason());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Token TTL配置 - 安全敏感操作后")
    void testGetTokenTtlConfig_SecuritySensitive() {
        Set<String> roles = Set.of("USER");

        StepVerifier.create(strategy.getTokenTtlConfig("user-4", roles, "tenant-1", true))
                .assertNext(config -> {
                    assertEquals(Duration.ofMinutes(15), config.getAccessTokenTtl());
                    assertEquals(Duration.ofDays(7), config.getRefreshTokenTtl());
                    assertTrue(config.isDowngraded());
                    assertEquals("安全敏感操作后降级", config.getReason());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("获取Token TTL配置 - 财务角色")
    void testGetTokenTtlConfig_FinanceRole() {
        Set<String> roles = Set.of("FINANCE_MANAGER");

        StepVerifier.create(strategy.getTokenTtlConfig("user-5", roles, "tenant-1", false))
                .assertNext(config -> {
                    assertEquals(Duration.ofHours(1), config.getAccessTokenTtl());
                    assertEquals(Duration.ofDays(7), config.getRefreshTokenTtl());
                    assertFalse(config.isDowngraded());
                    assertEquals("敏感角色（财务/HR）", config.getReason());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("TokenTtlConfig - getAccessTokenTtlSeconds")
    void testTokenTtlConfig_GetAccessTokenTtlSeconds() {
        TokenTtlStrategy.TokenTtlConfig config =
                new TokenTtlStrategy.TokenTtlConfig(Duration.ofHours(2), Duration.ofDays(7), false, "test");

        assertEquals(7200, config.getAccessTokenTtlSeconds());
    }

    @Test
    @DisplayName("TokenTtlConfig - getRefreshTokenTtlSeconds")
    void testTokenTtlConfig_GetRefreshTokenTtlSeconds() {
        TokenTtlStrategy.TokenTtlConfig config =
                new TokenTtlStrategy.TokenTtlConfig(Duration.ofHours(2), Duration.ofDays(7), false, "test");

        assertEquals(604800, config.getRefreshTokenTtlSeconds());
    }

    @Test
    @DisplayName("TokenTtlConfig - toString")
    void testTokenTtlConfig_ToString() {
        TokenTtlStrategy.TokenTtlConfig config =
                new TokenTtlStrategy.TokenTtlConfig(Duration.ofHours(2), Duration.ofDays(7), true, "test reason");

        String str = config.toString();

        assertTrue(str.contains("TokenTtlConfig"));
        assertTrue(str.contains("PT2H"));
        assertTrue(str.contains("PT168H"));
        assertTrue(str.contains("downgraded=true"));
        assertTrue(str.contains("test reason"));
    }
}
