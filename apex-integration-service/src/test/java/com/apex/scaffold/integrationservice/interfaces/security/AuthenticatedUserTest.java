package com.apex.scaffold.integrationservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthenticatedUserTest {
    @Test
    void threeArgumentConstructorLeavesDepartmentUnset() {
        var user = new AuthenticatedUser(7L, "open-id", "tenant-42");

        assertThat(user.deptId()).isNull();
    }

    @Test
    void nameUsesUserIdAndFallsBackToOpenId() {
        assertThat(new AuthenticatedUser(7L, "open-id", "tenant-42").getName()).isEqualTo("7");
        assertThat(new AuthenticatedUser(null, "open-id", "tenant-42").getName())
                .isEqualTo("open-id");
    }

    @Test
    void stringRepresentationOmitsDepartmentId() {
        var user = new AuthenticatedUser(7L, "open-id", "tenant-42", 9L);

        assertThat(user.toString())
                .isEqualTo("AuthenticatedUser[userId=7, openId=open-id, tenantId=tenant-42]")
                .doesNotContain("deptId");
    }
}
