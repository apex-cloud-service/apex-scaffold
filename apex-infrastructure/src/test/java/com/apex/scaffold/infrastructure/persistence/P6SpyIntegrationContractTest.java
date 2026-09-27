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
 * <p>验证 SQL 日志代理的基础设施可用性：
 * <ul>
 *   <li>{@code spy.properties} 存在于 classpath（p6spy 无配置则无法初始化代理）</li>
 *   <li>{@link P6SpyDriver} 可加载并按 URL 前缀自动委派真实驱动
 *       （spy.properties driverlist 注册 MySQL 驱动供 dev 使用，H2 经 DriverManager SPI 自动发现）</li>
 *   <li>经 {@code jdbc:p6spy:} URL 能真实执行 SQL 并取回结果</li>
 * </ul>
 *
 * <p>不依赖 Spring 上下文与外部数据库，纯 JDBC 层验证：探针经 P6Spy 代理 H2 内存库
 * （MySQL 兼容模式）执行，验证代理层转发与 SQL 执行链路本身。
 */
class P6SpyIntegrationContractTest {

    /** 探针 URL：经 P6Spy 代理 H2 内存库（MySQL 兼容模式），无外部依赖。 */
    private static final String PROBE_URL = "jdbc:p6spy:h2:mem:p6spy_contract_test;MODE=MySQL"
            + ";DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

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
        // P6SpyDriver 按 URL 前缀自动委派真实驱动（H2 经 DriverManager SPI 发现）
        assertThat(P6SpyDriver.class).isNotNull();

        try (Connection conn = DriverManager.getConnection(PROBE_URL, "sa", "");
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
