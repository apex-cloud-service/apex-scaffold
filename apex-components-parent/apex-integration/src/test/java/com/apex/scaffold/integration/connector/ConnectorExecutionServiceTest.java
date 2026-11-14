package com.apex.scaffold.integration.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.integration.connector.application.ConnectorExecutionService;
import com.apex.scaffold.integration.connector.domain.ConnectorLoader;
import com.apex.scaffold.integration.connector.domain.IntegrationConnector;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 连接器执行与 SPI 加载测试（方案 M5 / 批次 E1）。
 *
 * <p>覆盖：未知连接器拒绝、幂等键触发重试（失败后成功）、无幂等键不重试、
 * 重复类型显式拒绝、SPI 无第三方声明返回空。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("连接器执行与 SPI 测试")
class ConnectorExecutionServiceTest {

    private IntegrationConnector connector(String type, Mono<Map<String, Object>> response) {
        IntegrationConnector connector = mock(IntegrationConnector.class);
        when(connector.type()).thenReturn(type);
        when(connector.execute(any(), anyString(), any(), anyString())).thenReturn(response);
        return connector;
    }

    @Test
    @DisplayName("未知连接器拒绝")
    void rejectsUnknownConnector() {
        ConnectorExecutionService service = new ConnectorExecutionService(Map.of());
        StepVerifier.create(service.execute("GHOST", Map.of(), "op", Map.of(), null))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("带幂等键时失败重试，第二次成功")
    void retriesWithIdempotencyKey() {
        // 首次失败、重试成功（thenReturn 链式声明顺序即调用顺序）
        IntegrationConnector connector = mock(IntegrationConnector.class);
        AtomicInteger attempts = new AtomicInteger();
        when(connector.execute(any(), anyString(), any(), anyString()))
                .thenAnswer(invocation -> attempts.getAndIncrement() == 0
                        ? Mono.error(new IllegalStateException("transient"))
                        : Mono.just(Map.of("ok", true)));
        ConnectorExecutionService service = new ConnectorExecutionService(Map.of("HTTP", connector));
        StepVerifier.create(service.execute("HTTP", Map.of(), "op", Map.of(), "key-1"))
                .assertNext(payload -> assertThat(payload).containsEntry("ok", true))
                .verifyComplete();
        verify(connector, times(2)).execute(any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("无幂等键不重试（避免非幂等副作用重复）")
    void doesNotRetryWithoutIdempotencyKey() {
        IntegrationConnector connector = mock(IntegrationConnector.class);
        when(connector.execute(any(), anyString(), any(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("boom")));
        ConnectorExecutionService service = new ConnectorExecutionService(Map.of("HTTP", connector));
        StepVerifier.create(service.execute("HTTP", Map.of(), "op", Map.of(), null))
                .expectError(IllegalStateException.class)
                .verify();
        verify(connector, times(1)).execute(any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("重复连接器类型显式拒绝")
    void rejectsDuplicateType() {
        IntegrationConnector first = connector("HTTP", Mono.just(Map.of()));
        IntegrationConnector second = connector("HTTP", Mono.just(Map.of()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ConnectorLoader.merge(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复连接器类型");
    }

    @Test
    @DisplayName("SPI 发现 4 个内置连接器并按类型索引")
    void spiDiscoversBuiltInConnectors() {
        List<String> types =
                ConnectorLoader.load().stream().map(IntegrationConnector::type).toList();
        assertThat(types).containsExactlyInAnyOrder("HTTP", "WEBHOOK", "WECHAT", "CHUANGLAN");
        assertThat(ConnectorLoader.merge(List.of()).keySet())
                .containsExactlyInAnyOrder("HTTP", "WEBHOOK", "WECHAT", "CHUANGLAN");
    }
}
