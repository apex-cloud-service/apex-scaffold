package com.apex.scaffold.gateway.lb;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.loadbalancer.core.DelegatingServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Flux;

/**
 * 灰度标签负载均衡供应商（实例级路由）.
 *
 * <p>对应迁移方案 §15 灰度切流的实例级落地：作为 {@link ServiceInstanceListSupplier} 的装饰器，
 * 包裹 {@code withDiscoveryClient().withCaching()} 生成的供应商，在每次选实例前依据请求头
 * {@code X-Gray-Tag} 对下游实例流按元数据 {@code gray-tag} 二次过滤。
 *
 * <p>实例元数据约定（下游注册侧在 Nacos / {@code spring.cloud.nacos.discovery.metadata} 声明）：
 * <ul>
 *   <li>{@code gray-tag=gray} — 灰度实例</li>
 *   <li>{@code gray-tag=stable} — 稳定实例</li>
 * </ul>
 *
 * <p>过滤与回退策略（保证任何情况下都不空路由）：
 * <ol>
 *   <li>{@code X-Gray-Tag=gray}：优先返回灰度实例；无灰度实例时回退稳定实例；再无可标记实例时返回全量</li>
 *   <li>{@code X-Gray-Tag=stable} 或缺失：返回稳定实例；无稳定实例时返回全量</li>
 *   <li>无请求上下文（非 {@link RequestDataContext}）：直接委托被装饰供应商，不参与灰度过滤</li>
 * </ol>
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Slf4j
public class GrayTagServiceInstanceListSupplier extends DelegatingServiceInstanceListSupplier {

    /** 灰度标记请求头（与 {@code GrayRouteFilter} 约定一致） */
    public static final String HEADER_GRAY_TAG = "X-Gray-Tag";

    /** 实例元数据中的灰度标签键 */
    public static final String METADATA_GRAY_TAG = "gray-tag";

    /** 灰度标记值：命中灰度 */
    public static final String GRAY_VALUE = "gray";

    /** 灰度标记值：稳定版 */
    public static final String STABLE_VALUE = "stable";

    /**
     * 构造灰度标签供应商.
     *
     * @param delegate 被装饰的供应商（通常为 discovery + caching 组合）
     */
    public GrayTagServiceInstanceListSupplier(ServiceInstanceListSupplier delegate) {
        super(delegate);
    }

    @Override
    public Flux<List<ServiceInstance>> get() {
        return delegate.get();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Flux<List<ServiceInstance>> get(Request request) {
        String grayTag = resolveGrayTag(request);
        if (grayTag == null) {
            return delegate.get(request);
        }
        return delegate.get(request).map(instances -> filterByGrayTag(instances, grayTag));
    }

    /**
     * 从请求上下文解析灰度标记.
     *
     * <p>网关的 {@code ReactiveLoadBalancerClientFilter} 会构建 {@link RequestDataContext} 并携带
     * 原始请求头，因此可从 {@link RequestData#getHeaders()} 读取 {@code X-Gray-Tag}。
     *
     * @param request 负载均衡请求
     * @return 灰度标记值（gray / stable）；无请求上下文时返回 {@code null}
     */
    @SuppressWarnings("rawtypes")
    private String resolveGrayTag(Request request) {
        if (request == null) {
            return null;
        }
        Object context = request.getContext();
        if (!(context instanceof RequestDataContext requestDataContext)) {
            return null;
        }
        RequestData clientRequest = requestDataContext.getClientRequest();
        if (clientRequest == null) {
            return null;
        }
        HttpHeaders headers = clientRequest.getHeaders();
        if (headers == null) {
            return null;
        }
        String tag = headers.getFirst(HEADER_GRAY_TAG);
        return GRAY_VALUE.equalsIgnoreCase(tag) ? GRAY_VALUE : STABLE_VALUE;
    }

    /**
     * 按灰度标记过滤实例，并施加逐级回退.
     *
     * @param instances 被装饰供应商返回的实例集合
     * @param grayTag   灰度标记值
     * @return 过滤后的实例集合（永不为空，除非入参本身就为空）
     */
    private List<ServiceInstance> filterByGrayTag(List<ServiceInstance> instances, String grayTag) {
        if (instances == null || instances.isEmpty()) {
            return instances;
        }

        List<ServiceInstance> matched = instances.stream()
                .filter(instance -> grayTag.equalsIgnoreCase(metadataGrayTag(instance)))
                .toList();
        if (!matched.isEmpty()) {
            return matched;
        }

        if (GRAY_VALUE.equals(grayTag)) {
            List<ServiceInstance> stable = instances.stream()
                    .filter(instance -> STABLE_VALUE.equalsIgnoreCase(metadataGrayTag(instance)))
                    .toList();
            if (!stable.isEmpty()) {
                log.debug("灰度无匹配实例，回退稳定实例: count={}", stable.size());
                return stable;
            }
        }

        log.warn("无匹配灰度标签实例，回退全量实例: grayTag={}, total={}", grayTag, instances.size());
        return instances;
    }

    /**
     * 读取实例的灰度标签元数据.
     *
     * @param instance 服务实例
     * @return 元数据 {@code gray-tag} 值；缺失时返回 {@code null}
     */
    private static String metadataGrayTag(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        return metadata == null ? null : metadata.get(METADATA_GRAY_TAG);
    }
}
