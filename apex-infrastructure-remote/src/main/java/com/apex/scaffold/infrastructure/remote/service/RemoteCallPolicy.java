package com.apex.scaffold.infrastructure.remote.service;

import com.apex.scaffold.domain.gateway.RemoteApiException;
import com.apex.scaffold.domain.gateway.RemoteClientInputException;
import com.apex.scaffold.infrastructure.remote.config.RemoteApiProperties;
import com.apex.scaffold.infrastructure.remote.config.RemoteApiProperties.ServiceConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

/**
 * 远程调用超时、重试与异常映射统一策略。
 *
 * <p>对 {@link com.apex.scaffold.infrastructure.remote.service.RemoteApiClient} 的每次远程 HTTP 调用
 * 提供统一的超时解析、Reactor Retry 重试判断和异常包装逻辑，避免上层业务代码重复处理底层网络语义。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li><b>超时解析</b>：从 {@link ServiceConfig} 读取按服务级别的 connect / read 超时，
 *       未配置时回退到 {@link RemoteApiProperties} 的全局默认值</li>
 *   <li><b>重试策略</b>：对 5xx 服务端错误和未分类调用异常执行 Reactor
 *       {@link Retry#backoff} 退避重试，4xx 客户端错误不重试（语义安全）</li>
 *   <li><b>异常映射</b>：将 {@link WebClientResponseException} 和 Reactor 重试耗尽异常
 *       统一包装为 {@link RemoteApiException}，保留 HTTP 状态码和响应体</li>
 * </ul>
 *
 * <h2>重试参数来源</h2>
 * <p>重试次数和间隔优先取服务级 {@link ServiceConfig}，未配置时回退到
 * {@link RemoteApiProperties} 的全局默认值；最大退避固定为 10s。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 * @see RemoteApiProperties
 * @see RemoteApiException
 */
@RequiredArgsConstructor
@Slf4j
public final class RemoteCallPolicy {

    /** Reactor 重试耗尽异常的类名特征片段，用于解包判断 */
    private static final String RETRY_EXHAUSTED_CLASS_MARKER = "RetryExhausted";

    /** 远程服务全局配置属性 */
    private final RemoteApiProperties properties;

    /**
     * 构建重试服务端错误（5xx）与未分类调用异常的指数退避策略。
     *
     * <p>重试次数和退避间隔优先从 {@link ServiceConfig} 读取，未配置时回退到全局默认值；
     * 最大退避固定 10s，重试前输出 INFO 级别日志。
     *
     * @param config      对应服务的配置
     * @param serviceName 服务名称（用于日志）
     * @param path        请求路径（用于日志）
     * @return Reactor 退避重试策略
     */
    public RetryBackoffSpec retry(ServiceConfig config, String serviceName, String path) {
        return Retry.backoff(retryCount(config), Duration.ofMillis(retryDelay(config)))
                .maxBackoff(Duration.ofSeconds(10))
                .filter(error -> isRetryable(error, serviceName, path))
                .doBeforeRetry(signal -> log.info(
                        "远程调用第{}次重试, serviceName: {}, path: {}", signal.totalRetries() + 1, serviceName, path));
    }

    /**
     * 统一映射远程调用异常。
     *
     * @param serviceName 服务名
     * @param path 请求路径
     * @param error 原始异常
     * @return 标准远程调用异常
     */
    public RemoteApiException wrap(String serviceName, String path, Throwable error) {
        Throwable actual = unwrapRetry(error);
        if (actual instanceof RemoteApiException remoteApiException) {
            return remoteApiException;
        }
        if (actual instanceof CallNotPermittedException) {
            return new RemoteApiException(serviceName, path, actual);
        }
        if (actual instanceof WebClientResponseException responseException) {
            HttpStatusCode status = responseException.getStatusCode();
            String body = responseException.getResponseBodyAsString();
            if (status.is4xxClientError()) {
                return new RemoteClientInputException(
                        status.value(),
                        serviceName,
                        path,
                        body,
                        "远程服务拒绝当前请求, serviceName: " + serviceName + ", path: " + path + ", httpStatus: "
                                + status.value() + ", errorBody: " + body);
            }
            return new RemoteApiException(
                    status.value(),
                    serviceName,
                    path,
                    body,
                    "远程服务返回错误, serviceName: " + serviceName + ", path: " + path + ", httpStatus: " + status.value()
                            + ", errorBody: " + body);
        }
        return new RemoteApiException(
                serviceName, path, "远程调用失败, serviceName: " + serviceName + ", path: " + path, actual);
    }

    /**
     * 解析连接超时。
     *
     * @param config 服务配置
     * @return 连接超时时间（毫秒）
     */
    public int connectTimeout(ServiceConfig config) {
        return config.getConnectTimeout() == null ? properties.getDefaultConnectTimeout() : config.getConnectTimeout();
    }

    /**
     * 解析读取超时。
     *
     * @param config 服务配置
     * @return 读取超时时间（毫秒）
     */
    public int readTimeout(ServiceConfig config) {
        return config.getReadTimeout() == null ? properties.getDefaultReadTimeout() : config.getReadTimeout();
    }

    /** 判断异常是否可重试。 */
    private boolean isRetryable(Throwable error, String serviceName, String path) {
        boolean retryable;
        Integer status = null;
        if (error instanceof RemoteApiException remoteApiException) {
            retryable = remoteApiException.isServerError();
            status = remoteApiException.getHttpStatus();
        } else if (error instanceof WebClientResponseException responseException) {
            retryable = responseException.getStatusCode().is5xxServerError();
            status = responseException.getStatusCode().value();
        } else {
            retryable = true;
        }
        if (retryable) {
            log.warn("远程调用失败, 将进行重试, serviceName: {}, path: {}, httpStatus: {}", serviceName, path, status);
        }
        return retryable;
    }

    /** 解包 Reactor 重试耗尽异常。 */
    private Throwable unwrapRetry(Throwable error) {
        if (error != null
                && error.getCause() != null
                && error.getClass().getName().contains(RETRY_EXHAUSTED_CLASS_MARKER)) {
            return error.getCause();
        }
        return error;
    }

    /** 解析重试次数。 */
    private int retryCount(ServiceConfig config) {
        return config.getRetryCount() == null ? properties.getDefaultRetryCount() : config.getRetryCount();
    }

    /** 解析重试间隔。 */
    private long retryDelay(ServiceConfig config) {
        return config.getRetryDelay() == null ? properties.getDefaultRetryDelay() : config.getRetryDelay();
    }
}
