package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.RolePermissionEntity;
import com.apex.scaffold.domain.repository.RolePermissionRepository;
import com.apex.scaffold.infrastructure.persistence.converter.RolePermissionPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.RolePermissionMapper;
import com.apex.scaffold.infrastructure.persistence.po.RolePermissionPO;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link RolePermissionRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link RolePermissionEntity} 的持久化读写，通过 {@link RolePermissionPoConverter}
 * 在领域实体与 {@link RolePermissionPO} 之间转换，阻塞操作调度到 JDBC 专用线程池。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see RolePermissionRepository
 */
@Repository
@RequiredArgsConstructor
public class RolePermissionRepositoryImpl implements RolePermissionRepository {

    private final RolePermissionMapper rolePermissionMapper;
    private final RolePermissionPoConverter rolePermissionPoConverter;

    /**
     * 根据角色 ID 查询角色权限关联列表
     *
     * @param roleId 角色 ID
     * @return 角色权限关联列表
     */
    @Override
    public Mono<List<RolePermissionEntity>> findByRoleId(Long roleId) {
        return Mono.fromCallable(() -> rolePermissionMapper.selectList(
                        ConditionUtil.lambda(RolePermissionPO.class).eqIfPresent(RolePermissionPO::getRoleId, roleId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(rolePermissionPoConverter::toEntityList);
    }

    /**
     * 根据角色 ID 删除角色权限关联关系
     *
     * @param roleId 角色 ID
     * @return 影响行数
     */
    @Override
    public Mono<Integer> deleteByRoleId(Long roleId) {
        return Mono.fromCallable(() -> {
                    return rolePermissionMapper.delete(ConditionUtil.lambda(RolePermissionPO.class)
                            .eqIfPresent(RolePermissionPO::getRoleId, roleId));
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
