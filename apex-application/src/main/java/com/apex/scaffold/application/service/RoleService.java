package com.apex.scaffold.application.service;

import static com.apex.scaffold.common.scheduler.BlockingIoSchedulers.*;

import com.apex.scaffold.domain.gateway.RoleDeletionGateway;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import com.apex.scaffold.domain.model.entity.UserRoleEntity;
import com.apex.scaffold.domain.repository.RoleRepository;
import com.apex.scaffold.domain.repository.UserRoleRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 角色服务类
 *
 * <p>
 * 提供角色查询、校验和管理等功能，支持基于 RBAC 模型的角色管理
 *
 * <h2>线程模型</h2>
 * <p>角色级联删除（deleteRole）通过 {@link RoleDeletionGateway} 在同步 JDBC 事务中
 * 清理 {@code role} / {@code user_role} / {@code role_permission} 多表关系，
 * 阻塞 I/O 使用 {@code BlockingIoSchedulers#jdbc()} 专用线程池承载，
 * 与全局 boundedElastic 隔离，避免删除事务长耗时影响核心链路。</p>
 *
 * @author Apex Team
 * @version 1.0.1
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    /**
     * 角色仓储。
     *
     * <p>用于角色数据的持久化读写操作。</p>
     */
    private final RoleRepository roleRepository;

    /**
     * 用户角色关系仓储。
     *
     * <p>用于管理用户与角色的多对多关联关系。</p>
     */
    private final UserRoleRepository userRoleRepository;

    /** 角色聚合删除事务网关，确保多表清理位于真实 JDBC 事务内。 */
    private final RoleDeletionGateway roleDeletionGateway;

    /**
     * 根据用户 ID 获取角色列表
     *
     * <p>
     * 通过用户 ID 查询用户拥有的所有角色，返回角色实体列表
     *
     * @param userId 用户唯一标识
     * @return 角色列表
     */
    public Mono<List<RoleEntity>> getRolesByUserId(Long userId) {
        return roleRepository.findByUserId(userId);
    }

    /**
     * 检查用户是否有指定角色
     *
     * <p>
     * 通过用户 ID 获取所有角色，然后检查是否包含指定的角色编码，用于单角色验证场景
     *
     * @param userId 用户唯一标识
     * @param roleCode 角色编码
     * @return 角色检查结果，true-有角色，false-无角色
     */
    public Mono<Boolean> hasRole(Long userId, String roleCode) {
        return getRolesByUserId(userId)
                .map(roles -> roles.stream().anyMatch(role -> Objects.equals(role.getCode(), roleCode)));
    }

    /**
     * 获取所有角色
     *
     * <p>
     * 查询系统中所有的角色定义，返回完整的角色列表
     *
     * @return 角色列表
     */
    public Mono<List<RoleEntity>> getAllRoles() {
        return roleRepository.findAll();
    }

    /**
     * 根据角色编码获取角色
     *
     * <p>
     * 通过角色编码查询对应的角色信息，支持精确匹配
     *
     * @param code 角色编码
     * @return 角色实体对象
     */
    public Mono<RoleEntity> getRoleByCode(String code) {
        return roleRepository.findOne("code", code);
    }

    /**
     * 创建角色
     *
     * <p>新增角色定义并持久化到数据库。 方法成功后会记录创建日志。若存在角色编码唯一性约束，则由持久化层抛出相应异常。
     *
     * @param role 角色实体对象
     * @return 已创建的角色实体对象
     * @throws org.springframework.dao.DuplicateKeyException 当角色编码重复时由持久化层抛出
     */
    public Mono<RoleEntity> createRole(RoleEntity role) {
        return roleRepository.insert(role).map(rows -> {
            log.info("创建角色成功，code: {}, name: {}", role.getCode(), role.getName());
            return role;
        });
    }

    /**
     * 更新角色
     *
     * <p>
     * 根据角色 ID 查询已有记录后更新角色的名称与描述信息，并持久化到数据库。 方法成功后会记录更新日志。
     *
     * @param role 角色实体对象（必须包含 id；name/description 为期望更新的值）
     * @return 已更新的角色实体对象
     */
    public Mono<RoleEntity> updateRole(RoleEntity role) {
        return roleRepository.findById(role.getId()).flatMap(existingRole -> {
            existingRole.updateInfo(role.getName(), role.getDescription());
            return roleRepository.updateById(existingRole).map(rows -> {
                log.info("更新角色成功，id: {}, code: {}", existingRole.getId(), existingRole.getCode());
                return existingRole;
            });
        });
    }

    /**
     * 删除角色
     *
     * <p>
     * 从数据库中删除指定的角色记录，同时级联删除关联的用户和权限关系，并记录日志
     *
     * <p>实际删除委托给同步事务网关，并在弹性线程池执行阻塞 JDBC 调用。这样事务切面会在
     * SQL 全部执行完成后才结束，任一步骤失败均会回滚。</p>
     *
     * @param id 角色唯一标识
     * @return 删除结果，true-删除了角色记录（rows &gt; 0），false-未删除任何记录
     */
    public Mono<Boolean> deleteRole(Long id) {
        return Mono.fromCallable(() -> roleDeletionGateway.deleteRole(id))
                .subscribeOn(jdbc())
                .doOnSuccess(deleted -> log.info("删除角色完成，id: {}, deleted: {}", id, deleted));
    }

    /**
     * 为用户分配角色
     *
     * <p>
     * 建立用户与角色的关联关系，使用户获得对应角色的权限。 若关系已存在，具体行为取决于持久化层约束（可能返回 0 行或抛出唯一键冲突异常）。
     *
     * @param userId 用户唯一标识
     * @param roleId 角色唯一标识
     * @return 分配结果，true-插入成功（rows &gt; 0），false-未插入任何记录
     */
    public Mono<Boolean> assignRoleToUser(Long userId, Long roleId) {
        UserRoleEntity userRole = new UserRoleEntity();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);

        return userRoleRepository.insert(userRole).map(rows -> {
            log.info("为用户分配角色成功，userId: {}, roleId: {}", userId, roleId);
            return rows > 0;
        });
    }

    /**
     * 移除用户的角色
     *
     * <p>
     * 解除用户与角色的关联关系，使用户失去对应角色的权限
     *
     * @param userId 用户唯一标识
     * @param roleId 角色唯一标识
     * @return 移除结果，true-删除了关联记录（rows &gt; 0），false-未删除任何记录
     */
    public Mono<Boolean> removeRoleFromUser(Long userId, Long roleId) {
        return userRoleRepository.delete("user_id", userId, "role_id", roleId).map(rows -> {
            log.info("移除用户角色成功，userId: {}, roleId: {}", userId, roleId);
            return rows > 0;
        });
    }
}
