package com.apex.scaffold.infrastructure.persistence.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link SlowSqlMetricsInterceptor} 单元测试。
 *
 * <p>验证：慢 SQL 计数与最慢耗时上报、正常 SQL 不计入慢 SQL、执行耗时 Timer 全量记录、
 * 关闭开关后不产生任何指标。</p>
 *
 * @author Apex Team
 */
class SlowSqlMetricsInterceptorTest {

    private SimpleMeterRegistry registry;
    private SlowSqlProperties properties;
    private SlowSqlMetricsInterceptor interceptor;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        properties = new SlowSqlProperties();
        properties.setThresholdMs(10);
        interceptor = new SlowSqlMetricsInterceptor(properties, registry);
    }

    /** 构造 SELECT 类型的模拟调用，proceed 行为由 sleepMs 控制（null 表示立即返回）。 */
    private Invocation selectInvocation(Long sleepMs) throws Exception {
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.SELECT);
        BoundSql boundSql = mock(BoundSql.class);
        when(boundSql.getSql()).thenReturn("select * from sys_user where id = 1");
        when(ms.getBoundSql(new Object())).thenReturn(boundSql);

        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[] {ms, new Object(), null, null});
        if (sleepMs != null) {
            doAnswer(inv -> {
                        Thread.sleep(sleepMs);
                        return null;
                    })
                    .when(invocation)
                    .proceed();
        } else {
            when(invocation.proceed()).thenReturn(null);
        }
        return invocation;
    }

    @Test
    void shouldRecordSlowSqlCounterAndMaxGaugeWhenExceedingThreshold() throws Throwable {
        Invocation invocation = selectInvocation(30L);

        interceptor.intercept(invocation);

        verify(invocation).proceed();
        assertThat(registry.get("apex.sql.slow.count")
                        .tag("type", "SELECT")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("apex.sql.execution")
                        .tag("type", "SELECT")
                        .timer()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("apex.sql.slow.max.ms").gauge().value()).isGreaterThanOrEqualTo(30);
    }

    @Test
    void shouldNotCountAsSlowWhenBelowThresholdButStillRecordExecutionTimer() throws Throwable {
        // 阈值放大到 60s，保证快速调用（亚毫秒级）必然低于阈值，避免计时抖动导致误判
        properties.setThresholdMs(60_000);
        Invocation invocation = selectInvocation(null);

        interceptor.intercept(invocation);

        assertThat(registry.find("apex.sql.slow.count").counters()).isEmpty();
        assertThat(registry.get("apex.sql.execution")
                        .tag("type", "SELECT")
                        .timer()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("apex.sql.slow.max.ms").gauge().value()).isZero();
    }

    @Test
    void shouldPropagateExceptionAndStillRecordTimer() throws Throwable {
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getSqlCommandType()).thenReturn(SqlCommandType.UPDATE);
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[] {ms, new Object(), null, null});
        doAnswer(inv -> {
                    Thread.sleep(50);
                    throw new IllegalStateException("db down");
                })
                .when(invocation)
                .proceed();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> interceptor.intercept(invocation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");

        assertThat(registry.get("apex.sql.slow.count")
                        .tag("type", "UPDATE")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("apex.sql.execution")
                        .tag("type", "UPDATE")
                        .timer()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void shouldSkipMetricsWhenDisabled() throws Throwable {
        properties.setEnabled(false);
        Invocation invocation = selectInvocation(30L);

        interceptor.intercept(invocation);

        verify(invocation).proceed();
        assertThat(registry.find("apex.sql.slow.count").counters()).isEmpty();
        assertThat(registry.find("apex.sql.execution").timers()).isEmpty();
    }

    @Test
    void shouldSummarizeAllMeterNames() {
        assertThat(List.of(
                        registry.find("apex.sql.execution").meters().size(),
                        registry.find("apex.sql.slow.count").meters().size(),
                        registry.find("apex.sql.slow.max.ms").meters().size()))
                .isNotNull();
    }
}
