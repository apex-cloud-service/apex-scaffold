package com.apex.scaffold.infrastructure.cache.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link TenantCacheKeys} 租户化缓存键助手测试（方案批次 B9）。
 *
 * <p>覆盖：租户键构造、上下文缺失 fail-closed、显式租户键、平台键、键合规校验。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("TenantCacheKeys 租户化缓存键测试")
class TenantCacheKeysTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("当前租户键：prefix:tenant:<code>:segments")
    void buildsTenantScopedKey() {
        TenantContext.setTenantId("tenant-a");
        assertThat(TenantCacheKeys.of("auth:permissions", 42L)).isEqualTo("auth:permissions:tenant:tenant-a:42");
        assertThat(TenantCacheKeys.of("file:used-bytes")).isEqualTo("file:used-bytes:tenant:tenant-a");
    }

    @Test
    @DisplayName("上下文缺失 fail-closed，不生成无租户维度的键")
    void failsClosedWithoutTenantContext() {
        TenantContext.clear();
        assertThatThrownBy(() -> TenantCacheKeys.of("auth:permissions", 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("租户上下文未装配");
    }

    @Test
    @DisplayName("显式租户键用于消息消费侧还原；空租户拒绝")
    void buildsExplicitTenantKey() {
        assertThat(TenantCacheKeys.forTenant("auth:permissions", "tenant-b", 7L))
                .isEqualTo("auth:permissions:tenant:tenant-b:7");
        assertThatThrownBy(() -> TenantCacheKeys.forTenant("auth:permissions", " ", 7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("平台键显式声明 platform 段")
    void buildsPlatformKey() {
        assertThat(TenantCacheKeys.platform("sys:tenants", "catalog")).isEqualTo("sys:tenants:platform:catalog");
    }

    @Test
    @DisplayName("键合规校验：需含 tenant 或 platform 段")
    void validatesScoping() {
        assertThat(TenantCacheKeys.isTenantScoped("auth:permissions:tenant:tenant-a:1"))
                .isTrue();
        assertThat(TenantCacheKeys.isTenantScoped("sys:tenants:platform:catalog"))
                .isTrue();
        assertThat(TenantCacheKeys.isTenantScoped("auth:permissions:1")).isFalse();
        assertThat(TenantCacheKeys.isTenantScoped("")).isFalse();
        assertThat(TenantCacheKeys.isTenantScoped(null)).isFalse();
    }
}
