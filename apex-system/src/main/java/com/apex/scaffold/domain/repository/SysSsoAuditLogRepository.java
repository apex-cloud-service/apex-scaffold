package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.SysSsoAuditLogEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * SSO 审计日志仓储接口
 *
 * <p>定义单点登录（SSO）审计日志的持久化操作契约，记录所有 SSO 相关的操作日志。
 * 支持按提供商、用户、租户查询审计记录，用于安全审计、合规追踪和问题排查。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>记录 SSO 登录、登出、刷新令牌等操作</li>
 *   <li>按提供商查询审计日志（SAML、OAuth2、LDAP 等）</li>
 *   <li>按用户查询历史登录记录</li>
 *   <li>按租户查询所有 SSO 操作日志</li>
 *   <li>支持日志删除（数据清理）</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>安全审计：查询某用户的 SSO 登录历史</li>
 *   <li>故障排查：查询某提供商的失败登录记录</li>
 *   <li>合规报告：统计租户的 SSO 使用情况</li>
 *   <li>异常检测：监控异常的 SSO 操作模式</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>响应式编程：所有方法返回 Mono/Flux</li>
 *   <li>租户隔离：支持多租户环境的日志隔离</li>
 *   <li>不可变记录：审计日志只增不改，保证审计完整性</li>
 *   <li>时间戳记录：精确记录操作时间便于时序分析</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysSsoAuditLogEntity
 */
public interface SysSsoAuditLogRepository {

    /**
     * 根据ID查询SSO审计日志
     *
     * @param id 审计日志ID
     * @return 审计日志实体
     */
    Mono<SysSsoAuditLogEntity> findById(Long id);

    /**
     * 根据提供商ID查询审计日志列表
     *
     * @param providerId 提供商ID
     * @return 审计日志列表
     */
    Flux<SysSsoAuditLogEntity> findByProviderId(Long providerId);

    /**
     * 根据用户ID查询审计日志列表
     *
     * @param userId 用户ID
     * @return 审计日志列表
     */
    Flux<SysSsoAuditLogEntity> findByUserId(Long userId);

    /**
     * 根据租户ID查询审计日志列表
     *
     * @param tenantId 租户ID
     * @return 审计日志列表
     */
    Flux<SysSsoAuditLogEntity> findByTenantId(String tenantId);

    /**
     * 插入审计日志
     *
     * @param entity 审计日志实体
     * @return 影响行数
     */
    Mono<Integer> insert(SysSsoAuditLogEntity entity);

    /**
     * 根据ID删除审计日志
     *
     * @param id 审计日志ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
