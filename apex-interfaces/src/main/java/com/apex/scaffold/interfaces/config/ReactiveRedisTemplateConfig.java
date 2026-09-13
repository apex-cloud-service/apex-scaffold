package com.apex.scaffold.interfaces.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 响应式 Redis 模板配置。
 *
 * <p>Spring Boot 自动配置仅提供 {@code ReactiveRedisTemplate<Object, Object>} 与
 * {@code ReactiveStringRedisTemplate}，而业务组件（如 {@link com.apex.scaffold.interfaces.exception.DegradeFallbackService}）
 * 需要按 {@code String} 键 + {@code Object} 值注入响应式模板。此处补充
 * {@code ReactiveRedisTemplate<String, Object>}，键使用 UTF-8 字符串序列化，
 * 值使用 JSON 序列化，保证降级快照的读写对称。</p>
 *
 * <p>{@code it} profile 下由 {@code ItProfileMockRedisConfiguration} 提供
 * mock 模板，本配置类不参与，避免测试环境依赖真实连接工厂。</p>
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>提供 {@code ReactiveRedisTemplate<String, Object>} Bean，键采用 UTF-8 字符串序列化，值采用 JSON 序列化</li>
 *   <li>支持 Hash 结构的键值同样使用 String + JSON 序列化策略，保证读写对称</li>
 *   <li>通过 {@code @Profile("!it")} 在集成测试环境下自动排除，避免依赖真实 Redis 连接工厂</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see ReactiveRedisTemplate
 * @see RedisSerializationContext
 */
@Configuration
@Profile("!it")
public class ReactiveRedisTemplateConfig {

    /**
     * 创建 {@code ReactiveRedisTemplate<String, Object>} 模板。
     *
     * @param factory 响应式 Redis 连接工厂
     * @return 按 String 键 + JSON 值序列化的响应式模板
     */
    @Bean
    ReactiveRedisTemplate<String, Object> stringObjectReactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        RedisSerializationContext<String, Object> serializationContext =
                RedisSerializationContext.<String, Object>newSerializationContext()
                        .key(StringRedisSerializer.UTF_8)
                        .value(new GenericJackson2JsonRedisSerializer())
                        .hashKey(StringRedisSerializer.UTF_8)
                        .hashValue(new GenericJackson2JsonRedisSerializer())
                        .build();
        return new ReactiveRedisTemplate<>(factory, serializationContext);
    }
}
