package com.apex.scaffold.fileservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AuthenticatedUser} 单元测试.
 *
 * <p>覆盖网关透传用户信息的本地实现：compact 构造器对部门 ID 的缺省处理、
 * {@link AuthenticatedUser#getName()} 在用户 ID 存在与缺失时的分支，以及字符串表示，
 * 确保作为 Spring Security 认证主体时的行为符合预期。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@DisplayName("AuthenticatedUser 单元测试")
class AuthenticatedUserTest {

    @Test
    @DisplayName("三参数构造器 - 部门 ID 缺省为 null")
    void compactConstructorDefaultsDeptId() {
        AuthenticatedUser user = new AuthenticatedUser(100L, "openid-1", "default");

        assertThat(user.userId()).isEqualTo(100L);
        assertThat(user.openId()).isEqualTo("openid-1");
        assertThat(user.tenantId()).isEqualTo("default");
        assertThat(user.deptId()).isNull();
    }

    @Test
    @DisplayName("getName - 用户 ID 存在时返回其字符串形式")
    void getNameReturnsUserIdWhenPresent() {
        AuthenticatedUser user = new AuthenticatedUser(100L, "openid-1", "default", 9L);

        assertThat(user.getName()).isEqualTo("100");
    }

    @Test
    @DisplayName("getName - 用户 ID 缺失时回退为 OpenID")
    void getNameFallsBackToOpenIdWhenUserIdAbsent() {
        AuthenticatedUser user = new AuthenticatedUser(null, "openid-1", "default", 9L);

        assertThat(user.getName()).isEqualTo("openid-1");
    }

    @Test
    @DisplayName("toString - 不包含部门 ID")
    void toStringOmitsDeptId() {
        AuthenticatedUser user = new AuthenticatedUser(100L, "openid-1", "default", 9L);

        assertThat(user.toString()).isEqualTo("AuthenticatedUser[userId=100, openId=openid-1, tenantId=default]");
    }
}
