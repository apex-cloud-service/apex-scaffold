package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.PermissionEntity;
import com.apex.scaffold.domain.repository.PermissionRepository;
import com.apex.scaffold.infrastructure.persistence.converter.PermissionPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.PermissionMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.RolePermissionMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.UserRoleMapper;
import com.apex.scaffold.infrastructure.persistence.po.PermissionPO;
import com.apex.scaffold.infrastructure.persistence.po.RolePermissionPO;
import com.apex.scaffold.infrastructure.persistence.po.UserRolePO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link PermissionRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link PermissionEntity} 的持久化读写与角色权限关联查询，通过 {@link PermissionPoConverter}
 * 完成领域实体与 {@link PermissionPO} 之间的转换，阻塞操作调度到 JDBC 专用线程池。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see PermissionRepository
 */
@Repository
@RequiredArgsConstructor
public class PermissionRepositoryImpl implements PermissionRepository {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionPoConverter permissionPoConverter;

    /**
     * 根据用户 ID 查询用户拥有的权限列表
     *
     * <p>按 RBAC 链路查询：
     *
     * <ol>
     *   <li>通过 user_role 查询用户关联的 roleIds
     *   <li>通过 role_permission 查询权限 permissionIds
     *   <li>批量查询权限明细
     * </ol>
     *
     * @param userId 用户 ID
     * @return 权限列表
     */
    @Override
    /**
     * 查找ByUserId
     *
     * @param userId 参数
     *
     * @return 结果
     */
    public Mono<List<PermissionEntity>> findByUserId(Long userId) {
        return Mono.fromCallable(() -> {
                    List<Long> roleIds = userRoleMapper
                            .selectList(
                                    ConditionUtil.lambda(UserRolePO.class).eqIfPresent(UserRolePO::getUserId, userId))
                            .stream()
                            .map(UserRolePO::getRoleId)
                            .toList();

                    if (roleIds.isEmpty()) {
                        return List.<PermissionEntity>of();
                    }

                    List<Long> permissionIds = rolePermissionMapper
                            .selectList(ConditionUtil.lambda(RolePermissionPO.class)
                                    .inIfPresent(RolePermissionPO::getRoleId, roleIds))
                            .stream()
                            .map(RolePermissionPO::getPermissionId)
                            .toList();

                    if (permissionIds.isEmpty()) {
                        return List.<PermissionEntity>of();
                    }

                    return permissionMapper
                            .selectList(ConditionUtil.lambda(PermissionPO.class)
                                    .inIfPresent(PermissionPO::getId, permissionIds))
                            .stream()
                            .map(permissionPoConverter::toEntity)
                            .toList();
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 查询所有权限
     *
     * @return 权限列表
     */
    @Override
    /**
     * 查找All
     *
     * @return 结果
     */
    public Mono<List<PermissionEntity>> findAll() {
        return Mono.fromCallable(() -> permissionMapper.selectList(null))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(permissionPoConverter::toEntityList);
    }

    /**
     * 根据权限 ID 查询权限信息
     *
     * @param id 权限 ID
     * @return 权限信息，未找到时返回 Mono.empty()
     */
    @Override
    /**
     * 查找ById
     *
     * @param id 参数
     *
     * @return 结果
     */
    public Mono<PermissionEntity> findById(Long id) {
        return Mono.fromCallable(() -> permissionMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> {
                    if (po == null) {
                        return Mono.empty();
                    }
                    return Mono.just(permissionPoConverter.toEntity(po));
                });
    }

    /**
     * 根据字段查询单个权限
     *
     * <p>当前实现按权限编码（code）进行等值查询
     *
     * @param field 字段名（保留参数，当前实现未使用）
     * @param value 字段值
     * @return 权限信息，未找到时返回 Mono.empty()
     */
    @Override
    /**
     * 查找One
     *
     * @param field 参数
     *
     * @param value 参数
     *
     * @return 结果
     */
    public Mono<PermissionEntity> findOne(String field, Object value) {
        return Mono.fromCallable(() -> {
                    PermissionPO po = permissionMapper.selectOne(ConditionUtil.lambda(PermissionPO.class)
                            .ifPresent(value, (w, v) -> w.eq(PermissionPO::getCode, v)));
                    return permissionPoConverter.toEntity(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(p -> p);
    }

    /**
     * 新增权限
     *
     * @param entity 权限实体
     * @return 影响行数
     */
    @Override
    /**
     * 插入
     *
     * @param entity 参数
     *
     * @return 结果
     */
    public Mono<Integer> insert(PermissionEntity entity) {
        return Mono.fromCallable(() -> {
                    int rows = permissionMapper.insert(permissionPoConverter.toPo(entity));
                    return rows;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 更新权限信息
     *
     * @param entity 权限实体
     * @return 影响行数
     */
    @Override
    /**
     * 更新ById
     *
     * @param entity 参数
     *
     * @return 结果
     */
    public Mono<Integer> updateById(PermissionEntity entity) {
        return Mono.fromCallable(() -> permissionMapper.updateById(permissionPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 删除权限
     *
     * @param id 权限 ID
     * @return 影响行数
     */
    @Override
    /**
     * 删除ById
     *
     * @param id 参数
     *
     * @return 结果
     */
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> permissionMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
