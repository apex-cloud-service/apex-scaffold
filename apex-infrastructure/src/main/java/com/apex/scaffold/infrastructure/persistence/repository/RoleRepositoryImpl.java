package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import com.apex.scaffold.domain.repository.RoleRepository;
import com.apex.scaffold.infrastructure.persistence.converter.RolePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.RoleDeptMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.RoleMapper;
import com.apex.scaffold.infrastructure.persistence.mapper.UserRoleMapper;
import com.apex.scaffold.infrastructure.persistence.po.RoleDeptPO;
import com.apex.scaffold.infrastructure.persistence.po.RolePO;
import com.apex.scaffold.infrastructure.persistence.po.UserRolePO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link RoleRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link RoleEntity} 的持久化读写与角色关联查询，通过 {@link RolePoConverter}
 * 在领域实体与 {@link RolePO} 之间转换，阻塞操作调度到 JDBC 专用线程池。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see RoleRepository
 */
@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleDeptMapper roleDeptMapper;
    private final RolePoConverter rolePoConverter;

    /**
     * 根据用户 ID 查询用户拥有的角色列表
     *
     * <p>通过 user_role 关系表查询 roleIds，再批量查询角色信息
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    @Override
    public Mono<List<RoleEntity>> findByUserId(Long userId) {
        return Mono.fromCallable(() -> {
                    List<Long> roleIds = userRoleMapper
                            .selectList(
                                    ConditionUtil.lambda(UserRolePO.class).eqIfPresent(UserRolePO::getUserId, userId))
                            .stream()
                            .map(UserRolePO::getRoleId)
                            .toList();

                    if (roleIds.isEmpty()) {
                        return List.<RoleEntity>of();
                    }

                    List<RolePO> pos = roleMapper.selectList(ConditionUtil.lambda(RolePO.class)
                            .inIfPresent(RolePO::getId, roleIds)
                            .eq(RolePO::getStatus, 1));
                    return rolePoConverter.toEntityList(pos).stream()
                            .filter(RoleEntity::isEnabled)
                            .toList();
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 根据角色 ID 查询角色信息
     *
     * @param id 角色 ID
     * @return 角色信息，未找到时返回 Mono.empty()
     */
    @Override
    public Mono<RoleEntity> findById(Long id) {
        return Mono.fromCallable(() -> roleMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> {
                    if (po == null) {
                        return Mono.empty();
                    }
                    return Mono.just(rolePoConverter.toEntity(po));
                });
    }

    /**
     * 查询所有角色
     *
     * @return 角色列表
     */
    @Override
    public Mono<List<RoleEntity>> findAll() {
        return Mono.fromCallable(() -> roleMapper.selectList(null))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(rolePoConverter::toEntityList);
    }

    /**
     * 根据字段查询单个角色
     *
     * <p>当前实现按角色编码（code）进行等值查询
     *
     * @param field 字段名（保留参数，当前实现未使用）
     * @param value 字段值
     * @return 角色信息，未找到时返回 Mono.empty()
     */
    @Override
    public Mono<RoleEntity> findOne(String field, Object value) {
        return Mono.fromCallable(() -> {
                    RolePO po = roleMapper.selectOne(
                            ConditionUtil.lambda(RolePO.class).ifPresent(value, (w, v) -> w.eq(RolePO::getCode, v)));
                    return rolePoConverter.toEntity(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(r -> r);
    }

    /**
     * 新增角色
     *
     * @param entity 角色实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> insert(RoleEntity entity) {
        return Mono.fromCallable(() -> {
                    int rows = roleMapper.insert(rolePoConverter.toPo(entity));
                    return rows;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 更新角色信息
     *
     * @param entity 角色实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> updateById(RoleEntity entity) {
        return Mono.fromCallable(() -> roleMapper.updateById(rolePoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 删除角色
     *
     * @param id 角色 ID
     * @return 影响行数
     */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> roleMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<List<Long>> findDataScopeDeptIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Mono.just(List.of());
        }
        return Mono.fromCallable(
                        () -> roleDeptMapper
                                .selectList(ConditionUtil.lambda(RoleDeptPO.class)
                                        .inIfPresent(RoleDeptPO::getRoleId, roleIds))
                                .stream()
                                .map(RoleDeptPO::getDeptId)
                                .filter(java.util.Objects::nonNull)
                                .distinct()
                                .toList())
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
