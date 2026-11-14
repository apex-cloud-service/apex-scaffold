package com.apex.scaffold.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 直接执行生产 Store SQL 的隔离双库回归测试。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@EnabledIfEnvironmentVariable(named = "OUTBOX_DUAL_DB", matches = "true")
class DomainEventOutboxDialectTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private DomainEventOutboxStore store;

    private void initialize(String dialect) throws Exception {
        String prefix = "OUTBOX_" + dialect.toUpperCase();
        dataSource = new DriverManagerDataSource(
                System.getenv(prefix + "_URL"), System.getenv(prefix + "_USER"), System.getenv(prefix + "_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS sys_domain_event_outbox");
        Path root = Path.of(System.getProperty("maven.multiModuleProjectDirectory"));
        String schema =
                Files.readString(root.resolve("apex-services-bom/apex-auth-service/src/main/resources/db/migration/"
                        + dialect + "/V56__create_domain_event_outbox.sql"));
        for (String statement : schema.split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement);
            }
        }
        store = new DomainEventOutboxStore(dataSource);
    }

    private Instant databaseNow() {
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class).toInstant();
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void appendIsIdempotentAndSyncRespectsRollback(String dialect) throws Exception {
        initialize(dialect);
        TestEvent event = new TestEvent("duplicate");
        store.append(event);
        store.append(event);
        assertThat(store.countByStatus()).containsEntry("PENDING", 1L);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            store.appendSync(connection, new TestEvent("rollback"));
            connection.rollback();
        }
        assertThat(store.existsByEventId("rollback")).isFalse();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            store.appendSync(connection, new TestEvent("commit"));
            connection.commit();
        }
        assertThat(store.existsByEventId("commit")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void concurrentClaimsAreDisjointAndBounded(String dialect) throws Exception {
        initialize(dialect);
        for (int index = 0; index < 20; index++) {
            store.append(new TestEvent("concurrent-" + index));
        }
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                start.await();
                return claimAfterDeadlock("first");
            });
            var second = executor.submit(() -> {
                start.await();
                return claimAfterDeadlock("second");
            });
            start.countDown();
            List<OutboxEventRecord> left = first.get(20, TimeUnit.SECONDS);
            List<OutboxEventRecord> right = second.get(20, TimeUnit.SECONDS);
            assertThat(left).hasSize(10);
            assertThat(right).hasSize(10);
            var ids = new HashSet<Long>();
            left.forEach(record -> assertThat(ids.add(record.id())).isTrue());
            right.forEach(record -> assertThat(ids.add(record.id())).isTrue());
            assertThat(ids).hasSize(20);
            assertThat(store.claim("third", 10, 120)).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    private List<OutboxEventRecord> claimAfterDeadlock(String worker) {
        try {
            return store.claim(worker, 10, 120);
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof java.sql.SQLException sqlException
                    && "40001".equals(sqlException.getSQLState())) {
                // 与调度器下轮重新领取一致：MySQL RR 并发更新允许死锁回滚后重试。
                return store.claim(worker, 10, 120);
            }
            throw exception;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void claimAndRetentionExecuteAgainstExistingRows(String dialect) throws Exception {
        initialize(dialect);
        jdbc.update("INSERT INTO sys_domain_event_outbox (event_id,event_class,payload) VALUES ('seed','test','{}')");
        var record = store.claim("worker", 1, 120).get(0);
        store.markDelivered(record.id(), "worker");
        jdbc.update(
                "UPDATE sys_domain_event_outbox SET delivered_at=?",
                Timestamp.from(databaseNow().minusSeconds(7200)));
        assertThat(store.deleteDeliveredOlderThan(1, 1, 2)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void retryTimeoutAndOwnershipRemainIntact(String dialect) throws Exception {
        initialize(dialect);
        store.append(new TestEvent("retry"));
        var record = store.claim("owner", 1, 120).get(0);
        store.markDelivered(record.id(), "wrong-owner");
        assertThat(store.countByStatus()).containsEntry("PROCESSING", 1L);
        store.markFailed(record, "owner", 2, "temporary");
        assertThat(store.claim("early", 1, 120)).isEmpty();
        jdbc.update(
                "UPDATE sys_domain_event_outbox SET next_attempt_at=?",
                Timestamp.from(databaseNow().minusSeconds(1)));
        record = store.claim("retry-owner", 1, 120).get(0);
        assertThat(record.attempts()).isEqualTo(1);
        jdbc.update(
                "UPDATE sys_domain_event_outbox SET locked_at=?",
                Timestamp.from(databaseNow().minusSeconds(300)));
        record = store.claim("recovered", 1, 120).get(0);
        store.markFailed(record, "retry-owner", 2, "stale-owner");
        assertThat(store.countByStatus()).containsEntry("PROCESSING", 1L);
        store.markFailed(record, "recovered", 2, "dead");
        assertThat(store.countByStatus()).containsEntry("DEAD", 1L);
        jdbc.update(
                "UPDATE sys_domain_event_outbox SET created_at=?",
                Timestamp.from(databaseNow().minusSeconds(172800)));
        assertThat(store.deleteDeadOlderThan(1, 1, 2)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void deadDuplicateRevivesForDelivery(String dialect) throws Exception {
        initialize(dialect);
        TestEvent event = new TestEvent("revive");
        store.append(event);
        var record = store.claim("owner", 1, 120).get(0);
        store.markFailed(record, "owner", 1, "dead");
        store.append(event);
        assertThat(store.countByStatus()).containsEntry("PENDING", 1L);
        assertThat(store.claim("revived", 1, 120)).singleElement().satisfies(revived -> assertThat(revived.attempts())
                .isZero());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "postgresql"})
    void deliveredDuplicateDoesNotRequeue(String dialect) throws Exception {
        initialize(dialect);
        TestEvent event = new TestEvent("delivered");
        store.append(event);
        var record = store.claim("owner", 1, 120).get(0);
        store.markDelivered(record.id(), "owner");
        store.append(event);
        assertThat(store.countByStatus()).containsEntry("DELIVERED", 1L);
        assertThat(store.claim("again", 1, 120)).isEmpty();
    }

    private static class TestEvent extends AbstractDomainEvent {
        TestEvent(String id) {
            super("outbox-dialect", "test", id);
        }

        @Override
        public String getSourceId() {
            return "test";
        }
    }
}
