package com.apex.scaffold.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link ApexBlockingIoProperties} 配置绑定测试类。
 *
 * <p>验证 Spring Boot {@code @ConfigurationProperties} 能否正确将 YAML / 属性源
 * 绑定到 Java 对象，包括默认值基线对齐与自定义属性覆盖两项能力。
 * 本测试不启动真实 Web 容器与数据库，使用 {@code WebEnvironment=NONE}
 * 最小化上下文以获得快速执行速度。
 *
 * <h2>覆盖场景</h2>
 * <ol>
 *   <li>默认值场景：无任何外部覆盖时，六大专用池参数与 {@code application.yml}
 *       基线值完全一致；{@code snapshotRefreshMs} 默认为 3000 毫秒。</li>
 *   <li>覆盖场景：通过类级 {@code @TestPropertySource} 注入两条自定义属性，
 *       验证其能正确覆盖 YAML 默认值且不影响其他未覆盖字段。</li>
 * </ol>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
class ApexBlockingIoPropertiesTest {

    /**
     * 测试用公共基础配置类，仅用于激活 {@link ApexBlockingIoProperties} 的配置绑定。
     *
     * <p>通过 {@code @EnableConfigurationProperties} 显式注册目标配置类，
     * 避免扫描整个 Spring Boot 应用上下文带来的额外开销与依赖缺失问题。
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    @EnableConfigurationProperties(ApexBlockingIoProperties.class)
    static class SharedConfig {}

    /**
     * 默认值验证嵌套测试类：使用 application-test.yml 的基线配置，无任何外部覆盖。
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = SharedConfig.class)
    @ActiveProfiles("test")
    @DisplayName("默认值基线验证")
    class DefaultValuesBaseline {

        /**
         * 被测试的配置属性对象。{@code @Nested} 内部类的构造器首参为外部类实例，
         * 无法通过构造器注入 Spring Bean；此处以测试方法参数接收 {@link ApplicationContext}
         * 后显式获取，不使用任何字段注入注解。
         */
        private ApexBlockingIoProperties properties;

        /**
         * 从测试上下文解析被测配置属性（每个测试方法调用一次）。
         *
         * @param context Spring 测试上下文
         */
        private void resolveProperties(ApplicationContext context) {
            this.properties = context.getBean(ApexBlockingIoProperties.class);
        }

