package com.apex.scaffold.infrastructure.cache.service;

import com.apex.scaffold.domain.gateway.AuthorizationCache;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 将授权缓存契约适配到现有多级缓存。
 *
 * @author Apex Scaffold Team
 */
@Component
@RequiredArgsConstructor
public class AuthorizationCacheAdapter implements AuthorizationCache {
    private final MultiLevelCacheService cache;

    @Override
    public <T> Mono<T> get(String key, Class<T> type) {
        return cache.get(key, type);
    }

    @Override
    public <T> Mono<Boolean> set(String key, T value, Duration ttl) {
        return cache.set(key, value, ttl);
    }
}
