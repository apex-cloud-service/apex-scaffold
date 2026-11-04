package com.apex.scaffold.workflowservice.infrastructure.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.apex.scaffold.common.util.JsonUtil;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 工作流库的本地 Outbox 投递器。
 *
 * <p>事件与业务写在同一事务内写入 {@code sys_domain_event_outbox}，本投递器按状态领取并
 * 交给进程内事件总线（{@code ApplicationEventPublisher}）或远程发布器。只有投递成功才标记
 * DELIVERED；失败按指数退避重试，超过上限转 DEAD。租户与事件 ID 均来自持久化信封。
 *
 * <p>本实现刻意只依赖 JDBC，不引入整套消息设施，避免改变工作流服务现有单数据源装配。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.events.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowEventOutboxDispatcher {

    private final JdbcTemplate jdbc;

    private final ApplicationEventPublisher localBus;

    private final WorkflowEventPublisher remotePublisher;

    private final boolean remoteChannel;

    private final int maxAttempts;

    /**
     * 构造投递器。
     *
     * @param dataSource 工作流库数据源
     * @param localBus 进程内事件总线
     * @param remotePublisher 远程发布器（本地模式为空信号）
     * @param remoteChannel 是否使用远程通道
     * @param maxAttempts 最大尝试次数
     */
    public WorkflowEventOutboxDispatcher(
            DataSource dataSource,
            ApplicationEventPublisher localBus,
            WorkflowEventPublisher remotePublisher,
            @Value("${apex.events.channel:auto}") String channel,
            @Value("${apex.events.outbox.max-attempts:8}") int maxAttempts) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.localBus = localBus;
        this.remotePublisher = remotePublisher;
        this.remoteChannel = !"local".equalsIgnoreCase(channel);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /**
     * 周期性领取并投递待发事件。
     */
    @Scheduled(fixedDelayString = "${apex.events.outbox.poll-delay-ms:1000}")
    public void dispatch() {
        List<Pending> pending = claim();
        for (Pending record : pending) {
            deliver(record);
        }
    }

    /**
     * 抢占一批待投递记录（单实例工作流库，直接条件更新即可）。
     *
     * @return 本批记录
     */
    private List<Pending> claim() {
        List<Pending> records = new ArrayList<>();
        jdbc.query(
                "SELECT id, event_id, event_class, payload FROM sys_domain_event_outbox "
                        + "WHERE status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP "
                        + "ORDER BY id LIMIT 50",
                (ResultSet rs) -> {
                    records.add(new Pending(
                            rs.getLong("id"),
                            rs.getString("event_id"),
                            rs.getString("event_class"),
                            rs.getString("payload")));
                });
        for (Pending record : records) {
            jdbc.update(
                    "UPDATE sys_domain_event_outbox SET status='PROCESSING', locked_at=CURRENT_TIMESTAMP "
                            + "WHERE id=? AND status='PENDING'",
                    record.id());
        }
        return records;
    }

    /**
     * 投递单条事件；成功标记 DELIVERED，失败退避或转 DEAD。
     *
     * @param record 待投递记录
     */
    private void deliver(Pending record) {
        try {
            Class<?> type = Class.forName(record.eventClass());
            if (!AbstractDomainEvent.class.isAssignableFrom(type)) {
                throw new IllegalStateException("不支持的事件类型: " + record.eventClass());
            }
            @SuppressWarnings("unchecked")
            Class<? extends AbstractDomainEvent> eventType = (Class<? extends AbstractDomainEvent>) type;
            AbstractDomainEvent event = JsonUtil.getObjectMapper().readValue(record.payload(), eventType);
            if (remoteChannel) {
                remotePublisher.publish(event).block();
            } else {
                localBus.publishEvent(event);
            }
            jdbc.update(
                    "UPDATE sys_domain_event_outbox SET status='DELIVERED', delivered_at=CURRENT_TIMESTAMP, "
                            + "last_error=NULL WHERE id=?",
                    record.id());
        } catch (Exception error) {
            int attempts = nextAttempts(record.id());
            if (attempts >= maxAttempts) {
                jdbc.update(
                        "UPDATE sys_domain_event_outbox SET status='DEAD', attempts=?, last_error=? WHERE id=?",
                        attempts,
                        truncate(error.getMessage()),
                        record.id());
                log.error("工作流事件进入死信: eventId={}, attempts={}", record.eventId(), attempts, error);
            } else {
                jdbc.update(
                        "UPDATE sys_domain_event_outbox SET status='PENDING', attempts=?, "
                                + "next_attempt_at=?, last_error=? WHERE id=?",
                        attempts,
                        LocalDateTime.now().plusSeconds(backoffSeconds(attempts)),
                        truncate(error.getMessage()),
                        record.id());
                log.warn("工作流事件投递失败，等待重试: eventId={}, attempts={}", record.eventId(), attempts);
            }
        }
    }

    private int nextAttempts(long id) {
        Integer current =
                jdbc.queryForObject("SELECT attempts FROM sys_domain_event_outbox WHERE id=?", Integer.class, id);
        return (current == null ? 0 : current) + 1;
    }

    private long backoffSeconds(int attempts) {
        return Math.min(300L, 5L * (1L << Math.min(attempts, 6)));
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    /**
     * 待投递记录.
     *
     * @param id 主键
     * @param eventId 事件标识
     * @param payload 事件 JSON
     */
    private record Pending(long id, String eventId, String eventClass, String payload) {}
}
