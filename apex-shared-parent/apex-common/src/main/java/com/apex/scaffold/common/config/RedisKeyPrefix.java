package com.apex.scaffold.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redis Key 前缀版本管理器。
 *
 * <p>当 Redis 数据序列化格式发生不兼容变更时（如 Codec 迁移、数据 Schema 变更），
 * 可通过修改 {@code apex.redis.key-prefix} 配置递增版本号，
 * 使新旧格式的 Key 自动隔离，避免跨版本数据读取异常。
 *
 * <h2>使用示例</h2>
 * <pre>{@code * String key = redisKeyPrefix.wrap("token:access:" + subject);
 * // 当 key-prefix="v2:" 时，生成 "v2:token:access:xxx"
 * }</pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * apex:
 *   redis:
 *     key-prefix: ""   # 默认无前缀，向后兼容
 *     # key-prefix: "v2:"  # 格式变更时递增
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.0.0
 */
@Component
public class RedisKeyPrefix {

    @Value("${apex.redis.key-prefix:}")
    private String prefix;

    /**
     * 为原始 Key 追加版本前缀。
     *
     * @param rawKey 原始 Redis Key（不含前缀）
     * @return 带版本前缀的完整 Key
     */
    public String wrap(String rawKey) {
        if (prefix == null || prefix.isEmpty()) {
            return rawKey;
        }
        return prefix + rawKey;
    }

    /**
     * 获取当前版本前缀。
     *
     * @return 版本前缀，为空时返回空字符串
     */
    public String getPrefix() {
        return prefix == null ? "" : prefix;
    }
}
