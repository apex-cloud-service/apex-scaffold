package com.apex.scaffold.domain.gateway;

/**
 * 远程服务返回的 4xx 请求/业务输入错误。
 *
 * <p>该类型供重试与熔断策略识别：调用失败只影响当前输入，不应计入远程服务故障率。</p>
 *
 * @author weiqiang.ling
 * @version 1.0.0
 * @since 1.0.0
 */
public class RemoteClientInputException extends RemoteApiException {

    /**
     * 构造远程服务 4xx 输入错误异常。
     *
     * @param httpStatus HTTP 状态码
     * @param serviceName 远程服务名称
     * @param requestPath 请求路径
     * @param errorBody 错误响应体
     * @param message 异常消息
     */
    public RemoteClientInputException(
            int httpStatus, String serviceName, String requestPath, String errorBody, String message) {
        super(httpStatus, serviceName, requestPath, errorBody, message);
    }
}
