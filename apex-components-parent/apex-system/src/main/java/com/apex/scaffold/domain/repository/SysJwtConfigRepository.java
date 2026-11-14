package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.SysJwtConfigEntity;
import reactor.core.publisher.Mono;

/**
 * JWT 配置仓储接口。
 *
 * <p>定义系统 JWT 配置的持久化操作契约，由基础设施层实现（MyBatis-Plus）。
 * 支持 JWT 配置的 CRUD 操作和租户隔离查询。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>JWT 配置的增删改查</li>
 *   <li>按租户隔离查询 JWT 配置</li>
 *   <li>支持单租户单配置或多配置场景</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>所有查询和操作必须强制校验租户 ID，确保：
 * <ul>
 *   <li>每个租户只能访问自己的 JWT 配置</li>
 *   <li>配置更新和删除限定在租户范围内</li>
 *   <li>跨租户 JWT 配置隔离</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>敏感字段（secretKey、privateKey）在持久化前必须已加密</li>
 *   <li>查询结果中的敏感字段保持加密状态</li>
 *   <li>仅在实际使用时（签名/验证）临时解密</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SysJwtConfigEntity
 */
public interface SysJwtConfigRepository {

    /**
     * 根据 ID 查询 JWT 配置。
     *
     * @param id JWT 配置 ID
     * @return JWT 配置实体，不存在时返回 {@code Mono.empty()}
     */
    Mono<SysJwtConfigEntity> findById(Long id);

    /**
     * 根据租户 ID 查询 JWT 配置。
     *
     * <p>查询指定租户的 JWT 配置。通常每个租户只有一条配置记录，
     * 如果存在多条则返回最新的一条（按创建时间倒序）。
     *
     * @param tenantId 租户 ID
     * @return JWT 配置实体，不存在时返回 {@code Mono.empty()}
     */
    Mono<SysJwtConfigEntity> findByTenantId(String tenantId);

    /**
     * 新增 JWT 配置。
     *
     * <p>插入前应确保敏感字段（secretKey、privateKey）已加密。
     * 同一租户通常只维护一条配置记录。
     *
     * @param entity JWT 配置实体
     * @return 影响行数
     */
    Mono<Integer> insert(SysJwtConfigEntity entity);

    /**
     * 更新 JWT 配置。
     *
     * <p>更新前应校验配置是否属于当前租户。
     * 如果修改了敏感字段，需要重新加密。
     *
     * @param entity JWT 配置实体
     * @return 影响行数
     */
    Mono<Integer> updateById(SysJwtConfigEntity entity);

    /**
     * 删除 JWT 配置。
     *
     * <p>逻辑删除，基于 {@code deleted} 字段。
     * 删除前应确认没有正在使用该配置签发的有效令牌，或提供过渡方案。
     *
     * @param id JWT 配置 ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
