package com.apex.scaffold.infrastructure.chuanglan.exception;

/**
 * 创蓝云平台API调用异常
 *
 * <p>当调用创蓝253云通信平台API时，如果返回错误码或网络异常，抛出此异常。
 * 异常中包含创蓝平台返回的原始错误码和错误信息，便于定位问题。
 *
 * <h2>触发场景</h2>
 * <ul>
 *   <li>短信发送接口返回错误码（如账号密码错误、余额不足等）</li>
 *   <li>闪验token置换手机号接口返回错误码（如token无效、过期等）</li>
 *   <li>HTTP请求超时或网络不可达</li>
 *   <li>响应数据格式异常（如JSON解析失败）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class ChuanglanApiException extends RuntimeException {

    /** 创蓝平台返回的错误码 */
    @lombok.Getter
    private final String errorCode;

    /**
     * 构造创蓝API异常
     *
     * @param errorCode 创蓝平台返回的错误码
     * @param message 错误描述信息
     */
    public ChuanglanApiException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造创蓝API异常（带cause）
     *
     * @param message 错误描述信息
     * @param cause 原始异常
     */
    public ChuanglanApiException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "UNKNOWN";
    }
}
