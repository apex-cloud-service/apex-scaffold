package com.apex.scaffold.interfaces.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 扫描流量防护过滤器单元测试。
 *
 * <p>验证 {@link ScanTrafficGuardWebFilter} 的路径模式匹配、CONNECT 隧道拦截、
 * 状态码配置与放行逻辑。
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ScanTrafficGuardWebFilter 扫描流量防护过滤器测试")
class ScanTrafficGuardWebFilterTest {

    @Mock
    private WebFilterChain chain;

    @Mock
    private ScanTrafficGuardProperties properties;

    private ScanTrafficGuardWebFilter filter;

    /**
     * 执行对应的业务处理；该操作由 {@code setUp} 定义。
     *
     */
    @BeforeEach
    void setUp() {
        filter = new ScanTrafficGuardWebFilter(properties, new SimpleMeterRegistry());
        when(properties.getBlockStatus()).thenReturn(403);
        when(properties.isBlockConnectMethod()).thenReturn(true);
        when(properties.getPathPatterns()).thenReturn(List.of("/HNAP1", "/CFIDE", "/.git", "/.env", "/success.txt"));
    }

    /**
     * 验证 {@code shouldPassThroughNormalApiRequest} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("正常业务请求应该放行")
    void shouldPassThroughNormalApiRequest() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    /**
     * 验证 {@code shouldPassThroughHealthPath} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("健康检查路径应该放行")
    void shouldPassThroughHealthPath() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    /**
     * 验证 {@code shouldBlockKnownScanPath} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("已知扫描路径前缀应该被拦截")
    void shouldBlockKnownScanPath() {
        ServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/HNAP1/").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * 验证 {@code shouldBlockGitLeakProbe} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName(".git 路径下的探测请求应该被拦截")
    void shouldBlockGitLeakProbe() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/.git/HEAD").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * 验证 {@code shouldBlockEnvLeakProbe} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName(".env 前缀环境变量探测应该被拦截")
    void shouldBlockEnvLeakProbe() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/.env.production").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * 验证 {@code shouldBlockExactScanMarker} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("精确扫描标记文件应该被拦截")
    void shouldBlockExactScanMarker() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/success.txt").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * 验证 {@code shouldMatchPathCaseInsensitively} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("路径匹配应该大小写不敏感")
    void shouldMatchPathCaseInsensitively() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/.GIT/config").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * 验证 {@code shouldBlockConnectTunnelProbe} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("CONNECT 隧道探测请求应该被拦截")
    void shouldBlockConnectTunnelProbe() {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getMethod()).thenThrow(new IllegalArgumentException("No enum constant HttpMethod.CONNECT"));
        when(request.getPath()).thenReturn(RequestPath.parse("/", null));
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.setComplete()).thenReturn(Mono.empty());
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(exchange);
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    /**
     * 验证 {@code shouldPassThroughConnectWhenDisabled} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("关闭 CONNECT 拦截时 CONNECT 请求应该放行")
    void shouldPassThroughConnectWhenDisabled() {
        when(properties.isBlockConnectMethod()).thenReturn(false);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getMethod()).thenThrow(new IllegalArgumentException("No enum constant HttpMethod.CONNECT"));
        when(request.getPath()).thenReturn(RequestPath.parse("/", null));
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getRequest()).thenReturn(request);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    /**
     * 验证 {@code shouldReturnConfiguredStatus} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("配置 404 状态码时拦截响应应返回 404")
    void shouldReturnConfiguredStatus() {
        when(properties.getBlockStatus()).thenReturn(404);
        ServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/CFIDE/").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * 验证 {@code shouldMatchGlobPattern} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("通配符模式应匹配任意层级路径")
    void shouldMatchGlobPattern() {
        when(properties.getPathPatterns()).thenReturn(List.of("*/.env*"));
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/app/.env").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain, never()).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
