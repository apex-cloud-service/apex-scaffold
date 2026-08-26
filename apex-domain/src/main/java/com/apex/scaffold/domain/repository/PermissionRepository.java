package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.PermissionEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 权限仓储接口
 *
 * <p>权限领域的仓储接口，定义权限相关的持久化操作方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface PermissionRepository {
    /**
     * 根据用户ID查询权限列表
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    Mono<List<PermissionEntity>> findByUserId(Long userId);

    /**
     * 查询所有权限
     *
     * @return 权限列表
     */
    Mono<List<PermissionEntity>> findAll();

    /**
     * 根据ID查询权限
     *
     * @param id 权限ID
     * @return 权限信息
     */
    Mono<PermissionEntity> findById(Long id);

    /**
     * 根据字段查询单个权限
     *
     * @param field 字段名
     * @param value 字段值
     * @return 权限信息
     */
    Mono<PermissionEntity> findOne(String field, Object value);

    /**
     * 插入权限
     *
     * @param entity 权限信息
     * @return 影响行数
     */
    Mono<Integer> insert(PermissionEntity entity);

    /**
     * 更新权限
     *
     * @param entity 权限信息
     * @return 影响行数
     */
    Mono<Integer> updateById(PermissionEntity entity);

    /**
     * 删除权限
     *
     * @param id 权限ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
