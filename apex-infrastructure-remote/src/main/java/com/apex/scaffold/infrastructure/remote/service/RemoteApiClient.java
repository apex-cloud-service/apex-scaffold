package com.apex.scaffold.infrastructure.remote.service;

import com.apex.scaffold.domain.gateway.RemoteApiException;
import com.apex.scaffold.infrastructure.chaos.ChaosInjectionService;
import com.apex.scaffold.infrastructure.remote.config.RemoteApiProperties;
import com.apex.scaffold.infrastructure.remote.config.RemoteApiProperties.ServiceConfig;
import com.apex.scaffold.infrastructure.remote.filter.ApiCallLogFilter;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.RetryBackoffSpec;

/**
 * 远程API调用客户端
 *
 * <p>基于 Spring WebClient 实现的通用远程API调用服务，提供对外的HTTP接口调用能力。
 * 支持 GET、POST（JSON/表单）、PUT、DELETE 等常用HTTP方法，具备连接超时、读取超时、
 * 自动重试、自定义请求头、HTTP 响应压缩等特性。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li><b>多服务支持</b> - 通过 {@link RemoteApiProperties} 配置多个目标服务</li>
 *   <li><b>超时控制</b> - 支持按服务配置连接超时和读取超时时间</li>
 *   <li><b>自动重试</b> - 支持配置重试次数和重试间隔，仅对5xx错误和网络异常重试</li>
 *   <li><b>类型安全</b> - 支持通过 {@link Class} 或 {@link ParameterizedTypeReference} 指定响应类型</li>
 *   <li><b>响应式非阻塞</b> - 基于 WebFlux 响应式架构，返回 {@link Mono} 类型</li>
 *   <li><b>连接池复用</b> - 通过共享 {@link ConnectionProvider} 复用 TCP/TLS 连接，消除每次请求握手开销</li>
 *   <li><b>响应压缩</b> - 自动请求 gzip/deflate 压缩，减少网络传输量</li>
 *   <li><b>熔断保护</b> - 集成 resilience4j CircuitBreaker，防止级联故障</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // GET请求
 * Mono<UserDto> user = remoteApiClient.get("user-service", "/api/users/1", UserDto.class);
 *
 * // POST JSON请求
 * Mono<ResultDto> result = remoteApiClient.postJson("order-service", "/api/orders", body, ResultDto.class);
 *
 * // POST 表单请求
 * Mono<String> resp = remoteApiClient.postForm("auth-service", "/api/login", formData, String.class);
 *
 * // PUT请求
 * Mono<Void> update = remoteApiClient.put("user-service", "/api/users/1", updateBody);
 *
 * // DELETE请求
 * Mono<Void> delete = remoteApiClient.delete("order-service", "/api/orders/1");
 * }</pre>
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 * @see RemoteApiProperties
 * @see RemoteApiException
 * @see ConnectionProvider
 */
@Slf4j
@lombok.RequiredArgsConstructor
public class RemoteApiClient {

    /** 默认的返回类型引用，用于 Void 响应 */
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE_REF =
            new ParameterizedTypeReference<Map<String, Object>>() {};

    /** Python 分析服务当前支持的文件字段名 */
    private static final Set<String> SUPPORTED_FILE_FIELD_NAMES = Set.of(
            "image",
            "image_a",
            "image_b",
            "file",
            "identity_reference",
            "photo",
            "photos",
            "match_record",
            "feature_table",
            "model_json");

    /** 远程API配置属性 */
    private final RemoteApiProperties properties;

    /** WebClient构建器，由Spring Boot自动配置注入 */
    private final WebClient.Builder webClientBuilder;

    /**
     * 共享连接池（可为空）。
     *
     * <p>非空时 TCP/TLS 连接可复用，消除每次请求的握手开销；
     * 为空时回退到每次请求新建 {@link HttpClient} 的兼容模式（单元测试环境）。</p>
     */
    private final ConnectionProvider connectionProvider;

    /** 是否启用 HTTP 响应压缩（Accept-Encoding: gzip/deflate） */
    private final boolean compressResponse;

    /**
     * resilience4j 熔断器注册表（可为空）。
     *
     * <p>非空时按服务名查找对应的 {@link CircuitBreaker} 并施加熔断保护；
     * 为空时仅依赖重试机制。</p>
     */
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * 按服务名缓存的 CircuitBreaker 实例，避免每次请求查表。
     *
     * <p>key 为 YAML 中 {@code resilience4j.circuitbreaker.instances} 下配置的服务名。</p>
     */
    private final ConcurrentHashMap<String, Optional<CircuitBreaker>> circuitBreakerCache = new ConcurrentHashMap<>();

    /** API 调用日志服务（用于记录对外部服务的每次 HTTP 调用）。 */
    private final ApiCallLogService logService;

    /** JSON 序列化器，用于将请求体序列化为日志记录字符串。 */
    private final ObjectMapper objectMapper;

