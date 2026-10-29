package com.apex.scaffold.seata;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.core.model.BranchStatus;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.saga.engine.tm.SagaTransactionalTemplate;
import org.apache.seata.tm.api.GlobalTransaction;
import org.apache.seata.tm.api.GlobalTransactionRole;
import org.apache.seata.tm.api.TransactionalExecutor.ExecutionException;
import org.apache.seata.tm.api.transaction.TransactionInfo;

/**
 * SAGA 全局状态上报归属修正：只有本状态机自己发起的全局事务才允许上报终态。
 *
 * <p>{@code DbAndReportTcStateLogStore} 在状态机结束时无条件调用
 * {@link SagaTransactionalTemplate#reportTransaction(GlobalTransaction, GlobalStatus)}，而它拿到的
 * 事务对象有两个来源，都不一定是本状态机发起的：
 *
 * <ul>
 *   <li>状态机在外部全局事务内执行时，{@code beginTransaction} 返回外层事务的
 *       {@link GlobalTransactionRole#Participant} 视图，参与者成功即把外层 TC 事务提前置为
 *       Committed/Finished，外层后续失败无法回滚；
 *   <li>TC 驱动二阶段回滚时，SAGA 资源管理器在 RM 线程上执行补偿，结束时按 XID
 *       {@code reloadTransaction} 得到事务；{@code GlobalTransactionContext.reload} 在 Seata 2.2.0
 *       固定返回 {@link GlobalTransactionRole#Launcher}，若按角色放行会把外层事务误判为「补偿完成、
 *       全局已回滚」并再次提前终结它。
 * </ul>
 *
 * <p>因此改为按归属判断：只记录由本模板 {@code beginTransaction} 真正创建的事务 XID，仅这些 XID
 * 的终态上报会到达 TC；参与者视图与补偿线程 reload 出来的事务一律不上报，其全局终态由 TC 的
 * 二阶段分支结果决定。其余调用全部原样委托。
 *
 * @author Apex Team
 * @since 1.3.1
 */
final class SagaReportOwnership implements SagaTransactionalTemplate {
    private final SagaTransactionalTemplate delegate;

    /** 由本状态机 begin 出来的全局事务；只有它们才允许由 SAGA 上报终态。 */
    private final Set<String> ownedXids = ConcurrentHashMap.newKeySet();

    SagaReportOwnership(SagaTransactionalTemplate delegate) {
        this.delegate = delegate;
    }

    @Override
    public void commitTransaction(GlobalTransaction transaction) throws ExecutionException {
        delegate.commitTransaction(transaction);
    }

    @Override
    public void rollbackTransaction(GlobalTransaction transaction, Throwable failure)
            throws TransactionException, ExecutionException {
        delegate.rollbackTransaction(transaction, failure);
    }

    @Override
    public GlobalTransaction beginTransaction(TransactionInfo info) throws ExecutionException {
        GlobalTransaction transaction = delegate.beginTransaction(info);
        // 参与者视图来自已存在的 XID，不是本状态机创建的事务，不得登记为归属。
        if (transaction != null
                && transaction.getXid() != null
                && transaction.getGlobalTransactionRole() == GlobalTransactionRole.Launcher) {
            ownedXids.add(transaction.getXid());
        }
        return transaction;
    }

    @Override
    public GlobalTransaction reloadTransaction(String xid) throws ExecutionException, TransactionException {
        // 只用于补偿阶段读取事务状态，reload 出来的角色不可作为归属依据。
        return delegate.reloadTransaction(xid);
    }

    @Override
    public void reportTransaction(GlobalTransaction transaction, GlobalStatus status) throws ExecutionException {
        if (transaction == null) {
            return;
        }
        String xid = transaction.getXid();
        // 归属集合只登记本模板 begin 出来的 XID；remove 保证同一事务只上报一次终态。
        if (xid == null || !ownedXids.remove(xid)) {
            return;
        }
        delegate.reportTransaction(transaction, status);
    }

    @Override
    public long branchRegister(String resourceId, String clientId, String xid, String applicationData, String lockKeys)
            throws TransactionException {
        return delegate.branchRegister(resourceId, clientId, xid, applicationData, lockKeys);
    }

    @Override
    public void branchReport(String xid, long branchId, BranchStatus status, String applicationData)
            throws TransactionException {
        delegate.branchReport(xid, branchId, status, applicationData);
    }

    @Override
    public void triggerAfterCompletion(GlobalTransaction transaction) {
        delegate.triggerAfterCompletion(transaction);
    }

    @Override
    public void cleanUp(GlobalTransaction transaction) {
        delegate.cleanUp(transaction);
    }

    /** 被包装的模板，供测试观察真实 TC 调用。 */
    SagaTransactionalTemplate delegate() {
        return delegate;
    }
}
