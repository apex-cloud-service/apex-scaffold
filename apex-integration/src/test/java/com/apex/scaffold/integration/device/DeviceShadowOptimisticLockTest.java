package com.apex.scaffold.integration.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.service.DomainEventPublisher;
import com.apex.scaffold.integration.device.application.DeviceRuleEngineService;
import com.apex.scaffold.integration.device.application.DeviceTelemetryService;
import com.apex.scaffold.integration.device.domain.Device;
import com.apex.scaffold.integration.device.domain.DeviceShadow;
import com.apex.scaffold.integration.device.domain.DeviceShadowConflictException;
import com.apex.scaffold.integration.device.domain.repository.DeviceRepository;
import com.apex.scaffold.integration.device.domain.repository.DeviceShadowRepository;
import com.apex.scaffold.integration.device.domain.repository.DeviceTelemetryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 影子乐观锁与遥测受理测试（方案 M5 / 批次 E3）。
 *
 * <p>验证：影子 {@code reported} 条件更新冲突被应用层有限次重读重试消化
 * （重试成功与耗尽反例）；遥测受理成功链路（事件逐条发布 + 批量落库）；
 * 未知设备与非法指标拒绝。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("影子乐观锁与遥测受理测试")
class DeviceShadowOptimisticLockTest {

    private DeviceRepository deviceRepository;

    private DeviceShadowRepository shadowRepository;

    private DeviceTelemetryRepository telemetryRepository;

    private DeviceRuleEngineService ruleEngineService;

    private DomainEventPublisher eventPublisher;

    private DeviceTelemetryService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        shadowRepository = mock(DeviceShadowRepository.class);
        telemetryRepository = mock(DeviceTelemetryRepository.class);
        ruleEngineService = mock(DeviceRuleEngineService.class);
        eventPublisher = mock(DomainEventPublisher.class);
        service = new DeviceTelemetryService(
                deviceRepository,
                shadowRepository,
                telemetryRepository,
                ruleEngineService,
                eventPublisher,
                new ObjectMapper());

        Device device = new Device();
        device.setId(1L);
        device.setTenantId("t1");
        device.setDeviceKey("dev-1");
        when(deviceRepository.findByKey(eq("t1"), eq("dev-1"))).thenReturn(Mono.just(device));
        when(deviceRepository.findByKey(eq("t1"), eq("ghost"))).thenReturn(Mono.empty());
        when(deviceRepository.findByKey(eq("t2"), anyString())).thenReturn(Mono.empty());
        when(deviceRepository.touchActivity(anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(Mono.empty());
        when(ruleEngineService.evaluate(anyString(), anyString(), anyString())).thenReturn(Mono.empty());
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        when(telemetryRepository.insertAll(anyList())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("乐观锁冲突重试后成功（第二次条件更新命中）")
    void shadowConflictRetriedThenSuccess() {
        DeviceShadow current = shadow(0);
        DeviceShadow updated = shadow(1);
        when(shadowRepository.findByKey("t1", "dev-1")).thenReturn(Mono.just(current));
        when(shadowRepository.updateReported(eq("t1"), eq("dev-1"), anyString(), eq(0)))
                .thenReturn(Mono.error(new DeviceShadowConflictException("dev-1", 0)))
                .thenReturn(Mono.just(updated));

        StepVerifier.create(service.ingest("t1", "dev-1", points("{\"temperature\":71}")))
                .assertNext(ack -> {
                    assertThat(ack.acceptedCount()).isEqualTo(1);
                    assertThat(ack.shadowVersion()).isEqualTo(2);
                })
                .verifyComplete();
        verify(shadowRepository, times(2)).updateReported(eq("t1"), eq("dev-1"), anyString(), eq(0));
    }

    @Test
    @DisplayName("乐观锁冲突重试耗尽后以冲突错误终止")
    void shadowConflictExhausted() {
        when(shadowRepository.findByKey("t1", "dev-1")).thenReturn(Mono.just(shadow(0)));
        when(shadowRepository.updateReported(eq("t1"), eq("dev-1"), anyString(), eq(0)))
                .thenReturn(Mono.error(new DeviceShadowConflictException("dev-1", 0)));

        StepVerifier.create(service.ingest("t1", "dev-1", points("{\"temperature\":71}")))
                .expectError(DeviceShadowConflictException.class)
                .verify();
        verify(shadowRepository, times(3)).updateReported(eq("t1"), eq("dev-1"), anyString(), eq(0));
    }

    @Test
    @DisplayName("成功链路：逐条事件发布 + 批量落库（jdbc 隔离由仓储承担）")
    void ingestPublishesEventsAndPersistsBatch() {
        when(shadowRepository.findByKey("t1", "dev-1")).thenReturn(Mono.just(shadow(0)));
        when(shadowRepository.updateReported(eq("t1"), eq("dev-1"), anyString(), eq(0)))
                .thenReturn(Mono.just(shadow(1)));

        StepVerifier.create(service.ingest("t1", "dev-1", points("{\"temperature\":71}", "{\"humidity\":60}")))
                .assertNext(ack -> assertThat(ack.acceptedCount()).isEqualTo(2))
                .verifyComplete();

        verify(eventPublisher, times(2)).publish(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.apex.scaffold.integration.device.domain.DeviceTelemetry>> captor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(telemetryRepository).insertAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0).getTenantId()).isEqualTo("t1");
        assertThat(captor.getValue().get(0).getDeviceKey()).isEqualTo("dev-1");
    }

    @Test
    @DisplayName("未知设备（含跨租户查询空）以 NOT_FOUND 拒绝")
    void ingestUnknownDeviceRejected() {
        StepVerifier.create(service.ingest("t2", "dev-1", points("{\"temperature\":71}")))
                .expectError(com.apex.scaffold.common.exception.BusinessException.class)
                .verify();
        StepVerifier.create(service.ingest("t1", "ghost", points("{\"temperature\":71}")))
                .expectError(com.apex.scaffold.common.exception.BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("非法指标 JSON（非对象）被拒绝")
    void ingestIllegalMetricRejected() {
        StepVerifier.create(service.ingest("t1", "dev-1", points("[1,2]")))
                .expectError(com.apex.scaffold.common.exception.BusinessException.class)
                .verify();
        StepVerifier.create(service.ingest("t1", "dev-1", points("")))
                .expectError(com.apex.scaffold.common.exception.BusinessException.class)
                .verify();
    }

    private DeviceShadow shadow(int version) {
        DeviceShadow shadow = new DeviceShadow();
        shadow.setTenantId("t1");
        shadow.setDeviceKey("dev-1");
        shadow.setVersion(version);
        return shadow;
    }

    private List<DeviceTelemetryService.TelemetryPoint> points(String... metrics) {
        return List.of(metrics).stream()
                .map(metric -> new DeviceTelemetryService.TelemetryPoint(metric, null))
                .toList();
    }
}
