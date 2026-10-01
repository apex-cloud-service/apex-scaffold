package com.apex.scaffold.infrastructure.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.apex.scaffold.common.util.JsonUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 基于 JDBC 的领域事件 Outbox 存储。
 *
 * <p>事件 ID 唯一约束保证重复发布不会生成两条记录。领取动作使用实例令牌完成批次隔离，
 * 超时的 PROCESSING 记录会重新进入待投递状态。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 */
@Repository
@RequiredArgsConstructor
public class DomainEventOutboxStore {

    /**
     * 在当前 JDBC 连接上同步持久化领域事件。
     *
     * <p>与 {@link #append(AbstractDomainEvent)} 不同，该方法接收外部传入的 JDBC 连接，
     * 由调用方控制事务边界。适用于需要在同一事务中同步写入业务记录和事件记录的场景。</p>
     *
     * <p>事件 ID 唯一约束命中时：正在投递或已投递的记录保持原状（避免重复投递）；
     * 已进入 DEAD 的记录被复活为 PENDING 并清零重试计数，支持业务重试后重新投递。</p>
     *
     * @param connection 已打开的 JDBC 连接（调用方管理生命周期）
     * @param event      领域事件
     * @throws SQLException 写入失败时抛出
     */
    public void appendSync(Connection connection, AbstractDomainEvent event) throws SQLException {
        String sql = "INSERT INTO sys_domain_event_outbox "
                + "(event_id,event_class,payload,status,attempts,next_attempt_at,created_at) "
                + "VALUES (?,?,?,'PENDING',0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)) "
                + "ON DUPLICATE KEY UPDATE event_id=VALUES(event_id),"
                + "status=IF(status='DEAD','PENDING',status),"
                + "attempts=IF(status='DEAD',0,attempts),"
                + "next_attempt_at=IF(status='DEAD',CURRENT_TIMESTAMP(6),next_attempt_at),"
                + "locked_by=IF(status='DEAD',NULL,locked_by),"
                + "locked_at=IF(status='DEAD',NULL,locked_at),"
                + "last_error=IF(status='DEAD',NULL,last_error)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.getEventId());
            statement.setString(2, event.getClass().getName());
            statement.setString(3, JsonUtil.toJson(event));
            statement.executeUpdate();
        }
    }

    /** 数据库错误摘要最大长度。 */
    private static final int MAX_ERROR_LENGTH = 1000;

    /** 业务数据源。 */
    private final DataSource dataSource;

    /**
     * 持久化领域事件。
     *
     * <p>事件 ID 唯一约束命中时：正在投递或已投递的记录保持原状（避免重复投递）；
     * 已进入 DEAD 的记录被复活为 PENDING 并清零重试计数，支持业务重试后重新投递。</p>
     *
     * @param event 领域事件
     */
    public void append(AbstractDomainEvent event) {
        String sql = "INSERT INTO sys_domain_event_outbox "
                + "(event_id,event_class,payload,status,attempts,next_attempt_at,created_at) "
                + "VALUES (?,?,?,'PENDING',0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)) "
                + "ON DUPLICATE KEY UPDATE event_id=VALUES(event_id),"
                + "status=IF(status='DEAD','PENDING',status),"
                + "attempts=IF(status='DEAD',0,attempts),"
                + "next_attempt_at=IF(status='DEAD',CURRENT_TIMESTAMP(6),next_attempt_at),"
                + "locked_by=IF(status='DEAD',NULL,locked_by),"
                + "locked_at=IF(status='DEAD',NULL,locked_at),"
                + "last_error=IF(status='DEAD',NULL,last_error)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.getEventId());
            statement.setString(2, event.getClass().getName());
            statement.setString(3, JsonUtil.toJson(event));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("领域事件写入 Outbox 失败", exception);
        }
    }

    /**
     * 按状态统计 Outbox 记录数量。
     *
     * <p>用于监控积压情况：PENDING 待投递、PROCESSING 处理中、DEAD 超过重试上限。
     * 查询走单列状态分组，数据量小，监控间隔下无性能风险。</p>
     *
     * @return 状态到数量的映射；空表返回空 Map
     */
    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new HashMap<>(16);
        String sql = "SELECT status, COUNT(*) FROM sys_domain_event_outbox GROUP BY status";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                counts.put(resultSet.getString(1), resultSet.getLong(2));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("统计 Outbox 状态数量失败", exception);
        }
        return counts;
    }

    /**
     * 原子领取一批待投递事件。
     *
     * @param workerId 当前实例领取令牌
     * @param batchSize 最大领取数量
     * @param lockTimeoutSeconds 处理锁超时秒数
     * @return 当前实例领取的记录
     */
    public List<OutboxEventRecord> claim(String workerId, int batchSize, int lockTimeoutSeconds) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            recoverTimedOutRecords(connection, lockTimeoutSeconds);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE sys_domain_event_outbox SET status='PROCESSING',locked_by=?,locked_at=CURRENT_TIMESTAMP(6) "
                            + "WHERE status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP(6) ORDER BY id LIMIT ?")) {
                statement.setString(1, workerId);
                statement.setInt(2, batchSize);
                statement.executeUpdate();
            }
            List<OutboxEventRecord> records = selectClaimed(connection, workerId);
            connection.commit();
            return records;
        } catch (SQLException exception) {
            throw new IllegalStateException("领取 Outbox 事件失败", exception);
        }
    }

    /**
     * 标记事件投递完成。
     *
     * @param id Outbox 主键
     * @param workerId 领取令牌
     */
    public void markDelivered(long id, String workerId) {
        update(
                "UPDATE sys_domain_event_outbox SET status='DELIVERED',delivered_at=CURRENT_TIMESTAMP(6),"
                        + "locked_by=NULL,locked_at=NULL,last_error=NULL WHERE id=? AND locked_by=?",
                id,
                workerId);
    }

    /**
     * 记录投递失败，并按指数退避重试；超过阈值后进入 DEAD 状态。
     *
     * @param record 失败记录
     * @param workerId 领取令牌
     * @param maxAttempts 最大失败次数
     * @param error 错误摘要
     */
    public void markFailed(OutboxEventRecord record, String workerId, int maxAttempts, String error) {
        int attempts = record.attempts() + 1;
        boolean dead = attempts >= maxAttempts;
        long delaySeconds = Math.min(300L, 1L << Math.min(attempts, 8));
        Timestamp nextAttempt = Timestamp.from(Instant.now().plusSeconds(delaySeconds));
        update(
                "UPDATE sys_domain_event_outbox SET status=?,attempts=?,next_attempt_at=?,last_error=?,"
                        + "locked_by=NULL,locked_at=NULL WHERE id=? AND locked_by=?",
                record.id(),
                workerId,
                dead ? "DEAD" : "PENDING",
                attempts,
                nextAttempt,
                abbreviate(error));
    }

    /**
     * 删除超过保留期的已投递记录。
     *
     * <p>按 {@code delivered_at} 判断保留期，分批删除避免长事务锁，直到删除行数不足
     * 一批或达到 {@code maxBatches} 批为止。</p>
     *
     * @param olderThanHours 保留小时数
     * @param batchSize      单批删除行数上限
     * @param maxBatches     最大批次数
     * @return 实际删除的行数
     */
    public int deleteDeliveredOlderThan(int olderThanHours, int batchSize, int maxBatches) {
        return deleteRetained("DELIVERED", "delivered_at", olderThanHours, batchSize, maxBatches);
    }

    /**
     * 删除超过保留期的死信记录。
     *
     * <p>按 {@code created_at} 判断保留期，分批删除避免长事务锁。死信保留期通常长于
     * 已投递记录，便于人工排查后由系统自动收敛。</p>
     *
     * @param olderThanDays 保留天数
     * @param batchSize     单批删除行数上限
     * @param maxBatches    最大批次数
     * @return 实际删除的行数
     */
    public int deleteDeadOlderThan(int olderThanDays, int batchSize, int maxBatches) {
        return deleteRetained("DEAD", "created_at", olderThanDays * 24, batchSize, maxBatches);
    }

    /**
     * 分批删除指定状态的过期记录。
     *
     * @param status         目标状态（DELIVERED/DEAD）
     * @param timeColumn     保留期判断的时间列（delivered_at/created_at）
     * @param olderThanHours 保留小时数
     * @param batchSize      单批删除行数上限
     * @param maxBatches     最大批次数
     * @return 实际删除的行数
     */
    private int deleteRetained(String status, String timeColumn, int olderThanHours, int batchSize, int maxBatches) {
        String sql = "DELETE FROM sys_domain_event_outbox WHERE status=? AND " + timeColumn
                + " < TIMESTAMPADD(HOUR,-?,CURRENT_TIMESTAMP(6)) LIMIT ?";
        int total = 0;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int batch = 0; batch < maxBatches; batch++) {
                statement.setString(1, status);
                statement.setInt(2, olderThanHours);
                statement.setInt(3, batchSize);
                int deleted = statement.executeUpdate();
                total += deleted;
                if (deleted < batchSize) {
                    break;
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("清理 Outbox " + status + " 记录失败", exception);
        }
        return total;
    }

    /** 恢复超过处理时限的记录。 */
    private void recoverTimedOutRecords(Connection connection, int lockTimeoutSeconds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE sys_domain_event_outbox SET status='PENDING',locked_by=NULL,locked_at=NULL "
                        + "WHERE status='PROCESSING' AND locked_at<TIMESTAMPADD(SECOND,-?,CURRENT_TIMESTAMP(6))")) {
            statement.setInt(1, lockTimeoutSeconds);
            statement.executeUpdate();
        }
    }

    /** 查询当前令牌领取的事件。 */
    private List<OutboxEventRecord> selectClaimed(Connection connection, String workerId) throws SQLException {
        List<OutboxEventRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,event_id,event_class,payload,attempts FROM sys_domain_event_outbox "
                        + "WHERE status='PROCESSING' AND locked_by=? ORDER BY id")) {
            statement.setString(1, workerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(new OutboxEventRecord(
                            resultSet.getLong("id"),
                            resultSet.getString("event_id"),
                            resultSet.getString("event_class"),
                            resultSet.getString("payload"),
                            resultSet.getInt("attempts")));
                }
            }
        }
        return records;
    }

    /** 执行带领取令牌校验的状态更新。 */
    private void update(String sql, long id, String workerId, Object... values) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (Object value : values) {
                statement.setObject(index++, value);
            }
            statement.setLong(index++, id);
            statement.setString(index, workerId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("更新 Outbox 状态失败", exception);
        }
    }

    /** 截断数据库错误摘要。 */
    private String abbreviate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }
}
