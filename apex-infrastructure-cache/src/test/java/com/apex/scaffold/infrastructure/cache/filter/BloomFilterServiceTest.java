package com.apex.scaffold.infrastructure.cache.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BloomFilter 服务单元测试
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@DisplayName("BloomFilter 缓存穿透防护测试")
class BloomFilterServiceTest {

    private BloomFilterService bloomFilterService;

    @BeforeEach
    void setUp() {
        bloomFilterService = new BloomFilterService();
    }

    @Test
    @DisplayName("应正确判断已添加的 key 可能存在")
    void shouldReturnTrueForAddedKeys() {
        // Given
        String namespace = "user";
        String key = "user:123";

        // When
        bloomFilterService.put(namespace, key);

        // Then
        assertThat(bloomFilterService.mightContain(namespace, key)).isTrue();
    }

    @Test
    @DisplayName("应正确判断未添加的 key 一定不存在")
    void shouldReturnFalseForNonExistentKeys() {
        // Given
        String namespace = "user";
        bloomFilterService.createOrRebuild(namespace, 1000, 0.01);

        // When
        boolean result = bloomFilterService.mightContain(namespace, "nonexistent:999");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("应支持批量添加 key")
    void shouldSupportBatchPut() {
        // Given
        String namespace = "order";
        List<String> keys = Arrays.asList("order:1", "order:2", "order:3", "order:4", "order:5");

        // When
        bloomFilterService.putAll(namespace, keys);

        // Then
        for (String key : keys) {
            assertThat(bloomFilterService.mightContain(namespace, key))
                    .as("Key %s should be present", key)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("应支持多个独立的命名空间")
    void shouldSupportMultipleNamespaces() {
        // Given
        bloomFilterService.put("user", "user:1");
        bloomFilterService.put("order", "order:1");

        // When & Then
        assertThat(bloomFilterService.mightContain("user", "user:1")).isTrue();
        assertThat(bloomFilterService.mightContain("order", "order:1")).isTrue();
        assertThat(bloomFilterService.mightContain("user", "order:1")).isFalse();
        assertThat(bloomFilterService.mightContain("order", "user:1")).isFalse();
    }

    @Test
    @DisplayName("应正确重建 BloomFilter")
    void shouldRebuildFilter() {
        // Given
        String namespace = "product";
        bloomFilterService.put(namespace, "product:1");
        assertThat(bloomFilterService.mightContain(namespace, "product:1")).isTrue();

        // When: 重建过滤器（会清空已有数据）
        bloomFilterService.createOrRebuild(namespace, 1000, 0.01);

        // Then: 旧数据不再存在
        assertThat(bloomFilterService.mightContain(namespace, "product:1")).isFalse();

        // And: 重新添加后可以检测到
        bloomFilterService.put(namespace, "product:1");
        assertThat(bloomFilterService.mightContain(namespace, "product:1")).isTrue();
    }

    @Test
    @DisplayName("应正确移除命名空间")
    void shouldRemoveNamespace() {
        // Given
        String namespace = "temp";
        bloomFilterService.put(namespace, "temp:1");
        assertThat(bloomFilterService.getNamespaceCount()).isGreaterThan(0);

        // When
        bloomFilterService.remove(namespace);

        // Then
        assertThat(bloomFilterService.mightContain(namespace, "temp:1"))
                .as("After removal, uninitialized namespace should return true (conservative)")
                .isTrue();
    }

    @Test
    @DisplayName("大规模数据测试：验证误判率在可接受范围内")
    void shouldMaintainAcceptableFalsePositiveRate() {
        // Given
        String namespace = "large";
        int expectedSize = 10000;
        double targetFpp = 0.01; // 1% 误判率

        bloomFilterService.createOrRebuild(namespace, expectedSize, targetFpp);

        // 添加 10000 个已知 key
        for (int i = 0; i < expectedSize; i++) {
            bloomFilterService.put(namespace, "key:" + i);
        }

        // When: 测试 10000 个不存在的 key
        int falsePositives = 0;
        int testSize = 10000;
        for (int i = expectedSize; i < expectedSize + testSize; i++) {
            if (bloomFilterService.mightContain(namespace, "key:" + i)) {
                falsePositives++;
            }
        }

        // Then: 误判率应低于 2%（理论值 1%，实际允许波动）
        double actualFpp = (double) falsePositives / testSize;
        assertThat(actualFpp)
                .as("False positive rate should be within acceptable range")
                .isLessThan(0.02);
    }

    @Test
    @DisplayName("未初始化的命名空间应保守返回 true")
    void shouldReturnTrueForUninitializedNamespace() {
        // Given: 未初始化任何过滤器

        // When & Then: 保守策略，避免误拦截
        assertThat(bloomFilterService.mightContain("uninitialized", "any-key")).isTrue();
    }
}
