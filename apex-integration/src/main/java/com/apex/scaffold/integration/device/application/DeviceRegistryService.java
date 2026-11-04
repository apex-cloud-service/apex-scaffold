package com.apex.scaffold.integration.device.application;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.device.domain.Device;
import com.apex.scaffold.integration.device.domain.DeviceKey;
import com.apex.scaffold.integration.device.domain.DeviceShadow;
import com.apex.scaffold.integration.device.domain.MqttTopicPolicy;
import com.apex.scaffold.integration.device.domain.SecretHasher;
import com.apex.scaffold.integration.device.domain.repository.DeviceRepository;
import com.apex.scaffold.integration.device.domain.repository.DeviceShadowRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 设备注册 / 注销与设备凭据认证用例（方案 M5 / 批次 E3）。
 *
 * <p>注册：校验设备标识（topic 安全字符集）→ 生成随机 secret → PBKDF2 哈希落库
 * （明文仅在 {@link DeviceRegistration#plainSecret} 出现一次，不落库不落日志）→
 * 同步创建影子（version=0）。
 *
 * <p>认证（MQTT 钩子复用入口）：按 device_key 查租户设备 → PBKDF2 比对 secret →
 * 通过则刷新在线状态与活跃时间。失败一律以 {@code UNAUTHORIZED} 语义拒绝，
 * 不区分"设备不存在"与"密码错误"，避免设备枚举。
 *
 * <p>topic ACL 校验复用 {@link MqttTopicPolicy}（按租户命名空间
 * {@code apex/{tenantCode}/{deviceKey}/up|down}）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceRegistryService {

    private final DeviceRepository deviceRepository;

    private final DeviceShadowRepository shadowRepository;

    private final ObjectMapper objectMapper;

    /**
     * 注册设备。
     *
     * @param tenantId        租户编码
     * @param deviceKey       设备标识
     * @param certFingerprint X.509 证书指纹（可空）
     * @param firmwareVersion 固件版本（可空）
     * @param labels          标签（可空，JSON 对象）
     * @return 注册结果（含一次性明文 secret）
     */
    public Mono<DeviceRegistration> register(
            String tenantId, String deviceKey, String certFingerprint, String firmwareVersion, String labels) {
        return Mono.defer(() -> {
            DeviceKey key = DeviceKey.of(deviceKey);
            if (labels != null && !labels.isBlank()) {
                validateLabels(labels);
            }
            String plainSecret = SecretHasher.generateSecret();
            Device device = new Device();
            device.setTenantId(tenantId);
            device.setDeviceKey(key.value());
            device.setSecretHash(SecretHasher.hash(plainSecret));
            device.setCertFingerprint(certFingerprint);
            device.setFirmwareVersion(firmwareVersion);
            device.setStatus(Device.STATUS_OFFLINE);
            device.setLabels(labels);
            return deviceRepository.insert(device).flatMap(saved -> shadowRepository
                    .insert(shadowOf(saved))
                    .onErrorResume(e -> {
                        log.warn("设备影子创建失败，设备保留: tenant={}, device={}", tenantId, saved.getDeviceKey(), e);
                        return Mono.just(emptyShadow(saved));
                    })
                    .thenReturn(new DeviceRegistration(saved, plainSecret, false)));
        });
    }

    /**
     * 注销设备（逻辑删除设备；影子按 device_key 保留由设备删除联动清理）。
     *
     * @param tenantId 租户编码
     * @param id       设备主键
     */
    public Mono<Void> deregister(String tenantId, Long id) {
        return deviceRepository.delete(tenantId, id);
    }

    /**
     * 设备凭据认证（MQTT HTTP 认证钩子复用入口）。
     *
     * @param tenantId  租户编码
     * @param deviceKey 设备标识
     * @param rawSecret 明文 secret
     * @return 认证通过后的设备；失败以 UNAUTHORIZED 终止
     */
    public Mono<Device> authenticate(String tenantId, String deviceKey, String rawSecret) {
        if (tenantId == null || tenantId.isBlank() || deviceKey == null || deviceKey.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "设备认证参数缺失"));
        }
        return deviceRepository
                .findByKey(tenantId, deviceKey)
                .filter(device -> SecretHasher.matches(rawSecret == null ? "" : rawSecret, device.getSecretHash()))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "设备认证失败")))
                .flatMap(device -> deviceRepository
                        .touchActivity(tenantId, deviceKey, true)
                        .thenReturn(device));
    }

    /**
     * 校验 topic 是否属于设备本租户命名空间（ACL 复用入口）。
     *
     * @param tenantCode 租户编码
     * @param deviceKey  设备标识
     * @param topic      待校验 topic
     * @return 是否允许
     */
    public boolean authorizeTopic(String tenantCode, String deviceKey, String topic) {
        return MqttTopicPolicy.isAllowed(tenantCode, deviceKey, topic);
    }

    private DeviceShadow shadowOf(Device device) {
        DeviceShadow shadow = new DeviceShadow();
        shadow.setTenantId(device.getTenantId());
        shadow.setDeviceKey(device.getDeviceKey());
        shadow.setVersion(0);
        return shadow;
    }

    private DeviceShadow emptyShadow(Device device) {
        DeviceShadow shadow = new DeviceShadow();
        shadow.setTenantId(device.getTenantId());
        shadow.setDeviceKey(device.getDeviceKey());
        shadow.setVersion(0);
        return shadow;
    }

    private void validateLabels(String labels) {
        try {
            objectMapper.readValue(labels, new TypeReference<Object>() {});
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "设备标签 JSON 非法");
        }
    }
}
