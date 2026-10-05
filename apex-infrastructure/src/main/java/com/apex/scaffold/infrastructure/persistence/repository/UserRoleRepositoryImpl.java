package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.UserRoleEntity;
import com.apex.scaffold.domain.repository.UserRoleRepository;
import com.apex.scaffold.infrastructure.persistence.converter.UserRolePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.UserRoleMapper;
import com.apex.scaffold.infrastructure.persistence.po.UserRolePO;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

/**
 * {@link UserRoleRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link UserRoleEntity} 的持久化读写，通过 {@link UserRolePoConverter}
 * 在领域实体与 {@link UserRolePO} 之间转换，阻塞操作调度到 JDBC 专用线程池。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see UserRoleRepository
 */
@Repository
@RequiredArgsConstructor
public class UserRoleRepositoryImpl implements UserRoleRepository {

    private final UserRoleMapper userRoleMapper;
    private final UserRolePoConverter userRolePoConverter;
    private final TransactionTemplate transactionTemplate;

    /**
     * 根据用户 ID 查询用户角色关联列表
     *
     * @param userId 用户 ID
     * @return 用户角色关联列表
     */
    @Override
    public Mono<List<UserRoleEntity>> findByUserId(Long userId) {
        Callable<List<UserRoleEntity>> callable = () -> {
            List<UserRolePO> poList = userRoleMapper.selectList(
                    ConditionUtil.lambda(UserRolePO.class).eqIfPresent(UserRolePO::getUserId, userId));
            return userRolePoConverter.toEntityList(poList);
        };
        return Mono.fromCallable(callable).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 根据双字段条件删除用户角色关联关系
     *
     * @param field1 字段1（当前实现兼容 user_id）
     * @param val1 值1
     * @param field2 字段2（当前实现兼容 user_id/role_id）
     * @param val2 值2
     * @return 影响行数
     */
    @Override
    public Mono<Integer> delete(String field1, Object val1, String field2, Object val2) {
        Callable<Integer> callable = () -> {
            return userRoleMapper.delete(ConditionUtil.lambda(UserRolePO.class)
                    .ifPresent(val1, (w, v) -> w.eq(UserRolePO::getUserId, v))
                    .ifPresent(val2, (w, v) -> w.eq(UserRolePO::getRoleId, v)));
        };
        return Mono.fromCallable(callable).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 根据角色 ID 删除用户角色关联关系
     *
     * <p>该操作可能影响多个用户；当前实现不做批量用户缓存清理，由缓存自然过期或上层策略处理
     *
     * @param roleId 角色 ID
     * @return 影响行数
     */
    @Override
    public Mono<Integer> deleteByRoleId(Long roleId) {
        Callable<Integer> callable = () -> {
            return userRoleMapper.delete(
                    ConditionUtil.lambda(UserRolePO.class).eqIfPresent(UserRolePO::getRoleId, roleId));
        };
        return Mono.fromCallable(callable).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 新增用户角色关联关系
     *
     * @param userRole 用户角色关联实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> insert(UserRoleEntity userRole) {
        Callable<Integer> callable = () -> {
            UserRolePO po = userRolePoConverter.toPo(userRole);
            return userRoleMapper.insert(po);
        };
        return Mono.fromCallable(callable).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 事务性替换用户的全部角色。
     *
     * <p>在单事务内先删除多余的历史关联，再补齐缺失关联：被逻辑删除的关联
     * 优先通过 {@code restore} 复活，否则新增。任一环节失败整体回滚。</p>
     *
     * @param userId  目标用户 ID
     * @param roleIds 期望保留的角色 ID 集合
     * @return 完成信号
     */
    @Override
    public Mono<Void> replaceRoles(Long userId, Set<Long> roleIds) {
        return Mono.fromRunnable(() -> transactionTemplate.executeWithoutResult(status -> {
                    List<UserRolePO> current = userRoleMapper.selectList(
                            ConditionUtil.lambda(UserRolePO.class).eq(UserRolePO::getUserId, userId));
                    Set<Long> currentIds =
                            current.stream().map(UserRolePO::getRoleId).collect(java.util.stream.Collectors.toSet());
                    current.stream()
                            .filter(item -> !roleIds.contains(item.getRoleId()))
                            .forEach(item -> userRoleMapper.deleteById(item.getId()));
                    roleIds.stream()
                            .filter(roleId -> !currentIds.contains(roleId))
                            .forEach(roleId -> {
                                if (userRoleMapper.restore(userId, roleId) == 0) {
                                    UserRolePO po = new UserRolePO();
                                    po.setUserId(userId);
                                    po.setRoleId(roleId);
                                    userRoleMapper.insert(po);
                                }
                            });
                }))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }
}
