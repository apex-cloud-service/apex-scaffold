package com.apex.scaffold.authcenter.infrastructure.repository;

import com.apex.scaffold.authcenter.domain.model.DeviceTrust;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

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
    Flux<DeviceTrust> findByUserId(String userId);

    /**
     * 根据用户ID和设备ID查询
     */
    Mono<DeviceTrust> findByUserIdAndDeviceId(String userId, String deviceId);

    /**
     * 根据状态和过期时间查询
     */
    Flux<DeviceTrust> findByStatusAndExpiresAtBefore(DeviceTrust.TrustStatus status, LocalDateTime expiresAt);

    /**
     * 根据租户ID查询
     */
    Flux<DeviceTrust> findByTenantId(String tenantId);

    /**
     * 根据风险评分查询高风险设备
     */
    Flux<DeviceTrust> findByRiskScoreGreaterThan(Integer riskScore);
}
