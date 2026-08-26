package com.apex.scaffold.interfaces.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import com.apex.scaffold.interfaces.util.MessageUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * InputValidationFilter 单元测试
 *
 * <p>验证输入验证过滤器的安全防护功能，包括路径遍历检测、XSS攻击检测、SQL注入检测等
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InputValidationFilter 输入验证过滤器测试")
class InputValidationFilterTest {

    @Mock
    private InputValidationProperties validationProperties;

    @Mock
    private MessageUtils messageUtils;

    @Mock
    private SecurityMetricsRecorder securityMetricsRecorder;

    @Mock
    private WebFilterChain chain;

    /**
     * 执行 {@code shouldBlockPathTraversal} 操作。
     */
    @Test
    @DisplayName("路径遍历攻击应该被拦截")
    void shouldBlockPathTraversal() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/test/../../etc/passwd").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(validationProperties.isEnabled()).thenReturn(true);
        when(validationProperties.isPathTraversalEnabled()).thenReturn(true);
        when(validationProperties.isXssEnabled()).thenReturn(false);
        when(validationProperties.isSqlInjectionPrecheck()).thenReturn(false);
        when(validationProperties.getMaxParamLength()).thenReturn(1024);

        InputValidationFilter filter =
                new InputValidationFilter(validationProperties, messageUtils, securityMetricsRecorder);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(400);
    }

    /**
     * 执行 {@code shouldBlockXssAttack} 操作。
     */
    @Test
    @DisplayName("XSS攻击应该被拦截")
    void shouldBlockXssAttack() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test?data=<script>alert('xss')</script>")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(validationProperties.isEnabled()).thenReturn(true);
        when(validationProperties.isPathTraversalEnabled()).thenReturn(false);
        when(validationProperties.isXssEnabled()).thenReturn(true);
        when(validationProperties.isSqlInjectionPrecheck()).thenReturn(false);
        when(validationProperties.getMaxParamLength()).thenReturn(1024);

        InputValidationFilter filter =
                new InputValidationFilter(validationProperties, messageUtils, securityMetricsRecorder);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(400);
        verify(securityMetricsRecorder).recordXssIntercepted();
    }

    /**
     * 执行 {@code shouldBlockSqlInjection} 操作。
     */
    @Test
    @DisplayName("SQL注入攻击应该被拦截")
    void shouldBlockSqlInjection() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test?id=1'; DROP TABLE users;--")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(validationProperties.isEnabled()).thenReturn(true);
        when(validationProperties.isPathTraversalEnabled()).thenReturn(false);
        when(validationProperties.isXssEnabled()).thenReturn(false);
        when(validationProperties.isSqlInjectionPrecheck()).thenReturn(true);
        when(validationProperties.getMaxParamLength()).thenReturn(1024);

        InputValidationFilter filter =
                new InputValidationFilter(validationProperties, messageUtils, securityMetricsRecorder);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(400);
    }

    /**
     * 执行 {@code shouldAllowNormalRequest} 操作。
     */
    @Test
    @DisplayName("正常请求应该通过")
    void shouldAllowNormalRequest() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test?id=123&name=test")
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(validationProperties.isEnabled()).thenReturn(true);
        when(validationProperties.isPathTraversalEnabled()).thenReturn(true);
        when(validationProperties.isXssEnabled()).thenReturn(true);
        when(validationProperties.isSqlInjectionPrecheck()).thenReturn(true);
        when(validationProperties.getMaxParamLength()).thenReturn(1024);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        InputValidationFilter filter =
                new InputValidationFilter(validationProperties, messageUtils, securityMetricsRecorder);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    /**
     * 执行 {@code shouldAllowAllRequestsWhenDisabled} 操作。
     */
    @Test
    @DisplayName("禁用验证时应该允许所有请求")
    void shouldAllowAllRequestsWhenDisabled() {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/test/../../etc/passwd").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(validationProperties.isEnabled()).thenReturn(false);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        InputValidationFilter filter =
                new InputValidationFilter(validationProperties, messageUtils, securityMetricsRecorder);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
