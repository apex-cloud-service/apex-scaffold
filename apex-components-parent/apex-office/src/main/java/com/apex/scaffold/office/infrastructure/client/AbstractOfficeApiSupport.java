package com.apex.scaffold.office.infrastructure.client;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.infrastructure.config.OfficePlatformProperties;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 办公平台 API 调用支持类。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
public abstract class AbstractOfficeApiSupport {
    private static final String SUCCESS_CODE = "0";
    private final Duration timeout;
    protected final WebClient webClient;
    protected final OfficePlatformProperties.Platform properties;

    /** 构造 API 调用支持类并初始化超时、WebClient 与平台配置。 */
    protected AbstractOfficeApiSupport(
            WebClient.Builder builder,
            OfficePlatformProperties platformProperties,
            com.apex.scaffold.office.domain.OfficePlatform platform,
            String baseUrl) {
        this.timeout = platformProperties.getTimeout();
        this.webClient = builder.baseUrl(baseUrl).build();
        this.properties =
                platformProperties.getPlatforms().getOrDefault(platform, new OfficePlatformProperties.Platform());
    }

    /**
     * 以 GET 查询参数形式调用平台接口。
     *
     * @param path 接口路径（相对 {@code baseUrl}）
     * @param params 查询参数
     * @return 平台响应体，已通过 {@code checkedBody} 校验业务状态码
     */
    protected Mono<Map<String, Object>> getAccessToken(String path, Map<String, String> params) {
        return webClient
                .get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(path);
                    params.forEach(builder::queryParam);
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .map(this::checkedBody);
    }

    /**
     * 以不附加自定义请求头的方式发送 JSON POST 请求。
     *
     * @param path 接口路径（相对 {@code baseUrl}）
     * @param body 请求体，按 JSON 序列化
     * @return 平台响应体
     */
    protected Mono<Map<String, Object>> postJson(String path, Map<String, Object> body) {
        return postJson(path, body, Map.of());
    }

    /**
     * 发送 JSON POST 请求并附加自定义请求头。
     *
     * @param path 接口路径（相对 {@code baseUrl}）
     * @param body 请求体，按 JSON 序列化
     * @param headers 附加请求头，同名头将被覆盖
     * @return 平台响应体
     */
    protected Mono<Map<String, Object>> postJson(String path, Map<String, Object> body, Map<String, String> headers) {
        return webClient
                .post()
                .uri(path)
                .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .map(this::checkedBody);
    }

    /**
     * 以 GET 查询参数形式获取 JSON 响应（{@link #getAccessToken} 的语义别名）。
     *
     * @param path 接口路径（相对 {@code baseUrl}）
     * @param params 查询参数
     * @return 平台响应体
     */
    protected Mono<Map<String, Object>> getJson(String path, Map<String, String> params) {
        return getAccessToken(path, params);
    }

    /**
     * 发送带自定义请求头的 GET 请求。请求头常用于承载平台访问令牌。
     *
     * @param path 接口路径（相对 {@code baseUrl}）
     * @param headers 请求头
     * @return 平台响应体
     */
    protected Mono<Map<String, Object>> getJsonWithHeaders(String path, Map<String, String> headers) {
        return webClient
                .get()
                .uri(path)
                .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .map(this::checkedBody);
    }

    /**
     * 发送带自定义请求头的 DELETE 请求。
     *
     * @param path 接口路径（相对 {@code baseUrl}）
     * @param headers 请求头
     * @return 平台响应体
     */
    protected Mono<Map<String, Object>> deleteWithHeaders(String path, Map<String, String> headers) {
        return webClient
                .delete()
                .uri(path)
                .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .map(this::checkedBody);
    }

    /**
     * 发送带自定义请求头且带查询参数的 GET 请求。
     *
     * @param path 接口路径（相对 {@code baseUrl}）
     * @param params 查询参数，按名排序写入 URI
     * @param headers 请求头，常用于承载平台访问令牌
     * @return 平台响应体
     */
    protected Mono<Map<String, Object>> getJsonWithHeadersAndParams(
            String path, Map<String, String> params, Map<String, String> headers) {
        return webClient
                .get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(path);
                    params.forEach(builder::queryParam);
                    return builder.build();
                })
                .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .map(this::checkedBody);
    }

    /**
     * 发送带自定义请求头的 JSON POST 请求（无查询参数）。
     *
     * @param path 接口路径（相对 {@code baseUrl}）
     * @param body 请求体，按 JSON 序列化
     * @param headers 附加请求头
     * @return 平台响应体
     */
    protected Mono<Map<String, Object>> postJsonWithHeaders(
            String path, Map<String, Object> body, Map<String, String> headers) {
        return postJson(path, body, headers);
    }

    /**
     * 将平台响应映射为业务对象，并把平台错误统一转换为 {@link BusinessException}。
     *
     * @param mapper 响应体到业务对象的映射函数
     * @param request 平台请求信号
     * @param <T> 业务对象类型
     * @return 映射后的业务对象信号；请求失败或映射出错时以业务异常结束
     */
    protected <T> Mono<T> call(Function<Map<String, Object>, T> mapper, Mono<Map<String, Object>> request) {
        return request.map(mapper).onErrorMap(this::mapError);
    }

    /**
     * 读取平台密钥的环境变量值。
     *
     * @param name 平台名称，用于错误提示
     * @return 密钥值
     * @throws BusinessException 密钥引用或环境变量未配置时抛出，见 {@link ResultCode#BUSINESS_ERROR}
     */
    protected String secret(String name) {
        String reference = required(properties.getSecretReference(), name + " secret reference");
        return required(System.getenv(reference), name + " secret");
    }

    /**
     * 校验必填字符串，空值时快速失败。
     *
     * @param value 待校验的值
     * @param name  值的名称，用于错误提示
     * @return 校验通过的原值
     * @throws BusinessException 值为空或空白时抛出，见 {@link ResultCode#BUSINESS_ERROR}
     */
    protected String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, name + "未配置");
        }
        return value;
    }

    /**
     * 将对象转为非空文本，空值时快速失败。
     *
     * @param value 待转换的对象
     * @param name  值的名称，用于错误提示
     * @return 对象的字符串形式
     * @throws BusinessException 对象为 null 时抛出，见 {@link ResultCode#BUSINESS_ERROR}
     */
    protected String text(Object value, String name) {
        return required(value == null ? null : value.toString(), name);
    }

    private Map<String, Object> checkedBody(Map<String, Object> body) {
        Object code = body.get("code");
        if (code == null) {
            code = body.get("errcode");
        }
        if (code != null && !SUCCESS_CODE.equals(code.toString())) {
            throw new BusinessException(
                    ResultCode.BUSINESS_ERROR,
                    "办公平台 API 调用失败：" + code + " " + body.getOrDefault("msg", body.get("errmsg")));
        }
        return body;
    }

    private Throwable mapError(Throwable error) {
        if (error instanceof BusinessException) {
            return error;
        }
        return new BusinessException(ResultCode.BUSINESS_ERROR, "办公平台 API 调用失败：" + error.getMessage());
    }
}
