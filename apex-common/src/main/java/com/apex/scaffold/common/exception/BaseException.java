package com.apex.scaffold.common.exception;

import com.apex.scaffold.common.result.ResultCode;
import lombok.Getter;

/**
 * 基础异常类
 *
 * <p>系统中所有自定义异常的基类，提供多种构造方法支持不同的异常创建场景。
 * 支持通过 {@code messageKey} 进行国际化消息解析，同时保留直接传入 {@code msg} 的兼容方式。
 *
 * <h2>构造方法分类</h2>
 * <ul>
 *   <li><b>仅消息</b>：使用默认错误码 {@code ERROR(-1)}</li>
 *   <li><b>自定义状态码+消息</b>：指定错误码和消息文本</li>
 *   <li><b>预定义错误码</b>：使用 {@link ResultCode} 枚举中的 code 和 msg</li>
 *   <li><b>预定义错误码+自定义消息</b>：复用错误码但覆盖消息</li>
 *   <li><b>带 cause</b>：封装原始异常栈信息</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see ResultCode
 */
@Getter
public class BaseException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /** 异常状态码 */
    private final Integer code;

    /** 异常消息 */
    private final String msg;

    /** 国际化消息键 */
    private final String messageKey;

    /**
     * 构造基础异常（仅消息）
     *
     * <p>使用默认错误码 {@code ResultCode.ERROR.getCode()}。
     *
     * @param message 异常消息，直接作为 HTTP 响应中的 msg 字段值
     */
    public BaseException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
        this.msg = message;
        this.messageKey = ResultCode.ERROR.getMessageKey();
    }

    /**
     * 构造基础异常（自定义状态码和消息）
     *
     * @param code    异常状态码，作为 HTTP 响应中的 code 字段值
     * @param message 异常消息，作为 HTTP 响应中的 msg 字段值
     */
    public BaseException(Integer code, String message) {
        super(message);
        this.code = code;
        this.msg = message;
        this.messageKey = null;
    }

    /**
     * 构造基础异常（使用预定义错误码）
     *
     * <p>从 {@link ResultCode} 枚举中提取 code 和 msg。
     * msg 为简体中文默认值，可用于日志记录。若需要在 HTTP 响应中使用
     * 多语言消息，调用方应在捕获异常后通过 MessageUtils 解析。
     *
     * @param resultCode 错误码枚举，包含 code 和 msg（简体中文）
     */
    public BaseException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
        this.msg = resultCode.getMsg();
        this.messageKey = resultCode.getMessageKey();
    }

    /**
     * 构造基础异常（使用预定义错误码和自定义消息）
     *
     * <p>使用 resultCode 中的 code，但使用自定义 message 覆盖默认消息。
     *
     * @param resultCode 错误码枚举，提取 code 部分
     * @param message    自定义异常消息，覆盖 resultCode 中的默认 msg
     */
    public BaseException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
        this.msg = message;
        this.messageKey = resultCode.getMessageKey();
    }

    /**
     * 构造基础异常（带异常原因）
     *
     * <p>使用默认错误码，同时记录原始异常以便排查问题。
     *
     * @param message 异常消息
     * @param cause   原始异常，保留异常链信息
     */
    public BaseException(String message, Throwable cause) {
        super(message, cause);
        this.code = ResultCode.ERROR.getCode();
        this.msg = message;
        this.messageKey = ResultCode.ERROR.getMessageKey();
    }

    /**
     * 构造基础异常（带异常原因和预定义错误码）
     *
     * <p>使用预定义错误码，同时记录原始异常。
     *
     * @param resultCode 错误码枚举，提取 code 和 msg
     * @param cause      原始异常，保留异常链信息
     */
    public BaseException(ResultCode resultCode, Throwable cause) {
        super(resultCode.getMsg(), cause);
        this.code = resultCode.getCode();
        this.msg = resultCode.getMsg();
        this.messageKey = resultCode.getMessageKey();
    }

    /**
     * 构造基础异常（带异常原因和预定义错误码，自定义消息）
     *
     * <p>使用预定义错误码，自定义消息覆盖默认 msg，同时记录原始异常。
     *
     * @param resultCode 错误码枚举，提取 code 部分
     * @param message    自定义异常消息，覆盖 resultCode 中的默认 msg
     * @param cause      原始异常，保留异常链信息
     */
    public BaseException(ResultCode resultCode, String message, Throwable cause) {
        super(message, cause);
        this.code = resultCode.getCode();
        this.msg = message;
        this.messageKey = resultCode.getMessageKey();
    }

    /**
     * 构造基础异常（带异常原因、自定义状态码和消息）
     *
     * <p>完全自定义构造，灵活度最高。
     *
     * @param code    异常状态码
     * @param message 异常消息
     * @param cause   原始异常，保留异常链信息
     */
    public BaseException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.msg = message;
        this.messageKey = null;
    }
}
