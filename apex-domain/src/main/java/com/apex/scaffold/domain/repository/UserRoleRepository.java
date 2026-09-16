package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.UserRoleEntity;
import java.util.List;
import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * 用户角色关联仓储接口
 *
 * <p>定义用户与角色关联关系的持久化操作方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface UserRoleRepository {
    /**
     * 根据用户ID查询用户角色关联列表
     *
     * @param userId 用户ID
     * @return 用户角色关联列表
     */
    Mono<List<UserRoleEntity>> findByUserId(Long userId);

    /**
     * 根据双字段条件删除关联关系
     *
     * @param field1 字段1
     * @param val1 值1
     * @param field2 字段2
     * @param val2 值2
     * @return 影响行数
     */
    Mono<Integer> delete(String field1, Object val1, String field2, Object val2);

    /**
     * 根据角色ID删除关联关系
     *
     * @param roleId 角色ID
     * @return 影响行数
     */
    Mono<Integer> deleteByRoleId(Long roleId);

    /**
     * 插入用户角色关联
     *
     * @param userRole 关联信息
     * @return 影响行数
     */
    Mono<Integer> insert(UserRoleEntity userRole);

    /**
     * 在单个数据库事务中把用户的后台角色替换为目标集合。
     *
     * @param userId 用户ID
     * @param roleIds 角色ID集合
     * @return 替换完成信号
     */
    Mono<Void> replaceRoles(Long userId, Set<Long> roleIds);
}
