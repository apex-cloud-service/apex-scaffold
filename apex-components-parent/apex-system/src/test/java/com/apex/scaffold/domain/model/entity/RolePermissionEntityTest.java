package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RolePermissionEntity} 的单元测试类
 *
 * <p>测试角色权限关联实体的 getter/setter 方法以及继承自 BaseEntity 的行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class RolePermissionEntityTest extends BaseEntityTest<RolePermissionEntity> {

    /** {@inheritDoc} */
    @Override
    protected RolePermissionEntity createEntity() {
        return new RolePermissionEntity();
    }

    @Nested
    @DisplayName("setter 和 getter 方法")
    class SetterGetterTest {

        /**
         * 设置{@code tersShouldUpdateFieldsAndGettersShouldReturnCorrectValues}。
         */
        @Test
        @DisplayName("使用 setter 设置属性后 getter 应正确返回")
        void settersShouldUpdateFieldsAndGettersShouldReturnCorrectValues() {
            RolePermissionEntity entity = new RolePermissionEntity();
            entity.setRoleId(100L);
            entity.setPermissionId(200L);

            assertThat(entity.getRoleId()).isEqualTo(100L);
            assertThat(entity.getPermissionId()).isEqualTo(200L);
        }

        @Test
        @DisplayName("新创建的实体字段应为 null")
        void newEntityShouldHaveNullFields() {
            RolePermissionEntity entity = new RolePermissionEntity();
            assertThat(entity.getRoleId()).isNull();
            assertThat(entity.getPermissionId()).isNull();
        }
    }

    @Nested
    @DisplayName("业务场景")
    class BusinessScenarioTest {
        @Test
        @DisplayName("角色关联多个权限")
        void shouldSupportRoleWithMultiplePermissions() {
            RolePermissionEntity entity1 = new RolePermissionEntity();
            entity1.setRoleId(1L);
            entity1.setPermissionId(10L);

            RolePermissionEntity entity2 = new RolePermissionEntity();
            entity2.setRoleId(1L);
            entity2.setPermissionId(20L);

            assertThat(entity1.getRoleId()).isEqualTo(entity2.getRoleId());
            assertThat(entity1.getPermissionId()).isNotEqualTo(entity2.getPermissionId());
        }
    }
}
