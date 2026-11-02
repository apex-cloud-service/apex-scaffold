package com.apex.scaffold.integration.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.device.application.DeviceQueryService;
import com.apex.scaffold.integration.device.domain.Device;
import com.apex.scaffold.integration.device.domain.DeviceTelemetry;
import com.apex.scaffold.integration.device.domain.repository.DeviceRepository;
import com.apex.scaffold.integration.device.domain.repository.DeviceTelemetryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 设备查询用例测试（方案 M5 / 批次 E3）。
 *
 * <p>覆盖：分页与计数委托、详情不存在返回 404、遥测查询先校验设备归属再按
 * device_key 分页、归属校验失败时不触达遥测仓储。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("设备查询用例测试")
class DeviceQueryServiceTest {

    private DeviceRepository deviceRepository;

    private DeviceTelemetryRepository telemetryRepository;

    private DeviceQueryService service;

    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        telemetryRepository = mock(DeviceTelemetryRepository.class);
        service = new DeviceQueryService(deviceRepository, telemetryRepository);
    }

    @Test
    @DisplayName("分页查询委托设备仓储")
    void pageDelegatesToRepository() {
        Device device = device(1L, "dev-1");
        when(deviceRepository.page("t1", Device.STATUS_ONLINE, 1, 10))
                .thenReturn(Mono.just(PageResponse.of(List.of(device), 1L, 1, 10)));

        StepVerifier.create(service.page("t1", Device.STATUS_ONLINE, 1, 10))
                .assertNext(page -> assertThat(page.getTotal()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    @DisplayName("详情不存在或跨租户返回 404")
    void getMissingDeviceIsNotFound() {
        when(deviceRepository.findById("t1", 9L)).thenReturn(Mono.empty());

        StepVerifier.create(service.get("t1", 9L))
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()))
                .verify();
    }

    @Test
    @DisplayName("设备总数委托计数")
    void deviceCountDelegates() {
        when(deviceRepository.count("t1")).thenReturn(Mono.just(3L));

        StepVerifier.create(service.deviceCount("t1"))
                .assertNext(count -> assertThat(count).isEqualTo(3L))
                .verifyComplete();
    }

    @Test
    @DisplayName("遥测：归属校验通过后按 device_key 分页")
    void telemetryUsesResolvedDeviceKey() {
        Device device = device(1L, "dev-1");
        DeviceTelemetry telemetry = new DeviceTelemetry();
        telemetry.setTenantId("t1");
        telemetry.setDeviceKey("dev-1");
        when(deviceRepository.findById("t1", 1L)).thenReturn(Mono.just(device));
        when(telemetryRepository.page("t1", 1L, "dev-1", 1, 10))
                .thenReturn(Mono.just(PageResponse.of(List.of(telemetry), 1L, 1, 10)));

        StepVerifier.create(service.telemetry("t1", 1L, 1, 10))
                .assertNext(page -> {
                    assertThat(page.getTotal()).isEqualTo(1L);
                    assertThat(page.getList())
                            .extracting(DeviceTelemetry::getDeviceKey)
                            .containsExactly("dev-1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("遥测：设备不存在时不触达遥测仓储")
    void telemetrySkipsRepositoryWhenDeviceMissing() {
        when(deviceRepository.findById("t1", 9L)).thenReturn(Mono.empty());

        StepVerifier.create(service.telemetry("t1", 9L, 1, 10))
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()))
                .verify();

        verify(telemetryRepository, never()).page(anyString(), anyLong(), anyString(), anyInt(), anyInt());
    }

    private Device device(Long id, String deviceKey) {
        Device device = new Device();
        device.setId(id);
        device.setTenantId("t1");
        device.setDeviceKey(deviceKey);
        device.setStatus(Device.STATUS_ONLINE);
        return device;
    }
}
