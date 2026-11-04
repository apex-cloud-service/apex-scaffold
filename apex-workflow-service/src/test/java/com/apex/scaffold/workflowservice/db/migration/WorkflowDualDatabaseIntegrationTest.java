package com.apex.scaffold.workflowservice.db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.workflow.application.dto.WorkflowStatisticsDTO;
import com.apex.scaffold.workflow.application.query.WorkflowStatisticsService;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.ProcessInstanceMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.TaskMapper;
import com.apex.scaffold.workflowservice.config.WorkflowPersistenceConfig;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

/** 工作流 V1–V5 迁移及统计的真实 MySQL/PostgreSQL 回归。 */
class WorkflowDualDatabaseIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void shouldMigrateEmptyDatabaseThroughV5(String vendor) throws Exception {
        try (TestDatabase database = TestDatabase.create(vendor)) {
            Flyway flyway = database.flyway();
            int totalVersions = latestVersion();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(totalVersions);
            assertMigrationHistory(flyway);
            try (Connection connection = database.connect()) {
                for (String table : new String[] {
                    "wf_workflow_definition", "wf_process_instance", "wf_task", "wf_approval_record", "undo_log"
                }) {
                    assertThat(scalar(connection, "SELECT COUNT(*) FROM " + table))
                            .isZero();
                }
                assertThat(scalar(
                                connection,
                                "SELECT COUNT(*) FROM wf_scheduler_lease"
                                        + " WHERE lock_name = 'workflow-timeout-scan' AND owner = ''"))
                        .isEqualTo(1);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void shouldPreserveV2DataAndEnforceTenantKeysAfterUpgrade(String vendor) throws Exception {
        try (TestDatabase database = TestDatabase.create(vendor)) {
            Flyway previous = Flyway.configure()
                    .configuration(database.flyway().getConfiguration())
                    .target("2")
                    .load();
            assertThat(previous.migrate().migrationsExecuted).isEqualTo(2);
            try (Connection connection = database.connect()) {
                insertDefinition(connection, 1, "tenant-a");
                insertInstance(
                        connection, 1, "tenant-a", "RUNNING", LocalDate.now().atTime(8, 0), null, 0, 0);
                insertTask(connection, 1, "tenant-a", "PENDING", null, 0, null, 0);
            }
            Flyway latest = database.flyway();
            assertThat(latest.migrate().migrationsExecuted).isEqualTo(latestVersion() - 2);
            assertMigrationHistory(latest);
            try (Connection connection = database.connect()) {
                assertThat(scalar(connection, "SELECT version FROM wf_process_instance WHERE id = 1"))
                        .isZero();
                assertThat(scalar(connection, "SELECT version FROM wf_task WHERE id = 1"))
                        .isZero();
                assertThat(scalar(
                                connection,
                                "SELECT COUNT(*) FROM wf_process_instance WHERE business_key='case-1'"
                                        + " AND tenant_id='tenant-a' AND status='RUNNING'"))
                        .isEqualTo(1);
                insertDefinition(connection, 2, "tenant-b");
                insertInstance(
                        connection, 2, "tenant-b", "RUNNING", LocalDate.now().atTime(8, 0), null, 0, 0);
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("UPDATE wf_process_instance SET business_key='case-1' WHERE id=2");
                }
                assertThatThrownBy(() -> insertDefinition(connection, 3, "tenant-a"))
                        .isInstanceOf(SQLException.class)
                        .satisfies(error ->
                                assertThat(((SQLException) error).getSQLState()).startsWith("23"));
                assertThatThrownBy(() -> {
                            try (var statement = connection.createStatement()) {
                                statement.executeUpdate("INSERT INTO wf_process_instance"
                                        + " (id,tenant_id,workflow_definition_id,business_key,initiator_id)"
                                        + " VALUES (3,'tenant-a',1,'case-1',1)");
                            }
                        })
                        .isInstanceOf(SQLException.class)
                        .satisfies(error ->
                                assertThat(((SQLException) error).getSQLState()).startsWith("23"));
                assertThat(scalar(connection, "SELECT COUNT(*) FROM wf_workflow_definition"))
                        .isEqualTo(2);
                assertThat(scalar(connection, "SELECT COUNT(*) FROM wf_process_instance"))
                        .isEqualTo(2);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void shouldIsolateStatisticsForEachSubscriberTenant(String vendor) throws Exception {
        try (TestDatabase database = TestDatabase.create(vendor)) {
            database.flyway().migrate();
            seedStatistics(database);
            WorkflowStatisticsService service = database.statisticsService();
            Mono<WorkflowStatisticsDTO> statistics = service.getStatistics();
            assertStatistics(
                    statistics.contextWrite(tenant("tenant-a")).block(Duration.ofSeconds(10)),
                    2,
                    1,
                    1,
                    3,
                    1,
                    1,
                    90L,
                    Map.of("RUNNING", 1, "COMPLETED", 2));
            assertStatistics(
                    statistics.contextWrite(tenant("tenant-b")).block(Duration.ofSeconds(10)),
                    3,
                    2,
                    2,
                    1,
                    0,
                    2,
                    600L,
                    Map.of("RUNNING", 2, "COMPLETED", 1));
            assertStatistics(
                    statistics.contextWrite(tenant("empty-tenant")).block(Duration.ofSeconds(10)),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0L,
                    Map.of());
            StepVerifier.create(statistics)
                    .expectErrorMatches(error -> error instanceof IllegalStateException
                            && error.getMessage().contains("缺少有效租户上下文"))
                    .verify(Duration.ofSeconds(10));
            StepVerifier.create(statistics.contextWrite(tenant(" ")))
                    .expectError(IllegalStateException.class)
                    .verify(Duration.ofSeconds(10));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void shouldIsolateDailyTrendsAndFillEmptyDays(String vendor) throws Exception {
        try (TestDatabase database = TestDatabase.create(vendor)) {
            database.flyway().migrate();
            seedStatistics(database);
            var trend = database.statisticsService().getTrend(3);
            LocalDate today = LocalDate.now();
            var a = trend.contextWrite(tenant("tenant-a")).block(Duration.ofSeconds(10));
            assertThat(a).isNotNull();
            assertThat(a.getDates())
                    .containsExactly(
                            today.minusDays(2).toString(), today.minusDays(1).toString(), today.toString());
            assertThat(a.getStartedCounts()).containsExactly(0, 1, 2);
            assertThat(a.getCompletedCounts()).containsExactly(0, 1, 1);
            var b = trend.contextWrite(tenant("tenant-b")).block(Duration.ofSeconds(10));
            assertThat(b).isNotNull();
            assertThat(b.getStartedCounts()).containsExactly(0, 0, 3);
            assertThat(b.getCompletedCounts()).containsExactly(0, 0, 1);
            var empty = trend.contextWrite(tenant("empty-tenant")).block(Duration.ofSeconds(10));
            assertThat(empty).isNotNull();
            assertThat(empty.getStartedCounts()).containsExactly(0, 0, 0);
            assertThat(empty.getCompletedCounts()).containsExactly(0, 0, 0);
            StepVerifier.create(trend).expectError(IllegalStateException.class).verify(Duration.ofSeconds(10));
        }
    }

    private static Context tenant(String tenant) {
        return Context.of(TenantContext.REACTOR_CONTEXT_KEY, tenant);
    }

    private static void assertStatistics(
            WorkflowStatisticsDTO actual,
            int today,
            int running,
            int suspended,
            int pending,
            int timeout,
            int transferred,
            long average,
            Map<String, Integer> distribution) {
        assertThat(actual).isNotNull();
        assertThat(actual.getTodayStarted()).isEqualTo(today);
        assertThat(actual.getRunningTotal()).isEqualTo(running);
        assertThat(actual.getSuspendedCount()).isEqualTo(suspended);
        assertThat(actual.getPendingTaskTotal()).isEqualTo(pending);
        assertThat(actual.getTimeoutCount()).isEqualTo(timeout);
        assertThat(actual.getTransferredCount()).isEqualTo(transferred);
        assertThat(actual.getAvgCompletionSeconds()).isEqualTo(average);
        assertThat(actual.getStatusDistribution()).containsExactlyInAnyOrderEntriesOf(distribution);
    }

    private static void assertMigrationHistory(Flyway flyway) {
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        var applied = flyway.info().applied();
        assertThat(applied).hasSize(latestVersion());
        for (int i = 0; i < applied.length; i++) {
            assertThat(applied[i].getVersion().toString()).isEqualTo(Integer.toString(i + 1));
            assertThat(applied[i].getState().isApplied()).isTrue();
        }
    }

    private static void seedStatistics(TestDatabase database) throws SQLException {
        LocalDateTime today = LocalDate.now().atTime(8, 0);
        LocalDateTime yesterday = today.minusDays(1);
        try (Connection connection = database.connect()) {
            insertDefinition(connection, 1, "tenant-a");
            insertDefinition(connection, 2, "tenant-b");
            insertInstance(connection, 1, "tenant-a", "RUNNING", today, null, 1, 0);
            insertInstance(connection, 2, "tenant-a", "COMPLETED", today, today.plusSeconds(60), 0, 0);
            insertInstance(connection, 3, "tenant-a", "COMPLETED", yesterday, yesterday.plusSeconds(120), 0, 0);
            insertInstance(connection, 4, "tenant-a", "RUNNING", today, today.plusSeconds(9999), 1, 1);
            insertInstance(connection, 5, "tenant-b", "RUNNING", today, null, 1, 0);
            insertInstance(connection, 6, "tenant-b", "RUNNING", today, null, 1, 0);
            insertInstance(connection, 7, "tenant-b", "COMPLETED", today, today.plusSeconds(600), 0, 0);
            insertTask(connection, 1, "tenant-a", "PENDING", yesterday, 0, null, 0);
            insertTask(connection, 2, "tenant-a", "PENDING", yesterday, 1, null, 0);
            insertTask(connection, 3, "tenant-a", "PENDING", today.plusDays(1), 0, null, 0);
            insertTask(connection, 4, "tenant-a", "COMPLETED", yesterday, 0, 99L, 0);
            insertTask(connection, 5, "tenant-a", "PENDING", yesterday, 0, 99L, 1);
            insertTask(connection, 6, "tenant-b", "PENDING", null, 0, 99L, 0);
            insertTask(connection, 7, "tenant-b", "COMPLETED", yesterday, 0, 99L, 0);
        }
    }

    private static void insertDefinition(Connection connection, long id, String tenant) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO wf_workflow_definition"
                + " (id,tenant_id,workflow_code,workflow_name) VALUES (?,?,'shared-code','shared-name')")) {
            statement.setLong(1, id);
            statement.setString(2, tenant);
            statement.executeUpdate();
        }
    }

    private static void insertInstance(
            Connection connection,
            long id,
            String tenant,
            String status,
            LocalDateTime start,
            LocalDateTime end,
            int suspended,
            int deleted)
            throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO wf_process_instance"
                + " (id,tenant_id,workflow_definition_id,business_key,initiator_id,status,start_time,end_time,suspended,deleted)"
                + " VALUES (?,?,1,?,1,?,?,?,?,?)")) {
            statement.setLong(1, id);
            statement.setString(2, tenant);
            statement.setString(3, "case-" + id);
            statement.setString(4, status);
            statement.setTimestamp(5, Timestamp.valueOf(start));
            statement.setTimestamp(6, end == null ? null : Timestamp.valueOf(end));
            statement.setInt(7, suspended);
            statement.setInt(8, deleted);
            statement.executeUpdate();
        }
    }

