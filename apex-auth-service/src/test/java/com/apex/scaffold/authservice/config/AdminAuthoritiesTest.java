package com.apex.scaffold.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.auth.domain.model.entity.RoleEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AdminAuthoritiesTest {

    @Test
    void mapsAssignedRoleCodesToGrantedAuthorities() {
        List<RoleEntity> roles = List.of(RoleEntity.builder().code("ROLE_ADMIN").build());

        assertThat(AuthCenterConfig.toAuthorities(roles)).containsExactly(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
