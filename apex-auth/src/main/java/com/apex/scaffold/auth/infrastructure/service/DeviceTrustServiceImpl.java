package com.apex.scaffold.auth.infrastructure.service;

import com.apex.scaffold.auth.domain.model.DeviceTrust;
import com.apex.scaffold.auth.domain.service.DeviceTrustService;
import com.apex.scaffold.auth.infrastructure.repository.DeviceTrustRepository;
import com.apex.scaffold.common.audit.AuditLogService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 设备信任管理服务实现
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTrustServiceImpl implements DeviceTrustService {

    private final DeviceTrustRepository deviceTrustRepository;
    private final AuditLogService auditLogService;

    private static final int DEFAULT_TRUST_DAYS = 30;
    private static final int AUTH_COUNT_THRESHOLD_FOR_TRUST_UPGRADE = 10;

    @Override
    public Mono<DeviceTrust> registerDevice(
            String userId,
            String tenantId,
            String deviceId,
            String deviceName,
            DeviceTrust.DeviceType deviceType,
            String deviceFingerprint,
            String ip,
            String location,
            String metadata) {
        // 检查设备是否已注册
        return deviceTrustRepository
                .findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId)
                .flatMap(existing -> {
                    // 设备已存在，更新信息
                    existing.updateLastUsed(ip, location);
                    existing.setUpdatedAt(LocalDateTime.now());
                    return deviceTrustRepository.save(existing);
                })
                .switchIfEmpty(
                        // 新设备注册
                        Mono.defer(() -> {
                            DeviceTrust newDevice = DeviceTrust.builder()
                                    .userId(userId)
                                    .tenantId(tenantId)
                                    .deviceId(deviceId)
                                    .deviceName(deviceName)
                                    .deviceType(deviceType)
                                    .deviceFingerprint(deviceFingerprint)
                                    .trustLevel(DeviceTrust.TrustLevel.UNTRUSTED)
                                    .status(DeviceTrust.TrustStatus.PENDING)
                                    .firstAuthAt(LocalDateTime.now())
                                    .lastAuthAt(LocalDateTime.now())
                                    .lastIp(ip)
                                    .lastLocation(location)
                                    .authCount(1)
                                    .riskScore(50)
                                    .metadata(metadata)
                                    .expiresAt(LocalDateTime.now().plusDays(DEFAULT_TRUST_DAYS))
                                    .createdAt(LocalDateTime.now())
                                    .build();

                            return deviceTrustRepository
                                    .save(newDevice)
                                    .flatMap(saved -> auditLogService
                                            .log(
                                                    "DEVICE_REGISTERED",
                                                    userId,
                                                    tenantId,
                                                    "注册新设备: " + deviceName,
                                                    saved.getId().toString(),
                                                    null)
                                            .thenReturn(saved))
                                    .doOnNext(saved -> log.info(
                                            "注册新设备: userId={}, deviceId={}, name={}", userId, deviceId, deviceName));
                        }));
    }

    @Override
    @Cacheable(value = "device-trust", key = "#tenantId + ':' + #userId + ':' + #deviceId")
    public Mono<DeviceTrust.TrustLevel> verifyDeviceTrust(String userId, String tenantId, String deviceId) {
        return deviceTrustRepository
                .findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId)
                .filter(device -> device.isActive() && !device.isExpired())
                .map(DeviceTrust::getTrustLevel)
                .defaultIfEmpty(DeviceTrust.TrustLevel.UNTRUSTED);
    }

    /**
     * 更新设备使用信息，记录最近使用IP与位置并重算风险评分，满足条件时自动提升信任级别
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param deviceId 设备ID
     * @param ip 本次使用的IP地址
     * @param location 本次使用的地理位置
     */
    @Override
    @CacheEvict(value = "device-trust", key = "#tenantId + ':' + #userId + ':' + #deviceId")
    public Mono<Void> updateDeviceUsage(String userId, String tenantId, String deviceId, String ip, String location) {
        return deviceTrustRepository
                .findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId)
                .flatMap(device -> {
                    device.updateLastUsed(ip, location);
                    device.calculateRiskScore();
                    device.setUpdatedAt(LocalDateTime.now());

                    if (device.getAuthCount() > AUTH_COUNT_THRESHOLD_FOR_TRUST_UPGRADE
                            && device.getTrustLevel() == DeviceTrust.TrustLevel.UNTRUSTED) {
                        device.upgradeTrustLevel();
                    }

                    return deviceTrustRepository.save(device);
                })
                .then();
    }

    /**
     * 提升设备信任级别并记录审计日志
     *
     * @param deviceId 设备记录ID
     * @param operator 执行操作的运维人员标识
     */
    @Override
    @CacheEvict(value = "device-trust", allEntries = true)
    public Mono<Void> upgradeTrustLevel(Long deviceId, String operator) {
        return deviceTrustRepository
                .findById(deviceId)
                .flatMap(device -> {
                    DeviceTrust.TrustLevel oldLevel = device.getTrustLevel();
                    device.upgradeTrustLevel();
                    device.setUpdatedAt(LocalDateTime.now());

                    return deviceTrustRepository
                            .save(device)
                            .flatMap(saved -> auditLogService
                                    .log(
                                            "DEVICE_TRUST_UPGRADED",
                                            operator,
                                            device.getTenantId(),
                                            String.format("提升设备信任级别: %s -> %s", oldLevel, saved.getTrustLevel()),
                                            saved.getId().toString(),
                                            null)
                                    .thenReturn(saved))
                            .doOnNext(saved -> log.info(
                                    "提升设备信任级别: deviceId={}, {} -> {}, operator={}",
                                    deviceId,
                                    oldLevel,
                                    saved.getTrustLevel(),
                                    operator));
                })
                .then();
    }

    /**
     * 降低设备信任级别并记录审计日志
     *
     * @param deviceId 设备记录ID
     * @param operator 执行操作的运维人员标识
     */
    @Override
    @CacheEvict(value = "device-trust", allEntries = true)
    public Mono<Void> downgradeTrustLevel(Long deviceId, String operator) {
        return deviceTrustRepository
                .findById(deviceId)
                .flatMap(device -> {
                    DeviceTrust.TrustLevel oldLevel = device.getTrustLevel();
                    device.downgradeTrustLevel();
                    device.setUpdatedAt(LocalDateTime.now());

                    return deviceTrustRepository
                            .save(device)
                            .flatMap(saved -> auditLogService
                                    .log(
                                            "DEVICE_TRUST_DOWNGRADED",
                                            operator,
                                            device.getTenantId(),
                                            String.format("降低设备信任级别: %s -> %s", oldLevel, saved.getTrustLevel()),
                                            saved.getId().toString(),
                                            null)
                                    .thenReturn(saved))
                            .doOnNext(saved -> log.info(
                                    "降低设备信任级别: deviceId={}, {} -> {}, operator={}",
                                    deviceId,
                                    oldLevel,
                                    saved.getTrustLevel(),
                                    operator));
                })
                .then();
    }

    /**
     * 撤销设备信任，将设备状态置为已撤销且信任级别降为不可信
     *
     * @param deviceId 设备记录ID
     * @param operator 执行操作的运维人员标识
     */
    @Override
    @CacheEvict(value = "device-trust", allEntries = true)
    public Mono<Void> revokeDeviceTrust(Long deviceId, String operator) {
        return deviceTrustRepository
                .findById(deviceId)
                .flatMap(device -> {
                    device.setStatus(DeviceTrust.TrustStatus.REVOKED);
                    device.setTrustLevel(DeviceTrust.TrustLevel.UNTRUSTED);
                    device.setUpdatedAt(LocalDateTime.now());

                    return deviceTrustRepository
                            .save(device)
                            .flatMap(saved -> auditLogService
                                    .log(
                                            "DEVICE_TRUST_REVOKED",
                                            operator,
                                            device.getTenantId(),
                                            "撤销设备信任: " + device.getDeviceName(),
                                            saved.getId().toString(),
                                            null)
                                    .thenReturn(saved))
                            .doOnNext(saved -> log.info("撤销设备信任: deviceId={}, operator={}", deviceId, operator));
                })
                .then();
    }

    /**
     * 查询用户当前有效（激活且未过期）的可信设备列表
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 有效的设备信任记录流
     */
    @Override
    public Flux<DeviceTrust> getUserTrustedDevices(String userId, String tenantId) {
        return deviceTrustRepository
                .findByUserIdAndTenantId(userId, tenantId)
                .filter(device -> device.isActive() && !device.isExpired());
    }

    /**
     * 检查设备是否可信（激活、未过期且信任级别达到中级及以上）
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param deviceId 设备ID
     * @return 设备可信返回true，设备不存在或不可信返回false
     */
    @Override
    public Mono<Boolean> isDeviceTrusted(String userId, String tenantId, String deviceId) {
        return deviceTrustRepository
                .findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId)
                .map(device -> device.isActive()
                        && !device.isExpired()
                        && device.getTrustLevel().getLevel() >= DeviceTrust.TrustLevel.MEDIUM.getLevel())
                .defaultIfEmpty(false);
    }

    /**
     * 计算并持久化设备风险评分
     *
     * @param deviceId 设备记录ID
     * @return 最新的风险评分，设备不存在时返回默认值100
     */
    @Override
    public Mono<Integer> calculateDeviceRiskScore(Long deviceId) {
        return deviceTrustRepository
                .findById(deviceId)
                .flatMap(device -> {
                    device.calculateRiskScore();
                    return deviceTrustRepository.save(device).map(DeviceTrust::getRiskScore);
                })
                .defaultIfEmpty(100);
    }

    @Override
    public Mono<Boolean> detectDeviceAnomaly(
            String userId, String tenantId, String deviceId, String ip, String location) {
        return deviceTrustRepository
                .findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId)
                .flatMap(device -> {
                    boolean anomaly = false;

                    if (device.getLastIp() != null && !device.getLastIp().equals(ip)) {
                        if (device.getLastLocation() != null && !isSameRegion(device.getLastLocation(), location)) {
                            anomaly = true;
                            log.warn("检测到设备异常: 位置变化 {} -> {}", device.getLastLocation(), location);
                        }
                    }

                    if (device.getLastAuthAt() != null
                            && device.getLastAuthAt().plusDays(90).isBefore(LocalDateTime.now())) {
                        anomaly = true;
                        log.warn("检测到设备异常: 长时间未使用");
                    }

                    if (device.isHighRisk()) {
                        anomaly = true;
                        log.warn("检测到设备异常: 风险评分过高 {}", device.getRiskScore());
                    }

                    if (!anomaly) {
                        return Mono.just(false);
                    }

                    device.downgradeTrustLevel();
                    device.setStatus(DeviceTrust.TrustStatus.LOCKED);
                    return deviceTrustRepository.save(device).flatMap(saved -> auditLogService
                            .log(
                                    userId,
                                    saved.getTenantId(),
                                    "检测到设备异常: " + saved.getDeviceName(),
                                    "DEVICE_ANOMALY_DETECTED",
                                    "FAILURE",
                                    null)
                            .thenReturn(true));
                })
                .defaultIfEmpty(false);
    }

    /**
     * 清理过期设备，将已过有效期的激活设备状态置为过期
     *
     * @return 清理的过期设备数量
     */
    @Override
    public Mono<Long> cleanupExpiredDevices() {
        return deviceTrustRepository
                .findByStatusAndExpiresAtBefore(DeviceTrust.TrustStatus.ACTIVE, LocalDateTime.now())
                .flatMap(device -> {
                    device.setStatus(DeviceTrust.TrustStatus.EXPIRED);
                    return deviceTrustRepository.save(device);
                })
                .count()
                .doOnSuccess(count -> log.info("清理过期设备: {} 个", count));
    }

    /**
     * 根据设备记录ID获取设备信任详情
     *
     * @param deviceId 设备记录ID
     * @return 设备信任记录，不存在时为空Mono
     */
    @Override
    public Mono<DeviceTrust> getDeviceDetails(Long deviceId) {
        return deviceTrustRepository.findById(deviceId);
    }

    /**
     * 延长设备信任有效期并记录审计日志
     *
     * @param deviceId 设备记录ID
     * @param days 延长的天数
     * @param operator 执行操作的运维人员标识
     */
    @Override
    @CacheEvict(value = "device-trust", allEntries = true)
    public Mono<Void> extendDeviceTrust(Long deviceId, int days, String operator) {
        return deviceTrustRepository
                .findById(deviceId)
                .flatMap(device -> {
                    LocalDateTime newExpiry = device.getExpiresAt().plusDays(days);
                    device.setExpiresAt(newExpiry);
                    device.setUpdatedAt(LocalDateTime.now());

                    return deviceTrustRepository
                            .save(device)
                            .flatMap(saved -> auditLogService
                                    .log(
                                            operator,
                                            device.getTenantId(),
                                            String.format("延长设备信任有效期 %d 天至 %s", days, newExpiry),
                                            "DEVICE_TRUST_EXTENDED",
                                            "SUCCESS",
                                            null)
                                    .thenReturn(saved))
                            .doOnNext(saved ->
                                    log.info("延长设备信任有效期: deviceId={}, days={}, operator={}", deviceId, days, operator));
                })
                .then();
    }

    /**
     * 判断是否同一地区
     */
    private boolean isSameRegion(String location1, String location2) {
        if (location1 == null || location2 == null) {
            return false;
        }
        // 简化实现：比较国家代码
        String[] parts1 = location1.split("/");
        String[] parts2 = location2.split("/");
        return parts1.length > 0 && parts2.length > 0 && parts1[0].equals(parts2[0]);
    }
}