    private static void insertTask(
            Connection connection,
            long id,
            String tenant,
            String status,
            LocalDateTime timeout,
            int handled,
            Long transferred,
            int deleted)
            throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO wf_task"
                + " (id,tenant_id,process_instance_id,node_code,task_name,assignee_id,status,timeout_at,timeout_handled,transferred_from,deleted)"
                + " VALUES (?,?,1,'approval','approval',1,?,?,?,?,?)")) {
            statement.setLong(1, id);
            statement.setString(2, tenant);
            statement.setString(3, status);
            statement.setTimestamp(4, timeout == null ? null : Timestamp.valueOf(timeout));
            statement.setInt(5, handled);
            statement.setObject(6, transferred);
            statement.setInt(7, deleted);
            statement.executeUpdate();
        }
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private record TestDatabase(String vendor, JdbcDatabaseContainer<?> container) implements AutoCloseable {
        static TestDatabase create(String vendor) {
            JdbcDatabaseContainer<?> container = vendor.equals("mysql")
                    ? new MySQLContainer<>("mysql:9.0").withUsername("root")
                    : new PostgreSQLContainer<>("postgres:15");
            container
                    .withDatabaseName("apex_wf")
                    .withUrlParam("connectTimeout", vendor.equals("mysql") ? "5000" : "5")
                    .withUrlParam("socketTimeout", vendor.equals("mysql") ? "10000" : "10");
            if (vendor.equals("postgresql")) {
                container.withUrlParam("loginTimeout", "10");
            }
            try {
                container.start();
                return new TestDatabase(vendor, container);
            } catch (RuntimeException | Error error) {
                try {
                    container.close();
                } catch (RuntimeException cleanup) {
                    error.addSuppressed(cleanup);
                }
                throw error;
            }
        }

        Connection connect() throws SQLException {
            return DriverManager.getConnection(
                    container.getJdbcUrl(), container.getUsername(), container.getPassword());
        }

        Flyway flyway() {
            return Flyway.configure()
                    .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                    .locations("classpath:db/migration/" + vendor)
                    .baselineOnMigrate(false)
                    .cleanDisabled(true)
                    .validateMigrationNaming(true)
                    .load();
        }

        WorkflowStatisticsService statisticsService() throws SQLException {
            var configuration = new MybatisConfiguration();
            var dataSource = new DriverManagerDataSource(
                    container.getJdbcUrl(), container.getUsername(), container.getPassword());
            configuration.setDatabaseId(
                    new WorkflowPersistenceConfig().workflowDatabaseIdProvider().getDatabaseId(dataSource));
            configuration.setEnvironment(
                    new Environment("workflow-regression", new JdbcTransactionFactory(), dataSource));
            configuration.addMapper(ProcessInstanceMapper.class);
            configuration.addMapper(TaskMapper.class);
            var session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
            return new WorkflowStatisticsService(
                    session.getMapper(ProcessInstanceMapper.class), session.getMapper(TaskMapper.class));
        }

        @Override
        public void close() {
            container.close();
        }
    }

    /** 当前迁移链的最大版本号（新增迁移时无需手改断言）。 */
    private static int latestVersion() {
        try (java.util.stream.Stream<java.nio.file.Path> files =
                java.nio.file.Files.list(java.nio.file.Path.of("src/main/resources/db/migration/mysql"))) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("V") && name.endsWith(".sql"))
                    .mapToInt(name -> Integer.parseInt(name.substring(1, name.indexOf("__"))))
                    .max()
                    .orElseThrow();
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }
}
