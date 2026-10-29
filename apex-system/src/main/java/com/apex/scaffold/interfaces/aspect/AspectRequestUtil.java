package com.apex.scaffold.interfaces.aspect;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * 切面请求参数解析工具类。
 *
 * <p>项目同时存在函数式端点（Handler 方法参数为 {@link ServerRequest}）与注解式端点
 * （方法参数为 {@link ServerWebExchange}），各安全/审计切面均需从方法参数中定位请求
 * 对象。本工具将这一重复的 {@code for + instanceof} 查找逻辑收敛为函数式 API，并统一
 * 请求形态归一化规则，避免四处重复实现导致的语义漂移。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@link #firstArgOfType(Object[], Class)}：按参数数组顺序返回首个匹配实例，
 *       未找到返回 {@code null}</li>
 *   <li>{@link #resolveRequestMeta(Object[])}：优先 {@link ServerWebExchange}，
 *       其次 {@link ServerHttpRequest}，最后 {@link ServerRequest}，统一归一化为
 *       {@link RequestMeta} 请求元数据；三种形态均不存在时返回 {@code null}</li>
 *   <li>{@link #resolveServerHttpRequest(Object[])}：仅在能获得底层
 *       {@link ServerHttpRequest} 时返回（注解式端点与直传 {@link ServerHttpRequest}），
 *       函数式 {@link ServerRequest} 请改用 {@link #resolveRequestMeta(Object[])}</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 * @see ServerWebExchange
 * @see ServerRequest
 */
public final class AspectRequestUtil {

    /** 未知 IP 标识，与 {@code RequestUtil.UNKNOWN_IP} 语义一致。 */
    private static final String UNKNOWN_IP = "unknown";

    /** 私有构造函数，防止实例化。 */
    private AspectRequestUtil() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 从参数数组中按顺序返回首个指定类型实例。
     *
     * <p>等价于手写 {@code for (Object arg : args) if (arg instanceof T)} 查找，
     * 以函数式 API 消除切面中重复的遍历样板。空数组或空值参数返回 {@code null}。</p>
     *
     * @param args 方法参数数组，可为 {@code null}
     * @param type 目标类型
     * @param <T>  目标类型泛型
     * @return 首个匹配参数，未找到返回 {@code null}
     */
    public static <T> T firstArgOfType(Object[] args, Class<T> type) {
        if (args == null) {
            return null;
        }
        return Arrays.stream(args)
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElse(null);
    }

    /**
     * 从参数数组中解析统一请求元数据，兼容注解式与函数式端点。
     *
     * <p>查找优先级：{@link ServerWebExchange} → 直传的 {@link ServerHttpRequest} →
     * 函数式 {@link ServerRequest}。函数式端点直接消费 {@link ServerRequest} 自身的
     * {@code path()/method()/headers()/remoteAddress()}，<b>不依赖
     * {@code ServerRequest.exchange()}</b>（该调用在部分 WebFlux 构建与测试场景返回
     * {@code null}），保证两种端点形态在切面中行为一致。</p>
     *
     * @param args 方法参数数组，可为 {@code null}
     * @return 请求元数据，未找到请求对象时返回 {@code null}
     */
    public static RequestMeta resolveRequestMeta(Object[] args) {
        ServerWebExchange exchange = firstArgOfType(args, ServerWebExchange.class);
        if (exchange != null) {
            return fromHttpRequest(exchange.getRequest());
        }
        ServerHttpRequest directRequest = firstArgOfType(args, ServerHttpRequest.class);
        if (directRequest != null) {
            return fromHttpRequest(directRequest);
        }
        ServerRequest serverRequest = firstArgOfType(args, ServerRequest.class);
        if (serverRequest == null) {
            return null;
        }
        HttpHeaders headers = serverRequest.headers().asHttpHeaders();
        return new RequestMeta(
                serverRequest.path(), serverRequest.method(), headers, resolveRemoteAddress(serverRequest));
    }

    /**
     * 从参数数组中解析底层 HTTP 请求对象，兼容注解式与函数式端点。
     *
     * <p>仅当方法参数中存在 {@link ServerWebExchange} 或直传 {@link ServerHttpRequest}
     * 时返回底层 {@link ServerHttpRequest}；纯函数式 {@link ServerRequest} 无法可靠
     * 转换为 {@link ServerHttpRequest}（{@code exchange()} 在部分场景返回 {@code null}），
     * 此类调用方应改用 {@link #resolveRequestMeta(Object[])} 消费函数式请求。</p>
     *
     * @param args 方法参数数组，可为 {@code null}
     * @return 底层 HTTP 请求，未找到请求对象或无法转换时返回 {@code null}
     */
    public static ServerHttpRequest resolveServerHttpRequest(Object[] args) {
        ServerWebExchange exchange = firstArgOfType(args, ServerWebExchange.class);
        if (exchange != null) {
            return exchange.getRequest();
        }
        ServerHttpRequest directRequest = firstArgOfType(args, ServerHttpRequest.class);
        if (directRequest != null) {
            return directRequest;
        }
        ServerRequest serverRequest = firstArgOfType(args, ServerRequest.class);
        if (serverRequest == null || serverRequest.exchange() == null) {
            return null;
        }
        return serverRequest.exchange().getRequest();
    }

    /**
     * 从参数数组中解析请求头，兼容注解式与函数式端点。
     *
     * <p>{@link #resolveRequestMeta(Object[])} 的便捷封装：仅关心请求头
     * （如防重放、限流切面）时直接获取 {@link HttpHeaders}。</p>
     *
     * @param args 方法参数数组，可为 {@code null}
     * @return 请求头，未找到请求对象时返回 {@code null}
     */
    public static HttpHeaders resolveHttpHeaders(Object[] args) {
        RequestMeta meta = resolveRequestMeta(args);
        return meta == null ? null : meta.headers();
    }

    /**
     * 将 {@link ServerHttpRequest} 归一化为请求元数据。
     *
     * <p>对 URI 为 {@code null} 的场景（如单元测试 mock 未 stub URI）优雅降级，
     * 路径取空字符串，避免请求头解析等轻量消费场景因缺失 URI 而中断。</p>
     *
     * @param request 底层 HTTP 请求
     * @return 请求元数据
     */
    private static RequestMeta fromHttpRequest(ServerHttpRequest request) {
        URI uri = request.getURI();
        return new RequestMeta(
                uri == null ? "" : uri.getPath(),
                request.getMethod(),
                request.getHeaders(),
                request.getRemoteAddress());
    }

    /**
     * 提取函数式请求的远端地址。
     *
     * @param request 函数式请求
     * @return 远端地址，无法获取时返回 {@code null}
     */
    private static InetSocketAddress resolveRemoteAddress(ServerRequest request) {
        Optional<InetSocketAddress> remoteAddress = request.remoteAddress();
        return remoteAddress.orElse(null);
    }

    /**
     * 基于请求元数据提取客户端 IP（与 {@code RequestUtil} 语义一致）。
     *
     * @param meta 请求元数据
     * @return 客户端 IP，无法获取时返回 {@code unknown}
     */
    public static String resolveClientIp(RequestMeta meta) {
        if (meta == null) {
            return UNKNOWN_IP;
        }
        String forwardedFor = meta.headers().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = meta.headers().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        InetSocketAddress remoteAddress = meta.remoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return UNKNOWN_IP;
    }

    /**
     * 统一的请求元数据快照：路径、方法、请求头与远端地址。
     *
     * <p>由 {@link #resolveRequestMeta(Object[])} 从三种请求形态归一化而来，
     * 供切面在不强依赖具体请求对象的前提下读取通用请求信息。</p>
     *
     * @param path         请求路径
     * @param method       请求方法
     * @param headers      请求头
     * @param remoteAddress 远端地址，可能为 {@code null}
     */
    public record RequestMeta(String path, HttpMethod method, HttpHeaders headers, InetSocketAddress remoteAddress) {}
}
