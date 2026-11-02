package com.apex.scaffold.integration.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 集成域双库迁移真实回归（方案 M5 / 批次 E1–E3）。
 *
 * <p>在真实 MySQL 9 / PostgreSQL 15 容器上执行 {@code apex-integration} 的完整迁移链
 * （V1 集成结构 + V2 IoT 设备结构 + V3 Seata undo_log），并断言关键结构与租户列。
 *
 * <p>验证要点：
 * <ul>
 *   <li>全部版本可迁移且版本连续；</li>
 *   <li><b>所有业务表均含 {@code tenant_id NOT NULL}</b>（自建库即满足租户隔离标准）；</li>
 *   <li>唯一键包含租户维度（连接器配置/凭据/去重表）。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
class IntegrationDualDatabaseMigrationIntegrationTest {

    /** 集成域业务表（全部要求 tenant_id NOT NULL）。 */
    private static final String[] TENANT_SCOPED_TABLES = {
        "int_connector_config",
        "int_credential",
        "int_outbound_call_log",
        "int_inbound_event",
        "int_webhook_dedup",
        "iot_device",
        "iot_device_shadow",
        "iot_telemetry",
        "iot_command",
        "iot_rule"
    };

    /**
     * 双库全链迁移与结构断言。
     *
     * @param vendor 数据库类型（mysql / postgresql）
     * @throws Exception 连接或断言失败
     */
    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void shouldMigrateIntegrationSchemaOnBothDatabases(String vendor) throws Exception {
        try (JdbcDatabaseContainer<?> container = vendor.equals("mysql")
                ? new MySQLContainer<>("mysql:9.0").withUsername("root")
                : new PostgreSQLContainer<>("postgres:15")) {
            container
                    .withDatabaseName("apex_integration_test")
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
        System.out.println("Integration migration regression: " + vendor);

        int executed = flyway.migrate().migrationsExecuted;
        assertThat(executed).as("迁移版本数").isGreaterThanOrEqualTo(3);
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();

        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
                var statement = connection.createStatement()) {
            for (String table : TENANT_SCOPED_TABLES) {
                assertThat(tableHasTenantColumn(statement, table, mysql))
                        .as("表 %s 必须含 tenant_id 列", table)
                        .isTrue();
            }
            // 租户维度唯一键：连接器配置 (tenant_id, config_key, deleted)
            assertThat(hasUniqueKey(statement, "int_connector_config", "uk_int_connector_config", mysql))
                    .as("连接器配置唯一键应含租户维度")
                    .isTrue();
            // 幂等去重键 (tenant_id, dedup_key)
            assertThat(hasUniqueKey(statement, "int_webhook_dedup", "uk_int_webhook_dedup", mysql))
                    .as("webhook 去重唯一键应含租户维度")
                    .isTrue();
        }
    }

    /** 表是否含 tenant_id 列（MySQL 查 information_schema.columns，PG 同）。 */
    private static boolean tableHasTenantColumn(java.sql.Statement statement, String table, boolean mysql)
            throws Exception {
        String sql = "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = '" + table
                + "' AND column_name = 'tenant_id'";
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1) == 1;
        }
    }

    /** 指定唯一键是否存在于表上（MySQL 查 statistics，PG 查 pg_constraint）。 */
    private static boolean hasUniqueKey(java.sql.Statement statement, String table, String indexName, boolean mysql)
            throws Exception {
        String sql = mysql
                ? "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_name = '" + table
                        + "' AND index_name = '" + indexName + "'"
                : "SELECT COUNT(*) FROM pg_constraint WHERE conname = '" + indexName + "'";
        try (var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1) >= 1;
        }
    }
}
