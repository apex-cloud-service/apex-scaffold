package com.apex.scaffold.integration.device.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.integration.device.application.DeviceCommandService;
import com.apex.scaffold.integration.device.application.DeviceQueryService;
import com.apex.scaffold.integration.device.application.DeviceRegistryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link IotInternalHandler#stats} 单元测试（D4 计量/D5 聚合的设备数维度）。
 *
 * <p>覆盖：显式 {@code tenantId} 参数优先（system 计量门面无租户上下文调用）、
 * 缺失参数回退可信租户上下文。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("IotInternalHandler 设备统计测试")
class IotInternalHandlerStatsTest {

    private DeviceQueryService queryService;

    private IotInternalHandler handler;

    @BeforeEach
    void setUp() {
        queryService = mock(DeviceQueryService.class);
        handler = new IotInternalHandler(
                mock(DeviceRegistryService.class),
                mock(DeviceCommandService.class),
                queryService,
                Validation.buildDefaultValidatorFactory().getValidator(),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("显式 tenantId 参数优先，无需租户上下文")
    void stats_withExplicitTenantParam() {
        when(queryService.deviceCount("tenant-a")).thenReturn(Mono.just(7L));

        MockServerRequest request = MockServerRequest.builder()
                .uri(java.net.URI.create("/internal/v1/iot/devices/stats?tenantId=tenant-a"))
                .queryParam("tenantId", "tenant-a")
                .exchange(MockServerWebExchange.from(
                        MockServerHttpRequest.get("/internal/v1/iot/devices/stats?tenantId=tenant-a")
                                .build()))
                .build();

        StepVerifier.create(handler.stats(request))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(200))
                .verifyComplete();

        verify(queryService).deviceCount("tenant-a");
    }

    @Test
    @DisplayName("缺失参数时回退可信租户上下文")
    void stats_fallsBackToTenantContext() {
        when(queryService.deviceCount(anyString())).thenReturn(Mono.just(3L));

        MockServerRequest request = MockServerRequest.builder()
                .uri(java.net.URI.create("/internal/v1/iot/devices/stats"))
                .exchange(MockServerWebExchange.from(MockServerHttpRequest.get("/internal/v1/iot/devices/stats")
                        .build()))
                .build();

        StepVerifier.create(withTenantContext(request))
                .assertNext(response -> {
                    assertThat(response.statusCode().value()).isEqualTo(200);
                    assertThat(readBody(response).getDeviceCount()).isEqualTo(3L);
                })
                .verifyComplete();
    }

    private Mono<org.springframework.web.reactive.function.server.ServerResponse> withTenantContext(
            org.springframework.web.reactive.function.server.ServerRequest request) {
        return handler.stats(request).contextWrite(TenantContext.withTenant("tenant-ctx"));
    }

    private com.apex.scaffold.integration.device.interfaces.dto.DeviceStatsResponse readBody(
            org.springframework.web.reactive.function.server.ServerResponse response) {
        Object entity = ((org.springframework.web.reactive.function.server.EntityResponse<?>) response).entity();
        return (com.apex.scaffold.integration.device.interfaces.dto.DeviceStatsResponse)
                ((com.apex.scaffold.common.result.Result<?>) entity).getData();
    }
}
