package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.constant.SystemConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 生产迁移、管理面和健康探针配置防回归测试。
 *
 * @author Apex Team
 * @since 3.2.0
 */
class ProductionReadinessConfigurationTest {

    /**
     * 验证生产 Flyway 使用严格校验且不自动修复历史。
     *
     * @throws IOException 配置读取失败
     */
    @Test
    @DisplayName("生产Flyway禁止自动repair和baseline")
    void productionFlywayIsStrict() throws IOException {
        String yaml = read("application-prod.yml");
        PropertySource<?> properties = load("application-prod.yml");

        assertThat(yaml).contains("validate-on-migrate: true");
        assertThat(yaml).contains("baseline-on-migrate: false");
        assertThat(yaml).contains("repair: false");
        assertThat(properties.getProperty("spring.flyway.validate-on-migrate")).isEqualTo(true);
        assertThat(properties.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo(false);
        assertThat(properties.getProperty("spring.flyway.repair")).isEqualTo(false);
    }

    /**
     * 验证当前生产诊断策略及配置层级可被 Spring 正确解析。
     *
     * <h2>变更说明（Netty/线程池/IO 性能优化方案）</h2>
     * <ul>
     *   <li>P6Spy SQL 代理已关闭（p6spy: false），由 Druid stat 过滤器接管 SQL 监控</li>
     *   <li>Netty 日志级别从 DEBUG 降为 WARN，避免高 QPS 下每秒数万行日志</li>
     *   <li>框架层和应用层日志仍保留 DEBUG，用于业务排查</li>
     * </ul>
     *
     * @throws IOException 配置读取失败
     */
    @Test
    @DisplayName("生产诊断配置：Netty降为WARN，P6Spy已关闭，框架/应用仍保留DEBUG")
    void productionDiagnosticsConfiguredForPerformance() throws IOException {
        String yaml = read("application-prod.yml");
        PropertySource<?> properties = load("application-prod.yml");

        // P6Spy 已关闭（P3 优化）
        assertThat(yaml).contains("p6spy: false");
        assertThat(properties.getProperty("spring.datasource.dynamic.p6spy")).isEqualTo(false);

        // 框架层和应用层仍保留 DEBUG
        assertThat(yaml)
                .contains(
                        "root: DEBUG",
                        "com.apex: DEBUG",
                        "org.springframework.web: DEBUG",
                        "org.springframework.security: DEBUG",
                        "com.baomidou.mybatisplus: DEBUG");

        // Netty 已降为 WARN（P0-1 优化），不包含旧 DEBUG 值
        assertThat(yaml).contains("reactor.netty: WARN");
        assertThat(yaml).doesNotContain("reactor.netty: DEBUG");

        assertThat(properties.getProperty("logging.level.root")).isEqualTo("DEBUG");
    }

    /**
     * 验证管理端口隔离，readiness 包含数据库和 Redis。
     *
     * @throws IOException 配置读取失败
     */
    @Test
    @DisplayName("管理面独立且readiness覆盖必要依赖")
    void managementPlaneAndReadinessAreExplicit() throws IOException {
        String yaml = read("application-prod.yml");

        assertThat(yaml).contains("port: ${MANAGEMENT_SERVER_PORT:8081}");
        assertThat(yaml).contains("include: readinessState,db,redis");
        assertThat(yaml).contains("include: livenessState");
        assertThat(SystemConstants.PUBLIC_PATHS)
                .contains(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/wechat/bind-phone",
                        "/api/v1/auth/refresh",
                        "/api/v1/health")
                .doesNotContain("/api/**", "/api/v1/info", "/api/v1/onboarding/consents");
        assertThat(SystemConstants.PUBLIC_PATHS).contains("/actuator/prometheus");
        assertThat(SystemConstants.PUBLIC_PATHS)
                .doesNotContain(
                        "/api/v1/admin/photo-batch-upload",
                        "/api/v1/admin/photo-batch-upload/*",
                        "/api/v1/admin/vote-simulation/**");
        assertThat(new SecurityProperties().getAdminPaths())
                .contains(
                        "/api/v1/admin/photo-batch-upload",
                        "/api/v1/admin/photo-batch-upload/**",
                        "/api/v1/admin/vote-simulation/**");
        // 投票照片由浏览器直接加载，必须保持公开，否则磁盘文件存在时也会返回 401。
        assertThat(SystemConstants.PUBLIC_PATHS).contains("/uploads/apex/**");
        assertThat(SystemConstants.AUTHENTICATED_USER_PATHS)
                .containsExactly("/api/v1/auth/roles/my", "/api/v1/auth/permissions/my");
    }

    /**
     * 验证 {@code credentialsAreEnvironmentBound} 场景。
     *
     * @throws IOException 执行失败时
     */
    @Test
    @DisplayName("dev Redis无密码且prod Redis密码通过环境变量绑定")
    void credentialsAreEnvironmentBound() throws IOException {
        String sharedYaml = read("application.yml");
        String devYaml = read("application-dev.yml");
        String prodYaml = read("application-prod.yml");

        assertThat(sharedYaml).doesNotContain("REDIS_PASSWORD").doesNotContain("  redisson:");
        assertThat(devYaml).doesNotContain("REDIS_PASSWORD").doesNotContain("  redisson:");
        assertThat(prodYaml)
                .contains("password: ${REDIS_PASSWORD}")
                .doesNotContain("  redisson:")
                .contains("url: ${DB_MASTER_URL:}")
                .contains("username: ${DB_MASTER_USERNAME:}")
                .contains("password: ${DB_MASTER_PASSWORD:}")
                .doesNotContain("DB_MASTER_PASSWORD:Jiabei2008");
    }

    /**
     * 验证 {@code productionPoolsHaveBoundedCapacityAndWait} 场景。
     *
     * @throws IOException 执行失败时
     */
    @Test
    @DisplayName("生产数据库和Redis使用有界容量模型")
    void productionPoolsHaveBoundedCapacityAndWait() throws IOException {
        PropertySource<?> properties = load("application-prod.yml");

        assertThat(properties.getProperty("spring.datasource.dynamic.datasource.master.druid.max-active"))
                .isEqualTo("${DB_MAX_ACTIVE:40}");
        assertThat(properties.getProperty("spring.datasource.dynamic.datasource.master.druid.max-wait"))
                .isEqualTo("${DB_MAX_WAIT_MILLIS:3000}");
        assertThat(properties.getProperty("spring.data.redis.lettuce.pool.max-active"))
                .isEqualTo("${REDIS_POOL_MAX_ACTIVE:32}");
        assertThat(properties.getProperty("spring.data.redis.lettuce.pool.max-wait"))
                .isEqualTo("${REDIS_POOL_MAX_WAIT:2s}");
    }

    /**
     * 验证 Netty/线程池/IO 性能优化配置在生产环境中正确生效。
     *
     * <h2>覆盖项</h2>
     * <ul>
     *   <li>Druid keep-alive 已启用（P3）</li>
     *   <li>Trace 采样率从 100% 降为 10%（P3）</li>
     *   <li>通用阻塞 IO 专用池（jdbc/cache/storage）已收敛到 {@code apex.blocking-io.*}</li>
     * </ul>
     *
     * @throws IOException 配置资源读取失败
     */
    @Test
    @DisplayName("Netty/IO 优化：Druid keep-alive、trace 采样、阻塞 IO 池均正确配置")
    void nettyIoOptimizationSettingsAreApplied() throws IOException {
        // ── 生产配置验证 ──
        String prodYaml = read("application-prod.yml");
        PropertySource<?> prodProperties = load("application-prod.yml");

        // Druid keep-alive 已启用（P3）
        assertThat(prodYaml).contains("keep-alive: true");
        assertThat(prodYaml).contains("keep-alive-between-time-millis: 60000");
        assertThat(prodProperties.getProperty("spring.datasource.dynamic.datasource.master.druid.keep-alive"))
                .isEqualTo(true);

        // Trace 采样率从 100% 降为 10%（P3）
        assertThat(prodYaml).contains("sampling-rate: ${TRACE_SAMPLING_RATE:0.1}");
        assertThat(prodProperties.getProperty("trace.sampling-rate")).isEqualTo("${TRACE_SAMPLING_RATE:0.1}");

        // ── 共享配置验证 ──
        String sharedYaml = read("application.yml");
        PropertySource<?> sharedProperties = load("application.yml");

        // 通用阻塞 IO 专用池（v2.0 结构化嵌套格式）：jdbc/cache/storage 三类专用池已收敛到 apex.blocking-io.*
        assertThat(sharedYaml).as("结构化阻塞 IO 配置：JDBC 池线程数应存在").contains("threads: 32");
        assertThat(sharedProperties.getProperty("apex.blocking-io.jdbc.threads"))
                .as("JDBC 专用池最大线程数（PropertySource 解析）")
                .isEqualTo(32);
        assertThat(sharedProperties.getProperty("apex.blocking-io.cache.threads"))
                .as("缓存专用池最大线程数（PropertySource 解析）")
                .isEqualTo(4);
        assertThat(sharedProperties.getProperty("apex.blocking-io.storage.threads"))
                .as("存储专用池最大线程数（PropertySource 解析）")
                .isEqualTo(8);
    }

    /** 读取 classpath 文本资源。 */
    private String read(String resourceName) throws IOException {
        return new ClassPathResource(resourceName).getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * 通过 Spring Boot 实际 YAML 加载器解析配置，捕获重复键和层级错误。
     *
     * @param resourceName classpath 配置资源
     * @return 唯一 YAML 文档对应的属性源
     * @throws IOException 配置读取或解析失败
     */
    private PropertySource<?> load(String resourceName) throws IOException {
        List<PropertySource<?>> propertySources =
                new YamlPropertySourceLoader().load(resourceName, new ClassPathResource(resourceName));
        assertThat(propertySources).as("生产 YAML 文档数量").hasSize(1);
        return propertySources.get(0);
    }
}
