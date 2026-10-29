package com.apex.scaffold.officeservice.db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "apex.office.real-db", matches = "true")
class OfficeWorkflowBooleanIntegrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void validatesOfficeUpgradeAndTenantWorkflowKeys(String vendor) throws Exception {
        boolean mysql = vendor.equals("mysql");
        String container = System.getProperty(
                "apex.office." + vendor + ".container",
                mysql ? "apex-p0-gate-mysql-20261017" : "apex-p0-pg-b1ddd1785ccf");
        String database = "office_contract_" + UUID.randomUUID().toString().replace("-", "");
        String port = command(null, "podman", "port", container, mysql ? "3306" : "5432")
                .trim()
                .replaceFirst(".*:", "");
        String user = mysql ? "root" : database;
        String password = mysql
                ? command(null, "podman", "exec", container, "printenv", "MYSQL_ROOT_PASSWORD")
                        .trim()
                : UUID.randomUUID().toString();
        String options = mysql
                ? "?allowPublicKeyRetrieval=true&useSSL=false&connectTimeout=5000&socketTimeout=10000"
                : "?connectTimeout=5&socketTimeout=10";
        String prefix = "jdbc:" + vendor + "://127.0.0.1:" + port + "/";
        try {
            if (mysql) {
                try (var connection = DriverManager.getConnection(prefix + "mysql" + options, user, password);
                        var statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE " + database);
                }
            } else {
                command(
                        "CREATE ROLE " + user + " LOGIN PASSWORD '" + password + "';\nCREATE DATABASE " + database
                                + " OWNER " + user + " TEMPLATE template0;\n",
                        "podman",
                        "exec",
                        "-i",
                        container,
                        "psql",
                        "-U",
                        "postgres",
                        "-v",
                        "ON_ERROR_STOP=1");
            }
            Flyway office = Flyway.configure()
                    .dataSource(prefix + database + options, user, password)
                    .locations("classpath:db/migration/" + vendor)
                    .cleanDisabled(true)
                    .load();
            Flyway.configure()
                    .configuration(office.getConfiguration())
                    .target("3")
                    .load()
                    .migrate();
            try (var connection = office.getConfiguration().getDataSource().getConnection();
                    var statement = connection.createStatement()) {
                insertOfficeRows(statement, 1, mysql ? "NULL" : "0");
                insertOfficeRows(statement, 2, "1");
                assertThat(office.migrate().migrationsExecuted).isEqualTo(1);
                office.validate();
                assertThat(office.migrate().migrationsExecuted).isZero();
                insertOfficeRows(statement, 3, null);
                for (String spec : new String[] {
                    "office_calendar_event:is_all_day,is_recurring", "office_document_info:is_folder,is_archived"
                }) {
                    String[] parts = spec.split(":");
                    for (String column : parts[1].split(",")) {
                        try (var rows =
                                statement.executeQuery("SELECT " + column + " FROM " + parts[0] + " ORDER BY id")) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getInt(1)).isZero();
                            assertThat(rows.wasNull()).isFalse();
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getInt(1)).isEqualTo(1);
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getInt(1)).isZero();
                        }
                        assertThatThrownBy(() -> statement.executeUpdate(
                                        "UPDATE " + parts[0] + " SET " + column + "=NULL WHERE id=1"))
                                .isInstanceOf(SQLException.class);
                        for (int value : new int[] {1, 0}) {
                            statement.executeUpdate(
                                    "UPDATE " + parts[0] + " SET " + column + "=" + value + " WHERE id=1");
                            try (var rows =
                                    statement.executeQuery("SELECT " + column + " FROM " + parts[0] + " WHERE id=1")) {
                                assertThat(rows.next()).isTrue();
                                assertThat(rows.getInt(1)).isEqualTo(value);
                                assertThat(rows.wasNull()).isFalse();
                            }
                        }
                    }
                }
                statement.execute("DROP TABLE undo_log");
                Path root = Path.of("").toAbsolutePath();
                if (!root.resolve("apex-workflow-service").toFile().isDirectory()) {
                    root = root.getParent();
                }
                Flyway workflow = Flyway.configure()
                        .dataSource(prefix + database + options, user, password)
                        .table("workflow_contract_history")
                        .baselineOnMigrate(true)
                        .baselineVersion("0")
                        .locations("filesystem:"
                                + root.resolve("apex-workflow-service/src/main/resources/db/migration/" + vendor))
                        .cleanDisabled(true)
                        .load();
                workflow.migrate();
                workflow.validate();
                assertThat(workflow.migrate().migrationsExecuted).isZero();
                assertThat(
                                statement.executeUpdate(
                                        "INSERT INTO wf_workflow_definition(id,tenant_id,workflow_code,workflow_name) VALUES(1,'41','shared-code','name')"))
                        .isEqualTo(1);
                assertThat(
                                statement.executeUpdate(
                                        "INSERT INTO wf_workflow_definition(id,tenant_id,workflow_code,workflow_name) VALUES(2,'42','shared-code','name')"))
                        .isEqualTo(1);
                assertThatThrownBy(
                                () -> statement.executeUpdate(
                                        "INSERT INTO wf_workflow_definition(id,tenant_id,workflow_code,workflow_name) VALUES(3,'41','shared-code','name')"))
                        .isInstanceOf(SQLException.class)
                        .satisfies(error ->
                                assertThat(((SQLException) error).getSQLState()).startsWith("23"));
                assertThat(
                                statement.executeUpdate(
                                        "INSERT INTO wf_process_instance(id,tenant_id,workflow_definition_id,business_key,initiator_id) VALUES(1,'41',1,'shared-key',1)"))
                        .isEqualTo(1);
                assertThat(
                                statement.executeUpdate(
                                        "INSERT INTO wf_process_instance(id,tenant_id,workflow_definition_id,business_key,initiator_id) VALUES(2,'42',2,'shared-key',1)"))
                        .isEqualTo(1);
                String duplicate =
                        "INSERT INTO wf_process_instance(id,tenant_id,workflow_definition_id,business_key,initiator_id) VALUES(3,'41',1,'shared-key',1)";
                assertThatThrownBy(() -> statement.executeUpdate(duplicate))
                        .isInstanceOf(SQLException.class)
                        .satisfies(error ->
                                assertThat(((SQLException) error).getSQLState()).startsWith("23"));
                for (String status : new String[] {"COMPLETED", "REJECTED", "TERMINATED"}) {
                    assertThat(statement.executeUpdate("UPDATE wf_process_instance SET status='" + status
                                    + "',end_time=CURRENT_TIMESTAMP WHERE id=1"))
                            .isEqualTo(1);
                    assertThatThrownBy(() -> statement.executeUpdate(duplicate))
                            .isInstanceOf(SQLException.class)
                            .satisfies(error -> assertThat(((SQLException) error).getSQLState())
                                    .startsWith("23"));
                }
            }
        } finally {
            if (mysql) {
                try (var connection = DriverManager.getConnection(prefix + "mysql" + options, user, password);
                        var statement = connection.createStatement()) {
                    statement.execute("DROP DATABASE IF EXISTS " + database);
                }
            } else {
                command(
                        "DROP DATABASE IF EXISTS " + database + ";\nDROP ROLE IF EXISTS " + user + ";\n",
                        "podman",
                        "exec",
                        "-i",
                        container,
                        "psql",
                        "-U",
                        "postgres",
                        "-v",
                        "ON_ERROR_STOP=1");
            }
        }
    }

    private static void insertOfficeRows(Statement statement, int id, String flags) throws SQLException {
        String columns = flags == null ? "" : ",is_all_day,is_recurring";
        String values = flags == null ? "" : "," + flags + "," + flags;
        statement.executeUpdate(
                "INSERT INTO office_calendar_event(id,tenant_id,platform,platform_event_id,title,event_type,organizer_id,organizer_name,start_time,end_time"
                        + columns + ") VALUES(" + id + ",41,'feishu','event-" + id
                        + "','title','OTHER','owner','Owner',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP" + values + ")");
        columns = flags == null ? "" : ",is_folder,is_archived";
        statement.executeUpdate(
                "INSERT INTO office_document_info(id,tenant_id,platform,platform_document_id,document_type,title,owner_id,owner_name"
                        + columns + ") VALUES(" + id + ",41,'feishu','doc-" + id + "','DOC','title','owner','Owner'"
                        + values + ")");
    }

    private static String command(String input, String... args) throws Exception {
        Process process = new ProcessBuilder(args)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        try {
            try (var stream = process.getOutputStream()) {
                if (input != null) {
                    stream.write(input.getBytes(StandardCharsets.UTF_8));
                }
            }
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            process.destroyForcibly();
        }
    }
}
