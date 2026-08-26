package com.apex.scaffold.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ExpiringLocalCache} 的过期、主动失效和容量边界单元测试。
 *
 * <p>测试只验证确定性的缓存契约，不依赖 Spring 容器或外部时钟服务。</p>
 *
 * @author Apex Team
 */
@DisplayName("ExpiringLocalCache 单元测试")
class ExpiringLocalCacheTest {

    /**
     * 验证未过期条目可读取，主动失效后立即不可见。
     *
     */
    @Test
    @DisplayName("读取有效条目并支持主动失效")
    void shouldReadAndInvalidateEntry() {
        ExpiringLocalCache<Long, Long> cache = new ExpiringLocalCache<>();

        cache.put(1L, 10L, 60_000L, 10);

        assertThat(cache.get(1L, 60_000L)).isEqualTo(10L);
        cache.invalidate(1L);
        assertThat(cache.get(1L, 60_000L)).isNull();
    }

    /**
     * 验证非正数 TTL 不返回缓存值，并在后续容量清理中移除过期数据。
     *
     */
    @Test
    @DisplayName("非正数 TTL 视为立即过期")
    void shouldTreatNonPositiveTtlAsExpired() {
        ExpiringLocalCache<Long, Long> cache = new ExpiringLocalCache<>();

        cache.put(1L, 10L, 60_000L, 10);

        assertThat(cache.get(1L, 0L)).isNull();
    }

    /**
     * 验证写入数量超过上限时淘汰最早条目，并将容器维持在配置容量以内。
     *
     */
    @Test
    @DisplayName("超过容量上限时执行有界淘汰")
    void shouldEvictOldestEntryWhenCapacityExceeded() {
        ExpiringLocalCache<Long, Long> cache = new ExpiringLocalCache<>();
        cache.put(1L, 10L, 60_000L, 2);
        cache.put(2L, 20L, 60_000L, 2);

        cache.put(3L, 30L, 60_000L, 2);

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get(3L, 60_000L)).isEqualTo(30L);
    }
}