    /**
     * 混沌工程故障注入服务（可为空）。
     *
     * <p>非空时在发起真实 HTTP 调用前按配置失败率模拟外部 API 调用失败，
     * 用于验证熔断与重试链路；未注册或全局开关关闭时路径与现状完全一致。</p>
     */
    private final ChaosInjectionService chaosInjectionService;

    /**
     * 简化构造器 —— 供单元测试直接构造使用。
     *
     * <p>连接池、压缩、熔断器、混沌注入均使用安全默认值（无连接池、启用压缩、无熔断、无注入）。</p>
     *
     * @param properties 远程API配置属性
     * @param webClientBuilder WebClient构建器
     */
    public RemoteApiClient(RemoteApiProperties properties, WebClient.Builder webClientBuilder) {
        this(properties, webClientBuilder, null, true, null, null, null, null);
    }

    /**
     * 发送GET请求
     *
     * <p>向指定服务发送HTTP GET请求，返回指定类型的响应体。
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称（需在配置中已定义）
     * @param path 请求路径，如 /api/users/1
     * @param responseType 响应体类型
     * @return 响应体的Mono
     * @throws IllegalArgumentException 如果服务名未在配置中注册
     */
    public <T> Mono<T> get(String serviceName, String path, Class<T> responseType) {
        return get(serviceName, path, null, responseType);
    }

    /**
     * 发送GET请求（带查询参数）
     *
     * <p>向指定服务发送HTTP GET请求，附带URL查询参数。
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称（需在配置中已定义）
     * @param path 请求路径，如 /api/users
     * @param queryParams 查询参数，key为参数名，value为参数值
     * @param responseType 响应体类型
     * @return 响应体的Mono
     * @throws IllegalArgumentException 如果服务名未在配置中注册
     */
    public <T> Mono<T> get(String serviceName, String path, Map<String, String> queryParams, Class<T> responseType) {
        return execute(RemoteRequest.builder(responseType)
                .service(serviceName)
                .path(path)
                .queryParams(queryParams)
                .build());
    }

    /**
     * 发送GET请求（带查询参数和自定义请求头）
     *
     * <p>向指定服务发送HTTP GET请求，附带URL查询参数和自定义请求头。
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称（需在配置中已定义）
     * @param path 请求路径，如 /api/users
     * @param queryParams 查询参数，key为参数名，value为参数值
     * @param headers 自定义请求头，会与默认请求头合并（自定义优先）
     * @param responseType 响应体类型
     * @return 响应体的Mono
     * @throws IllegalArgumentException 如果服务名未在配置中注册
     */
    public <T> Mono<T> get(
            String serviceName,
            String path,
            Map<String, String> queryParams,
            Map<String, String> headers,
            Class<T> responseType) {
        return execute(RemoteRequest.builder(responseType)
                .service(serviceName)
                .path(path)
                .queryParams(queryParams)
                .headers(headers)
                .build());
    }

    /**
     * 发送GET请求（使用泛型类型引用）
     *
     * <p>适用于响应体包含泛型的情况，如 {@code List<UserDto>} 或 {@code Result<Page<UserDto>>}。
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称（需在配置中已定义）
     * @param path 请求路径
     * @param queryParams 查询参数
     * @param headers 自定义请求头
     * @param typeReference 泛型类型引用
     * @return 响应体的Mono
     * @throws IllegalArgumentException 如果服务名未在配置中注册
     */
    public <T> Mono<T> get(
            String serviceName,
            String path,
            Map<String, String> queryParams,
            Map<String, String> headers,
            ParameterizedTypeReference<T> typeReference) {
        return execute(RemoteRequest.builder(typeReference)
                .service(serviceName)
                .path(path)
                .queryParams(queryParams)
                .headers(headers)
                .build());
    }

    /**
     * 发送POST JSON请求
     *
     * <p>向指定服务发送HTTP POST请求，请求体和响应体均为JSON格式。
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称（需在配置中已定义）
     * @param path 请求路径
     * @param body 请求体对象（会被序列化为JSON）
     * @param responseType 响应体类型
     * @return 响应体的Mono
     * @throws IllegalArgumentException 如果服务名未在配置中注册
     */
    public <T> Mono<T> postJson(String serviceName, String path, Object body, Class<T> responseType) {
        return postJson(serviceName, path, body, null, responseType);
    }

    /**
     * 发送POST JSON请求（带自定义请求头）
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称（需在配置中已定义）
     * @param path 请求路径
     * @param body 请求体对象（会被序列化为JSON）
     * @param headers 自定义请求头
     * @param responseType 响应体类型
     * @return 响应体的Mono
     * @throws IllegalArgumentException 如果服务名未在配置中注册
     */
    public <T> Mono<T> postJson(
            String serviceName, String path, Object body, Map<String, String> headers, Class<T> responseType) {
        return execute(RemoteRequest.builder(responseType)
                .service(serviceName)
                .path(path)
                .method(HttpMethod.POST)
                .headers(headers)
                .jsonBody(Optional.ofNullable(body).orElse(Map.of()))
                .build());
    }

