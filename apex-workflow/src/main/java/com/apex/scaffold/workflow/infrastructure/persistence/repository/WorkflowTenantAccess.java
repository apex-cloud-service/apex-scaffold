package com.apex.scaffold.workflow.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import java.util.function.Function;
import reactor.core.publisher.Mono;

/**
 * 工作流仓储的可信响应式租户边界。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
final class WorkflowTenantAccess {
    private WorkflowTenantAccess() {}

    /** 订阅时捕获 Reactor 租户，再在 JDBC 池延迟执行显式带租户条件的操作。 */
    static <T> Mono<T> execute(Function<String, T> operation) {
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            if (tenant == null || tenant.isBlank()) {
                return Mono.error(new IllegalStateException("工作流仓储缺少有效租户上下文"));
            }
            return Mono.fromCallable(() -> operation.apply(tenant)).subscribeOn(BlockingIoSchedulers.jdbc());
        });
    }
}
