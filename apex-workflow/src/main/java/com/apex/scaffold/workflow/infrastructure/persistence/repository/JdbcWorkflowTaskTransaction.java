package com.apex.scaffold.workflow.infrastructure.persistence.repository;

import com.apex.scaffold.workflow.domain.model.ProcessInstance;
import com.apex.scaffold.workflow.domain.model.Task;
import com.apex.scaffold.workflow.domain.model.WorkflowDefinition;
import com.apex.scaffold.workflow.domain.repository.WorkflowTaskTransaction;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用同一数据源同步执行任务事务。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Component
public class JdbcWorkflowTaskTransaction implements WorkflowTaskTransaction {
    private final TaskRepositoryImpl tasks;
    private final ProcessInstanceRepositoryImpl instances;
    private final WorkflowDefinitionRepositoryImpl definitions;
    private final TransactionTemplate transactions;

    public JdbcWorkflowTaskTransaction(
            DataSource dataSource,
            TaskRepositoryImpl tasks,
            ProcessInstanceRepositoryImpl instances,
            WorkflowDefinitionRepositoryImpl definitions) {
        this.tasks = tasks;
        this.instances = instances;
        this.definitions = definitions;
        this.transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
    }

    @Override
    public void execute(String tenant, Consumer<Operations> operation) {
        transactions.executeWithoutResult(status -> operation.accept(new Operations() {
            @Override
            public Task findTask(Long id) {
                return tasks.findByIdSync(tenant, id);
            }

            @Override
            public int updateTask(Task task) {
                return tasks.updateByIdSync(tenant, task);
            }

            @Override
            public int updateTimeoutTask(Task task, Task expected) {
                return tasks.updateTimeoutByIdSync(tenant, task, expected);
            }

            @Override
            public int insertTask(Task task) {
                return tasks.insertSync(tenant, task);
            }

            @Override
            public WorkflowDefinition findDefinition(Long id) {
                return definitions.findByIdSync(tenant, id);
            }

            @Override
            public ProcessInstance findInstance(Long id) {
                return instances.findByIdSync(tenant, id);
            }

            @Override
            public int insertInstance(ProcessInstance instance) {
                return instances.insertSync(tenant, instance);
            }

            @Override
            public int updateInstance(ProcessInstance instance) {
                return instances.updateByIdSync(tenant, instance);
            }
        }));
    }
}
