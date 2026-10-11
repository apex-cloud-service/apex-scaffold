package com.apex.scaffold.gateway.filter;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 灰度路由过滤器（全局）.
 *
 * <p>对应迁移方案 §5.3 过滤器链第 6 位（order = 40）与 §15 灰度切流。
 *
 * <p>三级灰度判定（命中任一即视为灰度流量，短路返回）：
 * <ol>
 *   <li><b>Header 强制</b>：客户端 / 联调携带 {@code X-Gray-Tag: gray} 时强制灰度（用于人工验证）</li>
 *   <li><b>租户白名单</b>：请求所属 {@code X-Tenant-Id} 命中 {@code gray.tenant-whitelist} 时灰度</li>
 *   <li><b>用户尾号百分比</b>：{@code userId} 的稳定哈希对 100 取模 &lt; {@code gray.percentage} 时灰度，
 *       保证同一用户灰度状态稳定（同用户始终命中/不命中）</li>
 * </ol>
 *
 * <p>判定结果写入请求头 {@code X-Gray-Tag}（gray / stable）并透传给下游，
 * 便于 Phase 2–5 结合 {@code lb://} 实例标签完成真实流量切分。
 *
 * <p>配置项 {@code gray.*}（yml / Nacos）：
 * <ul>
 *   <li>{@code gray.enabled} — 灰度总开关（默认 false，关闭时全部走稳定版）</li>
 *   <li>{@code gray.percentage} — 用户尾号灰度百分比（0–100）</li>
 *   <li>{@code gray.tenant-whitelist} — 强制灰度的租户 ID 白名单</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Component
public class GrayRouteFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GrayRouteFilter.class);

    /** 过滤器顺序：限流（30）之后、访问日志（50）之前 */
    public static final int ORDER = 40;

    /** 灰度标记请求头名称 */
    public static final String HEADER_GRAY_TAG = "X-Gray-Tag";

    /** 灰度标记值：命中灰度 */
    public static final String GRAY_VALUE = "gray";

    /** 灰度标记值：稳定版 */
    public static final String STABLE_VALUE = "stable";

    /** 灰度总开关 */
    private final boolean enabled;

    /** 用户尾号灰度百分比（0–100） */
    private final int percentage;

    /** 强制灰度的租户白名单 */
    private final List<String> tenantWhitelist;

    /**
     * 构造灰度路由过滤器.
     *
     * @param enabled         灰度总开关
     * @param percentage      用户尾号灰度百分比
     * @param tenantWhitelist 租户白名单（逗号分隔）
     */
    public GrayRouteFilter(
            @Value("${gray.enabled:false}") boolean enabled,
            @Value("${gray.percentage:0}") int percentage,
            @Value("${gray.tenant-whitelist:}") List<String> tenantWhitelist) {
        this.enabled = enabled;
        this.percentage = percentage;
        this.tenantWhitelist = tenantWhitelist;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String grayTag = decide(exchange);
        ServerHttpRequest mutated =
                exchange.getRequest().mutate().header(HEADER_GRAY_TAG, grayTag).build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * 执行三级灰度判定.
     *
     * @param exchange 当前交换
     * @return 灰度标记值（{@link #GRAY_VALUE} 或 {@link #STABLE_VALUE}）
     */
    private String decide(ServerWebExchange exchange) {
        // 灰度关闭：全部稳定版
        if (!enabled) {
            return STABLE_VALUE;
        }

        // 第 1 级：Header 强制
        String forced = exchange.getRequest().getHeaders().getFirst(HEADER_GRAY_TAG);
        if (GRAY_VALUE.equalsIgnoreCase(forced)) {
            log.debug("灰度命中(Header 强制): tag={}", forced);
            return GRAY_VALUE;
        }

        // 第 2 级：租户白名单
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        if (StringUtils.hasText(tenantId) && tenantWhitelist.contains(tenantId)) {
            log.debug("灰度命中(租户白名单): tenantId={}", tenantId);
            return GRAY_VALUE;
        }

        // 第 3 级：用户尾号百分比
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (StringUtils.hasText(userId) && percentage > 0 && bucket(userId) < percentage) {
            log.debug("灰度命中(用户百分比): userId={}, bucket={}, pct={}", userId, bucket(userId), percentage);
            return GRAY_VALUE;
        }

        return STABLE_VALUE;
    }

    /**
     * 计算用户稳定灰度分桶（0–99）.
     *
     * <p>使用用户 ID 哈希的绝对值对 100 取模，保证同一用户分桶稳定。
     *
     * @param userId 用户 ID
     * @return 分桶值（0–99）
     */
    private int bucket(String userId) {
        return Math.abs(userId.hashCode()) % 100;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