    /**
     * 发送POST JSON请求（无请求体，使用泛型类型引用）
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称
     * @param path 请求路径
     * @param body 请求体对象
     * @param headers 自定义请求头
     * @param typeReference 泛型类型引用
     * @return 响应体的Mono
     */
    public <T> Mono<T> postJson(
            String serviceName,
            String path,
            Object body,
            Map<String, String> headers,
            ParameterizedTypeReference<T> typeReference) {
        return execute(RemoteRequest.builder(typeReference)
                .service(serviceName)
                .path(path)
                .method(HttpMethod.POST)
                .headers(headers)
                .jsonBody(body != null ? body : Map.of())
                .build());
    }

    /**
     * 发送一次性 POST JSON 请求，不应用通用网络重试策略。
     *
     * <p>用于有计费副作用的供应商提交接口。调用方收到网络异常时必须把结果标记
     * 为未知并通过查询协调，不能让基础客户端自动再次提交。</p>
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名
     * @param path 请求路径
     * @param body JSON 请求体
     * @param headers 自定义请求头
     * @param typeReference 泛型响应类型
     * @return 响应体 Mono
     */
    public <T> Mono<T> postJsonOnce(
            String serviceName,
            String path,
            Object body,
            Map<String, String> headers,
            ParameterizedTypeReference<T> typeReference) {
        return executeOnce(RemoteRequest.builder(typeReference)
                .service(serviceName)
                .path(path)
                .method(HttpMethod.POST)
                .headers(headers)
                .jsonBody(body != null ? body : Map.of())
                .logRequestBody(false)
                .build());
    }

    /**
     * 发送POST表单请求
     *
     * <p>向指定服务发送HTTP POST请求，内容类型为 {@code application/x-www-form-urlencoded}。
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称（需在配置中已定义）
     * @param path 请求路径
     * @param formData 表单数据，key为字段名，value为字段值
     * @param responseType 响应体类型
     * @return 响应体的Mono
     * @throws IllegalArgumentException 如果服务名未在配置中注册
     */
    public <T> Mono<T> postForm(String serviceName, String path, Map<String, String> formData, Class<T> responseType) {
        return postForm(serviceName, path, formData, null, responseType);
    }

    /**
     * 发送POST表单请求（带自定义请求头）
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称
     * @param path 请求路径
     * @param formData 表单数据
     * @param headers 自定义请求头
     * @param responseType 响应体类型
     * @return 响应体的Mono
     */
    public <T> Mono<T> postForm(
            String serviceName,
            String path,
            Map<String, String> formData,
            Map<String, String> headers,
            Class<T> responseType) {
        return execute(RemoteRequest.builder(responseType)
                .service(serviceName)
                .path(path)
                .method(HttpMethod.POST)
                .headers(headers)
                .formBody(formData)
                .build());
    }

    /**
     * 发送 multipart/form-data 请求（单文件 + 表单字段）。
     *
     * <p>用于调用 Python 分析服务这类 UploadFile 接口。当前仅允许文件字段名为
     * {@code image}、{@code image_a}、{@code image_b}、{@code file}、{@code photo}、{@code photos}、
     * {@code match_record}、{@code feature_table}、{@code model_json}，防止业务代码随意扩散不一致的字段名。
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称
     * @param path 请求路径
     * @param fileFieldName 文件字段名，仅支持白名单字段
     * @param fileResource 文件资源
     * @param formData 普通表单字段，可为空
     * @param headers 自定义请求头，可为空
     * @param responseType 响应体类型
     * @return 响应体的Mono
     */
    public <T> Mono<T> postMultipart(
            String serviceName,
            String path,
            String fileFieldName,
            Resource fileResource,
            Map<String, String> formData,
            Map<String, String> headers,
            Class<T> responseType) {
        return postMultipart(serviceName, path, fileFieldName, fileResource, null, formData, headers, responseType);
    }

    /**
     * 发送 multipart/form-data 请求（单文件 + 表单字段，可指定文件 Content-Type）。
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称
     * @param path 请求路径
     * @param fileFieldName 文件字段名，仅支持白名单字段
     * @param fileResource 文件资源
     * @param fileContentType 文件 Content-Type，可为空
     * @param formData 普通表单字段，可为空
     * @param headers 自定义请求头，可为空
     * @param responseType 响应体类型
     * @return 响应体的Mono
     */
    public <T> Mono<T> postMultipart(
            String serviceName,
            String path,
            String fileFieldName,
            Resource fileResource,
            MediaType fileContentType,
            Map<String, String> formData,
            Map<String, String> headers,
            Class<T> responseType) {
        return execute(RemoteRequest.builder(responseType)
                .service(serviceName)
                .path(path)
                .method(HttpMethod.POST)
                .headers(headers)
                .multipartBody(buildMultipartBody(fileFieldName, fileResource, fileContentType, formData))
                .build());
    }

