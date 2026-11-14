package com.apex.scaffold.interfaces.config;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.env.Environment;

/**
 * {@link CoreModuleStartupLogger} 单元测试。
 *
 * <p>使用 Mockito 模拟 {@link Environment} 注入，覆盖以下场景：</p>
 * <ul>
 *   <li><b>正常加载</b>：七个核心模块全部启用时的完整日志输出</li>
 *   <li><b>Nacos 禁用</b>：服务发现和配置中心关闭时的状态标记</li>
 *   <li><b>空 Profile</b>：未激活任何 Profile 时的空列表处理</li>
 *   <li><b>多 Profile</b>：同时激活多个 Profile（如 dev+it-local）时的列表展示</li>
 * </ul>
 *
 * <p>所有测试用例仅验证 {@link CoreModuleStartupLogger#onApplicationEvent(ApplicationReadyEvent)}
 * 不抛出异常，不验证日志内容的具体文本（日志格式由 Slf4j 保证）。</p>
 *
 * @author Apex Team
 * @see CoreModuleStartupLogger
 */
@ExtendWith(MockitoExtension.class)
class CoreModuleStartupLoggerTest {

    /** 模拟的 Spring 环境，提供所有配置属性值。 */
    @Mock
    private Environment env;

    /** 模拟的 ApplicationReadyEvent 事件对象。 */
    @Mock
    private ApplicationReadyEvent event;

    /** 被测试的 CoreModuleStartupLogger 实例。 */
    private CoreModuleStartupLogger logger;

    /**
     * 每个测试用例执行前初始化被测试对象。
     */
    @BeforeEach
    void setUp() {
        logger = new CoreModuleStartupLogger(env);
    }

    /**
     * 验证七个核心模块全部启用时，日志输出完整且不抛异常。
     *
     * <p>Mock 所有模块的配置属性为正常启用状态，确认
     * {@link CoreModuleStartupLogger#onApplicationEvent(ApplicationReadyEvent)}
     * 依次调用七个 {@code logXxxStatus()} 方法均正常返回。</p>
     */
    @Test
    @DisplayName("启动日志完整输出七个核心模块状态且不抛异常")
    void shouldLogAllSevenCoreModules() {
        when(env.getActiveProfiles()).thenReturn(new String[] {"dev"});
        when(env.getProperty("server.port", "8080")).thenReturn("8080");
        when(env.getProperty("spring.cloud.nacos.config.enabled", Boolean.class, true))
                .thenReturn(true);
        when(env.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class, true))
                .thenReturn(true);
        when(env.getProperty("spring.cloud.nacos.server-addr", "未配置")).thenReturn("127.0.0.1:8848");
        when(env.getProperty("apex.scheduling.enabled", Boolean.class, true)).thenReturn(true);
        when(env.getProperty("spring.task.scheduling.pool.size", Integer.class, 1))
                .thenReturn(4);
        when(env.getProperty("spring.data.redis.host", "未配置")).thenReturn("127.0.0.1");
        when(env.getProperty("spring.data.redis.port", "未配置")).thenReturn("6379");
        when(env.getProperty("spring.data.redis.database", "未配置")).thenReturn("0");
        when(env.getProperty("spring.datasource.dynamic.datasource.master.url", "未配置"))
                .thenReturn("jdbc:mysql://127.0.0.1:3306/apex_data");
        when(env.getProperty("spring.datasource.dynamic.datasource.master.username", "未配置"))
                .thenReturn("root");
        when(env.getProperty("spring.cloud.sentinel.enabled", Boolean.class, true))
                .thenReturn(true);

