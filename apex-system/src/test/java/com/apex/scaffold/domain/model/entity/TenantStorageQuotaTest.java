package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.domain.model.exception.TenantQuotaExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 存储配额领域断言测试（方案批次 B5/B6 补齐项）。
 *
 * <p>覆盖：字节→MB 向上取整换算、停用租户拒绝、NULL 配额不限制。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("租户存储配额领域断言测试")
class TenantStorageQuotaTest {

    private static final long MB = 1024L * 1024L;

    private TenantEntity activeTenant(Integer maxStorageMb) {
        TenantEntity entity = TenantEntity.create("default", "平台租户", "1", null);
        entity.setQuota(com.apex.scaffold.domain.model.valueobject.TenantQuota.of(null, maxStorageMb));
        return entity;
    }

    @Test
    @DisplayName("字节向上取整换算：1024MB 上限，已用 1023.5MB + 新增 0.6MB 超限")
    void ceilConversionEnforcesLimit() {
        TenantEntity tenant = activeTenant(1024);
        long used = 1023 * MB + 512 * 1024L; // 1023.5MB
        long increment = 629145; // 0.6MB
        // 向上取整：used=1024MB，increment=1MB → 1025 > 1024 超限
        assertThatThrownBy(() -> tenant.assertStorageQuota(used, increment))
                .isInstanceOf(TenantQuotaExceededException.class);
    }

    @Test
    @DisplayName("边界：恰好等于上限不超限")
    void boundaryExactLimitPasses() {
        TenantEntity tenant = activeTenant(1024);
        org.assertj.core.api.Assertions.assertThatCode(() -> tenant.assertStorageQuota(1023 * MB, MB))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("NULL 配额表示不限制")
    void nullQuotaUnlimited() {
        TenantEntity tenant = activeTenant(null);
        org.assertj.core.api.Assertions.assertThatCode(() -> tenant.assertStorageQuota(Long.MAX_VALUE / 2, MB))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("停用租户拒绝业务数据创建（生命周期优先于配额）")
    void disabledTenantRejects() {
        TenantEntity tenant = activeTenant(1024);
        tenant.disable();
        assertThatThrownBy(() -> tenant.assertStorageQuota(0, MB))
                .isInstanceOf(com.apex.scaffold.domain.model.exception.TenantLifecycleException.class);
    }
}
