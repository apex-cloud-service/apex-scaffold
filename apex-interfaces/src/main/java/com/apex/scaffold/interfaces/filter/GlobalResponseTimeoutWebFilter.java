package com.apex.scaffold.interfaces.filter;

import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.util.ResponseUtil;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 全局响应超时过滤器（三高-高可用兜底）
 *
 * <p>对整个过滤器链（含业务处理与响应写出）施加统一的处理时长上限，
 * 超时请求以 {@code 504 Gateway Timeout} + 错误码
 * {@link ResultCode#REQUEST_TIMEOUT} 返回统一 JSON 响应。</p>
 *
 * <h2>解决的问题</h2>
 * <ul>
 *   <li><b>高可用</b>：防止慢请求（锁等待、慢 SQL、下游无响应）长期占用
 *       Netty 连接与线程资源，避免线程耗尽引发雪崩</li>
 *   <li><b>故障隔离</b>：超时是熔断统计（异常比例/慢调用比例）的前置条件，
 *       没有超时边界则 Sentinel 慢调用熔断永远无法触发</li>
 *   <li><b>快速失败</b>：客户端（或网关）可在更短时间内切换重试与降级路径</li>
 * </ul>
 *
 * <h2>豁免规则</h2>
 * <ul>
 *   <li>请求 {@code Accept} 头包含 {@code text/event-stream} 的 SSE 长连接请求</li>
 *   <li>{@code apex.api.response-timeout} 配置为 0/负值时整体关闭</li>
 *   <li>响应已提交的请求不再回写 JSON（仅记录日志），避免破坏半成品响应</li>
 * </ul>
 *
 * <h2>执行顺序</h2>
 * <p>{@code @Order(HIGHEST_PRECEDENCE + 2)}：位于 TraceFilter（+0）与
 * BraveWebTracingFilter（+1）之后、其余业务过滤器之前 —— 超时日志可携带
 * traceId，且计时范围覆盖安全校验、限流与业务处理的完整链路。</p>
 *
 * @author Apex Team
 * @version 1.1.4
 * @since 1.1.4
 * @see ApiTimeoutProperties
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RequiredArgsConstructor
public class GlobalResponseTimeoutWebFilter implements WebFilter {

    private final ApiTimeoutProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isTimeoutEnabled() || isSseRequest(exchange)) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        return chain
                .filter(exchange)
                .timeout(properties.getResponseTimeout())
                .onErrorResume(TimeoutException.class, ex -> handleTimeout(exchange, path, ex));
    }

    /**
     * 判断是否为 SSE 长连接请求
     *
     * @param exchange 当前请求
     * @return Accept 头包含 text/event-stream 时返回 true
     */
    private boolean isSseRequest(ServerWebExchange exchange) {
        String accept = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    /**
     * 处理超时：记录告警日志并返回 504 统一错误响应
     *
     * @param exchange 当前请求
     * @param path     请求路径
     * @param ex       超时异常
     * @return 错误响应写出 Mono
     */
    private Mono<Void> handleTimeout(ServerWebExchange exchange, String path, TimeoutException ex) {
        log.warn("全局响应超时, path: {}, timeout: {}ms", path, properties.getResponseTimeout().toMillis(), ex);
        // 响应已提交（如流式写出中途超时）时无法回写 JSON，仅记录告警
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        return ResponseUtil.error(
                exchange,
                HttpStatus.GATEWAY_TIMEOUT,
                ResultCode.REQUEST_TIMEOUT.getCode(),
                ResultCode.REQUEST_TIMEOUT.getMsg());
    }
}
