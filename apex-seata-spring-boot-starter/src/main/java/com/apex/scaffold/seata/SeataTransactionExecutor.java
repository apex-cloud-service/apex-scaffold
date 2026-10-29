package com.apex.scaffold.seata;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.tm.api.GlobalTransaction;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * 在专用 JDBC 线程中执行同步 AT 事务；回调不得返回响应式结果。
 * @author Apex Team
 * @since 1.3.1
 */
@lombok.RequiredArgsConstructor
public final class SeataTransactionExecutor {
    public static final String XID_CONTEXT_KEY = SeataTransactionExecutor.class.getName() + ".xid";
    private final Supplier<GlobalTransaction> transactions;
    private final int timeoutMillis;

    /** 无入站事务时拥有全局事务，有受信 XID 时仅参与已有事务。 */
    public <T> Mono<T> execute(String name, Callable<T> callback) {
        return Mono.deferContextual(context -> Mono.fromCallable(
                        () -> invoke(context.getOrDefault(XID_CONTEXT_KEY, null), name, callback, true))
                .subscribeOn(BlockingIoSchedulers.jdbc()));
    }

    /** 将 Reactor 中受信的 XID 绑定到一个完整同步本地事务回调。 */
    public <T> Mono<T> branch(Callable<T> callback) {
        return Mono.deferContextual(context -> Mono.fromCallable(
                        () -> invoke(context.getOrDefault(XID_CONTEXT_KEY, null), null, callback, false))
                .subscribeOn(BlockingIoSchedulers.jdbc()));
    }

    private <T> T invoke(String xid, String name, Callable<T> callback, boolean create) throws Exception {
        String previous = RootContext.getXID();
        if (previous != null && xid != null && !previous.equals(xid)) {
            throw new IllegalStateException("JDBC 线程事务上下文与请求事务不一致");
        }
        GlobalTransaction owned = null;
        try {
            if (previous == null && xid != null) {
                RootContext.bind(xid);
            }
            if (RootContext.getXID() == null && create) {
                owned = transactions.get();
                owned.begin(timeoutMillis, name);
            }
            T result = callback.call();
            if (result instanceof Publisher<?>) {
                throw new IllegalArgumentException("Seata 同步回调不能返回 Publisher");
            }
            if (owned != null) {
                owned.commit();
            }
            return result;
        } catch (Exception | Error failure) {
            if (owned != null) {
                try {
                    owned.rollback();
                } catch (Exception rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        } finally {
            RootContext.unbind();
            if (previous != null) {
                RootContext.bind(previous);
            }
        }
    }
}
