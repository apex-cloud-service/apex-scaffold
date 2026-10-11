package com.apex.scaffold.gateway.lb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * {@link GrayTagServiceInstanceListSupplier} 单元测试.
 *
 * <p>覆盖迁移方案 §15 灰度切流的实例级路由：从请求头 {@code X-Gray-Tag} 读取灰度标记，
 * 对下游实例流按元数据 {@code gray-tag} 过滤；无匹配实例时逐级回退，保证不空路由。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("GrayTagServiceInstanceListSupplier 灰度标签负载均衡测试")
class GrayTagServiceInstanceListSupplierTest {

    private static final String SERVICE_ID = "apex-demo";

    private ServiceInstanceListSupplier delegate;

    @BeforeEach
    void setUp() {
        delegate = mock(ServiceInstanceListSupplier.class);
        when(delegate.getServiceId()).thenReturn(SERVICE_ID);
    }

    private ServiceInstance instance(String instanceId, String grayTag) {
        Map<String, String> metadata = grayTag == null ? Map.of() : Map.of("gray-tag", grayTag);
        return new DefaultServiceInstance(instanceId, SERVICE_ID, "127.0.0.1", 8080, false, metadata);
    }

    private Request<RequestDataContext> requestWithTag(String grayTag) {
        HttpHeaders headers = new HttpHeaders();
        if (grayTag != null) {
            headers.add("X-Gray-Tag", grayTag);
        }
        RequestData requestData = new RequestData(
                HttpMethod.GET,
                URI.create("http://" + SERVICE_ID + "/api/v1/demo"),
                headers,
                new LinkedMultiValueMap<>(),
                Map.of());
        return new DefaultRequest<>(new RequestDataContext(requestData, SERVICE_ID));
    }

    private GrayTagServiceInstanceListSupplier supplierWithInstances(ServiceInstance... instances) {
        when(delegate.get(any(Request.class))).thenReturn(Flux.just(List.of(instances)));
        when(delegate.get()).thenReturn(Flux.just(List.of(instances)));
        return new GrayTagServiceInstanceListSupplier(delegate);
    }

    @Nested
    @DisplayName("灰度流量")
    class GrayTrafficTests {

        @Test
        @DisplayName("X-Gray-Tag=gray 时仅保留 gray-tag=gray 的实例")
        void filtersInstancesByGrayTag() {
            ServiceInstance gray = instance("i-gray", "gray");
            ServiceInstance stable = instance("i-stable", "stable");
            GrayTagServiceInstanceListSupplier supplier = supplierWithInstances(gray, stable);

            StepVerifier.create(supplier.get(requestWithTag("gray")))
                    .assertNext(result -> assertThat(result).containsExactly(gray))
                    .verifyComplete();
        }

        @Test
        @DisplayName("无 gray 实例时回退到 stable 实例，保证不空路由")
        void fallsBackToAllWhenNoTaggedInstance() {
            ServiceInstance stableOne = instance("i-stable-1", "stable");
            ServiceInstance stableTwo = instance("i-stable-2", "stable");
            GrayTagServiceInstanceListSupplier supplier = supplierWithInstances(stableOne, stableTwo);

            StepVerifier.create(supplier.get(requestWithTag("gray")))
                    .assertNext(result -> assertThat(result).containsExactly(stableOne, stableTwo))
                    .verifyComplete();
        }

        @Test
        @DisplayName("全部实例均无标签时直接返回全量，保证不空路由")
        void returnsAllWhenEveryInstanceUntagged() {
            ServiceInstance one = instance("i-1", null);
            ServiceInstance two = instance("i-2", null);
            GrayTagServiceInstanceListSupplier supplier = supplierWithInstances(one, two);

            StepVerifier.create(supplier.get(requestWithTag("gray")))
                    .assertNext(result -> assertThat(result).containsExactly(one, two))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("稳定流量")
    class StableTrafficTests {

        @Test
        @DisplayName("stable 流量应排除 gray 实例")
        void stableTrafficExcludesGrayInstances() {
            ServiceInstance gray = instance("i-gray", "gray");
            ServiceInstance stable = instance("i-stable", "stable");
            GrayTagServiceInstanceListSupplier supplier = supplierWithInstances(gray, stable);

            StepVerifier.create(supplier.get(requestWithTag("stable")))
                    .assertNext(result -> assertThat(result).containsExactly(stable))
                    .verifyComplete();
        }

        @Test
        @DisplayName("无灰度标记时按稳定流量处理，排除 gray 实例")
        void absentTagTreatsAsStable() {
            ServiceInstance gray = instance("i-gray", "gray");
            ServiceInstance stable = instance("i-stable", "stable");
            GrayTagServiceInstanceListSupplier supplier = supplierWithInstances(gray, stable);

            StepVerifier.create(supplier.get(requestWithTag(null)))
                    .assertNext(result -> assertThat(result).containsExactly(stable))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("委托与兼容")
    class DelegationTests {

        @Test
        @DisplayName("getServiceId 应委托给被装饰的供应商")
        void delegatesServiceId() {
            GrayTagServiceInstanceListSupplier supplier = supplierWithInstances(instance("i-1", "gray"));

            assertThat(supplier.getServiceId()).isEqualTo(SERVICE_ID);
        }

        @Test
        @DisplayName("无请求上下文时退化为直接返回全量实例")
        void returnsAllWhenNoRequestContext() {
            ServiceInstance gray = instance("i-gray", "gray");
            ServiceInstance stable = instance("i-stable", "stable");
            GrayTagServiceInstanceListSupplier supplier = supplierWithInstances(gray, stable);

            StepVerifier.create(supplier.get(new DefaultRequest<>()))
                    .assertNext(result -> assertThat(result).containsExactly(gray, stable))
                    .verifyComplete();
        }
    }
}
