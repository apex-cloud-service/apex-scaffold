package com.apex.scaffold.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.model.entity.CreditAccountEntity;
import com.apex.scaffold.domain.repository.CreditAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link CreditService} 的并发/压力测试类
 *
 * <p>测试积分算力服务在高并发场景下的乐观锁重试机制、
 * 并发安全性和重试耗尽行为。
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
class CreditServiceConcurrencyTest {

    @Mock
    private CreditAccountRepository creditAccountRepository;

    @InjectMocks
    private CreditService creditService;

    // ==================== 场景1：乐观锁重试 ====================

    /**
     * 执行 {@code optimisticLockRetry} 操作。
     */
    @Test
    @DisplayName("乐观锁重试：incrementBalance 第一次抛出 OptimisticLockingFailureException，第二次成功")
    void optimisticLockRetry() {
        AtomicInteger saveCallCount = new AtomicInteger(0);

        when(creditAccountRepository.createIfNotExists(1L)).thenAnswer(inv -> {
            CreditAccountEntity account = new CreditAccountEntity();
            account.setUserId(1L);
            account.setBalance(0L);
            return Mono.just(account);
        });

        when(creditAccountRepository.incrementBalance(anyLong(), anyLong(), any()))
                .thenAnswer(inv -> {
                    int call = saveCallCount.incrementAndGet();
                    if (call == 1) {
                        return Mono.error(new OptimisticLockingFailureException("simulated conflict"));
                    }
                    return Mono.just(true);
                });

        creditService
                .addCredit(1L, 100L, "TEST")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).isTrue();
                    assertThat(saveCallCount.get()).isEqualTo(2);
                })
                .verifyComplete();
    }

    // ==================== 场景2：并发 addCredit ====================

    /**
     * 执行 {@code concurrentAddCredit} 操作。
     * @throws Exception 当操作无法完成时
     */
    @Test
    @DisplayName("并发 addCredit：5 个线程各加 10，初始余额 0，最终余额应为 50")
    void concurrentAddCredit() throws Exception {
        AtomicLong sharedBalance = new AtomicLong(0L);
        AtomicLong sharedVersion = new AtomicLong(0L);

        when(creditAccountRepository.createIfNotExists(anyLong())).thenAnswer(inv -> {
            CreditAccountEntity account = new CreditAccountEntity();
            account.setUserId(inv.getArgument(0));
            account.setBalance(sharedBalance.get());
            account.setVersion(sharedVersion.get());
            return Mono.just(account);
        });

        when(creditAccountRepository.incrementBalance(anyLong(), anyLong(), any()))
                .thenAnswer(inv -> {
                    long amount = inv.getArgument(1);
                    Long version = inv.getArgument(2);
                    long expectedVersion = version != null ? version : 0L;
                    if (sharedVersion.compareAndSet(expectedVersion, expectedVersion + 1)) {
                        sharedBalance.addAndGet(amount);
                        return Mono.just(true);
                    }
                    return Mono.just(false);
                });

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<?>> futures = new ArrayList<>(threadCount);

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    assertThat(creditService
                                    .addCredit(1L, 10L, "CONCURRENT_TEST")
                                    .block())
                            .isTrue();
                } finally {
                    latch.countDown();
                }
            }));
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        assertThat(sharedBalance.get()).isEqualTo(50L);
        assertThat(sharedVersion.get()).isEqualTo(5L);
    }

    // ==================== 场景3：并发 consumeCredit ====================

    /**
     * 执行 {@code concurrentConsumeCredit} 操作。
     * @throws Exception 当操作无法完成时
     */
    @Test
    @DisplayName("并发 consumeCredit：5 个线程各消费 5，初始余额 100，最终余额应为 75")
    void concurrentConsumeCredit() throws Exception {
        AtomicLong sharedBalance = new AtomicLong(100L);
        AtomicLong sharedVersion = new AtomicLong(0L);

        when(creditAccountRepository.findByUserId(anyLong())).thenAnswer(inv -> {
            CreditAccountEntity account = new CreditAccountEntity();
            account.setUserId(inv.getArgument(0));
            account.setBalance(sharedBalance.get());
            account.setVersion(sharedVersion.get());
            return Mono.just(account);
        });

        when(creditAccountRepository.decrementBalance(anyLong(), anyLong(), any()))
                .thenAnswer(inv -> {
                    long amount = inv.getArgument(1);
                    Long version = inv.getArgument(2);
                    long expectedVersion = version != null ? version : 0L;
                    if (sharedBalance.get() < amount) {
                        return Mono.just(false);
                    }
                    if (sharedVersion.compareAndSet(expectedVersion, expectedVersion + 1)) {
                        sharedBalance.addAndGet(-amount);
                        return Mono.just(true);
                    }
                    return Mono.just(false);
                });

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<?>> futures = new ArrayList<>(threadCount);

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    assertThat(creditService.consumeCredit(1L, 5L).block()).isTrue();
                } finally {
                    latch.countDown();
                }
            }));
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        assertThat(sharedBalance.get()).isEqualTo(75L);
        assertThat(sharedVersion.get()).isEqualTo(5L);
    }

    // ==================== 场景4：混合操作 ====================

    /**
     * 执行 {@code mixedOperations} 操作。
     * @throws Exception 当操作无法完成时
     */
    @Test
    @DisplayName("混合操作：3 个线程各加 10 + 3 个线程各消费 5，初始 50，最终应为 65")
    void mixedOperations() throws Exception {
        AtomicLong sharedBalance = new AtomicLong(50L);
        AtomicLong sharedVersion = new AtomicLong(0L);

        when(creditAccountRepository.createIfNotExists(anyLong())).thenAnswer(inv -> {
            CreditAccountEntity account = new CreditAccountEntity();
            account.setUserId(inv.getArgument(0));
            account.setBalance(sharedBalance.get());
            account.setVersion(sharedVersion.get());
            return Mono.just(account);
        });

        when(creditAccountRepository.findByUserId(anyLong())).thenAnswer(inv -> {
            CreditAccountEntity account = new CreditAccountEntity();
            account.setUserId(inv.getArgument(0));
            account.setBalance(sharedBalance.get());
            account.setVersion(sharedVersion.get());
            return Mono.just(account);
        });

        when(creditAccountRepository.incrementBalance(anyLong(), anyLong(), any()))
                .thenAnswer(inv -> {
                    long amount = inv.getArgument(1);
                    Long version = inv.getArgument(2);
                    long expectedVersion = version != null ? version : 0L;
                    if (sharedVersion.compareAndSet(expectedVersion, expectedVersion + 1)) {
                        sharedBalance.addAndGet(amount);
                        return Mono.just(true);
                    }
                    return Mono.just(false);
                });

        when(creditAccountRepository.decrementBalance(anyLong(), anyLong(), any()))
                .thenAnswer(inv -> {
                    long amount = inv.getArgument(1);
                    Long version = inv.getArgument(2);
                    long expectedVersion = version != null ? version : 0L;
                    if (sharedBalance.get() < amount) {
                        return Mono.just(false);
                    }
                    if (sharedVersion.compareAndSet(expectedVersion, expectedVersion + 1)) {
                        sharedBalance.addAndGet(-amount);
                        return Mono.just(true);
                    }
                    return Mono.just(false);
                });

        int addThreads = 3;
        int consumeThreads = 3;
        int totalThreads = addThreads + consumeThreads;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch latch = new CountDownLatch(totalThreads);
        List<Future<?>> futures = new ArrayList<>(totalThreads);

        for (int i = 0; i < addThreads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    assertThat(creditService.addCredit(1L, 10L, "MIXED_TEST").block())
                            .isTrue();
                } finally {
                    latch.countDown();
                }
            }));
        }

        for (int i = 0; i < consumeThreads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    assertThat(creditService.consumeCredit(1L, 5L).block()).isTrue();
                } finally {
                    latch.countDown();
                }
            }));
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        // 3*10 - 3*5 = 15, initial 50 → 65
        assertThat(sharedBalance.get()).isEqualTo(65L);
        assertThat(sharedVersion.get()).isEqualTo((long) totalThreads);
    }

    // ==================== 场景5：重试耗尽 ====================

    /**
     * 执行 {@code retryExhaustion} 操作。
     */
    @Test
    @DisplayName("重试耗尽：save 始终抛出 OptimisticLockingFailureException，重试耗尽后返回 false")
    void retryExhaustion() {
        AtomicInteger saveCallCount = new AtomicInteger(0);

        when(creditAccountRepository.createIfNotExists(1L)).thenAnswer(inv -> {
            CreditAccountEntity account = new CreditAccountEntity();
            account.setUserId(1L);
            account.setBalance(0L);
            return Mono.just(account);
        });

        when(creditAccountRepository.incrementBalance(anyLong(), anyLong(), any()))
                .thenAnswer(inv -> {
                    saveCallCount.incrementAndGet();
                    return Mono.error(new OptimisticLockingFailureException("persistent conflict"));
                });

        creditService
                .addCredit(1L, 100L, "TEST")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).isFalse();
                    // Retry.backoff(5) = 1 initial + 5 retries = 6 total attempts
                    assertThat(saveCallCount.get()).isEqualTo(6);
                })
                .verifyComplete();
    }
}
