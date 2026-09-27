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
     * 根据用户ID查询所有MFA配置
     */
    Flux<MfaConfig> findByUserId(String userId);

    /**
     * 根据用户ID和类型查询MFA配置
     */
    Mono<MfaConfig> findByUserIdAndType(String userId, MfaConfig.MfaType type);

    /**
     * 根据租户ID查询所有MFA配置
     */
    Flux<MfaConfig> findByTenantId(String tenantId);

    /**
     * 删除用户的指定类型MFA配置
     */
    Mono<Long> deleteByUserIdAndType(String userId, MfaConfig.MfaType type);
}
