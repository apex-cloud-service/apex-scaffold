package com.apex.scaffold.infrastructure.cache.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * BloomFilter 定时重建任务测试
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BloomFilter 定时重建任务测试")
class BloomFilterRebuildTaskTest {

    @Mock
    private com.apex.scaffold.infrastructure.cache.loader.BloomFilterCacheWarmer bloomFilterCacheWarmer;

    @Mock
    private com.apex.scaffold.infrastructure.cache.metrics.BloomFilterRebuildMetrics rebuildMetrics;

    @InjectMocks
    private BloomFilterRebuildTask bloomFilterRebuildTask;

    @Test
    @DisplayName("定时重建应成功调用预热器")
    void shouldCallWarmerOnScheduledRebuild() throws Exception {
        // Given
        doNothing().when(bloomFilterCacheWarmer).run(any());

        // When
        bloomFilterRebuildTask.rebuildBloomFilters();

        // Then
        verify(bloomFilterCacheWarmer, times(1)).run(null);
    }

    @Test
    @DisplayName("预热器异常不应导致任务失败")
    void shouldHandleWarmerExceptionGracefully() throws Exception {
        // Given
        doThrow(new RuntimeException("模拟预热失败")).when(bloomFilterCacheWarmer).run(any());

        // When & Then: 不应抛出异常
        bloomFilterRebuildTask.rebuildBloomFilters();

        verify(bloomFilterCacheWarmer, times(1)).run(null);
    }

    @Test
    @DisplayName("手动重建应返回成功状态")
    void shouldReturnTrueOnManualRebuildSuccess() throws Exception {
        // Given
        doNothing().when(bloomFilterCacheWarmer).run(any());

        // When
        boolean result = bloomFilterRebuildTask.manualRebuild();

        // Then
        assertThat(result).isTrue();
        verify(bloomFilterCacheWarmer, times(1)).run(null);
    }

    @Test
    @DisplayName("手动重建失败应返回失败状态")
    void shouldReturnFalseOnManualRebuildFailure() throws Exception {
        // Given
        doThrow(new RuntimeException("模拟预热失败")).when(bloomFilterCacheWarmer).run(any());

        // When
        boolean result = bloomFilterRebuildTask.manualRebuild();

        // Then
        assertThat(result).isFalse();
    }
}
