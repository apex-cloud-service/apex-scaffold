package com.apex.scaffold.integration.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.device.application.DeviceRegistration;
import com.apex.scaffold.integration.device.application.DeviceRegistryService;
import com.apex.scaffold.integration.device.domain.Device;
import com.apex.scaffold.integration.device.domain.SecretHasher;
import com.apex.scaffold.integration.device.domain.repository.DeviceRepository;
import com.apex.scaffold.integration.device.domain.repository.DeviceShadowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 设备注册与凭据认证测试（方案 M5 / 批次 E3）。
 *
 * <p>验证：注册生成随机 secret 且仅以明文出现在注册结果中（落库为 PBKDF2 哈希）；
 * 认证钩子正确/错误 secret 与未知设备的口径（统一 401，防设备枚举）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("设备注册与凭据认证测试")
class DeviceRegistryServiceTest {

    private DeviceRepository deviceRepository;

    private DeviceShadowRepository shadowRepository;

    private DeviceRegistryService service;

    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        shadowRepository = mock(DeviceShadowRepository.class);
        service = new DeviceRegistryService(deviceRepository, shadowRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("注册：secret 明文仅出现在结果中，落库为 PBKDF2 哈希")
    void registerStoresHashOnly() {
        when(deviceRepository.insert(any(Device.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(shadowRepository.insert(any(com.apex.scaffold.integration.device.domain.DeviceShadow.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        DeviceRegistration registration =
                service.register("t1", "dev-1", null, "fw-1", null).block();

        assertThat(registration).isNotNull();
        assertThat(registration.plainSecret()).isNotBlank();
        Device stored = registration.device();
        assertThat(stored.getSecretHash()).startsWith("pbkdf2_sha256$");
        assertThat(stored.getSecretHash()).doesNotContain(registration.plainSecret());
        assertThat(SecretHasher.matches(registration.plainSecret(), stored.getSecretHash()))
                .isTrue();
        assertThat(SecretHasher.matches("wrong-secret", stored.getSecretHash())).isFalse();

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).insert(captor.capture());
        assertThat(captor.getValue().getDeviceKey()).isEqualTo("dev-1");
        assertThat(captor.getValue().getTenantId()).isEqualTo("t1");
        verify(shadowRepository).insert(any(com.apex.scaffold.integration.device.domain.DeviceShadow.class));
    }

    @Test
    @DisplayName("注册：非法设备标识被拒绝")
    void registerRejectsIllegalKey() {
        StepVerifier.create(service.register("t1", "dev/1", null, null, null))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.register("t1", " ", null, null, null))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("认证：正确 secret 通过并刷新在线状态")
    void authenticateWithCorrectSecret() {
        Device device = new Device();
        device.setTenantId("t1");
        device.setDeviceKey("dev-1");
        device.setSecretHash(SecretHasher.hash("good-secret"));
        when(deviceRepository.findByKey("t1", "dev-1")).thenReturn(Mono.just(device));
        when(deviceRepository.touchActivity("t1", "dev-1", true)).thenReturn(Mono.empty());

        StepVerifier.create(service.authenticate("t1", "dev-1", "good-secret"))
                .assertNext(authenticated ->
                        assertThat(authenticated.getDeviceKey()).isEqualTo("dev-1"))
                .verifyComplete();
        verify(deviceRepository).touchActivity("t1", "dev-1", true);
    }

    @Test
    @DisplayName("认证：错误 secret 与未知设备统一 401（防枚举）")
    void authenticateRejectsWithUnauthorized() {
        Device device = new Device();
        device.setTenantId("t1");
        device.setDeviceKey("dev-1");
        device.setSecretHash(SecretHasher.hash("good-secret"));
        when(deviceRepository.findByKey("t1", "dev-1")).thenReturn(Mono.just(device));
        when(deviceRepository.findByKey("t1", "ghost")).thenReturn(Mono.empty());

        StepVerifier.create(service.authenticate("t1", "dev-1", "bad-secret"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.UNAUTHORIZED.getCode());
                })
                .verify();
        StepVerifier.create(service.authenticate("t1", "ghost", "any"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("认证：租户/设备标识缺失统一 401")
    void authenticateMissingParamsUnauthorized() {
        StepVerifier.create(service.authenticate(null, "dev-1", "any"))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.authenticate("t1", " ", "any"))
                .expectError(BusinessException.class)
                .verify();
    }
}
