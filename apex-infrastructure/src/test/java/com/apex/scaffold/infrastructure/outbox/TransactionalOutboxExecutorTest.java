package com.apex.scaffold.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.infrastructure.event.DomainEventOutboxStore;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link TransactionalOutboxExecutor} 事务原子性行为测试。
 *
 * <p>infrastructure 模块没有 Spring 测试上下文，故以 H2（MySQL 兼容模式）+ 真实
 * {@link DataSourceTransactionManager} 自包含验证"业务写 + Outbox 写"是否同事务提交/回滚。
 * 业务写入通过 {@link DataSourceUtils#getConnection} 走事务绑定连接，与生产端
 * {@code TransactionTemplate} 协作方式一致。</p>
 *
 * <p>生产端 {@link DomainEventOutboxStore#appendSync} 使用 MySQL 方言
 * （{@code ON DUPLICATE KEY UPDATE ... IF()}），H2 兼容模式无法解析；本测试以等价语义的
 * H2 友好子类替换落库实现，从而在同库同事务上真实验证提交与回滚的原子性。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.4.0
 */
@DisplayName("TransactionalOutboxExecutor 事务原子性")
class TransactionalOutboxExecutorTest {

    private static HikariDataSource dataSource;
    private static DataSourceTransactionManager transactionManager;

    private DomainEventOutboxStore outboxStore;
    private TransactionalOutboxExecutor executor;

    @BeforeAll
    static void setUpDataSource() throws Exception {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:h2:mem:tx_outbox_executor_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
                + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setDriverClassName("org.h2.Driver");
        createSchema(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
    }

    private static void createSchema(DataSource ds) throws SQLException {
        try (Connection connection = ds.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS sys_domain_event_outbox");
            statement.execute("CREATE TABLE sys_domain_event_outbox ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                    + "event_id VARCHAR(64) NOT NULL UNIQUE, event_class VARCHAR(255) NOT NULL, "
                    + "payload CLOB NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING', "
                    + "attempts INT NOT NULL DEFAULT 0, next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), "
                    + "locked_by VARCHAR(64), locked_at DATETIME(6), last_error VARCHAR(1000), "
                    + "created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), delivered_at DATETIME(6))");
            statement.execute("DROP TABLE IF EXISTS sys_biz_records");
            statement.execute(
                    "CREATE TABLE sys_biz_records (" + "id BIGINT NOT NULL PRIMARY KEY, biz_key VARCHAR(64) NOT NULL)");
        }
    }

    @BeforeEach
    void setUpExecutor() {
        outboxStore = new H2FriendlyOutboxStore(dataSource);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        executor = new TransactionalOutboxExecutor(transactionTemplate, outboxStore, dataSource);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM sys_domain_event_outbox");
            statement.execute("DELETE FROM sys_biz_records");
        } catch (SQLException exception) {
            throw new IllegalStateException("重置测试数据失败", exception);
        }
    }

    @AfterAll
    static void tearDownDataSource() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("业务与Outbox在同一事务中一并提交")
    void shouldCommitBusinessAndOutboxAtomically() {
        TestDomainEvent event = new TestDomainEvent("evt-commit-1");

        executor.execute(() -> writeBizInBoundTransaction(1L, "biz-commit"), event);

        assertThat(isBizRecordPresent(1L)).isTrue();
        assertThat(isOutboxRecordPresent(event.getEventId())).isTrue();
    }

    @Test
    @DisplayName("业务异常时业务与Outbox一同回滚")
    void shouldRollBackBusinessAndOutboxTogetherOnBusinessFailure() {
        TestDomainEvent event = new TestDomainEvent("evt-rollback-1");

        assertThatThrownBy(() -> executor.execute(
                        () -> {
                            writeBizInBoundTransaction(2L, "biz-rollback");
                            throw new IllegalStateException("模拟业务失败");
                        },
                        event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("模拟业务失败");

        assertThat(isBizRecordPresent(2L)).isFalse();
        assertThat(isOutboxRecordPresent(event.getEventId())).isFalse();
    }

    @Test
    @DisplayName("Outbox写入失败时业务一并回滚")
    void shouldRollBackBusinessWhenOutboxWriteFails() {
        DomainEventOutboxStore failingStore = new FailingOutboxStore(dataSource);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        TransactionalOutboxExecutor failingExecutor =
                new TransactionalOutboxExecutor(transactionTemplate, failingStore, dataSource);

        assertThatThrownBy(() -> failingExecutor.execute(
                        () -> writeBizInBoundTransaction(3L, "biz-outbox-fail"),
                        new TestDomainEvent("evt-outbox-fail")))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(SQLException.class);

        assertThat(isBizRecordPresent(3L)).isFalse();
    }

    @Test
    @DisplayName("真实存储实例应正确判断事件是否已登记")
    void realStoreExistsByEventIdQuery() throws SQLException {
        DomainEventOutboxStore realStore = new DomainEventOutboxStore(dataSource);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO sys_domain_event_outbox "
                    + "(event_id, event_class, payload) VALUES ('evt-existing', 'clazz', '{}')");
        }

        assertThat(realStore.existsByEventId("evt-existing")).isTrue();
        assertThat(realStore.existsByEventId("evt-absent")).isFalse();
        assertThat(realStore.existsByEventId("  ")).isFalse();
        assertThat(realStore.existsByEventId(null)).isFalse();
    }

    /** 业务写入须走事务绑定连接，才能与 Outbox 写入共享事务边界。 */
    private void writeBizInBoundTransaction(long id, String key) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement statement =
                connection.prepareStatement("INSERT INTO sys_biz_records (id, biz_key) VALUES (?, ?)")) {
            statement.setLong(1, id);
            statement.setString(2, key);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("写入测试业务表失败", exception);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private boolean isBizRecordPresent(long id) {
        return isPresent("SELECT 1 FROM sys_biz_records WHERE id = ?", statement -> statement.setLong(1, id));
    }

    private boolean isOutboxRecordPresent(String eventId) {
        return isPresent(
                "SELECT 1 FROM sys_domain_event_outbox WHERE event_id = ?",
                statement -> statement.setString(1, eventId));
    }

    private boolean isPresent(String sql, StatementBinder binder) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("查询测试数据失败", exception);
        }
    }

    /** 绑定 PreparedStatement 参数的函数式接口。 */
    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    /**
     * H2 友好的 Outbox 存储：以等价语义的 INSERT 替换 MySQL 方言，便于在 H2 上验证事务边界。
     */
    private static class H2FriendlyOutboxStore extends DomainEventOutboxStore {

        H2FriendlyOutboxStore(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public void appendSync(Connection connection, AbstractDomainEvent event) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO sys_domain_event_outbox (event_id, event_class, payload) VALUES (?, ?, ?)")) {
                statement.setString(1, event.getEventId());
                statement.setString(2, event.getClass().getName());
                statement.setString(3, JsonUtil.toJson(event));
                statement.executeUpdate();
            }
        }
    }

    /** 恒定写入失败的 Outbox 存储，用于验证 Outbox 失败时业务回滚。 */
    private static final class FailingOutboxStore extends DomainEventOutboxStore {

        FailingOutboxStore(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        public void appendSync(Connection connection, AbstractDomainEvent event) throws SQLException {
            throw new SQLException("outbox down");
        }
    }

    /** 测试用最小领域事件。 */
    private static final class TestDomainEvent extends AbstractDomainEvent {
        TestDomainEvent(String eventId) {
            super("TEST", null, eventId);
        }

        @Override
        public String getSourceId() {
            return getEventId();
        }
    }
}