    /**
     * Send a paid single-file multipart operation exactly once.
     *
     * <p>This deliberately bypasses the generic network/5xx retry policy.  A
     * lost response is an ambiguous submission and must be reconciled by the
     * caller rather than charged a second time.</p>
     */
    public <T> Mono<T> postMultipartOnce(
            String serviceName,
            String path,
            String fileFieldName,
            Resource fileResource,
            Map<String, String> formData,
            Map<String, String> headers,
            ParameterizedTypeReference<T> typeReference) {
        return executeOnce(RemoteRequest.builder(typeReference)
                .service(serviceName)
                .path(path)
                .method(HttpMethod.POST)
                .headers(headers)
                .multipartBody(buildMultipartBody(fileFieldName, fileResource, null, formData))
                .build());
    }

    /**
     * Send a paid multi-file multipart operation exactly once.
     *
     * <p>This is the multi-file equivalent of {@link #postMultipartOnce(String,
     * String, String, Resource, Map, Map, ParameterizedTypeReference)} and
     * deliberately never applies the generic retry policy.</p>
     */
    public <T> Mono<T> postMultipartOnce(
            String serviceName,
            String path,
            List<MultipartFilePart> fileParts,
            Map<String, String> formData,
            Map<String, String> headers,
            ParameterizedTypeReference<T> typeReference) {
        return executeOnce(RemoteRequest.builder(typeReference)
                .service(serviceName)
                .path(path)
                .method(HttpMethod.POST)
                .headers(headers)
                .multipartBody(buildMultipartBody(fileParts, formData))
                .build());
    }

    /**
     * 发送 multipart/form-data 请求（单文件 + 表单字段，支持泛型响应）。
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称
     * @param path 请求路径
     * @param fileFieldName 文件字段名，仅支持白名单字段
     * @param fileResource 文件资源
     * @param fileContentType 文件 Content-Type，可为空
     * @param formData 普通表单字段，可为空
     * @param headers 自定义请求头，可为空
     * @param typeReference 响应体泛型类型引用
     * @return 响应体的Mono
     */
    public <T> Mono<T> postMultipart(
            String serviceName,
            String path,
            String fileFieldName,
            Resource fileResource,
            MediaType fileContentType,
            Map<String, String> formData,
            Map<String, String> headers,
            ParameterizedTypeReference<T> typeReference) {
        return execute(RemoteRequest.builder(typeReference)
                .service(serviceName)
                .path(path)
                .method(HttpMethod.POST)
                .headers(headers)
                .multipartBody(buildMultipartBody(fileFieldName, fileResource, fileContentType, formData))
                .build());
    }

    /**
     * 发送 multipart/form-data 请求（多文件 + 表单字段，支持泛型响应）。
     *
     * <p>用于上传多个文件字段，例如 Python 个人偏好训练需要的
     * {@code match_record + feature_table}、预测模型 {@code model_json}，
     * 以及特征表构建和预测需要的重复 {@code photos} 字段。</p>
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称
     * @param path 请求路径
     * @param fileParts 文件 part 列表
     * @param formData 普通表单字段，可为空
     * @param headers 自定义请求头，可为空
     * @param typeReference 响应体泛型类型引用
     * @return 响应体的Mono
     */
    public <T> Mono<T> postMultipart(
            String serviceName,
            String path,
            List<MultipartFilePart> fileParts,
            Map<String, String> formData,
            Map<String, String> headers,
            ParameterizedTypeReference<T> typeReference) {
        return execute(RemoteRequest.builder(typeReference)
                .service(serviceName)
                .path(path)
                .method(HttpMethod.POST)
                .headers(headers)
                .multipartBody(buildMultipartBody(fileParts, formData))
                .build());
    }

    /**
     * 发送 multipart/form-data 请求（多文件 + 表单字段，使用 Class 响应类型）。
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称
     * @param path 请求路径
     * @param fileParts 文件 part 列表
     * @param formData 普通表单字段，可为空
     * @param headers 自定义请求头，可为空
     * @param responseType 响应体类型
     * @return 响应体的Mono
     */
    public <T> Mono<T> postMultipart(
            String serviceName,
            String path,
            List<MultipartFilePart> fileParts,
            Map<String, String> formData,
            Map<String, String> headers,
            Class<T> responseType) {
        return execute(RemoteRequest.builder(responseType)
                .service(serviceName)
                .path(path)
                .method(HttpMethod.POST)
                .headers(headers)
                .multipartBody(buildMultipartBody(fileParts, formData))
                .build());
    }

    /**
     * 发送PUT请求
     *
     * <p>向指定服务发送HTTP PUT请求，请求体为JSON格式。
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称（需在配置中已定义）
     * @param path 请求路径
     * @param body 请求体对象
     * @param responseType 响应体类型
     * @return 响应体的Mono
     * @throws IllegalArgumentException 如果服务名未在配置中注册
     */
    public <T> Mono<T> put(String serviceName, String path, Object body, Class<T> responseType) {
        return put(serviceName, path, body, null, responseType);
    }

    /**
     * 发送PUT请求（带自定义请求头）
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称
     * @param path 请求路径
     * @param body 请求体对象
     * @param headers 自定义请求头
     * @param responseType 响应体类型
     * @return 响应体的Mono
     */
    public <T> Mono<T> put(
            String serviceName, String path, Object body, Map<String, String> headers, Class<T> responseType) {
        return execute(RemoteRequest.builder(responseType)
                .service(serviceName)
                .path(path)
                .method(HttpMethod.PUT)
                .headers(headers)
                .jsonBody(Optional.ofNullable(body).orElse(Map.of()))
                .build());
    }

