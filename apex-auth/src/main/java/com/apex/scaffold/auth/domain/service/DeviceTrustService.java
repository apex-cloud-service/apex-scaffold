package com.apex.scaffold.auth.domain.service;

import com.apex.scaffold.auth.domain.model.DeviceTrust;
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
    Mono<DeviceTrust> registerDevice(
            String userId,
            String tenantId,
            String deviceId,
            String deviceName,
            DeviceTrust.DeviceType deviceType,
            String deviceFingerprint,
            String ip,
            String location,
            String metadata);

    /**
     * 验证设备信任
     *
     * @param userId 用户ID
     * @param deviceId 设备ID
     * @return 信任级别
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Mono<DeviceTrust.TrustLevel> verifyDeviceTrust(String userId, String tenantId, String deviceId);

    /**
     * 更新设备使用信息
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param deviceId 设备ID
     * @param ip 当前IP地址
     * @param location 当前位置
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Mono<Void> updateDeviceUsage(String userId, String tenantId, String deviceId, String ip, String location);

    /**
     * 提升设备信任级别
     *
     * @param deviceId 设备ID
     * @param operator 操作人
     * @return 操作完成信号
     */
    Mono<Void> upgradeTrustLevel(Long deviceId, String operator);

    /**
     * 降低设备信任级别
     *
     * @param deviceId 设备ID
     * @param operator 操作人
     * @return 操作完成信号
     */
    Mono<Void> downgradeTrustLevel(Long deviceId, String operator);

    /**
     * 撤销设备信任
     *
     * @param deviceId 设备ID
     * @param operator 操作人
     * @return 操作完成信号
     */
    Mono<Void> revokeDeviceTrust(Long deviceId, String operator);

    /**
     * 查询用户的可信设备列表
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 可信设备列表
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Flux<DeviceTrust> getUserTrustedDevices(String userId, String tenantId);

    /**
     * 检查设备是否可信
     *
     * @param userId 用户ID
     * @param deviceId 设备ID
     * @return 是否可信
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Mono<Boolean> isDeviceTrusted(String userId, String tenantId, String deviceId);

    /**
     * 计算设备风险评分
     *
     * @param deviceId 设备ID
     * @return 风险评分
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
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Mono<Boolean> detectDeviceAnomaly(String userId, String tenantId, String deviceId, String ip, String location);

    /**
     * 清理过期设备
     *
     * @return 清理的设备数量
     */
    Mono<Long> cleanupExpiredDevices();

    /**
     * 获取设备详情
     *
     * @param deviceId 设备ID
     * @return 设备信任记录，不存在时为空
     */
    Mono<DeviceTrust> getDeviceDetails(Long deviceId);

    /**
     * 延长设备信任有效期
     *
     * @param deviceId 设备ID
     * @param days 延长天数
     * @param operator 操作人
     * @return 操作完成信号
     */
    Mono<Void> extendDeviceTrust(Long deviceId, int days, String operator);
}
