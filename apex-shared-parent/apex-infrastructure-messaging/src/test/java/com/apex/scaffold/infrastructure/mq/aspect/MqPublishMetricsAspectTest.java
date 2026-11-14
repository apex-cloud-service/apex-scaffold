package com.apex.scaffold.infrastructure.mq.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link MqPublishMetricsAspect} 单元测试。
 *
 * <p>验证：发送成功/失败计数（按 mode 与 result 标签）、发送耗时 Timer 记录、
 * 方法名到发送模式的映射。</p>
 *
 * @author Apex Team
 */
class MqPublishMetricsAspectTest {

    private SimpleMeterRegistry registry;
    private MqPublishMetricsAspect aspect;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        aspect = new MqPublishMetricsAspect(registry);
    }

    private ProceedingJoinPoint joinPoint(String methodName, Object result) throws Throwable {
        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn(methodName);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    @Test
    void shouldCountSyncSuccessAndRecordLatency() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("sendSync", "ok");

        Object result = aspect.around(joinPoint);

        assertThat(result).isEqualTo("ok");
        assertThat(registry.get("apex.mq.publish.count")
                        .tag("mode", "sync")
                        .tag("result", "success")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("apex.mq.publish.latency")
                        .tag("mode", "sync")
                        .timer()
                        .count())
                .isEqualTo(1);
        assertThat(registry.find("apex.mq.publish.count")
                        .tag("result", "failed")
                        .counters())
                .isEmpty();
    }

    @Test
    void shouldCountAsyncFailureAndStillRecordLatency() throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint("sendAsync", null);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("broker down"));

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("broker down");

        assertThat(registry.get("apex.mq.publish.count")
                        .tag("mode", "async")
                        .tag("result", "failed")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("apex.mq.publish.latency")
                        .tag("mode", "async")
                        .timer()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void shouldResolveModesByMethodName() throws Throwable {
        String[] methods = {"sendOneWay", "sendDelay", "sendWithHeaders", "unknownMethod"};
        String[] modes = {"oneway", "delay", "headers", "unknown"};
        for (int i = 0; i < methods.length; i++) {
            ProceedingJoinPoint joinPoint = joinPoint(methods[i], null);
            aspect.around(joinPoint);
            assertThat(registry.get("apex.mq.publish.count")
                            .tag("mode", modes[i])
                            .tag("result", "success")
                            .counter()
                            .count())
                    .as(methods[i])
                    .isEqualTo(1);
        }
    }
}
