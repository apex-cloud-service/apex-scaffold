package com.apex.scaffold.domain.gateway;

import reactor.core.publisher.Mono;

/**
 * 租户开通事务网关（方案批次 B4）。
 *
 * <p>开通引导涉及多表写入，须收敛到基础设施事务边界（与 {@link RoleDeletionGateway}
 * 同范式），避免在返回 {@code Mono} 的应用方法上使用无效的 JDBC 事务。
 * 实现于基础设施层（{@code TenantProvisioningTransaction}），单事务完成
 * 默认部门、管理员账号、内置角色、授权关系与管理员回填，任一步失败整体回滚。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface TenantProvisioningGateway {

    /**
     * 开通请求参数。
     *
     * @param tenantCode    租户编码（唯一）
     * @param tenantName    租户名称
     * @param adminUsername 管理员登录名
     * @param adminPassword 管理员初始密码（明文，实现侧 BCrypt 存储）
     * @param packageCode   套餐编码（可空 = 全量授权）
     * @param maxUsers      用户配额（可空）
     * @param maxStorageMb  存储配额 MB（可空）
     * @param remark        备注
     */
    record ProvisioningRequest(
            String tenantCode,
            String tenantName,
            String adminUsername,
            String adminPassword,
            String packageCode,
            Integer maxUsers,
            Integer maxStorageMb,
            String remark) {}

    /**
     * 执行开通事务。
     *
     * @param request 开通参数
     * @return 管理员用户 ID（已回填至租户登记）
     */
    Mono<Long> provision(ProvisioningRequest request);
}
