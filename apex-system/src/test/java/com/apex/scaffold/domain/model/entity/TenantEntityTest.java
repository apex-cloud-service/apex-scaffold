package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.domain.model.exception.TenantLifecycleException;
import com.apex.scaffold.domain.model.exception.TenantQuotaExceededException;
import com.apex.scaffold.domain.model.valueobject.TenantQuota;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 租户聚合根实体生命周期与配额行为测试。
 *
 * <p>验证 TENANT-001 租户模型的核心业务规则：状态机迁移约束、
 * 终态保护、配额校验与业务写入门禁。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@DisplayName("TenantEntity 租户生命周期与配额测试")
class TenantEntityTest {

    private static final String TENANT_CODE = "T001";
    private static final String TENANT_NAME = "Acme Corp";
    private static final String ADMIN_ID = "admin-001";

    @Nested
    @DisplayName("创建租户")
    class CreateTests {

        @Test
        @DisplayName("新建租户默认为正常状态且可创建业务数据")
        void shouldCreateActiveTenant() {
            TenantEntity tenant = TenantEntity.create(TENANT_CODE, TENANT_NAME, ADMIN_ID, defaultQuota());

            assertThat(tenant.getTenantCode()).isEqualTo(TENANT_CODE);
            assertThat(tenant.getTenantName()).isEqualTo(TENANT_NAME);
            assertThat(tenant.getAdminUserId()).isEqualTo(ADMIN_ID);
            assertThat(tenant.getStatus()).isEqualTo(TenantEntity.TenantStatus.ACTIVE);
            assertThat(tenant.canCreateBusinessData()).isTrue();
            assertThat(tenant.isNew()).isTrue();
        }

