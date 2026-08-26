package com.apex.scaffold.interfaces.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 扫描流量防护过滤器。
 *
 * <p>公网服务器持续被扫描器/漏洞探测工具探测（{@code /HNAP1}、{@code /CFIDE/}、
 * {@code /.git/HEAD}、{@code /dana-cached/}、CONNECT 隧道等）。这些请求此前全部进入
 * {@link ApiGatewayFilter} 的指标统计并被计为 4xx 错误，造成"网关错误率"虚高，
 * 同时产生大量无效日志与下游校验开销。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>在过滤器链最前端（{@code @Order(HIGHEST_PRECEDENCE + 5)}，早于 {@link ApiGatewayFilter}
 *       与 {@link LoggingFilter}）拦截命中扫描路径的请求，直接返回配置的拒绝状态码</li>
 *   <li>拦截请求不经过 {@link ApiGatewayFilter}，因此不产生任何网关请求/错误指标，
 *       也不产生 {@link LoggingFilter} 的完整请求日志，从根源消除指标噪音与日志噪音</li>
 *   <li>拦截行为通过 Micrometer 计数器 {@code apex_gateway_scan_blocked_total}
 *       （带 method/path 标签）可观测，便于验证拦截效果</li>
 * </ul>
 *
 * <h2>实现风格</h2>
 * <p>判定与匹配逻辑全部采用函数式风格：{@link #isScanTraffic(ServerHttpRequest)} 通过
 * Stream {@code anyMatch} 组合匹配谓词；{@link #matches(String, String)} 通过
 * Stream {@code map}/{@code collect(Collectors.joining)} 将通配符模式编译为正则表达式，
 * 避免命令式循环与中间状态。所有判定方法均为无副作用纯函数，便于单元测试。</p>
 *
 * <h2>匹配语义</h2>
 * <p>路径模式见 {@link ScanTrafficGuardProperties}：不含通配符的模式按前缀匹配，
 * 含 {@code *} 的模式按 glob 匹配，均大小写不敏感（扫描器常变换大小写探测）。
 * CONNECT 隧道请求（配置项 {@code apex.security.scan-guard.block-connect-method} 开启时）无条件拦截。</p>
 *
 * <h2>执行顺序</h2>
 * <p>本过滤器位于 {@link TraceFilter}（{@code HIGHEST_PRECEDENCE}）之后、
 * {@link ApiGatewayFilter}（{@code HIGHEST_PRECEDENCE + 20}）之前，拦截时扫描请求仅产生
 * TraceId 生成的极低开销，不触发任何业务处理。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ScanTrafficGuardProperties
 * @see ApiGatewayFilter
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@ConditionalOnProperty(
        prefix = "apex.security.scan-guard",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ScanTrafficGuardWebFilter implements WebFilter {

    /** 扫描流量拦截计数指标名称，带 method/path 标签。 */
    private static final String METRIC_SCAN_BLOCKED = "apex.gateway.scan.blocked";

    /** 通配符符号（字符串形式，供 {@code split} 与 {@code contains} 使用） */
    private static final String WILDCARD = "*";

    /** glob 通配符转义段（作为正则连接符，等价于"匹配任意字符序列"） */
    private static final String GLOB_MATCH_ANY = ".*";

    /** 方法名无法解析时的占位值 */
    private static final String UNKNOWN_METHOD = "UNKNOWN";

    /** 扫描流量拦截配置属性。 */
    private final ScanTrafficGuardProperties properties;

    /** Micrometer 指标注册表。 */
    private final MeterRegistry meterRegistry;

    /**
     * 过滤请求：命中扫描特征的请求直接拦截，其余放行。
     *
     * <p>入口方法为命令式短路结构：{@link #isScanTraffic(ServerHttpRequest)} 与
     * {@link #block(ServerWebExchange, ServerHttpRequest)} 均无副作用于调用方，
     * 决策逻辑全部下沉到纯函数中，本方法只保留"拦截或放行"两种路径的分派。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param chain    WebFlux 过滤器链
     * @return 表示过滤完成的 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (isScanTraffic(request)) {
            return block(exchange, request);
        }
        return chain.filter(exchange);
    }

    /**
     * 判断请求是否属于扫描/漏洞探测流量（纯函数）。
     *
     * <p>CONNECT 隧道请求无条件判定为扫描流量；其余请求通过 Stream {@code anyMatch}
     * 对配置的模式列表应用 {@link #matches(String, String)} 谓词，任一命中即判定为扫描。
     *
     * @param request HTTP 请求
     * @return 如果是扫描流量返回 true
     */
    private boolean isScanTraffic(ServerHttpRequest request) {
        if (properties.isBlockConnectMethod() && isConnectMethod(request)) {
            return true;
        }
        String path = request.getPath().value();
        return properties.getPathPatterns().stream().anyMatch(pattern -> matches(pattern, path));
    }

    /**
     * 判断请求是否为 CONNECT 方法（纯函数）。
     *
     * <p>{@link HttpMethod} 枚举不含 CONNECT，真实 CONNECT 请求调用 {@code getMethod()} 时
     * Spring 可能抛 {@link IllegalArgumentException} 或返回 null。本方法以异常作为
     * "疑似非标准方法"信号，统一按 CONNECT 处理，确保代理/隧道探测请求在方法名可读前
     * 即被拦截。
     *
     * @param request HTTP 请求
     * @return 如果是 CONNECT 请求返回 true
     */
    private static boolean isConnectMethod(ServerHttpRequest request) {
        try {
            HttpMethod method = request.getMethod();
            return method != null && "CONNECT".equalsIgnoreCase(method.name());
        } catch (RuntimeException e) {
            return true;
        }
    }

    /**
     * 安全获取请求方法名（纯函数）。
     *
     * <p>非标准方法（如 CONNECT）在 {@link HttpMethod} 枚举中不存在，{@code getMethod()} 可能
     * 抛异常或返回 null，统一返回 {@link #UNKNOWN_METHOD} 避免拦截链路自身报错。
     * 该方法仅在计数器标签与日志中使用，不影响拦截判定结果。
     *
     * @param request HTTP 请求
     * @return 方法名或 UNKNOWN
     */
    private static String safeMethodName(ServerHttpRequest request) {
        try {
            HttpMethod method = request.getMethod();
            return method != null ? method.name() : UNKNOWN_METHOD;
        } catch (RuntimeException e) {
            return UNKNOWN_METHOD;
        }
    }

    /**
     * 拦截扫描请求（副作用方法）。
     *
     * <p>返回配置的拒绝状态码，登记拦截计数指标，并以 debug 级别记录一条拦截日志，
     * 避免扫描流量刷屏日志。日志默认为 debug，需要观察时通过日志配置开启。
     * 拦截响应由 {@code exchange.getResponse().setComplete()} 直接完成，不进入下游过滤器链。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param request  HTTP 请求
     * @return 直接完成响应
     */
    private Mono<Void> block(ServerWebExchange exchange, ServerHttpRequest request) {
        int statusCode = properties.getBlockStatus();
        HttpStatus status = HttpStatus.resolve(statusCode);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status != null ? status : HttpStatus.FORBIDDEN);

        meterRegistry
                .counter(
                        METRIC_SCAN_BLOCKED,
                        List.of(
                                Tag.of("method", safeMethodName(request)),
                                Tag.of("path", request.getPath().value())))
                .increment();

        log.debug(
                "拦截扫描探测请求, method: {}, path: {}, remoteAddress: {}, status: {}",
                safeMethodName(request),
                request.getPath(),
                request.getRemoteAddress(),
                statusCode);
        return response.setComplete();
    }

    /**
     * 路径模式匹配（纯函数）。
     *
     * <p>匹配过程分为两条无中间状态的函数式分支：
     * <ul>
     *   <li>不含通配符的模式按路径前缀匹配（大小写不敏感）</li>
     *   <li>含 {@code *} 的模式通过 Stream 将各段转义后以 {@code .*} 连接为正则，
     *       再整体匹配（{@code *} 可匹配任意字符序列，含路径分隔符）</li>
     * </ul>
     *
     * @param pattern 路径模式
     * @param path    请求路径
     * @return 如果命中返回 true
     */
    private static boolean matches(String pattern, String path) {
        String normalizedPath = path.toLowerCase(Locale.ROOT);
        if (!pattern.contains(WILDCARD)) {
            return normalizedPath.startsWith(pattern.toLowerCase(Locale.ROOT));
        }
        String regex = Arrays.stream(pattern.split(Pattern.quote(WILDCARD), -1))
                .map(segment -> Pattern.quote(segment.toLowerCase(Locale.ROOT)))
                .collect(Collectors.joining(GLOB_MATCH_ANY));
        return Pattern.compile(regex).matcher(normalizedPath).matches();
    }
}
