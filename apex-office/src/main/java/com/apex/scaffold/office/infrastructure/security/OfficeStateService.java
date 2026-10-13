package com.apex.scaffold.office.infrastructure.security;

import com.apex.scaffold.office.domain.OfficeState;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 办公平台 OAuth 状态管理服务
 *
 * <p>实现领域端口 {@link OfficeState}，使用响应式 Redis 存储 OAuth state，
 * 提供分布式环境下的一次性消费保证。
 *
 * <p>核心特性：
 * <ul>
 *   <li>防重放：state 只能被成功消费一次</li>
 *   <li>租户绑定：state 绑定租户、平台和回调地址</li>
 *   <li>自动过期：使用 Redis TTL 自动清理过期 state</li>
 *   <li>原子消费：使用 Lua 脚本实现比较并删除（CAS）操作</li>
 *   <li>多实例安全：分布式环境下只有一个实例能成功消费</li>
 * </ul>
 *
 * <p>安全机制：
 * <ul>
 *   <li>错误的租户、平台或回调地址不会删除 state</li>
 *   <li>消费失败不影响合法 state 的后续使用</li>
 *   <li>Base64 URL 安全编码，防止特殊字符注入</li>
 * </ul>
 *
 * <p>Redis key 结构：{@code office:state:{base64(tenantId)}:{base64(state)}}
 * <p>Redis value 结构：{@code {base64(platform)}:{base64(redirectUri)}}
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Service
@RequiredArgsConstructor
public class OfficeStateService implements OfficeState {
    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "office:state:";

    /**
     * Lua 脚本：原子比较并删除
     *
     * <p>只有当 key 存在且 value 完全匹配时才删除，返回 1；否则返回 0。
     * 确保错误的租户、平台或回调地址不会删除 state。
     */
    private static final RedisScript<Long> CONSUME = RedisScript.of(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """,
            Long.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public Mono<String> issue(String tenantId, String platform, String redirectUri, Duration ttl) {
        return Mono.defer(() -> {
            if (invalid(tenantId)
                    || invalid(platform)
                    || redirectUri == null
                    || ttl == null
                    || ttl.compareTo(Duration.ofMillis(1)) < 0) {
                return Mono.error(new IllegalArgumentException("授权状态参数无效"));
            }
            String state = UUID.randomUUID().toString();
            return redisTemplate
                    .opsForValue()
                    .setIfAbsent(key(tenantId, state), binding(platform, redirectUri), ttl)
                    .flatMap(saved -> Boolean.TRUE.equals(saved)
                            ? Mono.just(state)
                            : Mono.error(new IllegalStateException("授权状态未能保存")));
        });
    }

    @Override
    public Mono<Boolean> consume(String state, String tenantId, String platform, String redirectUri) {
        return Mono.defer(() -> {
            if (invalid(state) || invalid(tenantId) || invalid(platform) || redirectUri == null) {
                return Mono.just(false);
            }
            return redisTemplate
                    .execute(CONSUME, List.of(key(tenantId, state)), List.of(binding(platform, redirectUri)))
                    .next()
                    .map(result -> result == 1L)
                    .defaultIfEmpty(false);
        });
    }

    /**
     * 校验字符串是否无效
     *
     * @param value 待校验字符串
     * @return true-无效（null 或空白），false-有效
     */
    private boolean invalid(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 生成 Redis key
     *
     * <p>格式：{@code office:state:{base64(tenantId)}:{base64(state)}}
     *
     * @param tenantId 租户标识
     * @param state 状态值
     * @return Redis key
     */
    private String key(String tenantId, String state) {
        return KEY_PREFIX + encode(tenantId) + ":" + encode(state);
    }

    /**
     * 生成绑定值
     *
     * <p>格式：{@code {base64(platform)}:{base64(redirectUri)}}
     *
     * @param platform 平台类型
     * @param redirectUri 回调地址
     * @return 绑定值
     */
    private String binding(String platform, String redirectUri) {
        return encode(platform) + ":" + encode(redirectUri);
    }

    /**
     * Base64 URL 安全编码
     *
     * <p>使用 URL 安全字符集且不填充，防止特殊字符注入 Redis key。
     *
     * @param value 原始字符串
     * @return Base64 编码字符串
     */
    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
