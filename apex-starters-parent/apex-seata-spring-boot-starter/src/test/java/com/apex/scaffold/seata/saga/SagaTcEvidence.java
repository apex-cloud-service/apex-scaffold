package com.apex.scaffold.seata.saga;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.seata.core.exception.TransactionException;
import org.apache.seata.core.model.BranchStatus;
import org.apache.seata.core.model.GlobalStatus;
import org.apache.seata.saga.engine.tm.SagaTransactionalTemplate;
import org.apache.seata.tm.api.GlobalTransaction;
import org.apache.seata.tm.api.GlobalTransactionRole;
import org.apache.seata.tm.api.TransactionalExecutor.ExecutionException;
import org.apache.seata.tm.api.transaction.TransactionInfo;

/** 已初始化真实模板的透明观察器；所有事务、分支注册和报告均原样委托。 */
public final class SagaTcEvidence implements SagaTransactionalTemplate {
    private final SagaTransactionalTemplate delegate;
    private final List<Registration> registrations = new CopyOnWriteArrayList<>();
    private final List<Report> reports = new CopyOnWriteArrayList<>();

    public SagaTcEvidence(SagaTransactionalTemplate delegate) {
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
        return delegate.beginTransaction(info);
    }

    @Override
    public GlobalTransaction reloadTransaction(String xid) throws ExecutionException, TransactionException {
        return delegate.reloadTransaction(xid);
    }

    @Override
    public void reportTransaction(GlobalTransaction transaction, GlobalStatus status) throws ExecutionException {
        Report report = new Report(
                transaction.getXid(),
                status,
                transaction.getGlobalTransactionRole(),
                Thread.currentThread().getName());
        reports.add(report);
        System.out.println("SAGA_TC_REPORT " + report);
        delegate.reportTransaction(transaction, status);
    }

    @Override
    public long branchRegister(String resourceId, String clientId, String xid, String applicationData, String lockKeys)
            throws TransactionException {
        long branchId = delegate.branchRegister(resourceId, clientId, xid, applicationData, lockKeys);
        Registration registration =
                new Registration(branchId, xid, Thread.currentThread().getName(), resourceId);
        registrations.add(registration);
        System.out.println("SAGA_TC_REGISTER " + registration);
        return branchId;
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

    public List<Registration> registrations(String xid) {
        return registrations.stream()
                .filter(registration -> xid.equals(registration.xid()))
                .toList();
    }

    public List<Report> reports(String xid) {
        return reports.stream().filter(report -> xid.equals(report.xid())).toList();
    }

    public record Registration(long branchId, String xid, String thread, String resourceId) {}

    public record Report(String xid, GlobalStatus status, GlobalTransactionRole role, String thread) {}
}
