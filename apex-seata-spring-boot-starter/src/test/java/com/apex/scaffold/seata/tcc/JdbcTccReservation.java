package com.apex.scaffold.seata.tcc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 真实数据库的 TCC 测试动作；冻结、确认及释放均在各自本地事务中执行。 */
public final class JdbcTccReservation {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final List<PhaseTwoCall> calls = new CopyOnWriteArrayList<>();

    public JdbcTccReservation(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    public boolean prepare(BusinessActionContext context, int amount) {
        if (context == null || context.getBranchId() <= 0 || !context.getXid().equals(RootContext.getXID())) {
            throw new IllegalStateException("Try 必须经真实 TCC 代理注册分支");
        }
        return Boolean.TRUE.equals(transaction.execute(status -> {
            if (jdbc.update(
                            "UPDATE apex_seata_tcc_account SET balance=balance-? WHERE id=1 AND balance>=?",
                            amount,
                            amount)
                    != 1) {
                return false;
            }
            jdbc.update(
                    "INSERT INTO apex_seata_tcc_phase(xid,branch_id,amount,state) VALUES(?,?,?,'PREPARED')",
                    context.getXid(),
                    context.getBranchId(),
                    amount);
            return true;
        }));
    }

    public boolean confirm(BusinessActionContext context) {
        recordCall(context, "CONFIRMED");
        return Boolean.TRUE.equals(transaction.execute(status -> {
            List<String> states = jdbc.queryForList(
                    "SELECT state FROM apex_seata_tcc_phase WHERE xid=? AND branch_id=? FOR UPDATE",
                    String.class,
                    context.getXid(),
                    context.getBranchId());
            if (states.isEmpty()) return true;
            if ("CONFIRMED".equals(states.get(0))) return true;
            if (!"PREPARED".equals(states.get(0))) return false;
            jdbc.update(
                    "UPDATE apex_seata_tcc_phase SET state='CONFIRMED' WHERE xid=? AND branch_id=?",
                    context.getXid(),
                    context.getBranchId());
            return true;
        }));
    }

    public boolean cancel(BusinessActionContext context) {
        recordCall(context, "CANCELLED");
        return Boolean.TRUE.equals(transaction.execute(status -> {
            List<String> states = jdbc.queryForList(
                    "SELECT state FROM apex_seata_tcc_phase WHERE xid=? AND branch_id=? FOR UPDATE",
                    String.class,
                    context.getXid(),
                    context.getBranchId());
            if (states.isEmpty()) return true;
            if ("CANCELLED".equals(states.get(0))) return true;
            if (!"PREPARED".equals(states.get(0))) return false;
            int amount = jdbc.queryForObject(
                    "SELECT amount FROM apex_seata_tcc_phase WHERE xid=? AND branch_id=?",
                    Integer.class,
                    context.getXid(),
                    context.getBranchId());
            jdbc.update("UPDATE apex_seata_tcc_account SET balance=balance+? WHERE id=1", amount);
            jdbc.update(
                    "UPDATE apex_seata_tcc_phase SET state='CANCELLED' WHERE xid=? AND branch_id=?",
                    context.getXid(),
                    context.getBranchId());
            return true;
        }));
    }

    public List<PhaseTwoCall> calls() {
        return List.copyOf(calls);
    }

    public void clearCalls() {
        calls.clear();
    }

    private void recordCall(BusinessActionContext context, String state) {
        boolean fromRm = StackWalker.getInstance()
                .walk(frames ->
                        frames.anyMatch(frame -> frame.getClassName().equals("org.apache.seata.rm.AbstractRMHandler")
                                && (frame.getMethodName().equals("doBranchCommit")
                                        || frame.getMethodName().equals("doBranchRollback"))));
        if (!fromRm) throw new IllegalStateException("二阶段必须由真实 RM 接收 TC 指令后调用");
        calls.add(new PhaseTwoCall(context.getXid(), context.getBranchId(), state, fromRm));
    }

    public record PhaseTwoCall(String xid, long branchId, String state, boolean fromRm) {}
}
