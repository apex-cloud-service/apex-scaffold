package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.RolePermissionEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 角色权限关联仓储接口
 *
 * <p>角色权限关联领域的仓储接口，定义角色权限关联相关的持久化操作方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface RolePermissionRepository {
    /**
     * 根据角色ID查询角色权限关联列表
     *
     * @param roleId 角色ID
     * @return 角色权限关联列表
     */
    Mono<List<RolePermissionEntity>> findByRoleId(Long roleId);

    /**
     * 根据角色ID删除关联关系
     *
     * @param roleId 角色ID
     * @return 影响行数
     */
    Mono<Integer> deleteByRoleId(Long roleId);
}
