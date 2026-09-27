package com.apex.scaffold.authcenter.domain.service;

import com.apex.scaffold.authcenter.domain.model.DeviceTrust;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 设备信任管理服务接口
 * @author Apex Team
 * @since 1.2.0
 */
public interface DeviceTrustService {

    /**
     * 注册新设备
     * 
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param deviceId 设备ID
     * @param deviceName 设备名称
     * @param deviceType 设备类型
     * @param deviceFingerprint 设备指纹
     * @param ip IP地址
     * @param location 位置
     * @param metadata 设备元数据
     * @return 设备信任记录
     */
    Mono<DeviceTrust> registerDevice(String userId, String tenantId, String deviceId,
                                     String deviceName, DeviceTrust.DeviceType deviceType,
                                     String deviceFingerprint, String ip, String location,
                                     String metadata);

    /**
     * 验证设备信任
     * 
     * @param userId 用户ID
     * @param deviceId 设备ID
     * @return 信任级别
     */
    Mono<DeviceTrust.TrustLevel> verifyDeviceTrust(String userId, String deviceId);

    /**
     * 更新设备使用信息
     */
    Mono<Void> updateDeviceUsage(String userId, String deviceId, String ip, String location);

    /**
     * 提升设备信任级别
     */
    Mono<Void> upgradeTrustLevel(Long deviceId, String operator);

    /**
     * 降低设备信任级别
     */
    Mono<Void> downgradeTrustLevel(Long deviceId, String operator);

    /**
     * 撤销设备信任
     */
    Mono<Void> revokeDeviceTrust(Long deviceId, String operator);

    /**
     * 查询用户的可信设备列表
     */
    Flux<DeviceTrust> getUserTrustedDevices(String userId);

    /**
     * 检查设备是否可信
     */
    Mono<Boolean> isDeviceTrusted(String userId, String deviceId);

    /**
     * 计算设备风险评分
     */
    Mono<Integer> calculateDeviceRiskScore(Long deviceId);

    /**
     * 检测设备异常
     * 
     * @param userId 用户ID
     * @param deviceId 设备ID
     * @param ip 当前IP
     * @param location 当前位置
     * @return 是否异常
     */
    Mono<Boolean> detectDeviceAnomaly(String userId, String deviceId, String ip, String location);

    /**
     * 清理过期设备
     */
    Mono<Long> cleanupExpiredDevices();

    /**
     * 获取设备详情
     */
    Mono<DeviceTrust> getDeviceDetails(Long deviceId);

    /**
     * 延长设备信任有效期
     */
    Mono<Void> extendDeviceTrust(Long deviceId, int days, String operator);
}
