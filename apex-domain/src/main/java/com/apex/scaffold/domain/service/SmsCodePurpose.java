package com.apex.scaffold.domain.service;

/**
 * 短信验证码的业务用途。
 *
 * <p>不同用途使用不同的 Redis 命名空间，避免登录验证码被拿来完成账号注销，
 * 或注销验证码被拿来完成其他认证流程。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum SmsCodePurpose {
    /** 登录、注册和既有登录流程。 */
    LOGIN("login"),
    /** 账号注销二次验证。 */
    ACCOUNT_DELETION("account-deletion");

    private final String key;

    /**
     * 构造短信验证码用途枚举值。
     *
     * @param key 用于缓存键的稳定字符串值
     */
    SmsCodePurpose(String key) {
        this.key = key;
    }

    /** @return 用于缓存键的稳定值 */
    public String key() {
        return key;
    }
}
