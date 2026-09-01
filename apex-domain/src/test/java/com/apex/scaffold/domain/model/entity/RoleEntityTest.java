package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link RoleEntity} 的单元测试类
 *
 * <p>测试角色的创建、有效性判断、信息更新、启用/禁用等核心领域行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class RoleEntityTest {

    @Nested
    @DisplayName("静态工厂方法 create()")
    class CreateTest {
        @Test
        @DisplayName("使用有效参数应创建启用角色并设置正确初始状态")
        void withValidParamsShouldCreateEnabledRole() {
            String name = "管理员";
            String code = "ADMIN";
            String description = "系统管理员角色";

            RoleEntity role = RoleEntity.create(name, code, description);

            assertThat(role.getName()).isEqualTo(name);
            assertThat(role.getCode()).isEqualTo(code);
            assertThat(role.getDescription()).isEqualTo(description);
            assertThat(role.getStatus()).isEqualTo(1);
            assertThat(role.isEnabled()).isTrue();
            assertThat(role.getCreateTime()).isNotNull();
            assertThat(role.getUpdateTime()).isNotNull();
            assertThat(role.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("创建时间应与更新时间一致")
        void createTimeShouldEqualUpdateTime() {
            RoleEntity role = RoleEntity.create("用户", "USER", "普通用户角色");
            assertThat(role.getCreateTime()).isEqualTo(role.getUpdateTime());
        }
    }

    @Nested
    @DisplayName("有效性判断 isEnabled()")
    class IsEnabledTest {
        @Test
        @DisplayName("新创建的角色应处于启用状态")
        void newlyCreatedRoleShouldBeEnabled() {
            RoleEntity role = RoleEntity.create("管理员", "ADMIN", "描述");
            assertThat(role.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("禁用后应返回 false")
        void afterDisableShouldBeDisabled() {
            RoleEntity role = RoleEntity.create("管理员", "ADMIN", "描述");
            role.disable();
            assertThat(role.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("启用后应返回 true")
        void afterEnableShouldBeEnabled() {
            RoleEntity role = RoleEntity.create("管理员", "ADMIN", "描述");
            role.disable();
            role.enable();
            assertThat(role.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("status 为 null 时应返回 false")
        void nullStatusShouldReturnFalse() {
            RoleEntity role = new RoleEntity();
            assertThat(role.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("信息更新 updateInfo()")
    class UpdateInfoTest {
        @Test
        @DisplayName("应正确更新名称和描述")
        void shouldUpdateNameAndDescription() {
            RoleEntity role = RoleEntity.create("旧名称", "ROLE", "旧描述");
            role.updateInfo("新名称", "新描述");

            assertThat(role.getName()).isEqualTo("新名称");
            assertThat(role.getDescription()).isEqualTo("新描述");
            assertThat(role.getCode()).isEqualTo("ROLE");
        }

        @Test
        @DisplayName("应刷新更新时间")
        void shouldTouchUpdateTime() {
            RoleEntity role = RoleEntity.create("管理员", "ADMIN", "描述");
            var originalUpdateTime = role.getUpdateTime();
            role.updateInfo("新管理员", "新描述");
            assertThat(role.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }

        @Test
        @DisplayName("不应影响角色状态")
        void shouldNotAffectStatus() {
            RoleEntity role = RoleEntity.create("管理员", "ADMIN", "描述");
            role.updateInfo("新名称", "新描述");
            assertThat(role.isEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("启用/禁用 enable() / disable()")
    class EnableDisableTest {
        @Test
        @DisplayName("disable() 应将状态设为 0")
        void disableShouldSetStatusToZero() {
            RoleEntity role = RoleEntity.create("管理员", "ADMIN", "描述");
            role.disable();
            assertThat(role.getStatus()).isEqualTo(0);
            assertThat(role.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("enable() 应将状态设为 1")
        void enableShouldSetStatusToOne() {
            RoleEntity role = RoleEntity.create("管理员", "ADMIN", "描述");
            role.disable();
            role.enable();
            assertThat(role.getStatus()).isEqualTo(1);
            assertThat(role.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("disable() 应刷新更新时间")
        void disableShouldTouchUpdateTime() {
            RoleEntity role = RoleEntity.create("管理员", "ADMIN", "描述");
            var originalUpdateTime = role.getUpdateTime();
            role.disable();
            assertThat(role.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }

        @Test
        @DisplayName("enable() 应刷新更新时间")
        void enableShouldTouchUpdateTime() {
            RoleEntity role = RoleEntity.create("管理员", "ADMIN", "描述");
            role.disable();
            var originalUpdateTime = role.getUpdateTime();
            role.enable();
            assertThat(role.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }

        @Test
        @DisplayName("重复禁用应保持禁用状态")
        void repeatedDisableShouldStayDisabled() {
            RoleEntity role = RoleEntity.create("管理员", "ADMIN", "描述");
            role.disable();
            role.disable();
            assertThat(role.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("重复启用应保持启用状态")
        void repeatedEnableShouldStayEnabled() {
            RoleEntity role = RoleEntity.create("管理员", "ADMIN", "描述");
            role.enable();
            role.enable();
            assertThat(role.isEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("setter 方法")
    class SetterTest {

        /**
         * 设置{@code tersShouldUpdateFields}。
         */
        @Test
        @DisplayName("使用 setter 设置属性应正确更新")
        void settersShouldUpdateFields() {
            RoleEntity role = new RoleEntity();
            role.setName("超级管理员");
            role.setCode("SUPER_ADMIN");
            role.setDescription("超级管理员角色");
            role.setStatus(1);

            assertThat(role.getName()).isEqualTo("超级管理员");
            assertThat(role.getCode()).isEqualTo("SUPER_ADMIN");
            assertThat(role.getDescription()).isEqualTo("超级管理员角色");
            assertThat(role.getStatus()).isEqualTo(1);
        }
    }
}
