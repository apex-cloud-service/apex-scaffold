package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.RoleEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 角色仓储接口
 *
 * <p>仓储接口（领域层）
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface RoleRepository {
    /**
     * 根据用户ID查询角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    Mono<List<RoleEntity>> findByUserId(Long userId);

    /**
     * 查询角色的数据范围部门ID列表。
     *
     * @param roleIds 角色ID列表
     * @return 数据范围部门ID列表
     */
    default Mono<List<Long>> findDataScopeDeptIds(List<Long> roleIds) {
        return Mono.error(new IllegalStateException("Role data scope departments are not configured"));
    }

    /**
     * 根据ID查询角色
     *
     * @param id 角色ID
     * @return 角色信息
     */
    Mono<RoleEntity> findById(Long id);

    /**
     * 查询所有角色
     *
     * @return 角色列表
     */
    Mono<List<RoleEntity>> findAll();

    /**
     * 根据字段查询单个角色
     *
     * @param field 字段名
     * @param value 字段值
     * @return 角色信息
     */
    Mono<RoleEntity> findOne(String field, Object value);

    /**
     * 插入角色
     *
     * @param entity 角色信息
     * @return 影响行数
     */
    Mono<Integer> insert(RoleEntity entity);

    /**
     * 更新角色
     *
     * @param entity 角色信息
     * @return 影响行数
     */
    Mono<Integer> updateById(RoleEntity entity);

    /**
     * 删除角色
     *
     * @param id 角色ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
