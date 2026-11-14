package com.apex.scaffold.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * 网关监控指标配置契约测试。
 *
 * <p>网关以 WebFlux 和 Reactor Netty 承载请求，Micrometer 的 Reactor Netty 指标默认不启用。
 * 若仅配置 Prometheus registry 而未显式启用 {@code management.metrics.enable.reactor.netty}，
 * 监控栈无法获得 EventLoop、连接池与 ByteBuf 指标，增强大盘会出现空面板且相关告警无法生效。
 *
 * <p>本测试直接解析 {@code application.yml}，不启动 Spring 上下文、不依赖 Nacos、Prometheus
 * 或下游服务，因此可在离线构建中稳定验证配置契约。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("网关监控指标配置契约测试")
class GatewayMetricsConfigContractTest {

    /** 网关基础配置文件，默认与 dev 场景生效。 */
    private static final String BASE_CONFIG = "application.yml";

    /**
     * 验证网关显式启用 Reactor Netty 指标。
     *
     * <p>该开关是 EventLoop、连接池和 ByteBuf 指标能够注册到 Micrometer 并最终出现在
     * Prometheus 抓取结果中的前提。测试只验证静态配置，真实端点暴露仍需启动应用后检查。
     *
     * @throws IOException 读取或解析 {@code application.yml} 失败时抛出
     */
    @Test
    @DisplayName("application.yml 应显式启用 Reactor Netty 指标")
    void reactorNettyMetricsAreExplicitlyEnabled() throws IOException {
        StandardEnvironment environment = baseEnvironment();

        assertThat(environment.getProperty("management.metrics.enable.reactor.netty", Boolean.class))
                .as("management.metrics.enable.reactor.netty")
                .isTrue();
    }

    /**
     * 验证网关通过 Actuator Web 端点暴露 Prometheus 指标。
     *
     * <p>即使 Micrometer 已注册指标，若 {@code management.endpoints.web.exposure.include}
     * 未包含 {@code prometheus}，Prometheus 仍无法抓取。该断言与 Reactor Netty 开关共同
     * 构成网关指标采集的最小静态配置契约。
     *
     * @throws IOException 读取或解析 {@code application.yml} 失败时抛出
     */
    @Test
    @DisplayName("application.yml 应暴露 Prometheus Actuator 端点")
    void prometheusEndpointIsExposed() throws IOException {
        StandardEnvironment environment = baseEnvironment();

        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .as("management.endpoints.web.exposure.include")
                .contains("prometheus");
    }

    /**
     * 加载网关基础配置文件并构造仅包含本地 YAML 的环境。
     *
     * <p>每次调用返回独立环境，避免测试之间共享可变属性源。加载顺序保持文件内声明顺序，
     * 与 Spring Boot 对单个 YAML 文件的处理方式一致。
     *
     * @return 仅包含 {@code application.yml} 的标准环境
     * @throws IOException 读取或解析 YAML 失败时抛出
     */
    private static StandardEnvironment baseEnvironment() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (PropertySource<?> source : loader.load(BASE_CONFIG, new ClassPathResource(BASE_CONFIG))) {
            environment.getPropertySources().addLast(source);
        }
        return environment;
    }
}
