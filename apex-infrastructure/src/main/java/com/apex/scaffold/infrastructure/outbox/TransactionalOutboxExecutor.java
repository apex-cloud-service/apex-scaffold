package com.apex.scaffold.infrastructure.outbox;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.apex.scaffold.infrastructure.event.DomainEventOutboxStore;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 事务性 Outbox 执行器。
 *
 * <p>在同一数据库事务内执行"业务写 + Outbox 写"，保证两者原子性：业务成功则事件落箱，
 * 业务异常则事件随事务一并回滚。用于替代人为的"先发消息后改库"或"先改库后发消息"等
 * 存在一致性窗口的顺序编排，交由 RocketMQ 事务消息在回查阶段与 Outbox 记录对账收敛。</p>
 *
 * <p>复用 {@link DomainEventOutboxStore#appendSync(Connection, AbstractDomainEvent)}，
 * 由本类把事务绑定的 {@link DataSourceUtils#getConnection} 连接传入，使 Outbox 写入
 * 与业务写入共享同一事务边界，写入完成后再由 {@link DataSourceUtils#releaseConnection}
 * 归还连接，交由 {@link TransactionTemplate} 统一提交或回滚。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.4.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionalOutboxExecutor {

    /** 编程式事务模板：控制事务开启、提交与回滚。 */
    private final TransactionTemplate transactionTemplate;

    /** 领域事件 Outbox 存储。 */
    private final DomainEventOutboxStore outboxStore;

    /** 与 {@link TransactionTemplate} 一致的业务数据源，用于获取事务连接。 */
    private final DataSource dataSource;

    /**
     * 在单个事务内执行业务并登记 Outbox 事件。
     *
     * <p>业务回调与事件落箱在同一事务中执行：任一失败都会整体回滚。抛出的异常将连同
     * 底层原因一并向上传递，交由调用方决定是否继续对外抛错。</p>
     *
     * @param business 业务动作；应为阻塞写入（仓储/MyBatis/JDBC），不得包含网络或耗时 I/O
     * @param event    需要与该业务原子登记的领域事件
     */
    public void execute(Runnable business, AbstractDomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> {
            business.run();
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                outboxStore.appendSync(connection, event);
            } catch (SQLException exception) {
                throw new IllegalStateException("领域事件写入 Outbox 失败", exception);
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        });
    }
}
