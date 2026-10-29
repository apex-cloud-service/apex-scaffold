package com.apex.scaffold.seata;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.tm.api.GlobalTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class SeataTransactionExecutorTest {
    private final GlobalTransaction transaction = mock(GlobalTransaction.class);
    private final SeataTransactionExecutor executor = new SeataTransactionExecutor(() -> transaction, 30000);

    @AfterEach
    void clean() {
        RootContext.unbind();
    }

    @Test
    void bindsOnJdbcPoolAndCleansAfterCommit() throws Exception {
        doAnswer(call -> {
                    RootContext.bind("tc:8091:1");
                    return null;
                })
                .when(transaction)
                .begin(30000, "test");
        assertThat(executor.execute("test", () -> {
                            assertThat(Thread.currentThread().getName()).startsWith("apex.jdbc");
                            return RootContext.getXID();
                        })
                        .block())
                .isEqualTo("tc:8091:1");
        verify(transaction).commit();
        assertThat(executor.branch(RootContext::getXID).block()).isNull();
    }

    @Test
    void failureRollsBackAndCleans() throws Exception {
        doAnswer(call -> {
                    RootContext.bind("tc:8091:2");
                    return null;
                })
                .when(transaction)
                .begin(30000, "test");
        assertThatThrownBy(() -> executor.execute("test", () -> {
                            throw new IllegalArgumentException("failure");
                        })
                        .block())
                .isInstanceOf(IllegalArgumentException.class);
        verify(transaction).rollback();
        verify(transaction, never()).commit();
        assertThat(executor.branch(RootContext::getXID).block()).isNull();
    }

    @Test
    void joinsIncomingXidWithoutOwningCommit() throws Exception {
        assertThat(executor.execute("nested", RootContext::getXID)
                        .contextWrite(context -> context.put(SeataTransactionExecutor.XID_CONTEXT_KEY, "tc:8091:3"))
                        .block())
                .isEqualTo("tc:8091:3");
        verifyNoInteractions(transaction);
    }

    @Test
    void rejectsReactiveResultAndPreservesRollbackError() throws Exception {
        doAnswer(call -> {
                    RootContext.bind("tc:8091:4");
                    return null;
                })
                .when(transaction)
                .begin(30000, "test");
        doThrow(new IllegalStateException("rollback")).when(transaction).rollback();
        assertThatThrownBy(() ->
                        executor.execute("test", () -> Mono.just("invalid")).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasSuppressedException(new IllegalStateException("rollback"));
    }

    @Test
    void beginFailureCleansPartiallyBoundContext() throws Exception {
        doAnswer(call -> {
                    RootContext.bind("tc:8091:begin");
                    throw new IllegalStateException("begin failed");
                })
                .when(transaction)
                .begin(30000, "test");
        assertThatThrownBy(() -> executor.execute("test", () -> "unreachable").block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("begin failed");
        verify(transaction).rollback();
        verify(transaction, never()).commit();
    }

    @Test
    void commitFailureAttemptsRollback() throws Exception {
        doAnswer(call -> {
                    RootContext.bind("tc:8091:commit");
                    return null;
                })
                .when(transaction)
                .begin(30000, "test");
        doThrow(new IllegalStateException("commit failed")).when(transaction).commit();
        assertThatThrownBy(() -> executor.execute("test", () -> "written").block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("commit failed");
        verify(transaction).rollback();
    }

    @Test
    void isolatesConcurrentBranches() {
        CountDownLatch entered = new CountDownLatch(2);
        Mono<String> first = executor.branch(() -> {
                    entered.countDown();
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    return RootContext.getXID();
                })
                .contextWrite(c -> c.put(SeataTransactionExecutor.XID_CONTEXT_KEY, "tc:8091:5"));
        Mono<String> second = executor.branch(() -> {
                    entered.countDown();
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    return RootContext.getXID();
                })
                .contextWrite(c -> c.put(SeataTransactionExecutor.XID_CONTEXT_KEY, "tc:8091:6"));
        assertThat(Mono.zip(first, second).block().toList()).containsExactly("tc:8091:5", "tc:8091:6");
        assertThat(RootContext.getXID()).isNull();
    }
}
