package com.apex.scaffold.infrastructure.remote.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.domain.gateway.RemoteApiException;
import com.apex.scaffold.infrastructure.remote.config.RemoteApiProperties;
import com.apex.scaffold.infrastructure.remote.config.RemoteApiProperties.ServiceConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 远程API调用客户端单元测试类
 *
 * <p>测试 RemoteApiClient 的各类HTTP方法调用，包括 GET、POST JSON、POST 表单、
 * PUT、DELETE 等请求的发送、响应解析、错误处理和重试逻辑。
 *
 * <p>使用 Mockito 深度模拟 WebClient 调用链，验证请求构造和响应处理的正确性。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RemoteApiClientTest {

    /** 测试服务名称 */
    private static final String TEST_SERVICE = "user-service";

    /** 测试请求路径 */
    private static final String TEST_PATH = "/api/users/1";

    /** 测试基础URL */
    private static final String TEST_BASE_URL = "http://localhost:8081";

    /** 测试响应数据 */
    private static final String TEST_RESPONSE = "{\"id\":1,\"name\":\"张三\"}";

    @Mock
    private RemoteApiProperties properties;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RequestBodySpec requestBodySpec;

    @Mock
    private RequestHeadersSpec<?> requestHeadersSpec;

    @Mock
    private ResponseSpec responseSpec;

    /** 被测试的RemoteApiClient实例 */
    private RemoteApiClient remoteApiClient;

    /** 服务配置 */
    private ServiceConfig serviceConfig;

    /**
     * 每个测试方法执行前的初始化操作
     *
     * <p>创建测试所需的服务配置和RemoteApiClient实例，
     * 并设置WebClient模拟链的基础行为。
     */
    @BeforeEach
    void setUp() {
        serviceConfig = new ServiceConfig();
        serviceConfig.setBaseUrl(TEST_BASE_URL);
        serviceConfig.setConnectTimeout(3000);
        serviceConfig.setReadTimeout(8000);
        serviceConfig.setRetryCount(0);

        Map<String, ServiceConfig> services = new HashMap<>();
        services.put(TEST_SERVICE, serviceConfig);
        when(properties.getServices()).thenReturn(services);
        when(properties.getDefaultConnectTimeout()).thenReturn(5000);
        when(properties.getDefaultReadTimeout()).thenReturn(10000);
        when(properties.getDefaultRetryCount()).thenReturn(0);
        when(properties.getDefaultRetryDelay()).thenReturn(1000L);
        when(properties.getMaxInMemorySize()).thenReturn(10 * 1024 * 1024);

        when(webClientBuilder.clone()).thenReturn(webClientBuilder);
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.filter(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.defaultHeaders(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.clientConnector(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        remoteApiClient = new RemoteApiClient(properties, webClientBuilder);
    }

    /**
     * 配置GET请求的WebClient模拟链
     *
     * <p>设置从 WebClient.method() → uri() → retrieve() → bodyToMono() 的完整调用链，
     * 使GET请求返回预设的响应数据。
     */
    @SuppressWarnings("unchecked")
    private void setupGetMock() {
        when(webClient.method(eq(HttpMethod.GET))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just(TEST_RESPONSE));
    }

    /**
     * 配置POST请求的WebClient模拟链
     *
     * <p>设置从 POST → uri() → contentType() → bodyValue() → retrieve() → bodyToMono()
     * 的完整调用链。
     */
    @SuppressWarnings("unchecked")
    private void setupPostMock() {
        when(webClient.method(eq(HttpMethod.POST))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just(TEST_RESPONSE));
    }

    @Test
    @DisplayName("GET请求 - 成功返回响应数据")
    void get_Success() {
        setupGetMock();

        remoteApiClient
                .get(TEST_SERVICE, TEST_PATH, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();
    }

    /**
     * 验证 {@code buildClient_AppliesConfiguredMaxInMemorySize} 场景。
     */
    @Test
    @DisplayName("构建客户端时应用远程响应缓冲上限")
    void buildClient_AppliesConfiguredMaxInMemorySize() {
        setupGetMock();

        remoteApiClient.get(TEST_SERVICE, TEST_PATH, String.class).block();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ClientCodecConfigurer>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(webClientBuilder).codecs(captor.capture());
        ClientCodecConfigurer codecConfigurer = mock(ClientCodecConfigurer.class);
        ClientCodecConfigurer.ClientDefaultCodecs defaultCodecs = mock(ClientCodecConfigurer.ClientDefaultCodecs.class);
        when(codecConfigurer.defaultCodecs()).thenReturn(defaultCodecs);

        captor.getValue().accept(codecConfigurer);

        verify(defaultCodecs).maxInMemorySize(10 * 1024 * 1024);
    }

    @Test
    @DisplayName("GET请求 - 带查询参数成功返回")
    void get_WithQueryParams_Success() {
        setupGetMock();

        Map<String, String> queryParams = Map.of("page", "1", "size", "10");

        remoteApiClient
                .get(TEST_SERVICE, TEST_PATH, queryParams, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();
    }

    @Test
    @DisplayName("GET请求 - 带自定义请求头成功返回")
    void get_WithHeaders_Success() {
        setupGetMock();

        Map<String, String> headers = Map.of("Authorization", "Bearer token123", "X-Request-Id", "req-001");

        remoteApiClient
                .get(TEST_SERVICE, TEST_PATH, null, headers, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();
    }

    @Test
    @DisplayName("GET请求 - 使用泛型类型引用成功返回")
    void get_WithTypeReference_Success() {
        setupGetMock();

        ParameterizedTypeReference<Map<String, Object>> typeRef =
                new ParameterizedTypeReference<Map<String, Object>>() {};

        when(responseSpec.bodyToMono(eq(typeRef))).thenReturn(Mono.just(Map.of("id", 1, "name", "张三")));

        remoteApiClient
                .get(TEST_SERVICE, TEST_PATH, null, null, typeRef)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("id", 1);
                    assertThat(result).containsEntry("name", "张三");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("GET请求 - 服务未配置时抛出异常")
    void get_ServiceNotConfigured_ThrowsException() {
        assertThatThrownBy(() -> remoteApiClient.get("unknown-service", TEST_PATH, String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未找到远程服务配置")
                .hasMessageContaining("unknown-service");
    }

    @Test
    @DisplayName("GET请求 - baseUrl为空时抛出异常")
    void get_BaseUrlBlank_ThrowsException() {
        serviceConfig.setBaseUrl(null);

        assertThatThrownBy(() -> remoteApiClient.get(TEST_SERVICE, TEST_PATH, String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未配置baseUrl");
    }

    @Test
    @DisplayName("POST JSON请求 - 成功返回响应数据")
    void postJson_Success() {
        setupPostMock();

        Map<String, Object> body = Map.of("name", "李四", "age", 25);

        remoteApiClient
                .postJson(TEST_SERVICE, "/api/users", body, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();
    }

    @Test
    @DisplayName("POST JSON请求 - 空请求体成功返回")
    void postJson_NullBody_Success() {
        when(webClient.method(eq(HttpMethod.POST))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just(TEST_RESPONSE));

        remoteApiClient
                .postJson(TEST_SERVICE, "/api/users", null, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();
    }

    @Test
    @DisplayName("POST JSON请求 - 带自定义请求头成功返回")
    void postJson_WithHeaders_Success() {
        setupPostMock();

        Map<String, String> headers = Map.of("X-Api-Key", "key-001");
        Map<String, Object> body = Map.of("name", "王五");

        remoteApiClient
                .postJson(TEST_SERVICE, "/api/users", body, headers, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();
    }

    @Test
    @DisplayName("POST JSON请求 - 使用泛型类型引用")
    void postJson_WithTypeReference_Success() {
        when(webClient.method(eq(HttpMethod.POST))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        ParameterizedTypeReference<Map<String, Object>> typeRef =
                new ParameterizedTypeReference<Map<String, Object>>() {};
        when(responseSpec.bodyToMono(eq(typeRef))).thenReturn(Mono.just(Map.of("id", 2, "name", "赵六")));

        Map<String, Object> body = Map.of("name", "赵六");

        remoteApiClient
                .postJson(TEST_SERVICE, "/api/users", body, null, typeRef)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("id", 2);
                    assertThat(result).containsEntry("name", "赵六");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("POST表单请求 - 成功返回响应数据")
    void postForm_Success() {
        when(webClient.method(eq(HttpMethod.POST))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec)
                .when(requestBodySpec)
                .body(any(org.springframework.web.reactive.function.BodyInserters.FormInserter.class));
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just(TEST_RESPONSE));

        Map<String, String> formData = Map.of("username", "admin", "password", "123456");

        remoteApiClient
                .postForm(TEST_SERVICE, "/api/login", formData, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();
    }

    @Test
    @DisplayName("POST表单请求 - 空表单数据成功返回")
    void postForm_NullFormData_Success() {
        when(webClient.method(eq(HttpMethod.POST))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec)
                .when(requestBodySpec)
                .body(any(org.springframework.web.reactive.function.BodyInserters.FormInserter.class));
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just(TEST_RESPONSE));

        remoteApiClient
                .postForm(TEST_SERVICE, "/api/login", null, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();
    }

    @Test
    @DisplayName("POST表单请求 - 带自定义请求头成功返回")
    void postForm_WithHeaders_Success() {
        when(webClient.method(eq(HttpMethod.POST))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec)
                .when(requestBodySpec)
                .body(any(org.springframework.web.reactive.function.BodyInserters.FormInserter.class));
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just(TEST_RESPONSE));

        Map<String, String> formData = Map.of("token", "abc123");
        Map<String, String> headers = Map.of("X-Csrf-Token", "csrf-001");

        remoteApiClient
                .postForm(TEST_SERVICE, "/api/login", formData, headers, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"image", "file", "photo", "photos", "match_record", "feature_table", "model_json"})
    @DisplayName("POST Multipart请求 - 支持约定的 Python 文件字段")
    void postMultipart_SupportedFileFields_Success(String fileFieldName) {
        when(webClient.method(eq(HttpMethod.POST))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).body(any(BodyInserter.class));
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just(TEST_RESPONSE));

        ByteArrayResource image = new ByteArrayResource("fake-image".getBytes()) {
            /** {@inheritDoc} */
            @Override
            public String getFilename() {
                return "face.jpg";
            }
        };

        remoteApiClient
                .postMultipart(
                        TEST_SERVICE,
                        "/api/analyze",
                        fileFieldName,
                        image,
                        Map.of("method", "all", "scenario", "default"),
                        Map.of("X-Request-Id", "req-001"),
                        String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();
    }

    /**
     */
    @Test
    @DisplayName("POST Multipart请求 - image_a/image_b 多文件字段成功返回")
    void postMultipart_MultipleFileParts_Success() {
        when(webClient.method(eq(HttpMethod.POST))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).body(any(BodyInserter.class));
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        ParameterizedTypeReference<Map<String, Object>> typeRef =
                new ParameterizedTypeReference<Map<String, Object>>() {};
        when(responseSpec.bodyToMono(eq(typeRef))).thenReturn(Mono.just(Map.of("gender", "women")));

        ByteArrayResource imageA = namedResource("face-a.jpg", "fake-image-a");
        ByteArrayResource imageB = namedResource("face-b.jpg", "fake-image-b");

        remoteApiClient
                .postMultipart(
                        TEST_SERVICE,
                        "/api/files/compare",
                        List.of(
                                new RemoteApiClient.MultipartFilePart("image_a", imageA, null),
                                new RemoteApiClient.MultipartFilePart("image_b", imageB, null)),
                        null,
                        Map.of("X-Request-Id", "req-001"),
                        typeRef)
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result).containsEntry("gender", "women"))
                .verifyComplete();
    }

    @Test
    @DisplayName("POST Multipart请求 - 不支持的文件字段名抛出异常")
    void postMultipart_UnsupportedFileField_ThrowsException() {
        when(webClient.method(eq(HttpMethod.POST))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);

        ByteArrayResource image = new ByteArrayResource("fake-image".getBytes()) {
            /** {@inheritDoc} */
            @Override
            public String getFilename() {
                return "face.jpg";
            }
        };

        assertThatThrownBy(() -> remoteApiClient.postMultipart(
                        TEST_SERVICE, "/api/analyze", "avatar", image, null, null, String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的multipart文件字段名");
    }

    /**
     */
    @Test
    @DisplayName("PUT请求 - 成功返回响应数据")
    void put_Success() {
        when(webClient.method(eq(HttpMethod.PUT))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just(TEST_RESPONSE));

        Map<String, Object> body = Map.of("name", "更新后的名称");

        remoteApiClient
                .put(TEST_SERVICE, TEST_PATH, body, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();
    }

    @Test
    @DisplayName("PUT请求 - 带自定义请求头成功返回")
    void put_WithHeaders_Success() {
        when(webClient.method(eq(HttpMethod.PUT))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just(TEST_RESPONSE));

        Map<String, Object> body = Map.of("status", "active");
        Map<String, String> headers = Map.of("If-Match", "etag-001");

        remoteApiClient
                .put(TEST_SERVICE, TEST_PATH, body, headers, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();
    }

    /**
     * 删除{@code _Success}。
     */
    @Test
    @DisplayName("DELETE请求 - 成功完成")
    void delete_Success() {
        when(webClient.method(eq(HttpMethod.DELETE))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(Void.class))).thenReturn(Mono.empty());

        remoteApiClient.delete(TEST_SERVICE, TEST_PATH).as(StepVerifier::create).verifyComplete();
    }

    /**
     * 删除{@code _WithHeaders_Success}。
     */
    @Test
    @DisplayName("DELETE请求 - 带自定义请求头成功完成")
    void delete_WithHeaders_Success() {
        when(webClient.method(eq(HttpMethod.DELETE))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(Void.class))).thenReturn(Mono.empty());

        Map<String, String> headers = Map.of("X-Force-Delete", "true");

        remoteApiClient
                .delete(TEST_SERVICE, TEST_PATH, headers)
                .as(StepVerifier::create)
                .verifyComplete();
    }

    /**
     * 删除{@code withResponse_Success}。
     */
    @Test
    @DisplayName("DELETE请求 - 返回Map响应")
    void deleteWithResponse_Success() {
        when(webClient.method(eq(HttpMethod.DELETE))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);

        ParameterizedTypeReference<Map<String, Object>> mapTypeRef =
                new ParameterizedTypeReference<Map<String, Object>>() {};
        when(responseSpec.bodyToMono(eq(mapTypeRef))).thenReturn(Mono.just(Map.of("deleted", true, "id", 1)));

        Map<String, String> headers = Map.of("Accept", "application/json");

        remoteApiClient
                .deleteWithResponse(TEST_SERVICE, TEST_PATH, headers)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("deleted", true);
                    assertThat(result).containsEntry("id", 1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("RemoteApiException - isClientError返回true（4xx状态码）")
    void remoteApiException_IsClientError() {
        RemoteApiException ex =
                new RemoteApiException(404, TEST_SERVICE, TEST_PATH, "{\"error\":\"not found\"}", "资源未找到");

        assertThat(ex.isClientError()).isTrue();
        assertThat(ex.isServerError()).isFalse();
        assertThat(ex.getHttpStatus()).isEqualTo(404);
        assertThat(ex.getServiceName()).isEqualTo(TEST_SERVICE);
        assertThat(ex.getRequestPath()).isEqualTo(TEST_PATH);
        assertThat(ex.getErrorBody()).isEqualTo("{\"error\":\"not found\"}");
    }

    @Test
    @DisplayName("RemoteApiException - isServerError返回true（5xx状态码）")
    void remoteApiException_IsServerError() {
        RemoteApiException ex =
                new RemoteApiException(503, TEST_SERVICE, TEST_PATH, "{\"error\":\"service unavailable\"}", "服务不可用");

        assertThat(ex.isServerError()).isTrue();
        assertThat(ex.isClientError()).isFalse();
        assertThat(ex.getHttpStatus()).isEqualTo(503);
        assertThat(ex.getServiceName()).isEqualTo(TEST_SERVICE);
        assertThat(ex.getRequestPath()).isEqualTo(TEST_PATH);
    }

    @Test
    @DisplayName("RemoteApiException - 网络异常构造（httpStatus=-1）")
    void remoteApiException_NetworkError() {
        RuntimeException cause = new RuntimeException("Connection refused");
        RemoteApiException ex = new RemoteApiException(TEST_SERVICE, TEST_PATH, "网络连接失败", cause);

        assertThat(ex.getHttpStatus()).isEqualTo(-1);
        assertThat(ex.getServiceName()).isEqualTo(TEST_SERVICE);
        assertThat(ex.getRequestPath()).isEqualTo(TEST_PATH);
        assertThat(ex.getErrorBody()).isNull();
        assertThat(ex.getCause()).isEqualTo(cause);
        assertThat(ex.isClientError()).isFalse();
        assertThat(ex.isServerError()).isFalse();
    }

    @Test
    @DisplayName("重试判断 - 4xx响应不重试")
    void retryFilter_ClientError_NotRetried() {
        serviceConfig.setRetryCount(2);
        serviceConfig.setRetryDelay(1L);
        AtomicInteger attempts = new AtomicInteger();

        remoteApiClient
                .customRequest(
                        TEST_SERVICE,
                        TEST_PATH,
                        null,
                        String.class,
                        wc -> Mono.defer(() -> {
                            attempts.incrementAndGet();
                            return Mono.error(WebClientResponseException.create(
                                    400, "Bad Request", HttpHeaders.EMPTY, new byte[0], null));
                        }))
                .as(StepVerifier::create)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(RemoteApiException.class);
                    assertThat(((RemoteApiException) error).getHttpStatus()).isEqualTo(400);
                })
                .verify();

        assertThat(attempts).hasValue(1);
    }

    @Test
    @DisplayName("重试判断 - 5xx响应按retry-count重试")
    void retryFilter_ServerError_RetriedByConfiguredCount() {
        serviceConfig.setRetryCount(2);
        serviceConfig.setRetryDelay(1L);
        AtomicInteger attempts = new AtomicInteger();

        remoteApiClient
                .customRequest(
                        TEST_SERVICE,
                        TEST_PATH,
                        null,
                        String.class,
                        wc -> Mono.defer(() -> {
                            attempts.incrementAndGet();
                            return Mono.error(WebClientResponseException.create(
                                    503, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], null));
                        }))
                .as(StepVerifier::create)
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(RemoteApiException.class);
                    assertThat(((RemoteApiException) error).getHttpStatus()).isEqualTo(503);
                })
                .verify();

        assertThat(attempts).hasValue(3);
    }

    @Test
    @DisplayName("RemoteApiException - 简短构造（message + cause）")
    void remoteApiException_ShortConstructor() {
        RuntimeException cause = new RuntimeException("Timeout");
        RemoteApiException ex = new RemoteApiException("请求超时", cause);

        assertThat(ex.getHttpStatus()).isEqualTo(-1);
        assertThat(ex.getServiceName()).isNull();
        assertThat(ex.getRequestPath()).isNull();
        assertThat(ex.getErrorBody()).isNull();
        assertThat(ex.getMessage()).isEqualTo("请求超时");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    /**
     * 解析并获取{@code timeout_ServiceLevelOverridesGlobal}。
     */
    @Test
    @DisplayName("配置解析 - 服务级超时优先于全局默认超时")
    void resolveTimeout_ServiceLevelOverridesGlobal() {
        setupGetMock();

        remoteApiClient
                .get(TEST_SERVICE, TEST_PATH, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();

        verify(webClientBuilder).clientConnector(any());
    }

    /**
     * 解析并获取{@code timeout_UsesGlobalDefault}。
     */
    @Test
    @DisplayName("配置解析 - 未配置服务级超时时使用全局默认值")
    void resolveTimeout_UsesGlobalDefault() {
        serviceConfig.setConnectTimeout(null);
        serviceConfig.setReadTimeout(null);

        setupGetMock();

        remoteApiClient
                .get(TEST_SERVICE, TEST_PATH, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();

        verify(webClientBuilder).clientConnector(any());
    }

    @Test
    @DisplayName("默认请求头 - 配置的默认请求头自动附加到请求")
    void defaultHeaders_AppliedToRequest() {
        Map<String, String> defaultHeaders = new HashMap<>();
        defaultHeaders.put("X-Request-Source", "apex-service");
        defaultHeaders.put("X-Client-Version", "1.0.0");
        serviceConfig.setDefaultHeaders(defaultHeaders);

        setupGetMock();

        remoteApiClient
                .get(TEST_SERVICE, TEST_PATH, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();

        HttpHeaders appliedHeaders = captureAppliedHeaders();
        assertThat(appliedHeaders.getFirst("X-Request-Source")).isEqualTo("apex-service");
        assertThat(appliedHeaders.getFirst("X-Client-Version")).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("自定义请求头 - 覆盖默认请求头中的同名键")
    void customHeaders_OverrideDefault() {
        Map<String, String> defaultHeaders = new HashMap<>();
        defaultHeaders.put("X-Request-Source", "apex-service");
        serviceConfig.setDefaultHeaders(defaultHeaders);

        setupGetMock();

        Map<String, String> customHeaders = Map.of("X-Request-Source", "apex-service-test");

        remoteApiClient
                .get(TEST_SERVICE, TEST_PATH, null, customHeaders, String.class)
                .as(StepVerifier::create)
                .expectNext(TEST_RESPONSE)
                .verifyComplete();

        HttpHeaders appliedHeaders = captureAppliedHeaders();
        assertThat(appliedHeaders.getFirst("X-Request-Source")).isEqualTo("apex-service-test");
        assertThat(appliedHeaders.get("X-Request-Source")).containsExactly("apex-service-test");
    }

    @Test
    @DisplayName("customRequest - 灵活自定义请求")
    void customRequest_Success() {
        when(webClientBuilder.clone()).thenReturn(webClientBuilder);
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.clientConnector(any())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);

        remoteApiClient
                .customRequest(TEST_SERVICE, TEST_PATH, null, String.class, wc -> Mono.just("custom result"))
                .as(StepVerifier::create)
                .expectNext("custom result")
                .verifyComplete();
    }

    @Test
    @DisplayName("RemoteApiProperties - 默认值校验")
    void remoteApiProperties_DefaultValues() {
        RemoteApiProperties props = new RemoteApiProperties();

        assertThat(props.getDefaultConnectTimeout()).isEqualTo(5000);
        assertThat(props.getDefaultReadTimeout()).isEqualTo(10000);
        assertThat(props.getDefaultRetryCount()).isEqualTo(2);
        assertThat(props.getDefaultRetryDelay()).isEqualTo(1000L);
        assertThat(props.getServices()).isEmpty();
    }

    @Test
    @DisplayName("ServiceConfig - 默认值校验")
    void serviceConfig_DefaultValues() {
        ServiceConfig config = new ServiceConfig();

        assertThat(config.getBaseUrl()).isNull();
        assertThat(config.getConnectTimeout()).isNull();
        assertThat(config.getReadTimeout()).isNull();
        assertThat(config.getRetryCount()).isNull();
        assertThat(config.getRetryDelay()).isNull();
        assertThat(config.getDefaultHeaders()).isEmpty();
    }

    @Test
    @DisplayName("ServiceConfig - 完整配置赋值校验")
    void serviceConfig_FullConfiguration() {
        ServiceConfig config = new ServiceConfig();
        config.setBaseUrl("http://localhost:9090");
        config.setConnectTimeout(2000);
        config.setReadTimeout(5000);
        config.setRetryCount(3);
        config.setRetryDelay(500L);
        config.setDefaultHeaders(Map.of("X-App", "test"));

        assertThat(config.getBaseUrl()).isEqualTo("http://localhost:9090");
        assertThat(config.getConnectTimeout()).isEqualTo(2000);
        assertThat(config.getReadTimeout()).isEqualTo(5000);
        assertThat(config.getRetryCount()).isEqualTo(3);
        assertThat(config.getRetryDelay()).isEqualTo(500L);
        assertThat(config.getDefaultHeaders()).containsEntry("X-App", "test");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private HttpHeaders captureAppliedHeaders() {
        ArgumentCaptor<Consumer<HttpHeaders>> captor = ArgumentCaptor.forClass((Class) Consumer.class);
        verify(requestBodySpec).headers(captor.capture());
        HttpHeaders headers = new HttpHeaders();
        captor.getValue().accept(headers);
        return headers;
    }

    private ByteArrayResource namedResource(String filename, String content) {
        return new ByteArrayResource(content.getBytes()) {
            /** {@inheritDoc} */
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