    /**
     * 发送DELETE请求
     *
     * <p>向指定服务发送HTTP DELETE请求。
     *
     * @param serviceName 目标服务名称（需在配置中已定义）
     * @param path 请求路径
     * @throws IllegalArgumentException 如果服务名未在配置中注册
     */
    public Mono<Void> delete(String serviceName, String path) {
        return delete(serviceName, path, null);
    }

    /**
     * 发送DELETE请求（带自定义请求头）
     *
     * @param serviceName 目标服务名称
     * @param path 请求路径
     * @param headers 自定义请求头
     */
    public Mono<Void> delete(String serviceName, String path, Map<String, String> headers) {
        return execute(RemoteRequest.builder(Void.class)
                .service(serviceName)
                .path(path)
                .method(HttpMethod.DELETE)
                .headers(headers)
                .build());
    }

    /**
     * 发送DELETE请求（返回Map响应）
     *
     * @param serviceName 目标服务名称
     * @param path 请求路径
     * @param headers 自定义请求头
     * @return Map响应的Mono
     */
    public Mono<Map<String, Object>> deleteWithResponse(String serviceName, String path, Map<String, String> headers) {
        return execute(RemoteRequest.builder(MAP_TYPE_REF)
                .service(serviceName)
                .path(path)
                .method(HttpMethod.DELETE)
                .headers(headers)
                .build());
    }

    /**
     * 发送自定义HTTP请求
     *
     * <p>提供最灵活的请求方式，允许调用方完全自定义WebClient请求规格。
     *
     * @param <T> 响应体类型
     * @param serviceName 目标服务名称
     * @param path 请求路径
     * @param headers 自定义请求头
     * @param responseType 响应体类型
     * @param requestBuilder 请求构建函数，接收一个已配置好的WebClient，返回Mono响应
     * @return 响应体的Mono
     */
    public <T> Mono<T> customRequest(
            String serviceName,
            String path,
            Map<String, String> headers,
            Class<T> responseType,
            Function<WebClient, Mono<T>> requestBuilder) {
        ServiceConfig config = resolveServiceConfig(serviceName);
        WebClient webClient = buildWebClient(config, headers);
        return requestBuilder
                .apply(webClient)
                .retryWhen(buildRetrySpec(config, serviceName, path))
                .onErrorMap(e -> wrapException(serviceName, path, e));
    }

    /**
     * 解析服务配置
     *
     * <p>根据服务名称查找对应的配置，未配置时抛出异常。
     *
     * @param serviceName 服务名称
     * @return 服务配置，不会为null
     * @throws IllegalArgumentException 如果服务名未在配置中注册
     */
    private ServiceConfig resolveServiceConfig(String serviceName) {
        ServiceConfig config = properties.getServices().get(serviceName);
        if (config == null) {
            throw new IllegalArgumentException(
                    "未找到远程服务配置, serviceName: " + serviceName + ", 请检查 remote.api.services 配置");
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("远程服务未配置baseUrl, serviceName: " + serviceName);
        }
        return config;
    }

    /**
     * 构建WebClient实例
     *
     * <p>根据服务配置创建带有超时设置的WebClient实例。
     *
     * @param config 服务配置
     * @return 配置好的WebClient实例
     */
    private WebClient buildWebClient(ServiceConfig config) {
        return buildWebClientBuilder(config).build();
    }

    /**
     * 构建WebClient实例，并应用默认请求头与方法级请求头。
     *
     * @param config 服务配置
     * @param headers 方法级请求头，可为空；同名头会覆盖服务默认请求头
     * @return 配置好的WebClient实例
     */
    private WebClient buildWebClient(ServiceConfig config, Map<String, String> headers) {
        return buildWebClientBuilder(config)
                .defaultHeaders(httpHeaders -> applyHeaderValues(httpHeaders::set, config, headers))
                .build();
    }

