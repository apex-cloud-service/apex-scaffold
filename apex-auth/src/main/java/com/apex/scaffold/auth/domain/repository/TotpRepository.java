package com.apex.scaffold.auth.domain.repository;

import com.apex.scaffold.auth.domain.model.entity.TotpEntity;
import reactor.core.publisher.Mono;

/**
 * TOTP 仓储接口
 *
 * <p>定义 TOTP 多因素认证的数据访问操作。
 *
 * @author Apex Scaffold Team
 * @since 1.3.1
 */
public interface TotpRepository {

    /**
     * 保存 TOTP 配置
     *
     * @param totp TOTP 实体
     * @return 保存后的 TOTP 实体
     */
    Mono<TotpEntity> save(TotpEntity totp);

    /**
     * 更新 TOTP 配置
     *
     * @param totp TOTP 实体
     * @return 更新后的 TOTP 实体
     */
    Mono<TotpEntity> update(TotpEntity totp);

    /**
     * 根据 ID 查询 TOTP 配置
     *
     * @param id TOTP ID
     * @param tenantId 租户 ID
     * @return TOTP 实体
     */
    Mono<TotpEntity> findById(Long id, String tenantId);

    /**
     * 根据用户 ID 查询 TOTP 配置
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return TOTP 实体
     */
    Mono<TotpEntity> findByUserId(Long userId, String tenantId);

    /**
     * 删除 TOTP 配置（逻辑删除）
     *
     * @param id TOTP ID
     * @param tenantId 租户 ID
     * @return 是否删除成功
     */
    Mono<Boolean> deleteById(Long id, String tenantId);

    /**
     * 根据用户 ID 删除 TOTP 配置（逻辑删除）
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 是否删除成功
     */
    Mono<Boolean> deleteByUserId(Long userId, String tenantId);

    /**
     * 检查用户是否已配置 TOTP
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 是否已配置
     */
    Mono<Boolean> existsByUserId(Long userId, String tenantId);
}
