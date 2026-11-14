package com.apex.scaffold.infrastructure.remote.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 不可变的远程 HTTP 请求描述。
 *
 * <p>该对象使用 Builder 模式集中描述目标服务、路径、HTTP 方法、查询参数、请求头、
 * 请求体编码方式和响应类型。调用方只负责声明请求，连接超时、默认请求头、重试和异常
 * 转换统一由 {@link RemoteApiClient} 执行，避免因重载参数组合不断增加而产生不一致行为。
 *
 * <p>请求体使用 {@link BodyKind} 明确区分 JSON、表单和 multipart 数据。具体 multipart
 * 内容由 {@link RemoteApiClient} 在兼容方法中完成安全校验后放入本对象，因此不会绕过
 * 文件字段白名单。
 *
 * @param <T> 远程响应反序列化后的 Java 类型
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
public final class RemoteRequest<T> {

    /** 请求目标服务在配置文件中的名称。 */
    private final String serviceName;

    /** 相对于目标服务 base URL 的请求路径。 */
    private final String path;

    /** HTTP 请求方法。 */
    private final HttpMethod method;

    /** URL 查询参数。 */
    private final Map<String, String> queryParams;

    /** 覆盖服务默认值的请求头。 */
    private final Map<String, String> headers;

    /** 请求体编码方式。 */
    private final BodyKind bodyKind;

    /** 根据 {@link #bodyKind} 解释的请求体数据。 */
    private final Object body;

    /** 支持普通类和泛型结构的响应类型引用。 */
    private final ParameterizedTypeReference<T> responseType;

    /** 是否允许统一调用日志记录 JSON 请求体。 */
    private final boolean logRequestBody;

    /** 与旧版 WebClient 调用方式兼容的响应解码 Lambda。 */
    private final java.util.function.Function<WebClient.ResponseSpec, Mono<T>> responseDecoder;

    /**
     * 根据 Builder 快照创建不可变请求。
     *
     * @param builder 已完成校验的请求 Builder
     */
    private RemoteRequest(Builder<T> builder) {
        this.serviceName = requireText(builder.serviceName, "serviceName不能为空");
        this.path = requireText(builder.path, "path不能为空");
        this.method = Objects.requireNonNull(builder.method, "method不能为空");
        this.queryParams = Map.copyOf(builder.queryParams);
        this.headers = Map.copyOf(builder.headers);
        this.bodyKind = Objects.requireNonNull(builder.bodyKind, "bodyKind不能为空");
        this.body = builder.body;
        this.responseType = Objects.requireNonNull(builder.responseType, "responseType不能为空");
        this.logRequestBody = builder.logRequestBody;
        this.responseDecoder = Objects.requireNonNull(builder.responseDecoder, "responseDecoder不能为空");
    }

    /**
     * 创建使用普通响应类的请求 Builder。
     *
     * @param responseType 响应 Java 类
     * @param <T> 响应类型
     * @return 新的请求 Builder
     */
    public static <T> Builder<T> builder(Class<T> responseType) {
        Objects.requireNonNull(responseType, "responseType不能为空");
        return new Builder<>(
                ParameterizedTypeReference.forType(responseType),
                responseSpec -> responseSpec.bodyToMono(responseType));
    }

    /**
     * 创建支持泛型响应的请求 Builder。
     *
     * @param responseType 泛型响应类型引用
     * @param <T> 响应类型
     * @return 新的请求 Builder
     */
    public static <T> Builder<T> builder(ParameterizedTypeReference<T> responseType) {
        Objects.requireNonNull(responseType, "responseType不能为空");
        return new Builder<>(responseType, responseSpec -> responseSpec.bodyToMono(responseType));
    }

    /** @return 目标服务配置名称 */
    public String serviceName() {
        return serviceName;
    }

    /** @return 请求路径 */
    public String path() {
        return path;
    }

    /** @return HTTP 方法 */
    public HttpMethod method() {
        return method;
    }

    /** @return 不可变查询参数 */
    public Map<String, String> queryParams() {
        return queryParams;
    }

    /** @return 不可变请求头 */
    public Map<String, String> headers() {
        return headers;
    }

    /** @return 请求体编码方式 */
    public BodyKind bodyKind() {
        return bodyKind;
    }

    /** @return 请求体数据；无请求体时为 {@code null} */
    public Object body() {
        return body;
    }

    /** @return 响应泛型类型引用 */
    public ParameterizedTypeReference<T> responseType() {
        return responseType;
    }

    /** @return 是否记录 JSON 请求体；图片 Data URI 请求应关闭。 */
    public boolean logRequestBody() {
        return logRequestBody;
    }

    /**
     * 使用请求创建时选择的响应类型重载完成反序列化。
     *
     * <p>普通 {@link Class} 请求继续调用 WebClient 的 Class 重载；泛型请求调用
     * {@link ParameterizedTypeReference} 重载，在统一执行模板下保持原有兼容行为。
     *
     * @param responseSpec 已获取的 WebClient 响应声明
     * @return 反序列化后的响应流
     */
    Mono<T> decode(WebClient.ResponseSpec responseSpec) {
        return responseDecoder.apply(Objects.requireNonNull(responseSpec, "responseSpec不能为空"));
    }

