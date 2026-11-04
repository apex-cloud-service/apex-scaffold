package com.apex.scaffold.workflow.infrastructure.persistence.repository;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.workflow.domain.repository.WorkflowEventOutbox;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.springframework.stereotype.Component;

/**
 * 工作流事件 Outbox 的 JDBC 实现：直接使用调用方事务连接写入工作流库。
 *
 * <p>表结构与共享 Outbox 保持一致（PENDING/PROCESSING/DELIVERED/DEAD + 唯一 event_id），
 * 由工作流服务的 Outbox 重放器消费。写入使用事件 ID 唯一约束保证重复落箱不产生重复事件。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Component
public class JdbcWorkflowEventOutbox implements WorkflowEventOutbox {

    @Override
    public void appendSync(Connection connection, AbstractDomainEvent event) throws SQLException {
        String sql = isPostgresql(connection)
                ? "INSERT INTO sys_domain_event_outbox "
                        + "(event_id,event_class,payload,status,attempts,next_attempt_at,created_at) "
                        + "VALUES (?,?,?,'PENDING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (event_id) DO UPDATE SET status='PENDING',attempts=0,"
                        + "next_attempt_at=CURRENT_TIMESTAMP,locked_by=NULL,locked_at=NULL,last_error=NULL "
                        + "WHERE sys_domain_event_outbox.status='DEAD'"
                : "INSERT INTO sys_domain_event_outbox "
                        + "(event_id,event_class,payload,status,attempts,next_attempt_at,created_at) "
                        + "VALUES (?,?,?,'PENDING',0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)) "
                        + "ON DUPLICATE KEY UPDATE event_id=VALUES(event_id)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.getEventId());
            statement.setString(2, event.getClass().getName());
            statement.setString(3, JsonUtil.toJson(event));
            statement.executeUpdate();
        }
    }

    private boolean isPostgresql(Connection connection) throws SQLException {
        return "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName());
    }
}