    /**
     * 构建带超时、连接池和响应压缩的 {@code WebClient.Builder}。
     *
     * <p>核心性能优化（v2.1.0）：</p>
     * <ul>
     *   <li><b>连接池复用</b> — 通过共享 {@link ConnectionProvider} 复用 TCP/TLS 连接，
     *       消除每次请求的 TLS 完整握手和 TCP 三次握手开销。</li>
     *   <li><b>响应压缩</b> — 添加 {@code compress(true)} 自动请求 gzip/deflate 压缩，
     *       减少 Python 分析服务返回的大 JSON 响应体传输量。</li>
     *   <li><b>独立超时控制</b> — 连接超时、响应总超时按服务独立配置。</li>
     * </ul>
     *
     * <p>兼容性：当 {@link #connectionProvider} 为 {@code null} 时回退到
     * {@code HttpClient.create()} 无连接池模式（单元测试环境）。</p>
     *
     * @param config 服务配置
     * @return 配置完成的 WebClient.Builder
     */
    private WebClient.Builder buildWebClientBuilder(ServiceConfig config) {
        int connectTimeout = resolveConnectTimeout(config);
        int readTimeout = resolveReadTimeout(config);

        // 使用共享连接池（若已注入）或回退到 HttpClient.create() 无连接池模式
        HttpClient httpClient;
        if (connectionProvider != null) {
            httpClient = HttpClient.create(connectionProvider)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                    .responseTimeout(Duration.ofMillis(readTimeout + connectTimeout))
                    .compress(compressResponse);
        } else {
            httpClient = HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                    .responseTimeout(Duration.ofMillis(readTimeout + connectTimeout))
                    .compress(compressResponse);
        }

        log.debug(
                "构建 HTTP 客户端: baseUrl={}, connectTimeout={}ms, readTimeout={}ms, " + "connectionPool={}, compress={}",
                config.getBaseUrl(),
                connectTimeout,
                readTimeout,
                connectionProvider != null ? "shared" : "none",
                compressResponse);

        return webClientBuilder
                .clone()
                .baseUrl(config.getBaseUrl())
                .codecs(codecConfigurer ->
                        codecConfigurer.defaultCodecs().maxInMemorySize(properties.getMaxInMemorySize()))
                .filter(ApiCallLogFilter.create(logService))
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    /**
     * 执行不可变远程请求。
     *
     * <p>该方法是标准 GET、POST、PUT、DELETE 和 multipart 兼容 API 的统一执行模板。
     * 它依次解析服务配置、构建带超时的 WebClient、拼装 URI、合并请求头、编码请求体、
     * 反序列化响应，并统一应用重试和异常转换策略。
     *
     * @param request 已完整声明的不可变请求
     * @param <T> 响应体类型
     * @return 远程响应的异步结果
     */
    public <T> Mono<T> execute(RemoteRequest<T> request) {
        return execute(request, true);
    }

    /** Execute one request without the generic retry policy. */
    public <T> Mono<T> executeOnce(RemoteRequest<T> request) {
        return execute(request, false);
    }

    /**
     * 按服务配置执行 HTTP 请求并返回反序列化结果。
     *
     * @param request      已声明的不可变远程请求
     * @param retryEnabled true 表示按服务配置应用统一重试策略
     * @param <T>          响应体类型
     * @return 远程响应的异步结果
     */
    private <T> Mono<T> execute(RemoteRequest<T> request, boolean retryEnabled) {
        java.util.Objects.requireNonNull(request, "request不能为空");
        ServiceConfig config = resolveServiceConfig(request.serviceName());
        WebClient webClient = buildWebClient(config);
        RequestBodySpec spec = webClient.method(request.method()).uri(builder -> {
            builder.path(request.path());
            request.queryParams().forEach(builder::queryParam);
            return builder.build();
        });
        applyHeaders(spec, config, request.headers());

        // 注入 API 调用日志所需的服务名称属性
        spec.attribute(ApiCallLogFilter.ATTRIBUTE_SERVICE_NAME, request.serviceName());

        // 注入 API 调用日志所需的请求体文本（JSON/FORM 序列化，MULTIPART/NONE 跳过）
        if (request.logRequestBody()) {
            serializeRequestBody(request)
                    .ifPresent(bodyStr -> spec.attribute(ApiCallLogFilter.ATTRIBUTE_REQUEST_BODY, bodyStr));
        }

        // 混沌工程：按配置失败率模拟外部 API 调用失败（位于熔断与重试之前，模拟网络层故障）。
        // 经 Optional 链实现判定：混沌注入服务存在且命中注入概率时返回错误 Mono，否则走正常调用链路。
        Mono<T> requestMono = Optional.ofNullable(chaosInjectionService)
                .filter(ChaosInjectionService::shouldRemoteApiFail)
                .map(chaos -> {
                    log.warn("混沌注入: 模拟外部API调用失败, service={}, path={}", request.serviceName(), request.path());
                    return Mono.<T>error(
                            new RemoteApiException(request.serviceName(), request.path(), "混沌注入模拟外部API调用失败", null));
                })
                .orElseGet(() -> request.decode(applyBody(spec, request).retrieve()));

        // 熔断保护：按服务名查找 CircuitBreaker 并包裹调用链
        Optional<CircuitBreaker> cb = findCircuitBreaker(request.serviceName());
        if (cb.isPresent()) {
            requestMono = requestMono.transformDeferred(CircuitBreakerOperator.of(cb.get()));
        }

        Mono<T> result = retryEnabled
                ? requestMono.retryWhen(buildRetrySpec(config, request.serviceName(), request.path()))
                : requestMono;
        return result.onErrorMap(error -> wrapException(request.serviceName(), request.path(), error));
    }

    /**
     * 根据声明的请求体类型写入 WebClient 请求。
     *
     * @param spec 已包含方法、URI 和请求头的请求规格
     * @param request 不可变请求
     * @param <T> 响应体类型
     * @return 可直接执行 {@code retrieve()} 的请求规格
     */
    @SuppressWarnings("unchecked")
    private <T> WebClient.RequestHeadersSpec<?> applyBody(RequestBodySpec spec, RemoteRequest<T> request) {
        return switch (request.bodyKind()) {
            case NONE -> spec;
            case JSON -> spec.contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Optional.ofNullable(request.body()).orElse(Map.of()));
            case FORM -> {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                Map<String, String> values = (Map<String, String>) request.body();
                values.forEach(form::add);
                yield spec.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(BodyInserters.fromFormData(form));
            }
            case MULTIPART -> spec.body(BodyInserters.fromMultipartData(
                    (MultiValueMap<String, org.springframework.http.HttpEntity<?>>) request.body()));
        };
    }

