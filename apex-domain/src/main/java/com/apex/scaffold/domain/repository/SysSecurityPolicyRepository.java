package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.SysSecurityPolicyEntity;
import reactor.core.publisher.Mono;

/**
 * 安全策略仓储接口。
 *
 * <p>定义系统安全策略的持久化操作契约，由基础设施层实现（MyBatis-Plus）。
 * 支持安全策略的 CRUD 操作和租户隔离查询。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>安全策略的增删改查</li>
 *   <li>按租户隔离查询安全策略</li>
 *   <li>支持单租户单策略场景</li>
 * </ul>
 *
 * <h2>租户隔离约束</h2>
 * <p>所有查询和操作必须强制校验租户 ID，确保：
 * <ul>
 *   <li>每个租户只能访问自己的安全策略</li>
 *   <li>策略更新和删除限定在租户范围内</li>
 *   <li>跨租户安全策略隔离</li>
 * </ul>
 *
 * <h2>业务规则</h2>
 * <ul>
 *   <li>每个租户通常只维护一条安全策略</li>
 *   <li>策略变更对新用户和新登录立即生效</li>
 *   <li>密码策略变更不影响已有密码，仅在下次修改时应用</li>
 *   <li>IP 白名单/黑名单变更立即生效</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysSecurityPolicyEntity
 */
public interface SysSecurityPolicyRepository {

    /**
     * 根据 ID 查询安全策略。
     *
     * @param id 安全策略 ID
     * @return 安全策略实体，不存在时返回 {@code Mono.empty()}
     */
    Mono<SysSecurityPolicyEntity> findById(Long id);

    /**
     * 根据租户 ID 查询安全策略。
     *
     * <p>查询指定租户的安全策略。通常每个租户只有一条策略记录，
     * 如果存在多条则返回最新的一条（按创建时间倒序）。
     *
     * @param tenantId 租户 ID
     * @return 安全策略实体，不存在时返回 {@code Mono.empty()}
     */
    Mono<SysSecurityPolicyEntity> findByTenantId(String tenantId);

    /**
     * 新增安全策略。
     *
     * <p>插入前应校验策略参数的合法性：
     * <ul>
     *   <li>passwordMinLength 应在 6-32 之间</li>
     *   <li>maxLoginAttempts 应大于 0</li>
     *   <li>lockoutDuration 应大于 0</li>
     *   <li>IP 白名单和黑名单应为有效的 JSON 数组格式</li>
     * </ul>
     *
     * @param entity 安全策略实体
     * @return 影响行数
     */
    Mono<Integer> insert(SysSecurityPolicyEntity entity);

    /**
     * 更新安全策略。
     *
     * <p>更新前应校验策略是否属于当前租户。
     * 策略变更对新用户和新登录立即生效。
     *
     * @param entity 安全策略实体
     * @return 影响行数
     */
    Mono<Integer> updateById(SysSecurityPolicyEntity entity);

    /**
     * 删除安全策略。
     *
     * <p>逻辑删除，基于 {@code deleted} 字段。
     * 删除前应确认该租户有替代策略或使用系统默认策略。
     *
     * @param id 安全策略 ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
