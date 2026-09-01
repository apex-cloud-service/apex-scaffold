package com.apex.scaffold.interfaces.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.interfaces.aspect.AspectRequestUtil.RequestMeta;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * AspectRequestUtil 切面请求解析工具测试
 *
 * <p>验证统一请求解析工具对注解式端点（{@link ServerWebExchange}）、直传
 * {@link ServerHttpRequest} 与函数式端点（{@link ServerRequest}）三种请求形态的
 * 归一化、客户端 IP 提取降级链以及空参数防御行为。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 */
@DisplayName("AspectRequestUtil 切面请求解析工具测试")
class AspectRequestUtilTest {

    private static final String PATH = "/api/v1/auth/register";
    private static final String CLIENT_IP = "192.168.1.10";

    @Test
    @DisplayName("参数含 ServerWebExchange 时应以其请求信息构建 RequestMeta")
    void shouldResolveMetaFromServerWebExchange() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", "req-001");
        when(request.getURI()).thenReturn(URI.create(PATH));
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress(CLIENT_IP, 8080));
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getRequest()).thenReturn(request);

        RequestMeta meta = AspectRequestUtil.resolveRequestMeta(new Object[] {"ignored", exchange});

        assertThat(meta).isNotNull();
        assertThat(meta.path()).isEqualTo(PATH);
        assertThat(meta.method()).isEqualTo(HttpMethod.POST);
        assertThat(meta.headers().getFirst("X-Request-Id")).isEqualTo("req-001");
        assertThat(meta.remoteAddress()).isNotNull();
        assertThat(meta.remoteAddress().getAddress().getHostAddress()).isEqualTo(CLIENT_IP);
    }

    @Test
    @DisplayName("参数直传 ServerHttpRequest 时应直接以其构建 RequestMeta")
    void shouldResolveMetaFromDirectServerHttpRequest() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create(PATH));
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        RequestMeta meta = AspectRequestUtil.resolveRequestMeta(new Object[] {request});

        assertThat(meta).isNotNull();
        assertThat(meta.path()).isEqualTo(PATH);
        assertThat(meta.method()).isEqualTo(HttpMethod.GET);
    }

    @Test
    @DisplayName("函数式 ServerRequest 应直接消费自身请求信息且不调用 exchange()")
    void shouldResolveMetaFromServerRequestWithoutExchange() {
        ServerRequest serverRequest = mock(ServerRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", "req-002");
        ServerRequest.Headers serverHeaders = mock(ServerRequest.Headers.class);
        when(serverHeaders.asHttpHeaders()).thenReturn(headers);
        when(serverRequest.headers()).thenReturn(serverHeaders);
        when(serverRequest.path()).thenReturn(PATH);
        when(serverRequest.method()).thenReturn(HttpMethod.PUT);
        when(serverRequest.remoteAddress()).thenReturn(Optional.of(new InetSocketAddress(CLIENT_IP, 8080)));

        RequestMeta meta = AspectRequestUtil.resolveRequestMeta(new Object[] {serverRequest});

        assertThat(meta).isNotNull();
        assertThat(meta.path()).isEqualTo(PATH);
        assertThat(meta.method()).isEqualTo(HttpMethod.PUT);
        assertThat(meta.headers().getFirst("X-Request-Id")).isEqualTo("req-002");
        assertThat(meta.remoteAddress()).isNotNull();
        assertThat(meta.remoteAddress().getAddress().getHostAddress()).isEqualTo(CLIENT_IP);
        verify(serverRequest, never()).exchange();
    }

    @Test
    @DisplayName("参数无请求对象时应返回 null")
    void shouldReturnNullWhenNoRequestPresent() {
        assertThat(AspectRequestUtil.resolveRequestMeta(new Object[] {"someParam"}))
                .isNull();
        assertThat(AspectRequestUtil.resolveRequestMeta(null)).isNull();
        assertThat(AspectRequestUtil.resolveRequestMeta(new Object[0])).isNull();
    }

    @Test
    @DisplayName("X-Forwarded-For 存在时应取其首个 IP")
    void shouldResolveClientIpFromForwardedFor() {
        RequestMeta meta = metaWithHeaders(header -> header.set("X-Forwarded-For", "203.0.113.5, 10.0.0.1"));

        assertThat(AspectRequestUtil.resolveClientIp(meta)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("无 X-Forwarded-For 时应回退到 X-Real-IP")
    void shouldResolveClientIpFromRealIp() {
        RequestMeta meta = metaWithHeaders(header -> header.set("X-Real-IP", "198.51.100.7"));

        assertThat(AspectRequestUtil.resolveClientIp(meta)).isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("无转发头时应回退到远端地址")
    void shouldResolveClientIpFromRemoteAddress() {
        RequestMeta meta =
                new RequestMeta(PATH, HttpMethod.GET, new HttpHeaders(), new InetSocketAddress(CLIENT_IP, 8080));

        assertThat(AspectRequestUtil.resolveClientIp(meta)).isEqualTo(CLIENT_IP);
    }

    @Test
    @DisplayName("转发头为空或无法解析时应返回 unknown")
    void shouldReturnUnknownWhenNoAddress() {
        RequestMeta noHeaders = metaWithHeaders(header -> header.set("X-Forwarded-For", "   "));
        assertThat(AspectRequestUtil.resolveClientIp(noHeaders)).isEqualTo("unknown");

        RequestMeta noRemote = new RequestMeta(PATH, HttpMethod.GET, new HttpHeaders(), null);
        assertThat(AspectRequestUtil.resolveClientIp(noRemote)).isEqualTo("unknown");

        assertThat(AspectRequestUtil.resolveClientIp(null)).isEqualTo("unknown");
    }

    @Test
    @DisplayName("resolveHttpHeaders 应委托 resolveRequestMeta 返回请求头")
    void shouldResolveHttpHeadersFromArgs() {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Timestamp", "1234567890");
        when(exchange.getRequest()).thenReturn(request);
        when(request.getURI()).thenReturn(URI.create(PATH));
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getHeaders()).thenReturn(headers);

        HttpHeaders resolved = AspectRequestUtil.resolveHttpHeaders(new Object[] {exchange});

        assertThat(resolved).isNotNull();
        assertThat(resolved.getFirst("X-Timestamp")).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("resolveHttpHeaders 无请求对象时应返回 null")
    void shouldResolveNullHttpHeadersWithoutRequest() {
        assertThat(AspectRequestUtil.resolveHttpHeaders(new Object[] {"someParam"}))
                .isNull();
        assertThat(AspectRequestUtil.resolveHttpHeaders(null)).isNull();
    }

    @Test
    @DisplayName("函数式 ServerRequest 的 exchange() 返回 null 时应优雅降级")
    void shouldResolveNullServerHttpRequestWhenExchangeReturnsNull() {
        ServerRequest serverRequest = mock(ServerRequest.class);
        when(serverRequest.exchange()).thenReturn(null);

        assertThat(AspectRequestUtil.resolveServerHttpRequest(new Object[] {serverRequest}))
                .isNull();
    }

    @Test
    @DisplayName("ServerWebExchange 的 URI 为 null 时路径应优雅降级为空串")
    void shouldGracefullyDegradeWhenUriIsNull() {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(exchange.getRequest()).thenReturn(request);
        when(request.getURI()).thenReturn(null);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        RequestMeta meta = AspectRequestUtil.resolveRequestMeta(new Object[] {exchange});

        assertThat(meta).isNotNull();
        assertThat(meta.path()).isEmpty();
        assertThat(meta.method()).isEqualTo(HttpMethod.GET);
    }

    @Test
    @DisplayName("firstArgOfType 应返回首个匹配实例并忽略其他类型")
    void shouldFindFirstArgOfType() {
        Object[] args = {"someParam", "anotherParam", 42};

        assertThat(AspectRequestUtil.firstArgOfType(args, String.class)).isEqualTo("someParam");
        assertThat(AspectRequestUtil.firstArgOfType(args, Integer.class)).isEqualTo(42);
        assertThat(AspectRequestUtil.firstArgOfType(null, String.class)).isNull();
        assertThat(AspectRequestUtil.firstArgOfType(args, Boolean.class)).isNull();
    }

    /**
     * 构建仅含指定请求头的请求元数据
     *
     * @param headerSetter 请求头设置回调
     * @return 请求元数据
     */
    private RequestMeta metaWithHeaders(java.util.function.Consumer<HttpHeaders> headerSetter) {
        HttpHeaders headers = new HttpHeaders();
        headerSetter.accept(headers);
        return new RequestMeta(PATH, HttpMethod.GET, headers, null);
    }
}