    /**
     * 从已有的WebClient构建请求规格（用于inner class方式的customRequest）
     *
     * @param spec WebClient.RequestBodySpec
     * @param config 服务配置
     * @param headers 自定义请求头
     */
    private void applyHeaders(RequestBodySpec spec, ServiceConfig config, Map<String, String> headers) {
        spec.headers(httpHeaders -> applyHeaderValues(httpHeaders::set, config, headers));
    }

    /**
     * 将默认请求头与自定义请求头合并写入 HTTP 请求。
     *
     * <p>先写入服务配置中的默认 header，再覆盖写入调用方传入的自定义 header，
     * 因此当 key 冲突时自定义 header 优先。</p>
     *
     * @param headerSetter 向 HTTP 请求写入单个 header 键值对的 setter 函数
     * @param config 服务配置，携带默认请求头（可为 null，跳过）
     * @param headers 调用方自定义请求头，覆盖同 key 的默认值（可为 null，跳过）
     */
    private void applyHeaderValues(
            java.util.function.BiConsumer<String, String> headerSetter,
            ServiceConfig config,
            Map<String, String> headers) {
        if (config.getDefaultHeaders() != null) {
            config.getDefaultHeaders().forEach(headerSetter);
        }
        if (headers != null) {
            headers.forEach(headerSetter);
        }
    }

    /**
     * 构建单文件 multipart 表单体。
     *
     * @param fileFieldName 文件对应的表单字段名
     * @param fileResource 文件资源
     * @param fileContentType 文件的 MIME 类型，可为 null
     * @param formData 额外普通表单字段，可为 null
     * @return Spring 的 {@link MultiValueMap} 格式 multipart 请求体
     */
    private MultiValueMap<String, org.springframework.http.HttpEntity<?>> buildMultipartBody(
            String fileFieldName, Resource fileResource, MediaType fileContentType, Map<String, String> formData) {
        validateFileFieldName(fileFieldName);
        if (fileResource == null) {
            throw new IllegalArgumentException("multipart文件不能为空");
        }

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        MultipartBodyBuilder.PartBuilder filePart = builder.part(fileFieldName, fileResource);
        String filename = fileResource.getFilename();
        if (filename != null && !filename.isBlank()) {
            filePart.filename(filename);
        }
        if (fileContentType != null) {
            filePart.contentType(fileContentType);
        }
        if (formData != null) {
            formData.forEach(builder::part);
        }
        return builder.build();
    }

    /**
     * 将请求体序列化为日志记录字符串。
     *
     * <p>JSON 请求体使用 Jackson 序列化；表单请求体按 key=value 拼接；
     * Multipart 和无请求体的情况返回 {@link Optional#empty()}。</p>
     *
     * @param request 远程请求
     * @return 序列化后的请求体字符串，或空 Optional
     */
    private Optional<String> serializeRequestBody(RemoteRequest<?> request) {
        return switch (request.bodyKind()) {
            case NONE -> Optional.empty();
            case JSON -> {
                Object body = request.body();
                if (body == null) {
                    yield Optional.empty();
                }
                try {
                    String json = objectMapper != null ? objectMapper.writeValueAsString(body) : body.toString();
                    yield Optional.of(truncateBody(json));
                } catch (JsonProcessingException e) {
                    log.warn("JSON序列化请求体失败: {}", e.getMessage());
                    yield Optional.empty();
                }
            }
            case FORM -> {
                Object body = request.body();
                if (body instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> map = (Map<String, String>) body;
                    yield Optional.of(truncateBody(map.entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(java.util.stream.Collectors.joining("&"))));
                }
                yield Optional.empty();
            }
            case MULTIPART -> Optional.of("[multipart body]");
        };
    }

    /**
     * 截断超长请求体，避免日志膨胀。
     *
     * @param body 请求体字符串
     * @return 截断后的字符串
     */
    private String truncateBody(String body) {
        int maxLen = 4096;
        if (body != null && body.length() > maxLen) {
            return body.substring(0, maxLen) + "...[truncated]";
        }
        return body;
    }

