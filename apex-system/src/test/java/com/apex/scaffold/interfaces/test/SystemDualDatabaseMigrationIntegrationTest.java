package com.apex.scaffold.interfaces.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/** system 完整双库迁移链真实回归。 */
class SystemDualDatabaseMigrationIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void shouldMigrateEveryVersionAndValidate(String vendor) throws Exception {
        try (JdbcDatabaseContainer<?> container = vendor.equals("mysql")
                ? new MySQLContainer<>("mysql:9.0").withUsername("root")
                : new PostgreSQLContainer<>("postgres:15")) {
            container
                    .withDatabaseName("apex_reg")
                    .withUrlParam("connectTimeout", vendor.equals("mysql") ? "5000" : "5")
                    .withUrlParam("socketTimeout", vendor.equals("mysql") ? "10000" : "10");
            if (vendor.equals("postgresql")) {
                container.withUrlParam("loginTimeout", "10");
            }
            container.start();
            createAndMigrate(vendor, container);
        }
    }

    private static void createAndMigrate(String vendor, JdbcDatabaseContainer<?> container) throws Exception {
        boolean mysql = vendor.equals("mysql");
        Flyway flyway = Flyway.configure()
                .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                .locations("classpath:db/migration/" + vendor)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .load();
        System.out.println("Migration regression database: " + vendor + "/" + container.getDatabaseName());
        Flyway previous = Flyway.configure()
                .configuration(flyway.getConfiguration())
                .target("61")
                .load();
        int baselineCount = previous.info().pending().length;
        assertThat(previous.migrate().migrationsExecuted).isEqualTo(baselineCount);
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
                var statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO wf_workflow_definition (id,workflow_code,workflow_name,tenant_id,node_definition,enabled) VALUES (990062,'alignment-preserve','alignment-preserve','default','{}','ACTIVE')");
            if (Boolean.getBoolean("apex.schema.alignment.baseline")) {
                assertAlignedSchema(flyway, mysql);
                return;
            }
            if (mysql) {
                statement.executeUpdate("UPDATE wf_workflow_definition SET enabled=NULL WHERE id=990062");
                org.assertj.core.api.Assertions.assertThatThrownBy(flyway::migrate)
                        .hasStackTraceContaining("v62_requires_non_null_message_and_workflow");
                try (var rows = statement.executeQuery("SELECT enabled FROM wf_workflow_definition WHERE id=990062")) {
                    rows.next();
                    assertThat(rows.getString(1)).isNull();
                }
                try (var rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='off_sync_history' AND column_name='update_time'")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isZero();
                }
                statement.executeUpdate("UPDATE wf_workflow_definition SET enabled='ACTIVE' WHERE id=990062");
                flyway.repair();
            }
        }
        int remainingCount = flyway.info().pending().length;
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(remainingCount);
        assertAlignedSchema(flyway, mysql);
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        int expectedCount = flyway.info().all().length;
        assertThat(flyway.info().applied()).hasSize(expectedCount);
        for (int i = 0; i < expectedCount; i++) {
            assertThat(flyway.info().applied()[i].getVersion().toString()).isEqualTo(Integer.toString(i + 1));
            assertThat(flyway.info().applied()[i].getState().isApplied()).isTrue();
        }
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
                var statement = connection.createStatement()) {
            assertTenantUnificationAndRbacTenantization(statement, mysql);
            try (var rows = statement.executeQuery(
                    "SELECT config_type, config_value FROM sys_config WHERE config_key = 'workflow.version'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.getString(2)).isEqualTo("v1.4.0");
                assertThat(rows.next()).isFalse();
            }
            for (String permission : new String[] {
                "system:database:view",
                "system:job:page",
                "system:oauth2client:list",
                "office:app:create",
                "office:message:query",
                "office:message:send",
                "office:message:resend"
            }) {
                try (var rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id = p.id WHERE p.code = '"
                                + permission + "' AND p.status = 1 AND p.deleted = 0")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).as(permission + " 已保留且授予角色").isPositive();
                }
            }
            try (var rows = statement.executeQuery(
                    "SELECT version, is_latest, previous_version_id, node_definition, enabled FROM wf_workflow_definition WHERE 1 = 0")) {
                assertThat(rows.getMetaData().getColumnCount()).isEqualTo(5);
            }
            try (var rows = statement.executeQuery(
                    "SELECT revoked_at, revoked_by, revoke_reason, last_used_at, use_count FROM oauth2_authorization WHERE 1 = 0")) {
                assertThat(rows.getMetaData().getColumnCount()).isEqualTo(5);
            }
            try (var rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM sys_user u LEFT JOIN sys_user_social s ON s.user_id = u.id AND s.channel = 'WECHAT_APP' AND s.open_id = u.open_id WHERE u.open_id IS NOT NULL AND LEFT(u.open_id, 6) <> 'PHONE_' AND u.deleted = 0 AND s.id IS NULL")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).as("存量微信身份全部回填为社交绑定").isZero();
            }
            try (var rows =
                    statement.executeQuery("SELECT COUNT(*) FROM sys_user_social WHERE channel = 'WECHAT_APP'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isPositive();
            }
            try (var rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM sys_menu WHERE perms IN ('system:database:view', 'system:job:page') AND menu_type = 1")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
        }
    }

    static String jdbcUrl(String vendor, String port, String database) {
        return "jdbc:" + vendor + "://127.0.0.1:" + port + "/" + database
                + (vendor.equals("mysql")
                        ? "?allowPublicKeyRetrieval=true&useSSL=false&connectTimeout=5000&socketTimeout=10000"
                        : "?connectTimeout=5&loginTimeout=10&socketTimeout=10");
    }

    private static void assertAlignedSchema(Flyway flyway, boolean mysql) throws Exception {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
                var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery(
                    "SELECT enabled,node_definition FROM wf_workflow_definition WHERE id=990062")) {
                rows.next();
                assertThat(rows.getString(1)).isEqualTo("ACTIVE");
                assertThat(rows.getString(2)).isEqualTo("{}");
            }
            for (String specification : new String[] {
                "off_department:parent_id,status,sort_order",
                "off_employee:dept_id,status",
                "off_employee:employee_code,tenant_id",
                "off_message_template:template_code,enabled,tenant_id",
                "off_sync_history:sync_source,start_time",
                "sys_role_dept:tenant_id,dept_id",
                "oauth2_authorization:access_token_hash",
                "oauth2_authorization:refresh_token_hash",
                "oauth2_authorization:token_family",
                "oauth2_authorization:device_id",
                "oauth2_authorization:is_revoked",
                "oauth2_authorization:revoked_reason",
                "oauth2_authorization:last_used_at",
                "sys_user:is_locked",
                "sys_user:password_expires_at"
            }) {
                String[] parts = specification.split(":");
                var indexes = new java.util.LinkedHashMap<String, java.util.List<String>>();
                try (var rows = connection
                        .getMetaData()
                        .getIndexInfo(
                                mysql ? connection.getCatalog() : null,
                                mysql ? null : "public",
                                parts[0],
                                false,
                                false)) {
                    while (rows.next()) {
                        if (rows.getString("COLUMN_NAME") != null) {
                            indexes.computeIfAbsent(rows.getString("INDEX_NAME"), key -> new java.util.ArrayList<>())
                                    .add(rows.getString("COLUMN_NAME"));
                        }
                    }
                }
                var expectedKeys = java.util.List.of(parts[1].split(","));
                assertThat(indexes.values()).as(specification).anySatisfy(keys -> assertThat(keys)
                        .startsWith(expectedKeys.toArray(String[]::new)));
            }
            String schema = mysql ? "DATABASE()" : "'public'";
            try (var rows =
                    statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = "
                            + schema + " AND table_name IN ('biometric_auth','device_trust','encryption_key',"
                            + "'encryption_key_version','ip_access_control','jwt_key_version','login_history','mfa_config',"
                            + "'oauth2_client_secret_history','password_history','security_audit_log','token_rotation_history',"
                            + "'token_security_event','trusted_device','v_token_health')")) {
                rows.next();
                assertThat(rows.getInt(1)).as("补齐14表和1视图").isEqualTo(15);
            }
            for (String columns : new String[] {
                "oauth2_authorization:access_token_hash,access_token_encrypted,access_token_iv,refresh_token_hash,refresh_token_encrypted,refresh_token_iv,token_family,previous_jti,rotation_count,device_id,device_fingerprint,client_ip,is_revoked,last_used_ip,is_encrypted,revoked_reason",
                "sys_user:is_locked,lock_reason,locked_at,locked_until,failed_login_attempts,last_failed_login_at,password_changed_at,password_expires_at,must_change_password",
                "off_sync_history:update_time"
            }) {
                String[] parts = columns.split(":");
                try (var rows = statement.executeQuery("SELECT " + parts[1] + " FROM " + parts[0] + " WHERE 1 = 0")) {
                    assertThat(rows.getMetaData().getColumnCount()).isEqualTo(parts[1].split(",").length);
                }
            }
            try (var rows = statement.executeQuery(
                    "SELECT character_maximum_length FROM information_schema.columns WHERE table_schema = " + schema
                            + " AND table_name='sys_user' AND column_name='keycloak_sub'")) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(255);
            }
            try (var rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = " + schema
                            + " AND is_nullable='NO' AND ((table_name='office_message' AND column_name IN ('send_status','target_user_ids')) OR (table_name='wf_workflow_definition' AND column_name IN ('enabled','node_definition')))")) {
                rows.next();
                assertThat(rows.getInt(1)).as("恢复四处非空约束").isEqualTo(4);
            }
            try (var rows =
                    statement.executeQuery("SELECT column_default FROM information_schema.columns WHERE table_schema = "
                            + schema + " AND table_name='wf_workflow_definition' AND column_name='enabled'")) {
                rows.next();
                assertThat(rows.getString(1)).contains("DRAFT");
            }
        }
    }

    /**
     * 统一认证收口（B1/B2）真实双库演练断言：V76 租户编码统一 + V77 RBAC 租户化。
     *
     * <p>V76：默认租户编码 apex-default 已全库改名 default 且不残留；V77：sys_role
     * 含 tenant_id 列且唯一键已重建为 (tenant_id, code)，角色授权关系租户与角色对齐。
     */
    private static void assertTenantUnificationAndRbacTenantization(java.sql.Statement statement, boolean mysql)
            throws Exception {
        try (var rows = statement.executeQuery("SELECT tenant_code FROM sys_tenant WHERE deleted = 0")) {
            java.util.List<String> codes = new java.util.ArrayList<>();
            while (rows.next()) {
                codes.add(rows.getString(1));
            }
            org.assertj.core.api.Assertions.assertThat(codes).containsExactly("default");
        }
        try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_tenant WHERE tenant_code = 'apex-default'")) {
            rows.next();
            org.assertj.core.api.Assertions.assertThat(rows.getInt(1)).isZero();
        }
        try (var rows = statement.executeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'sys_role' AND column_name = 'tenant_id'")) {
            rows.next();
            org.assertj.core.api.Assertions.assertThat(rows.getInt(1)).isEqualTo(1);
        }
        try (var rows = statement.executeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'sys_role_permission' AND column_name = 'tenant_id'")) {
            rows.next();
            org.assertj.core.api.Assertions.assertThat(rows.getInt(1)).isEqualTo(1);
        }
        // 唯一键重建断言：MySQL 查 information_schema.statistics，PG 查 pg_indexes
        String uniqueIndexSql = mysql
                ? "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_name = 'sys_role' AND index_name = 'uk_tenant_code'"
                : "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'sys_role' AND indexname = 'uk_tenant_code'";
        try (var rows = statement.executeQuery(uniqueIndexSql)) {
            rows.next();
            org.assertj.core.api.Assertions.assertThat(rows.getInt(1)).isEqualTo(1);
        }
        String oldIndexSql = mysql
                ? "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_name = 'sys_role' AND index_name = 'uk_code'"
                : "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'sys_role' AND indexname = 'uk_role_code'";
        try (var rows = statement.executeQuery(oldIndexSql)) {
            rows.next();
            org.assertj.core.api.Assertions.assertThat(rows.getInt(1)).isZero();
        }
        // 存量角色全部归属 default 租户（V63 种子角色随 V77 回填）
        try (var rows = statement.executeQuery("SELECT COUNT(*) FROM sys_role WHERE tenant_id <> 'default'")) {
            rows.next();
            org.assertj.core.api.Assertions.assertThat(rows.getInt(1)).isZero();
        }
        try (var rows =
                statement.executeQuery("SELECT COUNT(*) FROM sys_role_permission WHERE tenant_id <> 'default'")) {
            rows.next();
            org.assertj.core.api.Assertions.assertThat(rows.getInt(1)).isZero();
        }
        // B3：套餐定义表与 platform-full 数据驱动种子（菜单/权限码全量聚合）
        try (var rows = statement.executeQuery(
                "SELECT status FROM sys_tenant_package WHERE package_code = 'platform-full' AND deleted = 0")) {
            org.assertj.core.api.Assertions.assertThat(rows.next()).isTrue();
            org.assertj.core.api.Assertions.assertThat(rows.getInt(1)).isEqualTo(1);
        }
        String seedCoverageSql = mysql
                ? "SELECT (SELECT COUNT(*) FROM sys_menu WHERE deleted = 0) - "
                        + "JSON_LENGTH((SELECT menu_ids FROM sys_tenant_package WHERE package_code = 'platform-full'))"
                : "SELECT (SELECT COUNT(*) FROM sys_menu WHERE deleted = 0) - "
                        + "jsonb_array_length((SELECT menu_ids FROM sys_tenant_package WHERE package_code = 'platform-full'))";
        try (var rows = statement.executeQuery(seedCoverageSql)) {
            rows.next();
            org.assertj.core.api.Assertions.assertThat(rows.getInt(1)).isZero();
        }
    }
}
