package com.apex.scaffold.common.exception;

/**
 * 403 Forbidden 异常
 *
 * 当用户没有权限访问资源时抛出
 * @author Apex Team
 * @since 1.2.0
 */
public class ForbiddenException extends BaseException {

    /** 构造 403 异常。 */
    public ForbiddenException(String message) {
        super(message);
    }

    /** 构造带根因的 403 异常。 */
    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