    /**
     * 构建多文件 multipart 表单体。
     *
     * @param fileParts 多个文件部件列表，每个部件包含字段名、资源和可选的 MIME 类型
     * @param formData 额外普通表单字段，可为 null
     * @return Spring 的 {@link MultiValueMap} 格式 multipart 请求体
     */
    private MultiValueMap<String, org.springframework.http.HttpEntity<?>> buildMultipartBody(
            List<MultipartFilePart> fileParts, Map<String, String> formData) {
        if (fileParts == null || fileParts.isEmpty()) {
            throw new IllegalArgumentException("multipart文件不能为空");
        }

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        for (MultipartFilePart part : fileParts) {
            if (part == null) {
                throw new IllegalArgumentException("multipart文件part不能为空");
            }
            validateFileFieldName(part.fieldName());
            if (part.resource() == null) {
                throw new IllegalArgumentException("multipart文件不能为空");
            }
            MultipartBodyBuilder.PartBuilder filePart = builder.part(part.fieldName(), part.resource());
            String filename = part.resource().getFilename();
            if (filename != null && !filename.isBlank()) {
                filePart.filename(filename);
            }
            if (part.contentType() != null) {
                filePart.contentType(part.contentType());
            }
        }
        if (formData != null) {
            formData.forEach(builder::part);
        }
        return builder.build();
    }

    /**
     * 校验 multipart 文件字段名是否在允许的集合内。
     *
     * @param fileFieldName 待校验的文件字段名，如 "file", "photo", "image" 等
     * @throws IllegalArgumentException 如果字段名不在 {@link #SUPPORTED_FILE_FIELD_NAMES} 白名单中
     */
    private void validateFileFieldName(String fileFieldName) {
        if (!SUPPORTED_FILE_FIELD_NAMES.contains(fileFieldName)) {
            throw new IllegalArgumentException(
                    "不支持的multipart文件字段名: " + fileFieldName + ", 仅支持 " + SUPPORTED_FILE_FIELD_NAMES);
        }
    }

    /**
     * multipart 文件 part 描述。
     *
     * @param fieldName 表单字段名
     * @param resource 文件资源
     * @param contentType 文件 Content-Type，可为空
     */
    public record MultipartFilePart(String fieldName, Resource resource, MediaType contentType) {}

    /**
     * 按服务名查找或创建 {@link CircuitBreaker} 实例。
     *
     * <p>查找顺序：先在 {@link CircuitBreakerRegistry} 中按 {@code serviceName} 精确匹配，
     * 若未配置则返回空。查找结果缓存在 {@link #circuitBreakerCache} 中避免重复查表。
     *
     * <p>注意：CircuitBreaker 的实例名必须与 YAML 中
     * {@code resilience4j.circuitbreaker.instances} 下的 key 精确匹配。
     * 例如 face-analysis 服务对应配置实例名 {@code face-analysis}。</p>
     *
     * @param serviceName 远程服务名称（对应 YAML 配置中的 services key）
     * @return 匹配的熔断器，若未配置则返回空 Optional
     */
    private Optional<CircuitBreaker> findCircuitBreaker(String serviceName) {
        if (circuitBreakerRegistry == null) {
            return Optional.empty();
        }
        return circuitBreakerCache.computeIfAbsent(serviceName, key -> {
            try {
                CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(key);
                log.debug("CircuitBreaker 已绑定: serviceName={}, state={}", key, cb.getState());
                return Optional.of(cb);
            } catch (Exception e) {
                log.debug("服务 {} 未配置 CircuitBreaker 实例，跳过熔断保护: {}", key, e.getMessage());
                return Optional.empty();
            }
        });
    }

    /**
     * 构建重试策略
     *
     * <p>仅对5xx服务端错误和网络异常（非RemoteApiException）进行重试，
     * 不在重试列表中的异常（如4xx客户端错误、业务异常）不会重试。
     *
     * @param config 服务配置
     * @param serviceName 服务名称
     * @param path 请求路径
     * @return 重试配置规格
     */
    private RetryBackoffSpec buildRetrySpec(ServiceConfig config, String serviceName, String path) {
        return callPolicy().retry(config, serviceName, path);
    }

    /**
     * 包装异常为RemoteApiException
     *
     * <p>将各种异常统一包装为RemoteApiException，保留原始异常信息。
     * 如果已经是RemoteApiException则直接返回。
     *
     * @param serviceName 服务名称
     * @param path 请求路径
     * @param e 原始异常
     * @return 包装后的RemoteApiException
     */
    private RemoteApiException wrapException(String serviceName, String path, Throwable e) {
        return callPolicy().wrap(serviceName, path, e);
    }

    /**
     * 解析连接超时时间
     *
     * @param config 服务配置
     * @return 连接超时毫秒数
     */
    private int resolveConnectTimeout(ServiceConfig config) {
        return callPolicy().connectTimeout(config);
    }

    /**
     * 解析读取超时时间
     *
     * @param config 服务配置
     * @return 读取超时毫秒数
     */
    private int resolveReadTimeout(ServiceConfig config) {
        return callPolicy().readTimeout(config);
    }

    /**
     * 创建绑定当前配置的远程调用策略。
     *
     * @return 远程调用策略
     */
    private RemoteCallPolicy callPolicy() {
        return new RemoteCallPolicy(properties);
    }
}
