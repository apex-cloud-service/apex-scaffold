package com.apex.scaffold.authcenter.infrastructure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.authcenter.domain.model.DeviceTrust;
import com.apex.scaffold.authcenter.infrastructure.repository.DeviceTrustRepository;
import com.apex.scaffold.common.audit.AuditLogService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceTrustServiceImpl 单元测试")
class DeviceTrustServiceImplTest {

    @Mock
    private DeviceTrustRepository deviceTrustRepository;

    @Mock
    private AuditLogService auditLogService;

    private DeviceTrustServiceImpl deviceTrustService;

    @BeforeEach
    void setUp() {
        deviceTrustService = new DeviceTrustServiceImpl(deviceTrustRepository, auditLogService);
        // Mock auditLogService for all possible method signatures
        when(auditLogService.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());
        when(auditLogService.log(anyString(), anyString(), anyString(), anyString(), anyString(), isNull()))
                .thenReturn(Mono.empty());
        lenient()
                .when(auditLogService.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("注册新设备 - 成功")
    void registerDevice_newDevice() {
        String userId = "user123";
        String tenantId = "tenant1";
        String deviceId = "device123";
        String deviceName = "iPhone 13";
        DeviceTrust.DeviceType deviceType = DeviceTrust.DeviceType.MOBILE;
        String deviceFingerprint = "fingerprint123";
        String ip = "192.168.1.1";
        String location = "Beijing";
        String metadata = "{}";

        DeviceTrust savedDevice = DeviceTrust.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .deviceId(deviceId)
                .deviceName(deviceName)
                .deviceType(deviceType)
                .trustLevel(DeviceTrust.TrustLevel.UNTRUSTED)
                .status(DeviceTrust.TrustStatus.PENDING)
                .build();

        when(deviceTrustRepository.findByUserIdAndDeviceId(userId, deviceId)).thenReturn(Mono.empty());
        when(deviceTrustRepository.save(any(DeviceTrust.class))).thenReturn(Mono.just(savedDevice));

        Mono<DeviceTrust> result = deviceTrustService.registerDevice(
                userId, tenantId, deviceId, deviceName, deviceType, deviceFingerprint, ip, location, metadata);

        StepVerifier.create(result)
                .assertNext(device -> {
                    assertThat(device.getId()).isEqualTo(1L);
                    assertThat(device.getUserId()).isEqualTo(userId);
                    assertThat(device.getDeviceId()).isEqualTo(deviceId);
                })
                .verifyComplete();

        verify(deviceTrustRepository).findByUserIdAndDeviceId(userId, deviceId);
        verify(deviceTrustRepository).save(any(DeviceTrust.class));
    }

    @Test
    @DisplayName("注册设备 - 设备已存在，更新信息")
    void registerDevice_existingDevice() {
        String userId = "user123";
        String deviceId = "device123";
        String ip = "192.168.1.2";
        String location = "Shanghai";

        DeviceTrust existingDevice = DeviceTrust.builder()
                .id(1L)
                .userId(userId)
                .deviceId(deviceId)
                .deviceName("iPhone 13")
                .trustLevel(DeviceTrust.TrustLevel.HIGH)
                .lastIp("192.168.1.1")
                .lastLocation("Beijing")
                .authCount(5)
                .build();

        when(deviceTrustRepository.findByUserIdAndDeviceId(userId, deviceId)).thenReturn(Mono.just(existingDevice));
        when(deviceTrustRepository.save(any(DeviceTrust.class))).thenReturn(Mono.just(existingDevice));

        Mono<DeviceTrust> result = deviceTrustService.registerDevice(
                userId, "tenant1", deviceId, "iPhone 13", DeviceTrust.DeviceType.MOBILE, "fp", ip, location, "{}");

        StepVerifier.create(result)
                .assertNext(device -> {
                    assertThat(device.getId()).isEqualTo(1L);
                })
                .verifyComplete();

        verify(deviceTrustRepository).findByUserIdAndDeviceId(userId, deviceId);
        verify(deviceTrustRepository).save(any(DeviceTrust.class));
    }

    @Test
    @DisplayName("验证设备信任 - 设备高信任")
    void verifyDeviceTrust_trusted() {
        String userId = "user123";
        String deviceId = "device123";

        DeviceTrust device = DeviceTrust.builder()
                .id(1L)
                .userId(userId)
                .deviceId(deviceId)
                .trustLevel(DeviceTrust.TrustLevel.HIGH)
                .status(DeviceTrust.TrustStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(deviceTrustRepository.findByUserIdAndDeviceId(userId, deviceId)).thenReturn(Mono.just(device));

        Mono<DeviceTrust.TrustLevel> result = deviceTrustService.verifyDeviceTrust(userId, deviceId);

        StepVerifier.create(result).expectNext(DeviceTrust.TrustLevel.HIGH).verifyComplete();

        verify(deviceTrustRepository).findByUserIdAndDeviceId(userId, deviceId);
    }

    @Test
    @DisplayName("验证设备信任 - 设备不存在")
    void verifyDeviceTrust_notFound() {
        String userId = "user123";
        String deviceId = "device123";

        when(deviceTrustRepository.findByUserIdAndDeviceId(userId, deviceId)).thenReturn(Mono.empty());

        Mono<DeviceTrust.TrustLevel> result = deviceTrustService.verifyDeviceTrust(userId, deviceId);

        StepVerifier.create(result).expectNext(DeviceTrust.TrustLevel.UNTRUSTED).verifyComplete();

        verify(deviceTrustRepository).findByUserIdAndDeviceId(userId, deviceId);
    }

    @Test
    @DisplayName("更新设备使用信息 - 成功")
    void updateDeviceUsage_success() {
        String userId = "user123";
        String deviceId = "device123";
        String ip = "192.168.1.2";
        String location = "Shanghai";

        DeviceTrust device = DeviceTrust.builder()
                .id(1L)
                .userId(userId)
                .deviceId(deviceId)
                .trustLevel(DeviceTrust.TrustLevel.UNTRUSTED)
                .authCount(5)
                .lastIp("192.168.1.1")
                .lastLocation("Beijing")
                .build();

        when(deviceTrustRepository.findByUserIdAndDeviceId(userId, deviceId)).thenReturn(Mono.just(device));
        when(deviceTrustRepository.save(any(DeviceTrust.class))).thenReturn(Mono.just(device));

        Mono<Void> result = deviceTrustService.updateDeviceUsage(userId, deviceId, ip, location);

        StepVerifier.create(result).verifyComplete();

        verify(deviceTrustRepository).findByUserIdAndDeviceId(userId, deviceId);
        verify(deviceTrustRepository).save(any(DeviceTrust.class));
    }

    @Test
    @DisplayName("提升设备信任级别 - 成功")
    void upgradeTrustLevel_success() {
        Long deviceId = 1L;
        String operator = "admin";

        DeviceTrust device = DeviceTrust.builder()
                .id(deviceId)
                .userId("user123")
                .tenantId("tenant1")
                .deviceId("device123")
                .trustLevel(DeviceTrust.TrustLevel.UNTRUSTED)
                .build();

        when(deviceTrustRepository.findById(deviceId)).thenReturn(Mono.just(device));
        when(deviceTrustRepository.save(any(DeviceTrust.class))).thenReturn(Mono.just(device));

        Mono<Void> result = deviceTrustService.upgradeTrustLevel(deviceId, operator);

        StepVerifier.create(result).verifyComplete();

        verify(deviceTrustRepository).findById(deviceId);
        verify(deviceTrustRepository).save(any(DeviceTrust.class));
    }

    @Test
    @DisplayName("降低设备信任级别 - 成功")
    void downgradeTrustLevel_success() {
        Long deviceId = 1L;
        String operator = "admin";

        DeviceTrust device = DeviceTrust.builder()
                .id(deviceId)
                .userId("user123")
                .tenantId("tenant1")
                .deviceId("device123")
                .trustLevel(DeviceTrust.TrustLevel.HIGH)
                .build();

        when(deviceTrustRepository.findById(deviceId)).thenReturn(Mono.just(device));
        when(deviceTrustRepository.save(any(DeviceTrust.class))).thenReturn(Mono.just(device));

        Mono<Void> result = deviceTrustService.downgradeTrustLevel(deviceId, operator);

        StepVerifier.create(result).verifyComplete();

        verify(deviceTrustRepository).findById(deviceId);
        verify(deviceTrustRepository).save(any(DeviceTrust.class));
    }

    @Test
    @DisplayName("撤销设备信任 - 成功")
    void revokeDeviceTrust_success() {
        Long deviceId = 1L;
        String operator = "admin";

        DeviceTrust device = DeviceTrust.builder()
                .id(deviceId)
                .userId("user123")
                .tenantId("tenant1")
                .deviceId("device123")
                .trustLevel(DeviceTrust.TrustLevel.HIGH)
                .status(DeviceTrust.TrustStatus.ACTIVE)
                .build();

        when(deviceTrustRepository.findById(deviceId)).thenReturn(Mono.just(device));
        when(deviceTrustRepository.save(any(DeviceTrust.class))).thenReturn(Mono.just(device));

        Mono<Void> result = deviceTrustService.revokeDeviceTrust(deviceId, operator);

        StepVerifier.create(result).verifyComplete();

        verify(deviceTrustRepository).findById(deviceId);
        verify(deviceTrustRepository).save(any(DeviceTrust.class));
    }
}
