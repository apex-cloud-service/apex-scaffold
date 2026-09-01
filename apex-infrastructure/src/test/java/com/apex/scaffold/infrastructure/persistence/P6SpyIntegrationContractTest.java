package com.apex.scaffold.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.p6spy.engine.spy.P6SpyDriver;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * P6Spy 集成契约测试。
 *
 * <p>验证 dev 环境 SQL 日志代理的基础设施可用性：
 * <ul>
 *   <li>{@code spy.properties} 存在于 classpath（p6spy 无配置则无法初始化代理）</li>
 *   <li>{@link P6SpyDriver} 可加载并代理真实驱动（dev 为 MySQL，见 spy.properties driverlist）</li>
 *   <li>经 {@code jdbc:p6spy:} URL 能真实执行 SQL 并取回结果</li>
 * </ul>
 *
 * <p>不依赖 Spring 上下文，纯 JDBC 层验证，快速且无外部依赖。
 */
class P6SpyIntegrationContractTest {

    /** dev 数据源 URL（与 application-dev.yml 保持一致，经 P6Spy 代理 MySQL）。 */
    private static final String DEV_URL = "jdbc:p6spy:mysql://127.0.0.1:3306/apex_data"
            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
            + "&allowPublicKeyRetrieval=true&useSSL=false";
    /** dev 数据库凭据（与 application-dev.yml 默认值一致，可经环境变量覆盖）。 */
    private static final String DEV_USER = System.getenv().getOrDefault("DB_MASTER_USERNAME", "root");

    private static final String DEV_PASSWORD = System.getenv().getOrDefault("DB_MASTER_PASSWORD", "apex-root");

    @Test
    void spyPropertiesIsPresentOnClasspath() {
        // p6spy 初始化时从 classpath 根加载 spy.properties；缺失会导致代理无法启用
        try (var in = getClass().getClassLoader().getResourceAsStream("spy.properties")) {
            assertThat(in).as("spy.properties 必须存在于 classpath 根").isNotNull();
            assertThat(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("modulelist")
                    .contains("appender=com.p6spy.engine.spy.appender.Slf4JLogger");
        } catch (Exception e) {
            throw new AssertionError("读取 spy.properties 失败", e);
        }
    }

    @Test
    void p6SpyDriverProxiesRealJdbcUrl() throws Exception {
        // driverlist 中注册的 MySQL 驱动必须可用（P6SpyDriver 按 URL 前缀自动委派）
        assertThat(P6SpyDriver.class).isNotNull();

        try (Connection conn = DriverManager.getConnection(DEV_URL, DEV_USER, DEV_PASSWORD);
                Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS p6spy_probe (id INT PRIMARY KEY, name VARCHAR(32))");
            stmt.execute("DELETE FROM p6spy_probe");
            stmt.execute("INSERT INTO p6spy_probe VALUES (1, 'probe')");

            try (ResultSet rs = stmt.executeQuery("SELECT name FROM p6spy_probe WHERE id = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("probe");
            }
            stmt.execute("DROP TABLE p6spy_probe");
        }
    }
}
