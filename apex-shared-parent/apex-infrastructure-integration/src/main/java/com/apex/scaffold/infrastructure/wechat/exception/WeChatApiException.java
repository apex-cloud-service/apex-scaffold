package com.apex.scaffold.infrastructure.wechat.exception;

import java.io.Serial;
import lombok.Getter;

/**
 * 微信API异常类
 *
 * <p>微信接口调用异常
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class WeChatApiException extends RuntimeException {

    /** 序列化版本 UID */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final Integer errcode;

    /** 错误信息 */
    private final String errmsg;

    /**
     * 构造微信 API 异常
     *
     * @param errcode 错误码
     * @param errmsg 错误信息
     */
    public WeChatApiException(Integer errcode, String errmsg) {
        super("微信 API 调用失败：[" + errcode + "] " + errmsg);
        this.errcode = errcode;
        this.errmsg = errmsg;
    }

    /**
     * 构造函数
     *
     * @param errcode 错误码
     * @param errmsg 错误信息
     * @param cause 原因
     */
    public WeChatApiException(Integer errcode, String errmsg, Throwable cause) {
        super("微信API调用失败: [" + errcode + "] " + errmsg, cause);
        this.errcode = errcode;
        this.errmsg = errmsg;
    }

    /** 常见微信错误码常量定义 */
    public static class ErrorCode {

        /** 私有构造函数，防止实例化 */
        private ErrorCode() {
            throw new UnsupportedOperationException("错误码常量类不能实例化");
        }

        /** 成功 */
        public static final int SUCCESS = 0;

        /** 系统繁忙 */
        public static final int SYSTEM_BUSY = -1;

        /** 无效的 AppID */
        public static final int INVALID_APPID = 40013;

        /** 无效的 Secret */
        public static final int INVALID_SECRET = 40001;

        /** 无效的 Code */
        public static final int INVALID_CODE = 40029;

        /** AccessToken 无效或过期 */
        public static final int ACCESS_TOKEN_INVALID = 40014;

        /** AccessToken 已过期 */
        public static final int ACCESS_TOKEN_EXPIRED = 42001;

        /** RefreshToken 已过期 */
        public static final int REFRESH_TOKEN_EXPIRED = 42002;

        /** 接口调用频率超限 */
        public static final int RATE_LIMIT = 45011;

        /** 内容可能违规 */
        public static final int CONTENT_RISKY = 87014;
    }
}
