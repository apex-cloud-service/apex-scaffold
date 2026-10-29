package com.apex.scaffold.workflow.domain.repository;

import com.apex.scaffold.workflow.domain.model.ProcessInstance;
import com.apex.scaffold.workflow.domain.model.Task;
import com.apex.scaffold.workflow.domain.model.WorkflowDefinition;
import java.util.function.Consumer;

/**
 * 同一租户内多步任务写入的同步事务端口。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface WorkflowTaskTransaction {

    /**
     * 在同一同步事务与线程内执行操作，异常触发整体回滚。
     *
     * @param tenant 可信租户标识
     * @param operation 事务内执行的仓储操作
     */
    void execute(String tenant, Consumer<Operations> operation);

    /** 仅供同步事务回调使用的仓储边界。 */
    interface Operations {

        /**
         * 查询当前租户任务，不存在时返回空。
         *
         * @param id 任务主键
         * @return 任务实体，不存在时返回 {@code null}
         */
        Task findTask(Long id);

        /**
         * 按租户、主键、待办状态和读取版本竞争更新任务。
         *
         * @param task 携带读取版本的目标任务
         * @return 受影响行数，0 表示竞争失败
         */
        int updateTask(Task task);

        /**
         * 按扫描快照的办理人、截止时间和策略追加条件 CAS，0 行表示预期竞争。
         *
         * @param task 待更新任务
         * @param expected 扫描时的任务快照
         * @return 受影响行数，0 表示快照已失效
         */
        int updateTimeoutTask(Task task, Task expected);

        /**
         * 在当前租户新增任务。
         *
         * @param task 待新增任务
         * @return 受影响行数
         */
        int insertTask(Task task);

        /**
         * 查询当前租户流程定义，不存在时返回空。
         *
         * @param id 流程定义主键
         * @return 流程定义实体，不存在时返回 {@code null}
         */
        WorkflowDefinition findDefinition(Long id);

        /**
         * 查询当前租户实例，不存在时返回空。
         *
         * @param id 流程实例主键
         * @return 流程实例实体，不存在时返回 {@code null}
         */
        ProcessInstance findInstance(Long id);

        /**
         * 在当前租户新增流程实例。
         *
         * @param instance 待新增流程实例
         * @return 受影响行数
         */
        int insertInstance(ProcessInstance instance);

        /**
         * 更新当前租户实例。
         *
         * @param instance 待更新流程实例
         * @return 受影响行数
         */
        int updateInstance(ProcessInstance instance);
    }
}
