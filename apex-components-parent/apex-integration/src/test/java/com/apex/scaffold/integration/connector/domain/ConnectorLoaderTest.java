package com.apex.scaffold.integration.connector.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ConnectorLoader} SPI 发现隔离测试。
 *
 * <p>覆盖：单条实现失败被跳过、描述符遍历失败时停止并保留已发现条目、
 * 内置实现优先于同名 SPI 实现且不抛异常。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("连接器 SPI 发现隔离")
class ConnectorLoaderTest {

    private IntegrationConnector connector(String type) {
        IntegrationConnector connector = mock(IntegrationConnector.class);
        when(connector.type()).thenReturn(type);
        return connector;
    }

    @Test
    @DisplayName("type() 抛异常的实现被跳过，其余条目保留")
    void skipsFailingType() {
        IntegrationConnector broken = mock(IntegrationConnector.class);
        when(broken.type()).thenThrow(new IllegalStateException("type 调用失败"));
        IntegrationConnector healthy = connector("CUSTOM");
        List<IntegrationConnector> target = new ArrayList<>();
        ConnectorLoader.collect(List.of(broken, healthy).iterator(), target);
        assertThat(target).containsExactly(healthy);
    }

    @Test
    @DisplayName("type() 返回空白的实现被跳过")
    void skipsBlankType() {
        IntegrationConnector healthy = connector("CUSTOM");
        List<IntegrationConnector> target = new ArrayList<>();
        ConnectorLoader.collect(List.of(connector("  "), healthy).iterator(), target);
        assertThat(target).containsExactly(healthy);
    }

    @Test
    @DisplayName("next() 抛 ServiceConfigurationError 时跳过该条目并继续")
    void continuesAfterNextError() {
        IntegrationConnector healthy = connector("CUSTOM");
        Iterator<IntegrationConnector> iterator = new Iterator<>() {
            private int index = -1;

            @Override
            public boolean hasNext() {
                return index < 1;
            }

            @Override
            public IntegrationConnector next() {
                index++;
                if (index == 0) {
                    throw new ServiceConfigurationError("坏实现实例化失败");
                }
                return healthy;
            }
        };
        List<IntegrationConnector> target = new ArrayList<>();
        ConnectorLoader.collect(iterator, target);
        assertThat(target).containsExactly(healthy);
    }

    @Test
    @DisplayName("描述符遍历中途失败时停止发现但保留已发现条目")
    void retainsDiscoveredOnDescriptorFailure() {
        IntegrationConnector healthy = connector("CUSTOM");
        AtomicInteger hasNextCalls = new AtomicInteger();
        Iterator<IntegrationConnector> iterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                if (hasNextCalls.incrementAndGet() == 1) {
                    return true;
                }
                throw new ServiceConfigurationError("描述符不可遍历");
            }

            @Override
            public IntegrationConnector next() {
                return healthy;
            }
        };
        List<IntegrationConnector> target = new ArrayList<>();
        ConnectorLoader.collect(iterator, target);
        assertThat(target).containsExactly(healthy);
        assertThat(hasNextCalls).hasValue(2);
    }

    @Test
    @DisplayName("内置实现与同名 SPI 并存时内置优先且不报错")
    void builtinWinsOverSpiDuplicate() {
        IntegrationConnector builtin = connector("HTTP");
        Map<String, IntegrationConnector> merged = ConnectorLoader.merge(List.of(builtin));
        assertThat(merged.get("HTTP")).isSameAs(builtin);
        assertThat(merged.keySet()).containsExactlyInAnyOrder("HTTP", "WEBHOOK", "WECHAT", "CHUANGLAN");
    }
}
