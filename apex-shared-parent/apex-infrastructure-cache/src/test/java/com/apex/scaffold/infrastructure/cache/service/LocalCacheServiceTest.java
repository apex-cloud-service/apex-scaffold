package com.apex.scaffold.infrastructure.cache.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

/**
 * 使用真实 Caffeine 验证本地缓存生命周期。
 *
 * @author Apex Team
 * @since 1.3.0
 */
class LocalCacheServiceTest {

    @Test
    void readsIgnoreExpiredEntryAndBatchReplacementUsesDefaultLifetime() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        LocalCacheService service = new LocalCacheService(manager, new ObjectMapper());

        assertThat(service.set("key", "expired", Duration.ofSeconds(-1)).block())
                .isTrue();
        assertThat(service.set("other", "fresh", null).block()).isTrue();
        assertThat(service.get("key", String.class).block()).isNull();
        assertThat(service.getBatch(List.of("key", "other"), String.class).block())
                .containsExactly("fresh");

        assertThat(service.set("replace", "expired", Duration.ofSeconds(-1)).block())
                .isTrue();
        assertThat(service.setBatch(Map.of("replace", "replacement")).block()).isTrue();
        assertThat(service.get("replace", String.class).block()).isEqualTo("replacement");
    }

    @Test
    void singleWriteReplacesExpiryAndDeleteAndClearRemoveEntries() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setAsyncCacheMode(true);
        LocalCacheService service = new LocalCacheService(manager, new ObjectMapper());

        assertThat(service.set("key", 1, Duration.ofSeconds(-1)).block()).isTrue();
        assertThat(service.set("key", 2, Duration.ofMinutes(1)).block()).isTrue();
        assertThat(service.get("key", Integer.class).block()).isEqualTo(2);
        assertThat(service.delete("key").block()).isEqualTo(1L);
        assertThat(service.get("key", Integer.class).block()).isNull();
        assertThat(service.set("other", 3, Duration.ofMinutes(1)).block()).isTrue();
        service.clear().block();
        assertThat(service.get("other", Integer.class).block()).isNull();
    }

    @Test
    void customTtlEntriesCountTowardNativeCacheCapacity() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder().maximumSize(1).executor(Runnable::run));
        LocalCacheService service = new LocalCacheService(manager, new ObjectMapper());

        assertThat(service.set("first", "value", Duration.ofMinutes(1)).block()).isTrue();
        assertThat(service.set("second", "value", Duration.ofMinutes(1)).block())
                .isTrue();
        CaffeineCache cache = (CaffeineCache) manager.getCache("localCache");
        assertThat(cache).isNotNull();
        cache.getNativeCache().cleanUp();
        assertThat(cache.getNativeCache().estimatedSize()).isEqualTo(1);
        assertThat(service.getBatch(List.of("first", "second"), String.class).block())
                .containsExactly("value");
    }
}