        /**
         * 验证六大专用池的默认参数与 application.yml 基线完全对齐。
         *
         * <p>依次断言 jdbc / cache / storage / event / remote / general 六个池的
         * {@code threads}、{@code queueCapacity}、{@code fair}、{@code taskTtlMs}
         * 四个字段，同时校验 {@code snapshotRefreshMs} 默认值为 3000 毫秒。
         * 该测试保证配置文件与 Java 对象的字段口径一致，防止误改默认值引发生产容量风险。
         *
         * <p>预期基线：
         * <ul>
         *   <li>jdbc：threads=32，queue=128，fair=false，taskTtlMs=-1</li>
         *   <li>cache：threads=4，queue=512，fair=false，taskTtlMs=-1</li>
         *   <li>storage：threads=8，queue=1024，fair=false，taskTtlMs=-1</li>
         *   <li>event：threads=2，queue=2048，fair=false，taskTtlMs=-1</li>
         *   <li>remote：threads=16，queue=1024，fair=false，taskTtlMs=-1</li>
         *   <li>general：threads=16，queue=1024，fair=false，taskTtlMs=-1</li>
         *   <li>snapshotRefreshMs：3000</li>
         * </ul>
         */
        @Test
        @DisplayName("默认值应与 application.yml 基线对齐")
        void defaultValuesShouldMatchBaseline(ApplicationContext context) {
            resolveProperties(context);
            assertNotNull(properties, "ApexBlockingIoProperties 应能从测试上下文解析");

            ApexBlockingIoProperties.PoolConfig jdbc = properties.getJdbc();
            assertEquals(32, jdbc.getThreads(), "jdbc.threads 默认值应为 32");
            assertEquals(128, jdbc.getQueueCapacity(), "jdbc.queueCapacity 默认值应为 128");
            assertFalse(jdbc.isFair(), "jdbc.fair 默认值应为 false");
            assertEquals(-1L, jdbc.getTaskTtlMs(), "jdbc.taskTtlMs 默认值应为 -1");

            ApexBlockingIoProperties.PoolConfig cache = properties.getCache();
            assertEquals(4, cache.getThreads(), "cache.threads 默认值应为 4");
            assertEquals(512, cache.getQueueCapacity(), "cache.queueCapacity 默认值应为 512");
            assertFalse(cache.isFair(), "cache.fair 默认值应为 false");
            assertEquals(-1L, cache.getTaskTtlMs(), "cache.taskTtlMs 默认值应为 -1");

            ApexBlockingIoProperties.PoolConfig storage = properties.getStorage();
            assertEquals(8, storage.getThreads(), "storage.threads 默认值应为 8");
            assertEquals(1024, storage.getQueueCapacity(), "storage.queueCapacity 默认值应为 1024");
            assertFalse(storage.isFair(), "storage.fair 默认值应为 false");
            assertEquals(-1L, storage.getTaskTtlMs(), "storage.taskTtlMs 默认值应为 -1");

            ApexBlockingIoProperties.PoolConfig event = properties.getEvent();
            assertEquals(2, event.getThreads(), "event.threads 默认值应为 2");
            assertEquals(2048, event.getQueueCapacity(), "event.queueCapacity 默认值应为 2048");
            assertFalse(event.isFair(), "event.fair 默认值应为 false");
            assertEquals(-1L, event.getTaskTtlMs(), "event.taskTtlMs 默认值应为 -1");

            ApexBlockingIoProperties.PoolConfig remote = properties.getRemote();
            assertEquals(16, remote.getThreads(), "remote.threads 默认值应为 16");
            assertEquals(1024, remote.getQueueCapacity(), "remote.queueCapacity 默认值应为 1024");
            assertFalse(remote.isFair(), "remote.fair 默认值应为 false");
            assertEquals(-1L, remote.getTaskTtlMs(), "remote.taskTtlMs 默认值应为 -1");

            ApexBlockingIoProperties.PoolConfig general = properties.getGeneral();
            assertEquals(16, general.getThreads(), "general.threads 默认值应为 16");
            assertEquals(1024, general.getQueueCapacity(), "general.queueCapacity 默认值应为 1024");
            assertFalse(general.isFair(), "general.fair 默认值应为 false");
            assertEquals(-1L, general.getTaskTtlMs(), "general.taskTtlMs 默认值应为 -1");

            assertEquals(3000L, properties.getSnapshotRefreshMs(), "snapshotRefreshMs 默认值应为 3000");
        }
    }

    /**
     * 自定义属性覆盖验证嵌套测试类：使用类级 TestPropertySource 注入覆盖属性。
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = SharedConfig.class)
    @ActiveProfiles("test")
    @TestPropertySource(properties = {"apex.blocking-io.jdbc.threads=64", "apex.blocking-io.storage.fair=true"})
    @DisplayName("自定义属性覆盖验证")
    class CustomPropertyOverride {

        /**
         * 被测试的配置属性对象。{@code @Nested} 内部类的构造器首参为外部类实例，
         * 无法通过构造器注入 Spring Bean；此处以测试方法参数接收 {@link ApplicationContext}
         * 后显式获取，不使用任何字段注入注解。
         */
        private ApexBlockingIoProperties properties;

        /**
         * 自定义属性通过类级 {@code @TestPropertySource} 覆盖默认值后应正确生效。
         *
         * <p>本类级别的 {@code @TestPropertySource} 注入两条覆盖：
         * {@code apex.blocking-io.jdbc.threads=64} 与 {@code apex.blocking-io.storage.fair=true}。
         * 断言被覆盖字段值更新为新值，同时<strong>未覆盖</strong>的其他字段（如
         * {@code jdbc.queueCapacity}、{@code storage.threads}、{@code remote.*} 等）
         * 仍然保持默认基线值不变，验证配置覆盖的颗粒度与隔离性。
         */
        @Test
        @DisplayName("自定义属性应通过属性源正确覆盖默认值")
        void customValuesShouldOverrideViaProperty(ApplicationContext context) {
            this.properties = context.getBean(ApexBlockingIoProperties.class);
            assertNotNull(properties, "ApexBlockingIoProperties 应能从测试上下文解析");

            ApexBlockingIoProperties.PoolConfig jdbc = properties.getJdbc();
            assertEquals(64, jdbc.getThreads(), "jdbc.threads 应被覆盖为 64");
            assertEquals(128, jdbc.getQueueCapacity(), "jdbc.queueCapacity 未覆盖时仍保持默认 128");
            assertFalse(jdbc.isFair(), "jdbc.fair 未覆盖时仍保持默认 false");
            assertEquals(-1L, jdbc.getTaskTtlMs(), "jdbc.taskTtlMs 未覆盖时仍保持默认 -1");

            ApexBlockingIoProperties.PoolConfig storage = properties.getStorage();
            assertEquals(8, storage.getThreads(), "storage.threads 未覆盖时仍保持默认 8");
            assertEquals(1024, storage.getQueueCapacity(), "storage.queueCapacity 未覆盖时仍保持默认 1024");
            assertTrue(storage.isFair(), "storage.fair 应被覆盖为 true");
            assertEquals(-1L, storage.getTaskTtlMs(), "storage.taskTtlMs 未覆盖时仍保持默认 -1");

            assertEquals(3000L, properties.getSnapshotRefreshMs(), "snapshotRefreshMs 未覆盖时仍保持默认 3000");
        }
    }
}
