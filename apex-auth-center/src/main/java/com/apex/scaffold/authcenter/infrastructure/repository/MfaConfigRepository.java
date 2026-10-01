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
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @return TODO
     */
    Flux<MfaConfig> findByUserId(String userId);

    /**
     * 根据用户ID和类型查询MFA配置
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @param type TODO
     * @return TODO
     */
    Mono<MfaConfig> findByUserIdAndType(String userId, MfaConfig.MfaType type);

    /**
     * 根据租户ID查询所有MFA配置
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param tenantId TODO
     * @return TODO
     */
    Flux<MfaConfig> findByTenantId(String tenantId);

    /**
     * 删除用户的指定类型MFA配置
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @param type TODO
     * @return TODO
     */
    Mono<Long> deleteByUserIdAndType(String userId, MfaConfig.MfaType type);
}
