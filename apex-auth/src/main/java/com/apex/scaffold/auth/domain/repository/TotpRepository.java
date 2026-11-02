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

    /**
     * 原子消费 TOTP 时间步（防重放，统一认证收口 A4）
     *
     * <p>仅当已记录的时间步小于给定步（或尚无记录）时写入并返回 true；
     * 并发验证同一时间步时仅有一个请求成功，实现同一验证码只能使用一次。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @param step     匹配成功的 TOTP 时间步
     * @return 消费成功返回 true；该时间步已被使用返回 false
     */
    Mono<Boolean> markStepUsed(Long userId, String tenantId, long step);
}
