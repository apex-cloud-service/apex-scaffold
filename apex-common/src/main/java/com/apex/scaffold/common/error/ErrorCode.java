package com.apex.scaffold.common.error;

/**
 * 统一错误码枚举
 *
 * <p>错误码规范:
 * <ul>
 *   <li>1000-1999: 通用错误</li>
 *   <li>2000-2999: 用户相关</li>
 *   <li>3000-3999: 业务逻辑</li>
 *   <li>4000-4999: 系统错误</li>
 *   <li>5000-5999: 第三方服务</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 */
/**
 * ErrorCode 组件。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ErrorCode {

    // ============================================
    // 通用错误 1000-1999
    // ============================================

    /**
     * 成功
     */
    SUCCESS(1000, "成功"),

    /**
     * 参数无效
     */
    PARAM_INVALID(1001, "参数无效"),

    /**
     * 参数缺失
     */
    PARAM_MISSING(1002, "必填参数缺失"),

    /**
     * 参数格式错误
     */
    PARAM_FORMAT_ERROR(1003, "参数格式错误"),

    /**
     * 认证失败
     */
    AUTH_FAILED(1004, "认证失败,请重新登录"),

    /**
     * Token过期
     */
    TOKEN_EXPIRED(1005, "Token已过期"),

    /**
     * Token无效
     */
    TOKEN_INVALID(1006, "Token无效"),

    /**
     * 权限不足
     */
    PERMISSION_DENIED(1007, "权限不足"),

    /**
     * 资源不存在
     */
    RESOURCE_NOT_FOUND(1008, "资源不存在"),

    /**
     * 资源已存在
     */
    RESOURCE_EXISTS(1009, "资源已存在"),

    /**
     * 请求频率超限
     */
    RATE_LIMIT_EXCEEDED(1010, "请求频率超限,请稍后重试"),

    // ============================================
    // 用户相关 2000-2999
    // ============================================

    /**
     * 用户不存在
     */
    USER_NOT_FOUND(2001, "用户不存在"),

    /**
     * 用户名已存在
     */
    USERNAME_EXISTS(2002, "用户名已存在"),

    /**
     * 邮箱已注册
     */
    EMAIL_EXISTS(2003, "邮箱已注册"),

    /**
     * 手机号已注册
     */
    PHONE_EXISTS(2004, "手机号已注册"),

    /**
     * 密码错误
     */
    PASSWORD_ERROR(2005, "密码错误"),

    /**
     * 账号被禁用
     */
    ACCOUNT_DISABLED(2006, "账号已被禁用"),

    /**
     * 账号被锁定
     */
    ACCOUNT_LOCKED(2007, "账号已被锁定,请稍后重试"),

    /**
     * 验证码错误
     */
    VERIFY_CODE_ERROR(2008, "验证码错误"),

    /**
     * 验证码过期
     */
    VERIFY_CODE_EXPIRED(2009, "验证码已过期"),

    // ============================================
    // 业务逻辑 3000-3999
    // ============================================

    /**
     * 业务规则校验失败
     */
    BUSINESS_RULE_VIOLATION(3001, "业务规则校验失败"),

    /**
     * 数据状态异常
     */
    DATA_STATUS_ERROR(3002, "数据状态异常"),

    /**
     * 操作不允许
     */
    OPERATION_NOT_ALLOWED(3003, "当前状态下不允许此操作"),

    /**
     * 余额不足
     */
    INSUFFICIENT_BALANCE(3004, "余额不足"),

    /**
     * 库存不足
     */
    INSUFFICIENT_STOCK(3005, "库存不足"),

    /**
     * 订单已取消
     */
    ORDER_CANCELLED(3006, "订单已取消"),

    /**
     * 订单已完成
     */
    ORDER_COMPLETED(3007, "订单已完成"),

    // ============================================
    // 系统错误 4000-4999
    // ============================================

    /**
     * 系统内部错误
     */
    SYSTEM_ERROR(4001, "系统内部错误,请联系管理员"),

    /**
     * 数据库操作失败
     */
    DATABASE_ERROR(4002, "数据库操作失败"),

    /**
     * 缓存操作失败
     */
    CACHE_ERROR(4003, "缓存操作失败"),

    /**
     * 文件上传失败
     */
    FILE_UPLOAD_ERROR(4004, "文件上传失败"),

    /**
     * 文件下载失败
     */
    FILE_DOWNLOAD_ERROR(4005, "文件下载失败"),

    /**
     * 服务不可用
     */
    SERVICE_UNAVAILABLE(4006, "服务暂时不可用,请稍后重试"),

    /**
     * 超时
     */
    TIMEOUT(4007, "请求超时"),

    /**
     * 并发冲突
     */
    CONCURRENCY_CONFLICT(4008, "数据并发冲突,请刷新后重试"),

    // ============================================
    // 第三方服务 5000-5999
    // ============================================

    /**
     * 微信服务异常
     */
    WECHAT_SERVICE_ERROR(5001, "微信服务异常"),

    /**
     * 支付宝服务异常
     */
    ALIPAY_SERVICE_ERROR(5002, "支付宝服务异常"),

    /**
     * 短信服务异常
     */
    SMS_SERVICE_ERROR(5003, "短信服务异常"),

    /**
     * 邮件服务异常
     */
    EMAIL_SERVICE_ERROR(5004, "邮件服务异常"),

    /**
     * 对象存储异常
     */
    STORAGE_SERVICE_ERROR(5005, "对象存储服务异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取错误码数值。
     *
     * @return 错误码数值
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取错误描述消息。
     *
     * @return 错误描述消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 根据code获取ErrorCode
     *
     * @param code 错误码
     * @return ErrorCode,未找到返回null
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode() == code) {
                return errorCode;
            }
        }
        return null;
    }
}
