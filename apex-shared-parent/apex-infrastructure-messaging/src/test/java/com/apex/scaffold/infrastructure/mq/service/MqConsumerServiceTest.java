package com.apex.scaffold.infrastructure.mq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

/**
 * {@link MqConsumerService.ProcessingResult} 的单元测试类
 *
 * <p>测试消息处理结果的四种工厂方法及状态判断。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("MqConsumerService 单元测试")
@ExtendWith(MockitoExtension.class)
class MqConsumerServiceTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> bucket;

    private MqConsumerService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-a");
        service = new MqConsumerService(redissonClient, 30L);
        lenient().doReturn(bucket).when(redissonClient).getBucket(anyString(), eq(StringCodec.INSTANCE));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("幂等键应包含租户、稳定消费组与事件ID")
    void shouldScopeIdempotentKeyByTenantConsumerAndEvent() {
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
        when(bucket.compareAndSet(anyString(), eq("SUCCESS"))).thenReturn(true);

        service.processMessageWithResult("consumer-a", "msg-1", "payload", message -> true);

        verify(redissonClient)
                .getBucket(eq("mq:idempotent:dGVuYW50LWE:Y29uc3VtZXItYQ:bXNnLTE"), eq(StringCodec.INSTANCE));
    }

    @Test
    @DisplayName("同一事件的不同消费者必须使用独立幂等键")
    void shouldNotSuppressAnotherConsumerOfSameEvent() {
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
        when(bucket.compareAndSet(anyString(), eq("SUCCESS"))).thenReturn(true);

        assertThat(service.processMessage("consumer-a", "msg-1", "payload", message -> true))
                .isTrue();
        assertThat(service.processMessage("consumer-b", "msg-1", "payload", message -> true))
                .isTrue();

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(redissonClient, times(2)).getBucket(keys.capture(), eq(StringCodec.INSTANCE));
        assertThat(keys.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("各维度含冒号、编码字符或Unicode时不得碰撞")
    void shouldEncodeScopeDimensionsWithoutDelimiterCollisions() {
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
        when(bucket.compareAndSet(anyString(), eq("SUCCESS"))).thenReturn(true);

        TenantContext.setTenantId("租户:a");
        service.processMessageWithResult("b", "c", "payload", message -> true);
        TenantContext.setTenantId("租户");
        service.processMessageWithResult("a:b", "c", "payload", message -> true);
        service.processMessageWithResult("a", "b:c", "payload", message -> true);
        service.processMessageWithResult("a", "b%3Ac", "payload", message -> true);
        service.processMessageWithResult("a", "b：c", "payload", message -> true);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(redissonClient, times(5)).getBucket(keys.capture(), eq(StringCodec.INSTANCE));
        assertThat(keys.getAllValues()).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("非法消费组应在访问Redis前被拒绝")
    void shouldRejectInvalidConsumerBeforeRedisAccess(String consumerGroup) {
        assertThatThrownBy(() -> service.processMessage(consumerGroup, "msg-1", "payload", message -> true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("消费组不能为空");

        verifyNoInteractions(redissonClient);
    }

    @Test
    @DisplayName("缺少租户上下文应拒绝访问幂等存储")
    void shouldRejectProcessingWithoutTenantContext() {
        TenantContext.clear();

        assertThatThrownBy(() -> service.processMessageWithResult("consumer-a", "msg-1", "payload", message -> true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("租户上下文不存在");

        verifyNoInteractions(redissonClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("空白租户应在访问Redis前被拒绝")
    void shouldRejectBlankTenantBeforeRedisAccess(String tenantId) {
        TenantContext.setTenantId(tenantId);

        assertThatThrownBy(() -> service.processMessageWithResult("consumer-a", "msg-1", "payload", message -> true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("租户上下文不能为空白");

        verifyNoInteractions(redissonClient);
    }

    @Test
    @DisplayName("首次处理应获得令牌并确认成功")
    void shouldClaimAndConfirmMessage() {
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
        when(bucket.compareAndSet(anyString(), eq("SUCCESS"))).thenReturn(true);

        MqConsumerService.ProcessingResult<String> result =
                service.processMessageWithResult("consumer-a", "msg-1", "payload", message -> true);

        assertThat(result.isSuccess()).isTrue();
        verify(bucket).setIfAbsent(anyString(), eq(Duration.ofMinutes(30)));
        verify(bucket).compareAndSet(anyString(), eq("SUCCESS"));
        verify(bucket).expire(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("处理返回false应释放当前实例令牌")
    void shouldReleaseOwnTokenWhenHandlerFails() {
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);

        MqConsumerService.ProcessingResult<String> result =
                service.processMessageWithResult("consumer-a", "msg-1", "payload", message -> false);

        assertThat(result.isSuccess()).isFalse();
        // H2 修复：业务失败同样 CAS 为 FAILED，保留幂等键到 TTL，避免瞬态失败重入。
        verify(bucket).compareAndSet(anyString(), eq("FAILED"));
        verify(bucket).expire(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("处理异常应释放当前实例令牌")
    void shouldReleaseOwnTokenWhenHandlerThrows() {
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);

        MqConsumerService.ProcessingResult<String> result =
                service.processMessageWithResult("consumer-a", "msg-1", "payload", message -> {
                    throw new IllegalStateException("temporary failure");
                });

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("temporary failure");
        // H2 修复：异常路径不把幂等键 CAS 为 null（否则重复投递将再次执行业务），
        // 而是 CAS 为 FAILED，直到 TTL；仅明确瞬态异常在后续可特例释放。
        verify(bucket).compareAndSet(anyString(), eq("FAILED"));
        verify(bucket).expire(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("已成功消息应跳过处理器")
    void shouldSkipSuccessfulDuplicate() {
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(false);
        when(bucket.get()).thenReturn("SUCCESS");

        MqConsumerService.ProcessingResult<String> result =
                service.processMessageWithResult("consumer-a", "msg-1", "payload", message -> {
                    throw new AssertionError("duplicate handler must not run");
                });

        assertThat(result.isDuplicate()).isTrue();
        verify(bucket, never()).compareAndSet(anyString(), anyString());
    }

    @Test
    @DisplayName("其他实例处理中应返回失败")
    void shouldRejectConcurrentProcessing() {
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(false);
        when(bucket.get()).thenReturn("PROCESSING:other");

        MqConsumerService.ProcessingResult<String> result =
                service.processMessageWithResult("consumer-a", "msg-1", "payload", message -> true);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("消息正在处理中");
    }

    @Test
    @DisplayName("成功处理但令牌确认失败应返回错误")
    void shouldFailWhenOwnershipConfirmationFails() {
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
        when(bucket.compareAndSet(anyString(), eq("SUCCESS"))).thenReturn(false);

        MqConsumerService.ProcessingResult<String> result =
                service.processMessageWithResult("consumer-a", "msg-1", "payload", message -> true);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("消息确认失败");
        verify(bucket, never()).expire(any(Duration.class));
    }

    @Test
    @DisplayName("非法TTL应使用默认值")
    void shouldUseDefaultTtlWhenConfiguredTtlIsInvalid() {
        MqConsumerService serviceWithInvalidTtl = new MqConsumerService(redissonClient, 0L);
        when(bucket.setIfAbsent(anyString(), any(Duration.class))).thenReturn(true);
        when(bucket.compareAndSet(anyString(), eq("SUCCESS"))).thenReturn(true);

        serviceWithInvalidTtl.processMessageWithResult("consumer-a", "msg-1", "payload", message -> true);

        verify(bucket).setIfAbsent(anyString(), eq(Duration.ofMinutes(30)));
        verify(bucket).expire(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("缺少消息ID应拒绝处理")
    void shouldRejectMissingMessageId() {
        MqConsumerService.ProcessingResult<String> result =
                service.processMessageWithResult("consumer-a", " ", "payload", message -> true);

        assertThat(result.isSuccess()).isFalse();
        verifyNoInteractions(redissonClient);
    }

    @Nested
    @DisplayName("ProcessingResult.success 方法")
    class SuccessTests {
        @Test
        @DisplayName("应创建包含正确 messageId 和 payload 的成功结果")
        void shouldCreateSuccessResult() {
            MqConsumerService.ProcessingResult<String> result =
                    MqConsumerService.ProcessingResult.success("msg-1", "payload-data");

            assertThat(result.getMessageId()).isEqualTo("msg-1");
            assertThat(result.getPayload()).isEqualTo("payload-data");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isDuplicate()).isFalse();
            assertThat(result.getErrorMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("ProcessingResult.failed 方法")
    class FailedTests {
        @Test
        @DisplayName("应创建包含失败原因的失败结果")
        void shouldCreateFailedResult() {
            MqConsumerService.ProcessingResult<Void> result =
                    MqConsumerService.ProcessingResult.failed("msg-1", "业务处理返回false");

            assertThat(result.getMessageId()).isEqualTo("msg-1");
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isDuplicate()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("业务处理返回false");
        }
    }

    @Nested
    @DisplayName("ProcessingResult.error 方法")
    class ErrorTests {
        @Test
        @DisplayName("应创建包含异常信息的错误结果")
        void shouldCreateErrorResult() {
            MqConsumerService.ProcessingResult<Void> result =
                    MqConsumerService.ProcessingResult.error("msg-1", "NullPointerException");

            assertThat(result.getMessageId()).isEqualTo("msg-1");
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isDuplicate()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("NullPointerException");
        }
    }

    @Nested
    @DisplayName("ProcessingResult.duplicate 方法")
    class DuplicateTests {
        @Test
        @DisplayName("应创建标记为重复消息的结果")
        void shouldCreateDuplicateResult() {
            MqConsumerService.ProcessingResult<Void> result = MqConsumerService.ProcessingResult.duplicate("msg-1");

            assertThat(result.getMessageId()).isEqualTo("msg-1");
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isDuplicate()).isTrue();
            assertThat(result.getErrorMessage()).isNull();
            assertThat(result.getPayload()).isNull();
        }
    }
}
