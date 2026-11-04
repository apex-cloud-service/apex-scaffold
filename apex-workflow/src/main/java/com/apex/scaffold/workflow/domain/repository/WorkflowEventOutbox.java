package com.apex.scaffold.workflow.domain.repository;

import com.apex.scaffold.common.event.AbstractDomainEvent;

/**
 * 工作流事件 Outbox 端口：在业务写事务的同一 JDBC 连接上落箱。
 *
 * <p>用于把"任务/实例状态变更"与"待投递事件"放进同一数据库事务，
 * 避免业务提交后事件直发失败导致的丢失窗口。实现必须使用调用方事务连接，
 * 不得另开连接或切换到事件线程池。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public interface WorkflowEventOutbox {

    /**
     * 在给定事务连接上追加事件。
     *
     * @param connection 调用方事务内的 JDBC 连接
     * @param event 待投递事件（含完整信封与租户）
     * @throws java.sql.SQLException 写入失败时抛出，由事务回滚
     */
    void appendSync(java.sql.Connection connection, AbstractDomainEvent event) throws java.sql.SQLException;
}
