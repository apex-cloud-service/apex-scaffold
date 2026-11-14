package com.apex.scaffold.seata;

import org.apache.seata.core.context.RootContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import reactor.core.publisher.Mono;

/**
 * 当前 Outbox 仅保证本地提交；全局事务中禁止写入以避免提前投递。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Aspect
public class SeataOutboxGuard {
    /**
     * 环绕 Outbox 写入入口，同步调用在 Seata XID 上下文中直接拒绝，响应式结果在订阅时再次校验。
     *
     * @param point 被拦截的 Outbox 写入连接点
     * @return 原始返回值；响应式写入透传原 {@link Mono}
     * @throws Throwable 全局事务处于活动状态时抛出 {@link IllegalStateException}，其余异常原样传播
     */
    @Around(
            "execution(* com.apex.scaffold.infrastructure.outbox.TransactionalOutboxExecutor.execute(..))"
                    + " || execution(* com.apex.scaffold.infrastructure.event.DomainEventOutboxStore.append*(..))"
                    + " || execution(* com.apex.scaffold.infrastructure.outbox.service.impl.MessageOutboxServiceImpl.append(..))")
    public Object guard(ProceedingJoinPoint point) throws Throwable {
        reject(RootContext.getXID() != null);
        Object result = point.proceed();
        if (result instanceof Mono<?> mono) {
            return Mono.deferContextual(context -> {
                reject(context.hasKey(SeataTransactionExecutor.XID_CONTEXT_KEY) || RootContext.getXID() != null);
                return mono;
            });
        }
        return result;
    }

    private void reject(boolean active) {
        if (active) {
            throw new IllegalStateException("当前 Outbox 不支持 Seata 全局事务，请在全局提交后使用幂等补偿流程");
        }
    }
}
