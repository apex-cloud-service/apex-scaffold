package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.SysSessionConfigEntity;
import reactor.core.publisher.Mono;

/**
 * 会话配置仓储接口。
 *
 * <p>定义系统会话配置的持久化操作契约，由基础设施层实现（MyBatis-Plus）。
 * 支持会话配置的 CRUD 操作和租户隔离查询。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>会话配置的增删改查</li>
 *   <li>按租户隔离查询会话配置</li>
 *   <li>支持单租户单配置场景</li>
 * </ul>
 *
 * <h2>租户隔离约束</h2>
 * <p>所有查询和操作必须强制校验租户 ID，确保：
 * <ul>
 *   <li>每个租户只能访问自己的会话配置</li>
 *   <li>配置更新和删除限定在租户范围内</li>
 *   <li>跨租户会话配置隔离</li>
 * </ul>
 *
 * <h2>业务规则</h2>
 * <ul>
 *   <li>每个租户通常只维护一条会话配置</li>
 *   <li>配置变更立即生效，但不影响已建立的会话</li>
 *   <li>禁用配置后新会话创建将失败</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysSessionConfigEntity
 */
public interface SysSessionConfigRepository {

    /**
     * 根据 ID 查询会话配置。
     *
     * @param id 会话配置 ID
     * @return 会话配置实体，不存在时返回 {@code Mono.empty()}
     */
    Mono<SysSessionConfigEntity> findById(Long id);

    /**
     * 根据租户 ID 查询会话配置。
     *
     * <p>查询指定租户的会话配置。通常每个租户只有一条配置记录，
     * 如果存在多条则返回最新的一条（按创建时间倒序）。
     *
     * @param tenantId 租户 ID
     * @return 会话配置实体，不存在时返回 {@code Mono.empty()}
     */
    Mono<SysSessionConfigEntity> findByTenantId(String tenantId);

    /**
     * 新增会话配置。
     *
     * <p>插入前应校验配置参数的合法性：
     * <ul>
     *   <li>maxSessions 应大于 0</li>
     *   <li>sessionTimeout 应大于 0</li>
     *   <li>rememberMeTtl 应大于 sessionTimeout</li>
     * </ul>
     *
     * @param entity 会话配置实体
     * @return 影响行数
     */
    Mono<Integer> insert(SysSessionConfigEntity entity);

    /**
     * 更新会话配置。
     *
     * <p>更新前应校验配置是否属于当前租户。
     * 配置变更立即生效，但不影响已建立的会话。
     *
     * @param entity 会话配置实体
     * @return 影响行数
     */
    Mono<Integer> updateById(SysSessionConfigEntity entity);

    /**
     * 删除会话配置。
     *
     * <p>逻辑删除，基于 {@code deleted} 字段。
     * 删除前应确认没有依赖该配置的活跃会话，或提供过渡方案。
     *
     * @param id 会话配置 ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
