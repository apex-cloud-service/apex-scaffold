package com.apex.scaffold.authcenter.infrastructure.repository;

import com.apex.scaffold.authcenter.domain.model.DeviceTrust;
import java.time.LocalDateTime;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 设备信任存储库
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
public interface DeviceTrustRepository extends ReactiveCrudRepository<DeviceTrust, Long> {

    /**
     * 根据用户ID查询所有设备
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
    Flux<DeviceTrust> findByUserIdAndTenantId(String userId, String tenantId);

    /**
     * 根据用户ID和设备ID查询
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
     * @param deviceId TODO
     * @return TODO
     */
    Mono<DeviceTrust> findByUserIdAndDeviceIdAndTenantId(String userId, String deviceId, String tenantId);

    /**
     * 根据状态和过期时间查询
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
     * @param status TODO
     * @param expiresAt TODO
     * @return TODO
     */
    Flux<DeviceTrust> findByStatusAndExpiresAtBefore(DeviceTrust.TrustStatus status, LocalDateTime expiresAt);

    /**
     * 根据租户ID查询
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
    Flux<DeviceTrust> findByTenantId(String tenantId);

    /**
     * 根据风险评分查询高风险设备
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param riskScore TODO
     * @return TODO
     */
    Flux<DeviceTrust> findByRiskScoreGreaterThan(Integer riskScore);
}