    /**
     * 校验字符串必须包含可见字符。
     *
     * @param value 待校验字符串
     * @param message 校验失败消息
     * @return 原字符串
     */
    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * 请求体编码类型。
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 3.3.0
     */
    public enum BodyKind {
        /** 请求不包含 body。 */
        NONE,
        /** 请求体由 WebClient JSON 编码器序列化。 */
        JSON,
        /** 请求体编码为 {@code application/x-www-form-urlencoded}。 */
        FORM,
        /** 请求体编码为 {@code multipart/form-data}。 */
        MULTIPART
    }

    /**
     * {@link RemoteRequest} 的流式 Builder。
     *
     * <p>Builder 可复用已有 Map，但在 {@link #build()} 时会复制所有集合，构建完成后的
     * 请求不会受到调用方后续修改影响。
     *
     * @param <T> 响应类型
     * @author Apex Team
     * @version 1.0.0
     * @since 3.3.0
     */
    @lombok.RequiredArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Builder<T> {

        /** 响应类型。 */
        private final ParameterizedTypeReference<T> responseType;

        /** 按响应类型重载构建的解码 Lambda。 */
        private final java.util.function.Function<WebClient.ResponseSpec, Mono<T>> responseDecoder;

        /** 目标服务名称。 */
        private String serviceName;

        /** 请求路径。 */
        private String path;

        /** HTTP 方法，默认 GET。 */
        private HttpMethod method = HttpMethod.GET;

        /** 查询参数 Builder 状态。 */
        private final Map<String, String> queryParams = new LinkedHashMap<>();

        /** 请求头 Builder 状态。 */
        private final Map<String, String> headers = new LinkedHashMap<>();

        /** 请求体类型，默认无请求体。 */
        private BodyKind bodyKind = BodyKind.NONE;

        /** 请求体。 */
        private Object body;

        /** 是否记录 JSON 请求体，默认开启以保持旧调用行为。 */
        private boolean logRequestBody = true;

        /**
         * 设置目标服务配置名称。
         *
         * @param serviceName 服务配置名称
         * @return 当前 Builder
         */
        public Builder<T> service(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        /**
         * 设置请求路径。
         *
         * @param path 相对请求路径
         * @return 当前 Builder
         */
        public Builder<T> path(String path) {
            this.path = path;
            return this;
        }

        /**
         * 设置 HTTP 方法。
         *
         * @param method HTTP 方法
         * @return 当前 Builder
         */
        public Builder<T> method(HttpMethod method) {
            this.method = method;
            return this;
        }

        /**
         * 合并查询参数，空 Map 等同于不设置。
         *
         * @param queryParams 查询参数
         * @return 当前 Builder
         */
        public Builder<T> queryParams(Map<String, String> queryParams) {
            if (queryParams != null) {
                queryParams.forEach(this.queryParams::put);
            }
            return this;
        }

        /**
         * 合并请求头，后加入的同名值覆盖先前值。
         *
         * @param headers 请求头
         * @return 当前 Builder
         */
        public Builder<T> headers(Map<String, String> headers) {
            if (headers != null) {
                headers.forEach(this.headers::put);
            }
            return this;
        }

        /**
         * 设置 JSON 请求体。
         *
         * @param body 任意可由 Jackson 序列化的对象
         * @return 当前 Builder
         */
        public Builder<T> jsonBody(Object body) {
            return body(BodyKind.JSON, body);
        }

        /**
         * 控制统一调用日志是否序列化请求体。
         *
         * @param enabled 是否允许记录 JSON 请求体
         * @return 当前 Builder
         */
        public Builder<T> logRequestBody(boolean enabled) {
            this.logRequestBody = enabled;
            return this;
        }

        /**
         * 设置表单请求体。
         *
         * @param formData 字符串表单字段
         * @return 当前 Builder
         */
        public Builder<T> formBody(Map<String, String> formData) {
            return body(BodyKind.FORM, formData == null ? Map.of() : Map.copyOf(formData));
        }

        /**
         * 设置已完成字段校验的 multipart 请求体。
         *
         * <p>该方法主要供 {@link RemoteApiClient} 的兼容 API 使用。业务代码应优先调用
         * {@code RemoteApiClient.postMultipart(...)}，确保文件字段经过白名单校验。
         *
         * @param multipartBody Spring multipart 数据
         * @return 当前 Builder
         */
        Builder<T> multipartBody(Object multipartBody) {
            return body(BodyKind.MULTIPART, multipartBody);
        }

        /**
         * 设置请求体类型和内容。
         *
         * @param bodyKind 请求体类型
         * @param body 请求体内容
         * @return 当前 Builder
         */
        private Builder<T> body(BodyKind bodyKind, Object body) {
            this.bodyKind = Objects.requireNonNull(bodyKind, "bodyKind不能为空");
            this.body = body;
            return this;
        }

        /**
         * 构建并校验不可变请求。
         *
         * @return 不可变远程请求
         */
        public RemoteRequest<T> build() {
            return new RemoteRequest<>(this);
        }
    }
}
