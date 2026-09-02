package com.apex.scaffold.domain.gateway;

import lombok.Getter;

/**
 * 远程API调用异常
 *
 *
 *<p>当调用外部项目的API接口时，如果发生HTTP错误（4xx/5xx状态码）、
 *网络超时、连接异常或响应解析失败等情况，将抛出此异常。
 *
 *<h2>触发场景</h2>
 *<ul>
 *  <li>HTTP请求超时（连接超时或读取超时）</li>
 *  <li>目标服务返回4xx客户端错误状态码</li>
 *  <li>目标服务返回5xx服务端错误状态码</li>
 *  <li>网络不可达或DNS解析失败</li>
 *  <li>响应体解析失败（如反序列化异常）</li>
 *  <li>所有重试次数耗尽后仍然失败</li>
 *</ul>
 *
 *<h2>使用示例</h2>
 *<pre>{@code * try {
 *    String result = remoteApiClient.get(serviceName, path, String.class).block();
 *} catch (RemoteApiException e) {
 *    log.error("远程调用失败, serviceName: {}, httpStatus: {}, errorBody: {}",
 *        e.getServiceName(), e.getHttpStatus(), e.getErrorBody());
 *}
 *}</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class RemoteApiException extends RuntimeException implements RemoteClientFailure {

    /** HTTP状态码，-1表示非HTTP层面错误（如网络不可达） */
    private final int httpStatus;

    /** 目标服务名称 */
    private final String serviceName;

    /** 请求路径 */
    private final String requestPath;

    /** 远程服务返回的错误响应体 */
    private final String errorBody;

    /** 是否由熔断器打开触发 */
    private final boolean circuitBreakerOpen;

    /**
     * 构造HTTP状态码异常。
     *
     * @param httpStatus HTTP状态码
     * @param serviceName 目标服务名称
     * @param requestPath 请求路径
     * @param errorBody 远程服务返回的错误响应体
     * @param message 错误描述信息
     */
    public RemoteApiException(
            int httpStatus, String serviceName, String requestPath, String errorBody, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.serviceName = serviceName;
        this.requestPath = requestPath;
        this.errorBody = errorBody;
        this.circuitBreakerOpen = false;
    }

    /**
     * 构造网络层异常（如连接超时、网络不可达）。
     *
     * @param serviceName 目标服务名称
     * @param requestPath 请求路径
     * @param message 错误描述信息
     * @param cause 原始异常
     */
    public RemoteApiException(String serviceName, String requestPath, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = -1;
        this.serviceName = serviceName;
        this.requestPath = requestPath;
        this.errorBody = null;
        this.circuitBreakerOpen = false;
    }

    /**
     * 构造简短消息异常（仅消息和cause）。
     *
     * @param message 错误描述信息
     * @param cause 原始异常
     */
    public RemoteApiException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = -1;
        this.serviceName = null;
        this.requestPath = null;
        this.errorBody = null;
        this.circuitBreakerOpen = false;
    }

    /**
     * 构造熔断器打开异常（用于服务降级场景）。
     *
     * <p>当 Resilience4j CircuitBreaker 处于 OPEN 状态时，
     * 通过此构造函数标记异常来源，供上层业务代码做降级处理。
     *
     * @param serviceName 目标服务名称
     * @param requestPath 请求路径
     * @param cause 原始熔断异常
     */
    public RemoteApiException(String serviceName, String requestPath, Throwable cause) {
        super("熔断器已打开, serviceName: " + serviceName + ", path: " + requestPath, cause);
        this.httpStatus = 503;
        this.serviceName = serviceName;
        this.requestPath = requestPath;
        this.errorBody = "CIRCUIT_BREAKER_OPEN";
        this.circuitBreakerOpen = true;
    }

    /**
     * 判断是否为客户端错误（4xx）
     *
     * @return true表示4xx状态码
     */
    @Override
    public boolean isClientError() {
        return httpStatus >= 400 && httpStatus < 500;
    }

    /**
     * 判断是否为服务端错误（5xx）
     *
     * @return true表示5xx状态码
     */
    public boolean isServerError() {
        return httpStatus >= 500 && httpStatus < 600;
    }

    /**
     * 判断是否由熔断器打开触发。
     *
     * <p>当此方法返回 {@code true} 时，表示当前异常并非远程服务实际返回 503，
     * 而是本地 Resilience4j CircuitBreaker 处于 OPEN 状态直接拒绝请求。
     * 上层业务可据此执行降级逻辑。
     *
     * @return true 表示由熔断器触发
     */
    public boolean isCircuitBreakerOpen() {
        return circuitBreakerOpen;
    }
}
