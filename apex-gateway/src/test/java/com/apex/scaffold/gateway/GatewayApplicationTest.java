package com.apex.scaffold.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.gateway.config.GatewayExceptionHandler;
import com.apex.scaffold.gateway.filter.GrayRouteFilter;
import com.apex.scaffold.gateway.filter.JwtSignatureFilter;
import com.apex.scaffold.gateway.filter.RequestLoggingFilter;
import com.apex.scaffold.gateway.filter.RequestRateLimitFilter;
import com.apex.scaffold.gateway.filter.TraceWebFilter;
import com.apex.scaffold.gateway.filter.UserContextRelayFilter;
import com.apex.scaffold.gateway.security.GatewayJwtVerifierService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

/**
 * 网关上下文加载测试.
 *
 * <p>验证迁移方案 §1.2.5 要求的 Spring 上下文可正常启动，且过滤器链与验签服务
 * 全部按预期装配。使用 {@code dev} 环境（本地默认关闭 Nacos/服务发现），
 * 保证无外部依赖时也能完成上下文加载。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("GatewayApplication 上下文加载测试")
class GatewayApplicationTest {

    private final TraceWebFilter traceWebFilter;
    private final JwtSignatureFilter jwtSignatureFilter;
    private final UserContextRelayFilter userContextRelayFilter;
    private final RequestRateLimitFilter requestRateLimitFilter;
    private final GrayRouteFilter grayRouteFilter;
    private final RequestLoggingFilter requestLoggingFilter;
    private final GatewayJwtVerifierService gatewayJwtVerifierService;
    private final GatewayExceptionHandler gatewayExceptionHandler;

    /** 构造测试实例并注入网关过滤器与验签组件。 */
    GatewayApplicationTest(
            TraceWebFilter traceWebFilter,
            JwtSignatureFilter jwtSignatureFilter,
            UserContextRelayFilter userContextRelayFilter,
            RequestRateLimitFilter requestRateLimitFilter,
            GrayRouteFilter grayRouteFilter,
            RequestLoggingFilter requestLoggingFilter,
            GatewayJwtVerifierService gatewayJwtVerifierService,
            GatewayExceptionHandler gatewayExceptionHandler) {
        this.traceWebFilter = traceWebFilter;
        this.jwtSignatureFilter = jwtSignatureFilter;
        this.userContextRelayFilter = userContextRelayFilter;
        this.requestRateLimitFilter = requestRateLimitFilter;
        this.grayRouteFilter = grayRouteFilter;
        this.requestLoggingFilter = requestLoggingFilter;
        this.gatewayJwtVerifierService = gatewayJwtVerifierService;
        this.gatewayExceptionHandler = gatewayExceptionHandler;
    }

    @Test
    @DisplayName("上下文应成功加载并装配全部过滤器与验签服务")
    void contextLoads() {
        assertThat(traceWebFilter).isNotNull();
        assertThat(jwtSignatureFilter).isNotNull();
        assertThat(userContextRelayFilter).isNotNull();
        assertThat(requestRateLimitFilter).isNotNull();
        assertThat(grayRouteFilter).isNotNull();
        assertThat(requestLoggingFilter).isNotNull();
        assertThat(gatewayJwtVerifierService).isNotNull();
        assertThat(gatewayExceptionHandler).isNotNull();
    }

    @Test
    @DisplayName("过滤器链顺序应符合迁移方案 §5.3 约定")
    void filterChainOrderShouldMatchSpec() {
        assertThat(traceWebFilter.getOrder()).isEqualTo(-1);
        assertThat(jwtSignatureFilter.getOrder()).isEqualTo(10);
        assertThat(userContextRelayFilter.getOrder()).isEqualTo(20);
        assertThat(requestRateLimitFilter.getOrder()).isEqualTo(30);
        assertThat(grayRouteFilter.getOrder()).isEqualTo(40);
        assertThat(requestLoggingFilter.getOrder()).isEqualTo(50);
    }
}
