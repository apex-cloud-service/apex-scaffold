package com.apex.scaffold.common.constant;

/**
 * 缓存常量类
 *
 * <p>定义缓存相关的常量
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class CacheConstants {

    /** 私有构造函数，防止实例化 */
    private CacheConstants() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }

    /** 缓存Key前缀 */
    public static final String CACHE_PREFIX = "apex:";

    /** 用户Token缓存前缀 */
    public static final String USER_TOKEN_KEY = CACHE_PREFIX + "user:token:";

    /** 用户信息缓存前缀 */
    public static final String USER_INFO_KEY = CACHE_PREFIX + "user:info:";

    /** 验证码缓存前缀 */
    public static final String CAPTCHA_KEY = CACHE_PREFIX + "captcha:";

    /** 微信AccessToken缓存前缀 */
    public static final String WECHAT_ACCESS_TOKEN_KEY = CACHE_PREFIX + "wechat:access_token";

    /** 微信JSAPITicket缓存前缀 */
    public static final String WECHAT_JSAPI_TICKET_KEY = CACHE_PREFIX + "wechat:jsapi_ticket";

    /** 分布式锁前缀 */
    public static final String LOCK_PREFIX = CACHE_PREFIX + "lock:";

    /** 登录锁前缀 */
    public static final String LOGIN_LOCK_KEY = LOCK_PREFIX + "login:";

    /** 默认缓存过期时间（秒） */
    public static final long DEFAULT_EXPIRE_TIME = 3600L;

    /** Token缓存过期时间（秒） */
    public static final long TOKEN_EXPIRE_TIME = 7200L;

    /** 验证码过期时间（秒） */
    public static final long CAPTCHA_EXPIRE_TIME = 300L;

    /** 微信AccessToken过期时间（秒）- 提前5分钟过期 */
    public static final long WECHAT_TOKEN_EXPIRE_TIME = 6600L;
}
