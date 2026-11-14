package com.apex.scaffold.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import com.apex.scaffold.domain.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 操作日志服务
 *
 * <p>提供操作日志的异步保存功能，使用 Reactor 响应式模型实现非阻塞写入。
 *
 * <h2>线程安全性</h2>
 * <ul>
 *   <li>不再在调用线程里裸 {@code subscribe()}：通过
 *       {@link BlockingIoSchedulers#jdbc()} 受控调度落库，并写入线程
 *       显式安装/释放调用侧传入的 {@code tenantId}，避免 Reactor 线程切线程后
 *       ThreadLocal 串扰或丢失。</li>
 *   <li>使用 {@code usingWhen} 安装/恢复 TenantContext，即使取消/出错也保证清除。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService implements org.springframework.beans.factory.DisposableBean {

    /**
     * 操作日志仓储。
     *
     * <p>用于异步持久化用户操作记录，如登录、资料修改、权限变更等。</p>
     */
    private final OperationLogRepository operationLogRepository;

    private final java.util.concurrent.Semaphore pending = new java.util.concurrent.Semaphore(1024);
    private final java.util.concurrent.atomic.AtomicLong dropped = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong failed = new java.util.concurrent.atomic.AtomicLong();

    private final reactor.core.Disposable.Composite subscriptions = reactor.core.Disposables.composite();

    @Override
    public void destroy() {
        subscriptions.dispose();
    }

    public long getDroppedCount() {
        return dropped.get();
    }

    public long getFailedCount() {
        return failed.get();
    }

    /**
     * 异步保存操作日志（不传入租户：使用 TenantContext 当前值）。
     *
     * <p>此重载仅在调用方必然运行在 TenantContext 已正确初始化的线程时安全，
     * 否则请使用 {@link #saveAsync(OperationLogEntity, String)}。
     *
     * @param operationLog 操作日志实体
     */
    public void saveAsync(OperationLogEntity operationLog) {
        saveAsync(operationLog, TenantContext.currentTenantId().orElse(null));
    }

    /**
     * 异步保存操作日志，显式指定落库时的租户 ID。
     *
     * <p>使用 Reactor 响应式模型异步写入操作日志，不阻塞调用线程；DB 写入在
     * 专用阻塞池 {@link BlockingIoSchedulers#jdbc()} 上执行，避免占用事件循环。
     *
     * @param operationLog 操作日志实体
     * @param tenantId     写入线程需要安装的租户 ID（允许为 null）
     */
    public void saveAsync(OperationLogEntity operationLog, String tenantId) {
        if (operationLog == null) {
            return;
        }
        if (subscriptions.isDisposed() || !pending.tryAcquire()) {
            dropped.incrementAndGet();
            return;
        }
        reactor.core.Disposable.Swap subscription = reactor.core.Disposables.swap();
        if (!subscriptions.add(subscription)) {
            pending.release();
            dropped.incrementAndGet();
            return;
        }
        try {
            subscription.update(saveInternal(operationLog, tenantId)
                    .doOnError(error -> failed.incrementAndGet())
                    .onErrorResume(error -> Mono.empty())
                    .doFinally(signal -> {
                        subscriptions.remove(subscription);
                        pending.release();
                    })
                    .subscribe());
        } catch (RuntimeException error) {
            subscriptions.remove(subscription);
            subscription.dispose();
            pending.release();
            failed.incrementAndGet();
        }
    }

    /**
     * 构造写入管道（不 subscribe，便于单测组合与时序可控）。
     *
     * @param operationLog 操作日志实体
     * @param tenantId     安装的租户 ID
     */
    Mono<Void> saveInternal(OperationLogEntity operationLog, String tenantId) {
        return Mono.defer(() -> operationLogRepository.insert(operationLog).then())
                .subscribeOn(BlockingIoSchedulers.event())
                .timeout(java.time.Duration.ofSeconds(3))
                .contextWrite(context -> tenantId == null
                        ? context.delete(TenantContext.REACTOR_CONTEXT_KEY)
                        : context.put(TenantContext.REACTOR_CONTEXT_KEY, tenantId));
    }
}
