package com.apex.scaffold.interfaces.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import brave.Tracing;
import brave.handler.SpanHandler;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.ObjectProvider;

/**
 * TraceConfig 链路追踪配置单元测试。
 *
 * <p>验证 zipkinSpanHandler 在所有 Profile 下均可创建，不设置 Profile 限制。
 *
 * @author Apex Team
 * @since 1.0.0
 */
class TraceConfigTest {

    private TraceConfig traceConfig;

    /**
     * 执行对应的业务处理；该操作由 {@code setUp} 定义。
     *
     * @param testInfo 方法输入 {@code testInfo}，类型为 {@code TestInfo}
     * @throws Exception 当该操作无法按约定完成时
     */
    @BeforeEach
    void setUp(TestInfo testInfo) throws Exception {
        traceConfig = new TraceConfig();
        setField(traceConfig, "serviceName", "apex-service");
        setField(traceConfig, "samplingRate", 1.0f);
        setField(traceConfig, "zipkinUrl", "http://localhost:9411/api/v2/spans");
    }

    /**
     * 执行对应的业务处理；该操作由 {@code tearDown} 定义。
     *
     */
    @AfterEach
    void tearDown() {
        if (traceConfig != null) {
            traceConfig.destroy();
        }
    }

    /**
     * 验证 {@code shouldCreateZipkinSpanHandlerInAllProfiles} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("zipkinSpanHandler() 应在所有环境下创建，不再限制 @Profile(\"!dev\")")
    void shouldCreateZipkinSpanHandlerInAllProfiles() {
        SpanHandler handler = traceConfig.zipkinSpanHandler();
        assertNotNull(handler, "zipkinSpanHandler 不应为 null，应在所有 Profile 下创建");
    }

    /**
     * 验证 {@code shouldNotHaveProfileAnnotation} 场景下的预期行为。
     *
     * @throws NoSuchMethodException 当该操作无法按约定完成时
     */
    @Test
    @DisplayName("zipkinSpanHandler 方法不应有 @Profile 注解")
    void shouldNotHaveProfileAnnotation() throws NoSuchMethodException {
        var method = TraceConfig.class.getMethod("zipkinSpanHandler");
        var profileAnnotation = method.getAnnotation(org.springframework.context.annotation.Profile.class);
        assertTrue(profileAnnotation == null, "zipkinSpanHandler() 不应包含 @Profile 注解，应创建在所有环境下");
    }

    /**
     * 验证 {@code shouldUseZipkinSpanHandlerWhenAvailable} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("tracing() 应通过 ObjectProvider 注入 zipkinSpanHandler")
    void shouldUseZipkinSpanHandlerWhenAvailable() {
        SpanHandler zipkinHandler = traceConfig.zipkinSpanHandler();
        ObjectProvider<SpanHandler> provider = new ObjectProvider<>() {
            /**
             * {@inheritDoc}
             *
             * @return SpanHandler 类型的实例
             */
            @Override
            public SpanHandler getObject() {
                return zipkinHandler;
            }

            /**
             * {@inheritDoc}
             *
             * @param args 方法输入 {@code args}，类型为 {@code Object[]}
             * @return SpanHandler 类型的实例
             */
            @Override
            public SpanHandler getObject(Object... args) {
                return zipkinHandler;
            }

            /**
             * {@inheritDoc}
             *
             * @return SpanHandler 类型的实例
             */
            @Override
            public SpanHandler getIfAvailable() {
                return zipkinHandler;
            }

            /**
             * {@inheritDoc}
             *
             * @return SpanHandler 类型的实例
             */
            @Override
            public SpanHandler getIfUnique() {
                return zipkinHandler;
            }
        };

        Tracing tracing = traceConfig.tracing(provider);
        assertNotNull(tracing, "Tracing 实例不应为 null");
        assertSame(traceConfig.tracer(tracing), tracing.tracer(), "tracer() 应返回 Tracing 中的 Tracer");
    }

    /**
     * 验证 {@code shouldCreateTracingWithoutZipkinHandler} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("tracing() 在 zipkinHandler 不可用时仍应正常创建")
    void shouldCreateTracingWithoutZipkinHandler() {
        ObjectProvider<SpanHandler> emptyProvider = new ObjectProvider<>() {
            /**
             * {@inheritDoc}
             *
             * @return SpanHandler 类型的实例
             */
            @Override
            public SpanHandler getObject() {
                throw new UnsupportedOperationException();
            }

            /**
             * {@inheritDoc}
             *
             * @param args 方法输入 {@code args}，类型为 {@code Object[]}
             * @return SpanHandler 类型的实例
             */
            @Override
            public SpanHandler getObject(Object... args) {
                throw new UnsupportedOperationException();
            }

            /**
             * {@inheritDoc}
             *
             * @return SpanHandler 类型的实例
             */
            @Override
            public SpanHandler getIfAvailable() {
                return null;
            }

            /**
             * {@inheritDoc}
             *
             * @return SpanHandler 类型的实例
             */
            @Override
            public SpanHandler getIfUnique() {
                return null;
            }
        };

        Tracing tracing = traceConfig.tracing(emptyProvider);
        assertNotNull(tracing, "即使 Zipkin 不可用，Tracing 实例也不应为 null");
    }

    /**
     * 验证 {@code shouldCreateZipkinSpanHandlerWithoutException} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("zipkinSpanHandler 创建过程不应抛出异常")
    void shouldCreateZipkinSpanHandlerWithoutException() {
        assertDoesNotThrow(() -> traceConfig.zipkinSpanHandler(), "zipkinSpanHandler() 创建过程不应抛出异常");
    }

    /**
     * 执行对应的业务处理；该操作由 {@code setField} 定义。
     *
     * @param target 方法输入 {@code target}，类型为 {@code Object}
     * @param fieldName 方法输入 {@code fieldName}，类型为 {@code String}
     * @param value 方法输入 {@code value}，类型为 {@code Object}
     * @throws Exception 当该操作无法按约定完成时
     */
    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = TraceConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
