package com.apex.scaffold.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.model.entity.RoleEntity;
import com.apex.scaffold.infrastructure.persistence.converter.RolePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.RoleDeptMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.RoleMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.UserRoleMapper;
import com.apex.scaffold.infrastructure.persistence.po.RoleDeptPO;
import com.apex.scaffold.infrastructure.persistence.po.RolePO;
import com.apex.scaffold.infrastructure.persistence.po.UserRolePO;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleRepositoryImplTest {

    @Test
    void findByUserIdExcludesDisabledRoles() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RolePoConverter converter = mock(RolePoConverter.class);
        RoleDeptMapper roleDeptMapper = mock(RoleDeptMapper.class);
        RoleRepositoryImpl repository = new RoleRepositoryImpl(roleMapper, userRoleMapper, roleDeptMapper, converter);
        UserRolePO relation = new UserRolePO();
        relation.setUserId(1L);
        relation.setRoleId(2L);
        RolePO disabledRole = new RolePO();
        disabledRole.setId(2L);
        disabledRole.setStatus(0);
        RoleEntity disabledEntity = RoleEntity.create("管理员", "ADMIN", "test");
        disabledEntity.disable();

        when(userRoleMapper.selectList(any())).thenReturn(List.of(relation));
        when(roleMapper.selectList(any())).thenReturn(List.of(disabledRole));
        when(converter.toEntityList(List.of(disabledRole))).thenReturn(List.of(disabledEntity));

        List<RoleEntity> roles = repository.findByUserId(1L).block();

        assertThat(roles).isEmpty();
    }

    @Test
    void findsDistinctCustomDepartmentIdsByRoleIds() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleDeptMapper roleDeptMapper = mock(RoleDeptMapper.class);
        RolePoConverter converter = mock(RolePoConverter.class);
        RoleRepositoryImpl repository = new RoleRepositoryImpl(roleMapper, userRoleMapper, roleDeptMapper, converter);
        RoleDeptPO first = new RoleDeptPO();
        first.setRoleId(1L);
        first.setDeptId(10L);
        RoleDeptPO duplicate = new RoleDeptPO();
        duplicate.setRoleId(2L);
        duplicate.setDeptId(10L);
        RoleDeptPO second = new RoleDeptPO();
        second.setRoleId(2L);
        second.setDeptId(12L);
        when(roleDeptMapper.selectList(any())).thenReturn(List.of(first, duplicate, second));

        List<Long> departmentIds =
                repository.findDataScopeDeptIds(List.of(1L, 2L)).block();

        assertThat(departmentIds).containsExactly(10L, 12L);
    }

    @Test
    void returnsEmptyWithoutQueryingWhenRoleIdsAreEmpty() {
        RoleMapper roleMapper = mock(RoleMapper.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleDeptMapper roleDeptMapper = mock(RoleDeptMapper.class);
        RolePoConverter converter = mock(RolePoConverter.class);
        RoleRepositoryImpl repository = new RoleRepositoryImpl(roleMapper, userRoleMapper, roleDeptMapper, converter);

        List<Long> departmentIds = repository.findDataScopeDeptIds(List.of()).block();

        assertThat(departmentIds).isEmpty();
        verifyNoInteractions(roleDeptMapper);
    }
}
