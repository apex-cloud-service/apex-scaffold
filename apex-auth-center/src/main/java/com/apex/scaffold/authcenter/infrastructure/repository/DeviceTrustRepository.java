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
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 设备信任记录列表
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Flux<DeviceTrust> findByUserIdAndTenantId(String userId, String tenantId);

    /**
     * 根据用户ID和设备ID查询
     *
     * @param userId 用户ID
     * @param deviceId 设备ID
     * @param tenantId 租户ID
     * @return 设备信任记录，不存在时为空
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Mono<DeviceTrust> findByUserIdAndDeviceIdAndTenantId(String userId, String deviceId, String tenantId);

    /**
     * 根据状态和过期时间查询
     *
     * @param status 信任状态
     * @param expiresAt 过期时间
     * @return 在指定时间前过期的该状态设备列表
     */
    Flux<DeviceTrust> findByStatusAndExpiresAtBefore(DeviceTrust.TrustStatus status, LocalDateTime expiresAt);

    /**
     * 根据租户ID查询
     *
     * @param tenantId 租户ID
     * @return 租户下的设备信任记录列表
     */
    Flux<DeviceTrust> findByTenantId(String tenantId);

    /**
     * 根据风险评分查询高风险设备
     *
     * @param riskScore 风险评分阈值
     * @return 风险评分大于阈值的高风险设备列表
     */
    Flux<DeviceTrust> findByRiskScoreGreaterThan(Integer riskScore);
}
