package com.apex.scaffold.common.exception;

import com.apex.scaffold.common.result.ResultCode;

/**
 * 业务异常类
 *
 * <p>用于封装业务逻辑层面的异常，继承自BaseException
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class BusinessException extends BaseException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造业务异常（仅消息）
     *
     * @param message 异常消息
     */
    public BusinessException(String message) {
        super(message);
    }

    /**
     * 构造业务异常（自定义状态码和消息）
     *
     * @param code 异常状态码
     * @param message 异常消息
     */
    public BusinessException(Integer code, String message) {
        super(code, message);
    }

    /**
     * 构造业务异常（使用预定义错误码）
     *
     * @param resultCode 错误码枚举
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode);
    }

    /**
     * 构造业务异常（使用预定义错误码和自定义消息）
     *
     * @param resultCode 错误码枚举
     * @param message 自定义异常消息
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(resultCode, message);
    }

    /**
     * 构造业务异常（带原因）
     *
     * @param message 异常消息
     * @param cause 原始异常原因
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造业务异常（使用预定义错误码和自定义消息，带原因）
     *
     * <p>使用预定义错误码，自定义消息覆盖默认 msg，同时记录原始异常以便排查问题。
     *
     * @param resultCode 错误码枚举
     * @param message    自定义异常消息
     * @param cause      原始异常，保留异常链信息
     */
    public BusinessException(ResultCode resultCode, String message, Throwable cause) {
        super(resultCode, message, cause);
    }
}
