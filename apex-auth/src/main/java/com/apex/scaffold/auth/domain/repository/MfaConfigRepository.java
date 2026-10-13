package com.apex.scaffold.auth.domain.repository;

import com.apex.scaffold.auth.domain.model.entity.MfaConfigEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * MFA 配置仓储接口
 *
 * <p>定义多因素认证配置的数据访问操作。
 *
 * @author Apex Scaffold Team
 * @since 2026-09-21
 */
public interface MfaConfigRepository {

    /**
     * 保存 MFA 配置
     *
     * @param config MFA 配置实体
     * @return 保存后的 MFA 配置实体
     */
    Mono<MfaConfigEntity> save(MfaConfigEntity config);

    /**
     * 更新 MFA 配置
     *
     * @param config MFA 配置实体
     * @return 更新后的 MFA 配置实体
     */
    Mono<MfaConfigEntity> update(MfaConfigEntity config);

    /**
     * 根据 ID 查询 MFA 配置
     *
     * @param id 配置 ID
     * @param tenantId 租户 ID
     * @return MFA 配置实体
     */
    Mono<MfaConfigEntity> findById(Long id, String tenantId);

    /**
     * 根据用户 ID 查询所有 MFA 配置
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return MFA 配置列表
     */
    Flux<MfaConfigEntity> findByUserId(Long userId, String tenantId);

    /**
     * 根据用户 ID 和 MFA 类型查询配置
     *
     * @param userId 用户 ID
     * @param mfaType MFA 类型
     * @param tenantId 租户 ID
     * @return MFA 配置实体
     */
    Mono<MfaConfigEntity> findByUserIdAndType(Long userId, String mfaType, String tenantId);

    /**
     * 查询用户已启用的 MFA 配置
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 已启用的 MFA 配置列表
     */
    Flux<MfaConfigEntity> findEnabledByUserId(Long userId, String tenantId);

    /**
     * 查询用户必需的 MFA 配置
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 必需的 MFA 配置列表
     */
    Flux<MfaConfigEntity> findRequiredByUserId(Long userId, String tenantId);

    /**
     * 删除 MFA 配置（逻辑删除）
     *
     * @param id 配置 ID
     * @param tenantId 租户 ID
     * @return 是否删除成功
     */
    Mono<Boolean> deleteById(Long id, String tenantId);

    /**
     * 根据用户 ID 删除所有 MFA 配置（逻辑删除）
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return 是否删除成功
     */
    Mono<Boolean> deleteByUserId(Long userId, String tenantId);

    /**
     * 检查用户是否已配置指定类型的 MFA
     *
     * @param userId 用户 ID
     * @param mfaType MFA 类型
     * @param tenantId 租户 ID
     * @return 是否已配置
     */
    Mono<Boolean> existsByUserIdAndType(Long userId, String mfaType, String tenantId);
}
