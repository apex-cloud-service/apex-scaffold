package com.apex.scaffold.infrastructure.persistence.service;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.domain.gateway.RoleDeletionGateway;
import com.apex.scaffold.infrastructure.persistence.mapper.RoleMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.RolePermissionMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.UserRoleMapper;
import com.apex.scaffold.infrastructure.persistence.po.RolePermissionPO;
import com.apex.scaffold.infrastructure.persistence.po.UserRolePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色聚合删除事务实现。
 *
 * <p>该服务只执行同步 MyBatis 调用，因此 Spring JDBC 事务会覆盖全部 SQL。任何关联表
 * 或角色表删除失败都会触发整体回滚。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class RoleDeletionService implements RoleDeletionGateway {

    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final RoleMapper roleMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRole(Long roleId) {
        userRoleMapper.delete(ConditionUtil.lambda(UserRolePO.class).eqIfPresent(UserRolePO::getRoleId, roleId));
        rolePermissionMapper.delete(
                ConditionUtil.lambda(RolePermissionPO.class).eqIfPresent(RolePermissionPO::getRoleId, roleId));
        return roleMapper.deleteById(roleId) > 0;
    }
}
