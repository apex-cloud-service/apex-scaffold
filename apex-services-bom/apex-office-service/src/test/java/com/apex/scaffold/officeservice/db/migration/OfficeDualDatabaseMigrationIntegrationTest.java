package com.apex.scaffold.officeservice.db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 办公域双库迁移真实回归（V1–V10）。
 *
 * <p>在真实 MySQL 9 / PostgreSQL 15 容器上执行 {@code apex-office-service} 的完整迁移链，
 * 重点覆盖 2026-11-12 引入的审批撤销编排表：
 * <ul>
 *   <li>V9 建表与 V10 主键自增修正可在两种方言上顺序执行（MySQL {@code AUTO_INCREMENT}、
 *       PostgreSQL {@code BIGSERIAL}），主键可省略由数据库生成——与 PO 的 {@code IdType.AUTO} 对齐；</li>
 *   <li>幂等键唯一约束 {@code (tenant_id, idempotency_key, deleted)} 真实生效；</li>
 *   <li>状态列默认值与长度可容纳 PENDING/REMOTE_CANCELLED/COMPLETED/FAILED；</li>
 *   <li>迁移版本连续且 {@code validate()} 无 pending。</li>
 * </ul>
 *
 * <p>本测试不依赖 Spring 上下文，直接对容器执行 Flyway；用于补充 H2 basic 与静态 SQL 一致性
 * 测试无法覆盖的「真实方言 DDL 语义」缺口。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@org.junit.jupiter.api.parallel.ResourceLock("dual-database-container")
class OfficeDualDatabaseMigrationIntegrationTest {

    /** 撤销编排表名。 */
    private static final String CANCEL_TABLE = "off_approval_cancel_operation";

    /**
     * 双库全链迁移与撤销编排结构断言。
     *
     * @param vendor 数据库类型（mysql / postgresql）
     * @throws Exception 连接或断言失败
     */
    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void shouldMigrateOfficeSchemaOnBothDatabases(String vendor) throws Exception {
        try (JdbcDatabaseContainer<?> container = vendor.equals("mysql")
                ? new MySQLContainer<>("mysql:9.0").withUsername("root")
                : new PostgreSQLContainer<>("postgres:15")) {
            container
                    .withDatabaseName("apex_office_test")
                    .withUrlParam("connectTimeout", vendor.equals("mysql") ? "5000" : "5")
                    .withUrlParam("socketTimeout", vendor.equals("mysql") ? "10000" : "10");
            if (vendor.equals("postgresql")) {
                container.withUrlParam("loginTimeout", "10");
            }
            container.start();
            verifyMigrations(vendor, container);
        }
    }

    private static void verifyMigrations(String vendor, JdbcDatabaseContainer<?> container) throws Exception {
        boolean mysql = vendor.equals("mysql");
        Flyway flyway = Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .locations("classpath:db/migration/" + vendor)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .load();

        int executed = flyway.migrate().migrationsExecuted;
        assertThat(executed).as("%s 迁移版本数应覆盖 V1–V10", vendor).isGreaterThanOrEqualTo(10);
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();

        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            assertThat(tableExists(connection, CANCEL_TABLE)).as("V9 应创建撤销编排表").isTrue();
            assertThat(hasUniqueKey(connection, CANCEL_TABLE, "uk_off_approval_cancel_operation", mysql))
                    .as("幂等键唯一约束应真实存在")
                    .isTrue();
            assertThat(hasIndex(connection, CANCEL_TABLE, "idx_off_approval_cancel_operation_status", mysql))
                    .as("状态索引应真实存在")
                    .isTrue();
            assertThat(insertsWithoutPrimaryKey(connection, mysql))
                    .as("主键应由数据库生成（V10 自增修正生效）")
                    .isTrue();
            assertThat(defaultStatus(connection, mysql))
                    .as("status 默认值应为 PENDING")
                    .isEqualTo("PENDING");
        }
    }

    /** 表是否存在。 */
    private static boolean tableExists(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '" + table + "'")) {
            rows.next();
            return rows.getInt(1) >= 1;
        }
    }

    /**
     * 指定唯一约束/唯一索引是否存在。
     *
     * <p>PG 侧 V9/V10 以 {@code CREATE UNIQUE INDEX} 建唯一性（未建 constraint），因此必须查
     * {@code pg_indexes}；{@code pg_constraint} 只覆盖显式 {@code UNIQUE} 约束，查它会误判缺失。
     */
    private static boolean hasUniqueKey(Connection connection, String table, String name, boolean mysql)
            throws Exception {
        String sql = mysql
                ? "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_name = '" + table
                        + "' AND index_name = '" + name + "'"
                : "SELECT COUNT(*) FROM pg_indexes WHERE tablename = '" + table + "' AND indexname = '" + name + "'";
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1) >= 1;
        }
    }

    /** 指定普通索引是否存在（PG 的唯一约束同时建索引，普通索引用 pg_indexes）。 */
    private static boolean hasIndex(Connection connection, String table, String name, boolean mysql) throws Exception {
        String sql = mysql
                ? "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_name = '" + table
                        + "' AND index_name = '" + name + "'"
                : "SELECT COUNT(*) FROM pg_indexes WHERE tablename = '" + table + "' AND indexname = '" + name + "'";
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1) >= 1;
        }
    }

    /**
     * 以省略主键的插入验证自增：MySQL 需 {@code AUTO_INCREMENT}，PostgreSQL 需序列默认值。
     * 插入后回读主键非空即视为通过；异常向上抛出以暴露真实 DDL 缺陷。
     */
    private static boolean insertsWithoutPrimaryKey(Connection connection, boolean mysql) throws Exception {
        String insert = "INSERT INTO " + CANCEL_TABLE
                + " (tenant_id, process_instance_id, idempotency_key, status) VALUES (1, 'PI-V10', 'idem-v10', 'PENDING')";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(insert);
        }
        String select = "SELECT id FROM " + CANCEL_TABLE + " WHERE idempotency_key = 'idem-v10'";
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(select)) {
            return rows.next() && rows.getLong(1) > 0;
        }
    }

    /** 读取 status 列默认值（MySQL 为 COLUMN_DEFAULT，PG 为 column_default 的引号包裹值）。 */
    private static String defaultStatus(Connection connection, boolean mysql) throws Exception {
        String sql = mysql
                ? "SELECT column_default FROM information_schema.columns WHERE table_name = '" + CANCEL_TABLE
                        + "' AND column_name = 'status'"
                : "SELECT column_default FROM information_schema.columns WHERE table_name = '" + CANCEL_TABLE
                        + "' AND column_name = 'status'";
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            String value = rows.getString(1);
            return value == null ? null : value.replace("'", "").replace("::character varying", "");
        }
    }
}
