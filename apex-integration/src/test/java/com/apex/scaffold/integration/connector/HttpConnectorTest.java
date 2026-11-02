package com.apex.scaffold.integration.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.integration.connector.domain.connector.HttpConnector;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * HTTP 连接器测试（方案 M5 / 批次 E2）。
 *
 * <p>以 WebClient {@code ExchangeFunction} mock 验证：none/basic/bearer/header
 * 四种认证头、GET 查询参数拼装、JSON 响应反序列化、错误状态与兜底包络。
 * 重试语义由 {@code ConnectorExecutionServiceTest} 覆盖。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("HTTP 连接器认证头与请求构造测试")
class HttpConnectorTest {

    private ExchangeFunction exchangeFunction;

    private HttpConnector connector;

    @BeforeEach
    void setUp() {
        exchangeFunction = mock(ExchangeFunction.class);
        connector = new HttpConnector(
                WebClient.builder().exchangeFunction(exchangeFunction).build());
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenAnswer(invocation -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body("{\"ok\":true}")
                        .build()));
    }

    private ClientRequest capturedRequest() {
        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchangeFunction).exchange(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("bearer 认证注入 Authorization: Bearer 头")
    void bearerAuthHeader() {
        connector
                .execute(
                        Map.of("baseUrl", "http://svc", "auth", Map.of("type", "bearer", "secret", "tok-1")),
                        "GET /x",
                        Map.of(),
                        "idem-1")
                .block();
        assertThat(capturedRequest().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer tok-1");
    }

    @Test
    @DisplayName("basic 认证注入 Base64 用户名密码头")
    void basicAuthHeader() {
        connector
                .execute(
                        Map.of(
                                "baseUrl",
                                "http://svc",
                                "auth",
                                Map.of("type", "basic", "username", "user", "secret", "pass")),
                        "GET /x",
                        Map.of(),
                        null)
                .block();
        String expected = "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes());
        assertThat(capturedRequest().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("header 认证注入自定义 API Key 头")
    void headerAuth() {
        connector
                .execute(
                        Map.of(
                                "baseUrl",
                                "http://svc",
                                "auth",
                                Map.of("type", "header", "headerName", "X-API-Key", "secret", "k-1")),
                        "GET /x",
                        Map.of(),
                        null)
                .block();
        assertThat(capturedRequest().headers().getFirst("X-API-Key")).isEqualTo("k-1");
    }

    @Test
    @DisplayName("none 认证不注入鉴权头")
    void noneAuthSendsNoAuthHeader() {
        connector
                .execute(Map.of("baseUrl", "http://svc", "auth", Map.of("type", "none")), "GET /x", Map.of(), null)
                .block();
        assertThat(capturedRequest().headers().getFirst(HttpHeaders.AUTHORIZATION))
                .isNull();
    }

    @Test
    @DisplayName("GET 载荷拍平为查询参数")
    void getPayloadBecomesQuery() {
        connector
                .execute(Map.of("baseUrl", "http://svc"), "GET /list", Map.of("page", 2, "size", 20), null)
                .block();
        ClientRequest request = capturedRequest();
        assertThat(request.method().name()).isEqualTo("GET");
        assertThat(request.url().toString()).contains("page=2").contains("size=20");
    }

    @Test
    @DisplayName("operation 非 HTTP 语义时回退配置 method/path，POST 发送 JSON 体")
    void fallsBackToConfiguredMethodAndPath() {
        connector
                .execute(
                        Map.of("baseUrl", "http://svc", "method", "POST", "path", "/notify"),
                        "sendMessage",
                        Map.of("text", "hi"),
                        null)
                .block();
        ClientRequest request = capturedRequest();
        assertThat(request.method().name()).isEqualTo("POST");
        assertThat(request.url().toString()).endsWith("/notify");
    }

    @Test
    @DisplayName("JSON 对象响应直接反序列化为载荷")
    void jsonBodyDeserialized() {
        StepVerifier.create(connector.execute(Map.of("baseUrl", "http://svc"), "GET /x", Map.of(), null))
                .assertNext(payload -> assertThat(payload).containsEntry("ok", true))
                .verifyComplete();
    }

    @Test
    @DisplayName("非 JSON 响应返回状态+原文包络")
    void plainBodyWrapped() {
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenAnswer(invocation -> Mono.just(
                        ClientResponse.create(HttpStatus.OK).body("plain-text").build()));
        StepVerifier.create(connector.execute(Map.of("baseUrl", "http://svc"), "GET /x", Map.of(), null))
                .assertNext(payload -> {
                    assertThat(payload).containsEntry("status", 200).containsEntry("body", "plain-text");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("HTTP 4xx/5xx 状态以业务错误终止")
    void errorStatusFails() {
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenAnswer(invocation -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                        .body("{\"msg\":\"bad\"}")
                        .build()));
        StepVerifier.create(connector.execute(Map.of("baseUrl", "http://svc"), "GET /x", Map.of(), null))
                .expectError(BusinessException.class)
                .verify();
    }
}
