package com.apex.scaffold.interfaces.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.annotation.DataSource;
import com.apex.scaffold.common.context.DataSourceContext;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 数据源动态切换切面测试。
 *
 * <p>通过 {@link AspectJProxyFactory} 加载真实 {@link DataSourceSwitchAspect}，
 * 验证方法级/类级注解的切换、响应式（Mono）适配、空白数据源跳过与切换埋点。
 *
 * @author Apex Team
 */
@DisplayName("DataSourceSwitchAspect 测试")
class DataSourceSwitchAspectTest {

    private SimpleMeterRegistry registry;
    private DataSourceSwitchAspect aspect;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = new SimpleMeterRegistry();
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        aspect = new DataSourceSwitchAspect(provider);
    }

    @AfterEach
    void tearDown() {
        DataSourceContext.clear();
        DynamicDataSourceContextHolder.clear();
    }

    /** 方法级注解探针服务 */
    static class ProbeService {

        @DataSource("slave")
        public String probeSync() {
            return DynamicDataSourceContextHolder.peek();
        }

        @DataSource("slave")
        public Mono<String> probeMono() {
            // 延迟到订阅期读取切换结果：doFirst 在订阅时执行，
            // 响应式方法体在装配期执行（此时切换尚未发生）
            return Mono.defer(() -> Mono.just(DynamicDataSourceContextHolder.peek()));
        }

        @DataSource("")
        public String blankDataSource() {
            return DynamicDataSourceContextHolder.peek();
        }
    }

    /** 类级注解 + 方法级覆盖探针服务 */
    @DataSource("report")
    static class ClassLevelService {

        public String run() {
            return DynamicDataSourceContextHolder.peek();
        }

        @DataSource("master")
        public String overrideWithMethodLevel() {
            return DynamicDataSourceContextHolder.peek();
        }
    }

    /**
     * 用 AspectJProxyFactory 包装目标对象并加载切面。
     */
    private <T> T proxy(Object target, Class<T> type) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    @Nested
    @DisplayName("方法级切换")
    class MethodLevelTest {

        @Test
        void shouldSwitchDuringSyncExecutionAndRestoreAfter() {
            ProbeService proxy = proxy(new ProbeService(), ProbeService.class);

            assertThat(proxy.probeSync()).isEqualTo("slave");
            // 方法结束后自动释放
            assertThat(DynamicDataSourceContextHolder.peek()).isNull();
            assertThat(DataSourceContext.current()).isEmpty();
        }

        @Test
        void shouldSwitchDuringReactiveExecutionAndRestoreOnTermination() {
            ProbeService proxy = proxy(new ProbeService(), ProbeService.class);

            StepVerifier.create(proxy.probeMono()).expectNext("slave").verifyComplete();
            assertThat(DynamicDataSourceContextHolder.peek()).isNull();
            assertThat(DataSourceContext.current()).isEmpty();
        }

        @Test
        void shouldRecordSwitchMetric() {
            ProbeService proxy = proxy(new ProbeService(), ProbeService.class);

            proxy.probeSync();

            assertThat(registry.get("apex.datasource.switch.count").tag("target", "slave").counter().count())
                    .isEqualTo(1);
        }

        @Test
        void shouldSkipSwitchForBlankDataSourceName() {
            ProbeService proxy = proxy(new ProbeService(), ProbeService.class);

            assertThat(proxy.blankDataSource()).isNull();
            assertThat(registry.find("apex.datasource.switch.count").counters()).isEmpty();
        }
    }

    @Nested
    @DisplayName("类级注解与优先级")
    class ClassAndPrecedenceTest {

        @Test
        void shouldApplyClassLevelAnnotation() {
            ClassLevelService proxy = proxy(new ClassLevelService(), ClassLevelService.class);

            assertThat(proxy.run()).isEqualTo("report");
            assertThat(DynamicDataSourceContextHolder.peek()).isNull();
        }

        @Test
        void shouldPreferMethodLevelOverClassLevel() {
            ClassLevelService proxy = proxy(new ClassLevelService(), ClassLevelService.class);

            assertThat(proxy.overrideWithMethodLevel()).isEqualTo("master");
            assertThat(DynamicDataSourceContextHolder.peek()).isNull();
        }
    }
}
