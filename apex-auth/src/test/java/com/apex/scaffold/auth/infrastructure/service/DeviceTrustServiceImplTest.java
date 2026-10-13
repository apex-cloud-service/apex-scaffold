package com.apex.scaffold.auth.infrastructure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.auth.domain.model.DeviceTrust;
import com.apex.scaffold.auth.infrastructure.repository.DeviceTrustRepository;
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

        when(deviceTrustRepository.findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId))
                .thenReturn(Mono.empty());
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

        verify(deviceTrustRepository).findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId);
        verify(deviceTrustRepository).save(any(DeviceTrust.class));
    }

    @Test
    @DisplayName("注册新设备时审计失败应使注册失败")
    void registerDevice_auditFailure_failsOperation() {
        String userId = "user123";
        String tenantId = "tenant1";
        String deviceId = "device123";
        DeviceTrust savedDevice = DeviceTrust.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .deviceId(deviceId)
                .build();

        when(deviceTrustRepository.findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId))
                .thenReturn(Mono.empty());
        when(deviceTrustRepository.save(any(DeviceTrust.class))).thenReturn(Mono.just(savedDevice));
        when(auditLogService.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.error(new IllegalStateException("审计服务不可用")));

        StepVerifier.create(deviceTrustService.registerDevice(
                        userId,
                        tenantId,
                        deviceId,
                        "iPhone 13",
                        DeviceTrust.DeviceType.MOBILE,
                        "fingerprint",
                        "192.168.1.1",
                        "Beijing",
                        "{}"))
                .expectErrorMessage("审计服务不可用")
                .verify();
    }

    @Test
    @DisplayName("注册设备 - 设备已存在，更新信息")
    void registerDevice_existingDevice() {
        String userId = "user123";
        String tenantId = "tenant1";
        String deviceId = "device123";
        String ip = "192.168.1.2";
        String location = "Shanghai";

        DeviceTrust existingDevice = DeviceTrust.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .deviceId(deviceId)
                .deviceName("iPhone 13")
                .trustLevel(DeviceTrust.TrustLevel.HIGH)
                .lastIp("192.168.1.1")
                .lastLocation("Beijing")
                .authCount(5)
                .build();

        when(deviceTrustRepository.findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId))
                .thenReturn(Mono.just(existingDevice));
        when(deviceTrustRepository.save(any(DeviceTrust.class))).thenReturn(Mono.just(existingDevice));

        Mono<DeviceTrust> result = deviceTrustService.registerDevice(
                userId, tenantId, deviceId, "iPhone 13", DeviceTrust.DeviceType.MOBILE, "fp", ip, location, "{}");

        StepVerifier.create(result)
                .assertNext(device -> {
                    assertThat(device.getId()).isEqualTo(1L);
                })
                .verifyComplete();

        verify(deviceTrustRepository).findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId);
        verify(deviceTrustRepository).save(any(DeviceTrust.class));
    }

    @Test
    @DisplayName("验证设备信任 - 设备高信任")
    void verifyDeviceTrust_trusted() {
        String userId = "user123";
        String tenantId = "tenant1";
        String deviceId = "device123";

        DeviceTrust device = DeviceTrust.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .deviceId(deviceId)
                .trustLevel(DeviceTrust.TrustLevel.HIGH)
                .status(DeviceTrust.TrustStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(deviceTrustRepository.findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId))
                .thenReturn(Mono.just(device));

        Mono<DeviceTrust.TrustLevel> result = deviceTrustService.verifyDeviceTrust(userId, tenantId, deviceId);

        StepVerifier.create(result).expectNext(DeviceTrust.TrustLevel.HIGH).verifyComplete();

        verify(deviceTrustRepository).findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId);
    }

    @Test
    @DisplayName("验证设备信任 - 设备不存在")
    void verifyDeviceTrust_notFound() {
        String userId = "user123";
        String tenantId = "tenant1";
        String deviceId = "device123";

        when(deviceTrustRepository.findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId))
                .thenReturn(Mono.empty());

        Mono<DeviceTrust.TrustLevel> result = deviceTrustService.verifyDeviceTrust(userId, tenantId, deviceId);

        StepVerifier.create(result).expectNext(DeviceTrust.TrustLevel.UNTRUSTED).verifyComplete();

        verify(deviceTrustRepository).findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId);
    }

    @Test
    @DisplayName("更新设备使用信息 - 成功")
    void updateDeviceUsage_success() {
        String userId = "user123";
        String tenantId = "tenant1";
        String deviceId = "device123";
        String ip = "192.168.1.2";
        String location = "Shanghai";

        DeviceTrust device = DeviceTrust.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .deviceId(deviceId)
                .trustLevel(DeviceTrust.TrustLevel.UNTRUSTED)
                .authCount(5)
                .lastIp("192.168.1.1")
                .lastLocation("Beijing")
                .build();

        when(deviceTrustRepository.findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId))
                .thenReturn(Mono.just(device));
        when(deviceTrustRepository.save(any(DeviceTrust.class))).thenReturn(Mono.just(device));

        Mono<Void> result = deviceTrustService.updateDeviceUsage(userId, tenantId, deviceId, ip, location);

        StepVerifier.create(result).verifyComplete();

        verify(deviceTrustRepository).findByUserIdAndDeviceIdAndTenantId(userId, deviceId, tenantId);
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
    @DisplayName("提升设备信任时审计失败应使操作失败")
    void upgradeTrustLevel_auditFailure_failsOperation() {
        DeviceTrust device = DeviceTrust.builder()
                .id(1L)
                .tenantId("tenant1")
                .trustLevel(DeviceTrust.TrustLevel.UNTRUSTED)
                .build();

        when(deviceTrustRepository.findById(1L)).thenReturn(Mono.just(device));
        when(deviceTrustRepository.save(any(DeviceTrust.class))).thenReturn(Mono.just(device));
        when(auditLogService.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.error(new IllegalStateException("审计服务不可用")));

        StepVerifier.create(deviceTrustService.upgradeTrustLevel(1L, "admin"))
                .expectErrorMessage("审计服务不可用")
                .verify();
    }

    @Test
    @DisplayName("降低设备信任时审计失败应使操作失败")
    void downgradeTrustLevel_auditFailure_failsOperation() {
        DeviceTrust device = DeviceTrust.builder()
                .id(1L)
                .tenantId("tenant1")
                .trustLevel(DeviceTrust.TrustLevel.HIGH)
                .build();

        when(deviceTrustRepository.findById(1L)).thenReturn(Mono.just(device));
        when(deviceTrustRepository.save(any(DeviceTrust.class))).thenReturn(Mono.just(device));
        when(auditLogService.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.error(new IllegalStateException("审计服务不可用")));

        StepVerifier.create(deviceTrustService.downgradeTrustLevel(1L, "admin"))
                .expectErrorMessage("审计服务不可用")
                .verify();
    }

    @Test
    @DisplayName("撤销设备信任时审计失败应使操作失败")
    void revokeDeviceTrust_auditFailure_failsOperation() {
        DeviceTrust device = DeviceTrust.builder()
                .id(1L)
                .tenantId("tenant1")
                .deviceName("iPhone 13")
                .trustLevel(DeviceTrust.TrustLevel.HIGH)
                .build();

        when(deviceTrustRepository.findById(1L)).thenReturn(Mono.just(device));
        when(deviceTrustRepository.save(any(DeviceTrust.class))).thenReturn(Mono.just(device));
        when(auditLogService.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.error(new IllegalStateException("审计服务不可用")));

        StepVerifier.create(deviceTrustService.revokeDeviceTrust(1L, "admin"))
                .expectErrorMessage("审计服务不可用")
                .verify();
    }

    @Test
    @DisplayName("延长设备信任时审计失败应使操作失败")
    void extendDeviceTrust_auditFailure_failsOperation() {
        DeviceTrust device = DeviceTrust.builder()
                .id(1L)
                .tenantId("tenant1")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(deviceTrustRepository.findById(1L)).thenReturn(Mono.just(device));
        when(deviceTrustRepository.save(any(DeviceTrust.class))).thenReturn(Mono.just(device));
        when(auditLogService.log(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.error(new IllegalStateException("审计服务不可用")));

        StepVerifier.create(deviceTrustService.extendDeviceTrust(1L, 7, "admin"))
                .expectErrorMessage("审计服务不可用")
                .verify();
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
