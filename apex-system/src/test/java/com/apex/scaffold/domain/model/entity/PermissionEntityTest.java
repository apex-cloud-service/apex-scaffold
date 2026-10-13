package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link PermissionEntity} 的单元测试类
 *
 * <p>测试权限的创建、有效性判断、信息更新、启用/禁用等核心领域行为
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class PermissionEntityTest {

    @Nested
    @DisplayName("静态工厂方法 create()")
    class CreateTest {
        @Test
        @DisplayName("使用有效参数应创建启用权限并设置正确初始状态")
        void withValidParamsShouldCreateEnabledPermission() {
            String name = "读取用户";
            String code = "user:read";
            String description = "读取用户信息权限";
            Integer type = PermissionEntity.PermissionType.API.getCode();

            PermissionEntity permission = PermissionEntity.create(name, code, description, type);

            assertThat(permission.getName()).isEqualTo(name);
            assertThat(permission.getCode()).isEqualTo(code);
            assertThat(permission.getDescription()).isEqualTo(description);
            assertThat(permission.getType()).isEqualTo(type);
            assertThat(permission.getStatus()).isEqualTo(1);
            assertThat(permission.isEnabled()).isTrue();
            assertThat(permission.getCreateTime()).isNotNull();
            assertThat(permission.getUpdateTime()).isNotNull();
            assertThat(permission.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("创建时间应与更新时间一致")
        void createTimeShouldEqualUpdateTime() {
            PermissionEntity permission =
                    PermissionEntity.create("权限", "perm:code", "描述", PermissionEntity.PermissionType.MENU.getCode());
            assertThat(permission.getCreateTime()).isEqualTo(permission.getUpdateTime());
        }
    }

    @Nested
    @DisplayName("有效性判断 isEnabled()")
    class IsEnabledTest {
        @Test
        @DisplayName("新创建的权限应处于启用状态")
        void newlyCreatedPermissionShouldBeEnabled() {
            PermissionEntity permission =
                    PermissionEntity.create("权限", "perm:read", "描述", PermissionEntity.PermissionType.API.getCode());
            assertThat(permission.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("禁用后应返回 false")
        void afterDisableShouldBeDisabled() {
            PermissionEntity permission = PermissionEntity.create("权限", "perm:read", "描述", 1);
            permission.disable();
            assertThat(permission.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("启用后应返回 true")
        void afterEnableShouldBeEnabled() {
            PermissionEntity permission = PermissionEntity.create("权限", "perm:read", "描述", 1);
            permission.disable();
            permission.enable();
            assertThat(permission.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("status 为 null 时应返回 false")
        void nullStatusShouldReturnFalse() {
            PermissionEntity permission = new PermissionEntity();
            assertThat(permission.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("信息更新 updateInfo()")
    class UpdateInfoTest {
        @Test
        @DisplayName("应正确更新名称、描述和状态")
        void shouldUpdateNameDescriptionAndStatus() {
            PermissionEntity permission = PermissionEntity.create("旧权限", "old:perm", "旧描述", 1);
            permission.updateInfo("新权限", "新描述", 0);

            assertThat(permission.getName()).isEqualTo("新权限");
            assertThat(permission.getDescription()).isEqualTo("新描述");
            assertThat(permission.getStatus()).isEqualTo(0);
            assertThat(permission.getCode()).isEqualTo("old:perm");
        }

        @Test
        @DisplayName("应刷新更新时间")
        void shouldTouchUpdateTime() {
            PermissionEntity permission = PermissionEntity.create("旧权限", "old:perm", "旧描述", 1);
            var originalUpdateTime = permission.getUpdateTime();
            permission.updateInfo("新权限", "新描述", 0);
            assertThat(permission.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }
    }

    @Nested
    @DisplayName("启用/禁用 enable() / disable()")
    class EnableDisableTest {
        @Test
        @DisplayName("disable() 应将状态设为 0")
        void disableShouldSetStatusToZero() {
            PermissionEntity permission = PermissionEntity.create("权限", "perm:code", "描述", 1);
            permission.disable();
            assertThat(permission.getStatus()).isEqualTo(0);
            assertThat(permission.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("enable() 应将状态设为 1")
        void enableShouldSetStatusToOne() {
            PermissionEntity permission = PermissionEntity.create("权限", "perm:code", "描述", 1);
            permission.disable();
            permission.enable();
            assertThat(permission.getStatus()).isEqualTo(1);
            assertThat(permission.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("disable() 应刷新更新时间")
        void disableShouldTouchUpdateTime() {
            PermissionEntity permission = PermissionEntity.create("权限", "perm:code", "描述", 1);
            var originalUpdateTime = permission.getUpdateTime();
            permission.disable();
            assertThat(permission.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }

        @Test
        @DisplayName("enable() 应刷新更新时间")
        void enableShouldTouchUpdateTime() {
            PermissionEntity permission = PermissionEntity.create("权限", "perm:code", "描述", 1);
            permission.disable();
            var originalUpdateTime = permission.getUpdateTime();
            permission.enable();
            assertThat(permission.getUpdateTime()).isAfterOrEqualTo(originalUpdateTime);
        }

        @Test
        @DisplayName("重复禁用应保持禁用状态")
        void repeatedDisableShouldStayDisabled() {
            PermissionEntity permission = PermissionEntity.create("权限", "perm:code", "描述", 1);
            permission.disable();
            permission.disable();
            assertThat(permission.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("重复启用应保持启用状态")
        void repeatedEnableShouldStayEnabled() {
            PermissionEntity permission = PermissionEntity.create("权限", "perm:code", "描述", 1);
            permission.enable();
            permission.enable();
            assertThat(permission.isEnabled()).isTrue();
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
            PermissionEntity permission = new PermissionEntity();
            permission.setName("写入用户");
            permission.setCode("user:write");
            permission.setDescription("写入用户信息权限");
            permission.setType(PermissionEntity.PermissionType.BUTTON.getCode());
            permission.setStatus(1);

            assertThat(permission.getName()).isEqualTo("写入用户");
            assertThat(permission.getCode()).isEqualTo("user:write");
            assertThat(permission.getDescription()).isEqualTo("写入用户信息权限");
            assertThat(permission.getType()).isEqualTo(2);
            assertThat(permission.getStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("PermissionType 枚举")
    class PermissionTypeEnumTest {
        @Test
        @DisplayName("MENU 的 code 应为 1")
        void menuCodeShouldBeOne() {
            assertThat(PermissionEntity.PermissionType.MENU.getCode()).isEqualTo(1);
        }

        @Test
        @DisplayName("BUTTON 的 code 应为 2")
        void buttonCodeShouldBeTwo() {
            assertThat(PermissionEntity.PermissionType.BUTTON.getCode()).isEqualTo(2);
        }

        @Test
        @DisplayName("API 的 code 应为 3")
        void apiCodeShouldBeThree() {
            assertThat(PermissionEntity.PermissionType.API.getCode()).isEqualTo(3);
        }
    }
}
