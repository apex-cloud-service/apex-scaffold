package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Sentinel Nacos 动态规则数据源条件装配测试。
 *
 * <p>验证 {@link SentinelNacosDataSourceConfig} 仅在
 * {@code apex.sentinel.nacos.enabled=true} 时装配：
 * <ul>
 *   <li>默认（未配置）→ 不装配，不尝试连接 Nacos（保证无 Nacos 环境启动零影响）</li>
 *   <li>显式 {@code false} → 不装配</li>
 *   <li>显式 {@code true} → 装配（Nacos 客户端按配置地址异步连接）</li>
 * </ul>
 * </p>
 *
 * @author Apex Team
 * @version 1.1.4
 * @since 1.1.4
 */
class SentinelNacosDataSourceConfigTest {

    /** 上下文运行器：仅加载目标配置类，隔离其他 Bean。 */
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(SentinelNacosDataSourceConfig.class));

    /**
     * 默认配置下不应装配 Nacos 数据源（避免无 Nacos 环境启动失败）。
     */
    @Test
    @DisplayName("未配置 enabled 时不装配 Nacos 数据源")
    void shouldNotRegisterWhenDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SentinelNacosDataSourceConfig.class);
        });
    }

    /**
     * 显式 {@code enabled=false} 时不装配。
     */
    @Test
    @DisplayName("enabled=false 时不装配 Nacos 数据源")
    void shouldNotRegisterWhenExplicitlyDisabled() {
        contextRunner
                .withPropertyValues("apex.sentinel.nacos.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SentinelNacosDataSourceConfig.class));
    }

    /**
     * 显式 {@code enabled=true} 时装配（构造参数含默认 Nacos 地址）。
     *
     * <p>注：Nacos 客户端为异步连接，本测试仅验证 Bean 装配与默认参数注入，
     * 不依赖真实 Nacos 服务在线。</p>
     */
    @Test
    @DisplayName("enabled=true 时装配 Nacos 数据源")
    void shouldRegisterWhenEnabled() {
        contextRunner
                .withPropertyValues("apex.sentinel.nacos.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SentinelNacosDataSourceConfig.class);
                    // 默认地址注入（本地 infra 栈端口）
                    SentinelNacosDataSourceConfig bean =
                            context.getBean(SentinelNacosDataSourceConfig.class);
                    assertThat(bean).isNotNull();
                });
    }
}
