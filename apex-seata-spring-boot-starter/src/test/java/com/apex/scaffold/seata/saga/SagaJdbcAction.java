package com.apex.scaffold.seata.saga;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.core.model.BranchType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

/** 同步本地事务动作；先采集引擎/RM 原始调用证据，再把整个 JDBC 事务隔离到专用池。 */
public final class SagaJdbcAction {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate local;

    public SagaJdbcAction(JdbcTemplate jdbc, TransactionTemplate local) {
        this.jdbc = jdbc;
        this.local = local;
    }

    public Boolean debit(String businessKey, Integer amount, Boolean failAfterWrites) {
        return debit(businessKey, amount, failAfterWrites, Invocation.capture());
    }

    Boolean debit(String businessKey, Integer amount, Boolean failAfterWrites, Invocation invocation) {
        return Mono.fromCallable(() -> local.execute(status -> {
                    lockAccount(businessKey);
                    if (!phase(businessKey, "FORWARD").isEmpty()) {
                        return true;
                    }
                    assertThat(jdbc.update(
                                    "UPDATE apex_saga_tc_account SET balance=balance-? WHERE business_key=? AND balance>=?",
                                    amount,
                                    businessKey,
                                    amount))
                            .isEqualTo(1);
                    insertAudit(businessKey, "FORWARD", amount, invocation);
                    if (failAfterWrites) {
                        // 两次写入已成功，随后失败必须由同一本地事务同时回滚。
                        throw new ForcedLocalFailure();
                    }
                    return true;
                }))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .block(TIMEOUT);
    }

    public Boolean restore(String businessKey, Integer amount) {
        return restore(businessKey, amount, Invocation.capture());
    }

    Boolean restore(String businessKey, Integer amount, Invocation invocation) {
        return Mono.fromCallable(() -> local.execute(status -> {
                    lockAccount(businessKey);
                    List<Map<String, Object>> forward = phase(businessKey, "FORWARD");
                    if (forward.isEmpty() || !phase(businessKey, "COMPENSATE").isEmpty()) {
                        return true;
                    }
                    assertThat(forward.get(0)).containsEntry("amount", amount).containsEntry("xid", invocation.xid());
                    assertThat(jdbc.update(
                                    "UPDATE apex_saga_tc_account SET balance=balance+? WHERE business_key=?",
                                    amount,
                                    businessKey))
                            .isEqualTo(1);
                    insertAudit(businessKey, "COMPENSATE", amount, invocation);
                    return true;
                }))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .block(TIMEOUT);
    }

    private void lockAccount(String businessKey) {
        jdbc.queryForObject(
                "SELECT balance FROM apex_saga_tc_account WHERE business_key=? FOR UPDATE", Integer.class, businessKey);
    }

    private List<Map<String, Object>> phase(String businessKey, String phase) {
        return jdbc.queryForList(
                "SELECT amount,xid FROM apex_saga_tc_audit WHERE business_key=? AND phase=?", businessKey, phase);
    }

    private void insertAudit(String businessKey, String phase, int amount, Invocation invocation) {
        assertThat(Thread.currentThread().getName()).startsWith("apex.jdbc");
        jdbc.update(
                "INSERT INTO apex_saga_tc_audit(business_key,phase,xid,branch_type,amount,jdbc_thread,source_thread,call_stack,from_tc_rollback) "
                        + "VALUES(?,?,?,?,?,?,?,?,?)",
                businessKey,
                phase,
                invocation.xid(),
                invocation.branchType(),
                amount,
                Thread.currentThread().getName(),
                invocation.sourceThread(),
                invocation.callStack(),
                invocation.fromTcRollback());
    }

    public static List<Audit> audits(JdbcTemplate jdbc, String businessKey) {
        return jdbc.query(
                "SELECT phase,xid,branch_type,amount,jdbc_thread,source_thread,call_stack,from_tc_rollback "
                        + "FROM apex_saga_tc_audit WHERE business_key=? ORDER BY id",
                (row, index) -> new Audit(
                        row.getString("phase"),
                        row.getString("xid"),
                        row.getString("branch_type"),
                        row.getInt("amount"),
                        row.getString("jdbc_thread"),
                        row.getString("source_thread"),
                        row.getString("call_stack"),
                        row.getBoolean("from_tc_rollback")),
                businessKey);
    }

    public record Audit(
            String phase,
            String xid,
            String branchType,
            int amount,
            String jdbcThread,
            String sourceThread,
            String callStack,
            boolean fromTcRollback) {}

    record Invocation(String xid, String branchType, String sourceThread, String callStack) {
        static Invocation capture() {
            BranchType branchType = RootContext.getBranchType();
            String stack = Arrays.stream(new Throwable().getStackTrace())
                    .map(StackTraceElement::toString)
                    .collect(Collectors.joining("\n"));
            return new Invocation(
                    RootContext.getXID(),
                    branchType == null ? null : branchType.name(),
                    Thread.currentThread().getName(),
                    stack);
        }

        boolean fromTcRollback() {
            return callStack.contains("org.apache.seata.saga.rm.SagaResourceManager.branchRollback(")
                    && callStack.contains("org.apache.seata.rm.AbstractRMHandler.doBranchRollback(");
        }
    }

    public static final class ForcedLocalFailure extends IllegalStateException {
        ForcedLocalFailure() {
            super("force-db2-local-rollback-after-debit-and-audit");
        }
    }
}
