package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link UserRoleEntity} 的单元测试类
 *
 * <p>测试用户角色关联实体的 getter/setter 方法以及继承自 BaseEntity 的行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class UserRoleEntityTest extends BaseEntityTest<UserRoleEntity> {

    /** {@inheritDoc} */
    @Override
    protected UserRoleEntity createEntity() {
        return new UserRoleEntity();
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
            UserRoleEntity entity = new UserRoleEntity();
            entity.setUserId(100L);
            entity.setRoleId(200L);

            assertThat(entity.getUserId()).isEqualTo(100L);
            assertThat(entity.getRoleId()).isEqualTo(200L);
        }

        @Test
        @DisplayName("新创建的实体字段应为 null")
        void newEntityShouldHaveNullFields() {
            UserRoleEntity entity = new UserRoleEntity();
            assertThat(entity.getUserId()).isNull();
            assertThat(entity.getRoleId()).isNull();
        }
    }

    @Nested
    @DisplayName("业务场景")
    class BusinessScenarioTest {
        @Test
        @DisplayName("用户关联多个角色")
        void shouldSupportUserWithMultipleRoles() {
            UserRoleEntity entity1 = new UserRoleEntity();
            entity1.setUserId(1L);
            entity1.setRoleId(10L);

            UserRoleEntity entity2 = new UserRoleEntity();
            entity2.setUserId(1L);
            entity2.setRoleId(20L);

            assertThat(entity1.getUserId()).isEqualTo(entity2.getUserId());
            assertThat(entity1.getRoleId()).isNotEqualTo(entity2.getRoleId());
        }

        @Test
        @DisplayName("不同用户关联同一角色")
        void shouldSupportDifferentUsersWithSameRole() {
            UserRoleEntity entity1 = new UserRoleEntity();
            entity1.setUserId(1L);
            entity1.setRoleId(100L);

            UserRoleEntity entity2 = new UserRoleEntity();
            entity2.setUserId(2L);
            entity2.setRoleId(100L);

            assertThat(entity1.getUserId()).isNotEqualTo(entity2.getUserId());
            assertThat(entity1.getRoleId()).isEqualTo(entity2.getRoleId());
        }
    }
}
