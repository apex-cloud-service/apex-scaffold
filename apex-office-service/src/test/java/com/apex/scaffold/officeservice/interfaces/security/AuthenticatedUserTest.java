package com.apex.scaffold.officeservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AuthenticatedUser 单元测试")
class AuthenticatedUserTest {

    @Nested
    @DisplayName("getName")
    class GetNameTest {

        @Test
        @DisplayName("userId 存在时返回 userId 字符串")
        void withUserId() {
            AuthenticatedUser user = new AuthenticatedUser(100L, "openid-1", "tenant-1");
            assertThat(user.getName()).isEqualTo("100");
        }

        @Test
        @DisplayName("userId 为空时返回 openId")
        void withoutUserId() {
            AuthenticatedUser user = new AuthenticatedUser(null, "openid-1", "tenant-1");
            assertThat(user.getName()).isEqualTo("openid-1");
        }
    }

    @Nested
    @DisplayName("构造器")
    class ConstructorTest {

        @Test
        @DisplayName("三参数构造器部门 ID 为 null")
        void threeArgCtor() {
            AuthenticatedUser user = new AuthenticatedUser(1L, "openid", "tenant");
            assertThat(user.deptId()).isNull();
            assertThat(user.userId()).isEqualTo(1L);
            assertThat(user.openId()).isEqualTo("openid");
            assertThat(user.tenantId()).isEqualTo("tenant");
        }

        @Test
        @DisplayName("四参数构造器保留部门 ID")
        void fourArgCtor() {
            AuthenticatedUser user = new AuthenticatedUser(1L, "openid", "tenant", 99L);
            assertThat(user.deptId()).isEqualTo(99L);
        }
    }

    @Test
    @DisplayName("toString 只包含 userId/openId/tenantId")
    void toStringFormat() {
        AuthenticatedUser user = new AuthenticatedUser(1L, "openid", "tenant", 99L);
        assertThat(user.toString()).isEqualTo("AuthenticatedUser[userId=1, openId=openid, tenantId=tenant]");
    }

    @Test
    @DisplayName("作为 Principal 的 equals/hashCode 基于字段")
    void equalsAndHashCode() {
        AuthenticatedUser a = new AuthenticatedUser(1L, "openid", "tenant", 99L);
        AuthenticatedUser b = new AuthenticatedUser(1L, "openid", "tenant", 99L);
        AuthenticatedUser c = new AuthenticatedUser(2L, "openid", "tenant", 99L);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }
}