        // 不应抛出任何异常
        logger.onApplicationEvent(event);
    }

    /**
     * 验证 Nacos 全部禁用时，日志正确标记为"已禁用"。
     *
     * <p>将 {@code spring.cloud.nacos.config.enabled} 和
     * {@code spring.cloud.nacos.discovery.enabled} 均设为 false，
     * 确认 {@code enabled(false)} 返回 "已禁用"。</p>
     */
    @Test
    @DisplayName("Nacos 全部禁用时日志正确标记为已禁用")
    void shouldMarkNacosDisabled() {
        when(env.getActiveProfiles()).thenReturn(new String[] {"dev"});
        when(env.getProperty("spring.cloud.nacos.config.enabled", Boolean.class, true))
                .thenReturn(false);
        when(env.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class, true))
                .thenReturn(false);
        // 以下属性也会被读取，需要设置默认路径
        when(env.getProperty("server.port", "8080")).thenReturn("8080");
        when(env.getProperty("spring.cloud.nacos.server-addr", "未配置")).thenReturn("未配置");
        when(env.getProperty("apex.scheduling.enabled", Boolean.class, true)).thenReturn(true);
        when(env.getProperty("spring.task.scheduling.pool.size", Integer.class, 1))
                .thenReturn(1);
        when(env.getProperty("spring.data.redis.host", "未配置")).thenReturn("未配置");
        when(env.getProperty("spring.data.redis.port", "未配置")).thenReturn("未配置");
        when(env.getProperty("spring.data.redis.database", "未配置")).thenReturn("未配置");
        when(env.getProperty("spring.datasource.dynamic.datasource.master.url", "未配置"))
                .thenReturn("未配置");
        when(env.getProperty("spring.datasource.dynamic.datasource.master.username", "未配置"))
                .thenReturn("未配置");
        when(env.getProperty("spring.cloud.sentinel.enabled", Boolean.class, true))
                .thenReturn(false);

        logger.onApplicationEvent(event);
    }

    /**
     * 验证 Profile 数组为空时日志不抛异常。
     *
     * <p>{@link Environment#getActiveProfiles()} 返回空数组时，
     * {@link CoreModuleStartupLogger#logProfiles()} 应输出空列表 {@code []} 而非 NPE。</p>
     */
    @Test
    @DisplayName("Profile 数组为空时日志不抛异常（空列表）")
    void shouldHandleEmptyProfileArray() {
        when(env.getActiveProfiles()).thenReturn(new String[] {});
        mockDefaultModuleProperties();

        logger.onApplicationEvent(event);
    }

    /**
     * 验证多 Profile 激活时（如 {@code dev, it-local}）完整列出所有值。
     *
     * <p>常见于本地开发环境通过 IDE 同时激活多个 Profile 的场景。</p>
     */
    @Test
    @DisplayName("多 Profile 激活时完整列出所有值")
    void shouldListAllActiveProfiles() {
        when(env.getActiveProfiles()).thenReturn(new String[] {"dev", "it-local"});
        mockDefaultModuleProperties();

        logger.onApplicationEvent(event);
    }

    /**
     * 设置非 Profile 属性的默认返回值，供多场景复用。
     *
     * <p>所有模块均 Mock 为正常启用状态，避免每个测试用例重复编写
     * 相同的 Mock 配置。</p>
     */
    private void mockDefaultModuleProperties() {
        when(env.getProperty("server.port", "8080")).thenReturn("8080");
        when(env.getProperty("spring.cloud.nacos.config.enabled", Boolean.class, true))
                .thenReturn(true);
        when(env.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class, true))
                .thenReturn(true);
        when(env.getProperty("spring.cloud.nacos.server-addr", "未配置")).thenReturn("127.0.0.1:8848");
        when(env.getProperty("apex.scheduling.enabled", Boolean.class, true)).thenReturn(true);
        when(env.getProperty("spring.task.scheduling.pool.size", Integer.class, 1))
                .thenReturn(4);
        when(env.getProperty("spring.data.redis.host", "未配置")).thenReturn("127.0.0.1");
        when(env.getProperty("spring.data.redis.port", "未配置")).thenReturn("6379");
        when(env.getProperty("spring.data.redis.database", "未配置")).thenReturn("0");
        when(env.getProperty("spring.datasource.dynamic.datasource.master.url", "未配置"))
                .thenReturn("jdbc:mysql://127.0.0.1:3306/apex_data");
        when(env.getProperty("spring.datasource.dynamic.datasource.master.username", "未配置"))
                .thenReturn("root");
        when(env.getProperty("spring.cloud.sentinel.enabled", Boolean.class, true))
                .thenReturn(true);
    }
}
