package com.apex.scaffold.domain.gateway;

/**
 * 角色聚合删除事务的领域网关。
 *
 * <p>删除角色必须同时清理用户角色关系和角色权限关系。本端口将三项同步数据库写操作
 * 收敛到基础设施事务边界，避免在返回 {@code Mono} 的应用方法上使用无效的 JDBC
 * {@code @Transactional}。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface RoleDeletionGateway {

    /**
     * 在同一数据库事务内删除角色及其关联关系。
     *
     * @param roleId 待删除角色标识
     * @return 角色主记录被删除时返回 {@code true}
     */
    boolean deleteRole(Long roleId);
}
