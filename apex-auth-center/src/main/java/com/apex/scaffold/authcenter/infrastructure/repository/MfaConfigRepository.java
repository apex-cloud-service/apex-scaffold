package com.apex.scaffold.authcenter.infrastructure.repository;

import com.apex.scaffold.authcenter.domain.model.MfaConfig;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * MFA配置存储库
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
public interface MfaConfigRepository extends ReactiveCrudRepository<MfaConfig, Long> {

    /**
     * 根据用户 ID 查询所有 MFA 配置。
     *
     * @param userId 用户 ID
     * @return MFA 配置列表
     */
    Flux<MfaConfig> findByUserId(String userId);

    /**
     * 根据用户 ID 和租户 ID 查询所有 MFA 配置。
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     * @return MFA 配置列表
     */
    Flux<MfaConfig> findByUserIdAndTenantId(String userId, String tenantId);

    /**
     * 根据用户 ID 和类型查询 MFA 配置。
     *
     * @param userId 用户 ID
     * @param type MFA 类型
     * @return MFA 配置
     */
    Mono<MfaConfig> findByUserIdAndType(String userId, MfaConfig.MfaType type);

    /**
     * 根据用户 ID、类型和租户 ID 查询 MFA 配置。
     *
     * @param userId 用户 ID
     * @param type MFA 类型
     * @param tenantId 租户 ID
     * @return MFA 配置
     */
    Mono<MfaConfig> findByUserIdAndTypeAndTenantId(String userId, MfaConfig.MfaType type, String tenantId);

    /**
     * 根据租户 ID 查询所有 MFA 配置。
     *
     * @param tenantId 租户 ID
     * @return MFA 配置列表
     */
    Flux<MfaConfig> findByTenantId(String tenantId);

    /**
     * 删除用户的指定类型 MFA 配置。
     *
     * @param userId 用户 ID
     * @param type MFA 类型
     * @return 删除的记录数
     */
    Mono<Long> deleteByUserIdAndType(String userId, MfaConfig.MfaType type);
}