        @Test
        @DisplayName("租户编码不能为空")
        void shouldRejectBlankCode() {
            assertThatThrownBy(() -> TenantEntity.create(" ", TENANT_NAME, ADMIN_ID, defaultQuota()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("停用与启用迁移")
    class DisableEnableTests {

        @Test
        @DisplayName("正常租户可以停用，停用后不能创建业务数据")
        void shouldDisableActiveTenant() {
            TenantEntity tenant = activeTenant();

            tenant.disable();

            assertThat(tenant.getStatus()).isEqualTo(TenantEntity.TenantStatus.DISABLED);
            assertThat(tenant.canCreateBusinessData()).isFalse();
        }

        @Test
        @DisplayName("已停用租户可以重新启用")
        void shouldEnableDisabledTenant() {
            TenantEntity tenant = activeTenant();
            tenant.disable();

            tenant.enable();

            assertThat(tenant.getStatus()).isEqualTo(TenantEntity.TenantStatus.ACTIVE);
            assertThat(tenant.canCreateBusinessData()).isTrue();
        }

        @Test
        @DisplayName("已停用租户再次停用应拒绝")
        void shouldRejectDisablingDisabledTenant() {
            TenantEntity tenant = activeTenant();
            tenant.disable();

            assertThatThrownBy(tenant::disable).isInstanceOf(TenantLifecycleException.class);
        }
    }

    @Nested
    @DisplayName("冻结与解冻迁移")
    class FreezeUnfreezeTests {

        @Test
        @DisplayName("正常租户可以冻结，冻结后不能创建业务数据")
        void shouldFreezeActiveTenant() {
            TenantEntity tenant = activeTenant();

            tenant.freeze();

            assertThat(tenant.getStatus()).isEqualTo(TenantEntity.TenantStatus.FROZEN);
            assertThat(tenant.canCreateBusinessData()).isFalse();
        }

        @Test
        @DisplayName("已冻结租户可以解冻恢复为正常")
        void shouldUnfreezeFrozenTenant() {
            TenantEntity tenant = activeTenant();
            tenant.freeze();

            tenant.unfreeze();

            assertThat(tenant.getStatus()).isEqualTo(TenantEntity.TenantStatus.ACTIVE);
        }

        @Test
        @DisplayName("已停用租户不能直接冻结")
        void shouldRejectFreezingDisabledTenant() {
            TenantEntity tenant = activeTenant();
            tenant.disable();

            assertThatThrownBy(tenant::freeze).isInstanceOf(TenantLifecycleException.class);
        }
    }

    @Nested
    @DisplayName("注销与归档迁移")
    class DeactivateArchiveTests {

        @Test
        @DisplayName("正常租户可以注销")
        void shouldDeactivateActiveTenant() {
            TenantEntity tenant = activeTenant();

            tenant.deactivate();

            assertThat(tenant.getStatus()).isEqualTo(TenantEntity.TenantStatus.DEACTIVATED);
            assertThat(tenant.canCreateBusinessData()).isFalse();
        }

        @Test
        @DisplayName("已停用租户可以注销")
        void shouldDeactivateDisabledTenant() {
            TenantEntity tenant = activeTenant();
            tenant.disable();

            tenant.deactivate();

            assertThat(tenant.getStatus()).isEqualTo(TenantEntity.TenantStatus.DEACTIVATED);
        }

        @Test
        @DisplayName("已注销租户可以归档")
        void shouldArchiveDeactivatedTenant() {
            TenantEntity tenant = activeTenant();
            tenant.deactivate();

            tenant.archive();

            assertThat(tenant.getStatus()).isEqualTo(TenantEntity.TenantStatus.ARCHIVED);
        }

        @Test
        @DisplayName("归档为终态，任何迁移均被拒绝")
        void shouldRejectAnyMigrationFromArchived() {
            TenantEntity tenant = archivedTenant();

            assertThatThrownBy(tenant::enable).isInstanceOf(TenantLifecycleException.class);
            assertThatThrownBy(tenant::disable).isInstanceOf(TenantLifecycleException.class);
            assertThatThrownBy(tenant::freeze).isInstanceOf(TenantLifecycleException.class);
            assertThatThrownBy(tenant::deactivate).isInstanceOf(TenantLifecycleException.class);
            assertThatThrownBy(tenant::archive).isInstanceOf(TenantLifecycleException.class);
        }

        @Test
        @DisplayName("未注销的租户不能直接归档")
        void shouldRejectArchivingActiveTenant() {
            TenantEntity tenant = activeTenant();

            assertThatThrownBy(tenant::archive).isInstanceOf(TenantLifecycleException.class);
        }
    }

    @Nested
    @DisplayName("配额校验")
    class QuotaTests {

        @Test
        @DisplayName("配额内用户增长应通过")
        void shouldAllowUsersWithinQuota() {
            TenantEntity tenant = activeTenant();

            tenant.assertUserQuota(50, 1);

            assertThat(tenant.getQuota().getMaxUsers()).isEqualTo(100);
        }

        @Test
        @DisplayName("超出用户配额应抛出配额异常")
        void shouldRejectUsersExceedingQuota() {
            TenantEntity tenant = activeTenant();

            assertThatThrownBy(() -> tenant.assertUserQuota(100, 1)).isInstanceOf(TenantQuotaExceededException.class);
        }

        @Test
        @DisplayName("停用租户不允许任何配额增长")
        void shouldRejectQuotaGrowthForDisabledTenant() {
            TenantEntity tenant = activeTenant();
            tenant.disable();

            assertThatThrownBy(() -> tenant.assertUserQuota(10, 1)).isInstanceOf(TenantLifecycleException.class);
        }
    }

    private TenantEntity activeTenant() {
        return TenantEntity.create(TENANT_CODE, TENANT_NAME, ADMIN_ID, defaultQuota());
    }

    private TenantEntity archivedTenant() {
        TenantEntity tenant = activeTenant();
        tenant.deactivate();
        tenant.archive();
        return tenant;
    }

    private TenantQuota defaultQuota() {
        return TenantQuota.of(100, 10240);
    }
}
