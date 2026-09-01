package com.apex.scaffold.common.util.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 雪花算法分布式 ID 生成器单元测试
 *
 * @author Apex Team
 */
class SnowflakeIdGeneratorTest {

    @Nested
    @DisplayName("基本生成")
    class BasicTests {

        @Test
        @DisplayName("连续生成 ID 单调递增且互不重复")
        void shouldGenerateUniqueIncreasingIds() {
            SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
            long prev = generator.nextId();
            for (int i = 0; i < 1000; i++) {
                long next = generator.nextId();
                assertThat(next).isGreaterThan(prev);
                prev = next;
            }
        }

        @Test
        @DisplayName("同一毫秒内序列应能生成 4096 个以上不重复 ID")
        void shouldGenerateBeyondSequenceMask() {
            SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0, 0);
            Set<Long> ids = new HashSet<>();
            for (int i = 0; i < 5000; i++) {
                ids.add(generator.nextId());
            }
            assertThat(ids).hasSize(5000);
        }

        @Test
        @DisplayName("非法数据中心/机器 ID 应抛异常")
        void shouldRejectInvalidIds() {
            assertThatThrownBy(() -> new SnowflakeIdGenerator(32, 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SnowflakeIdGenerator(1, 32)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SnowflakeIdGenerator(-1, 1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("并发唯一性")
    class ConcurrencyTests {

        @Test
        @DisplayName("多线程并发生成 ID 应全部唯一")
        void shouldBeUniqueUnderConcurrency() throws Exception {
            int threads = 8;
            int perThread = 10000;
            SnowflakeIdGenerator generator = new SnowflakeIdGenerator(2, 3);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            Set<Long> ids = ConcurrentHashMap.newKeySet();
            CountDownLatch latch = new CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        for (int i = 0; i < perThread; i++) {
                            ids.add(generator.nextId());
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(30, TimeUnit.SECONDS);
            pool.shutdown();
            assertThat(ids).hasSize(threads * perThread);
        }
    }
}
