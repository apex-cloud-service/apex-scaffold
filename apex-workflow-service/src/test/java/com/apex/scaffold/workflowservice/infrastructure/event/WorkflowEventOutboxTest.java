package com.apex.scaffold.workflowservice.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.contract.event.TaskCompletedEvent;
import com.apex.scaffold.workflow.domain.repository.WorkflowEventOutbox;
import com.apex.scaffold.workflow.infrastructure.persistence.repository.JdbcWorkflowEventOutbox;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 工作流事件与业务写同事务落箱、投递成功的回归测试。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("工作流事件 Outbox")
class WorkflowEventOutboxTest {

    private Connection connection;

    private WorkflowEventOutbox outbox;

    private final List<Object> delivered = new ArrayList<>();

    private WorkflowEventOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:wf-outbox;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        connection
                .createStatement()
                .execute("CREATE TABLE IF NOT EXISTS sys_domain_event_outbox ("
                        + "id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(64) NOT NULL, "
                        + "event_class VARCHAR(255) NOT NULL, payload TEXT NOT NULL, "
                        + "status VARCHAR(16) NOT NULL, attempts INT NOT NULL DEFAULT 0, "
                        + "next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "locked_by VARCHAR(64), locked_at TIMESTAMP, last_error VARCHAR(1000), "
                        + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, delivered_at TIMESTAMP, "
                        + "UNIQUE (event_id))");
        outbox = new JdbcWorkflowEventOutbox();
        ApplicationEventPublisher bus = delivered::add;
        dispatcher = new WorkflowEventOutboxDispatcher(
                new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true),
                bus,
                new WorkflowEventPublisher() {
                    @Override
                    public reactor.core.publisher.Mono<Void> publish(
                            com.apex.scaffold.common.event.AbstractDomainEvent event) {
                        return reactor.core.publisher.Mono.empty();
                    }
                },
                "local",
                3);
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.createStatement().execute("DROP TABLE IF EXISTS sys_domain_event_outbox");
        connection.close();
    }

    @Test
    @DisplayName("业务事务回滚时事件不落箱")
    void shouldNotPersistEventWhenTransactionRollsBack() throws Exception {
        connection.setAutoCommit(false);
        outbox.appendSync(connection, completedEvent());
        connection.rollback();
        assertThat(countRows()).isZero();
    }

    @Test
    @DisplayName("业务事务提交后事件保留并可按稳定 ID 投递")
    void shouldPersistAndDeliverAfterCommit() throws Exception {
        connection.setAutoCommit(false);
        TaskCompletedEvent event = completedEvent();
        outbox.appendSync(connection, event);
        connection.commit();

        assertThat(countRows()).isOne();

        dispatcher.dispatch();

        assertThat(countRows()).isOne();
        assertThat(column("status")).isEqualTo("DELIVERED");
        assertThat(delivered).hasSize(1);
        assertThat(((TaskCompletedEvent) delivered.get(0)).getEventId()).isEqualTo(event.getEventId());
    }

    @Test
    @DisplayName("投递失败保留 PENDING 并累计尝试次数，不提前标记完成")
    void shouldKeepPendingOnDeliveryFailure() throws Exception {
        ApplicationEventPublisher failing = published -> {
            throw new IllegalStateException("listener unavailable");
        };
        WorkflowEventOutboxDispatcher failingDispatcher = new WorkflowEventOutboxDispatcher(
                new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true),
                failing,
                new WorkflowEventPublisher() {
                    @Override
                    public reactor.core.publisher.Mono<Void> publish(
                            com.apex.scaffold.common.event.AbstractDomainEvent event) {
                        return reactor.core.publisher.Mono.empty();
                    }
                },
                "local",
                5);
        connection.setAutoCommit(false);
        outbox.appendSync(connection, completedEvent());
        connection.commit();

        failingDispatcher.dispatch();

        assertThat(column("status")).isEqualTo("PENDING");
        assertThat(Integer.parseInt(column("attempts"))).isPositive();
    }

    private TaskCompletedEvent completedEvent() {
        TaskCompletedEvent event = new TaskCompletedEvent(5001L, 3001L, 100L, "APPROVE", "ok", null);
        event.attachTenantContext("1");
        return event;
    }

    private int countRows() {
        Integer count = jdbc().queryForObject("SELECT COUNT(*) FROM sys_domain_event_outbox", Integer.class);
        return count == null ? 0 : count;
    }

    private String column(String name) {
        return jdbc().queryForObject("SELECT " + name + " FROM sys_domain_event_outbox", String.class);
    }

    private org.springframework.jdbc.core.JdbcTemplate jdbc() {
        return new org.springframework.jdbc.core.JdbcTemplate(
                new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
    }
}
