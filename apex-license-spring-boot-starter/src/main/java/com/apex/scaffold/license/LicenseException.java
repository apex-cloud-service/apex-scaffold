package com.apex.scaffold.license;

/**
 * License异常
 *
 * @author Apex Team
 * @version 1.0.0
 */
public class LicenseException extends RuntimeException {

    /**
     * 使用错误消息构造License异常
     *
     * @param message 错误消息,描述License校验失败的具体原因
     */
    public LicenseException(String message) {
        super(message);
    }

    /**
     * 使用错误消息与底层原因构造License异常
     *
     * @param message 错误消息,描述License校验失败的具体原因
     * @param cause   底层触发异常,用于保留完整异常堆栈
     */
    public LicenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
