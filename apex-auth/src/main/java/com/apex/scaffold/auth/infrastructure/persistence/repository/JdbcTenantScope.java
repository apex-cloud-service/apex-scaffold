package com.apex.scaffold.auth.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * 租户作用域 JDBC 辅助工具（包内可见）。
 *
 * <p>为持久层提供统一的租户隔离执行入口：{@code call} 在 Reactor 上下文与
 * 请求头双路校验租户标识（缺失或不匹配即拒绝），并将阻塞操作调度到 JDBC
 * 专用线程池；{@code query}/{@code update} 生成带 {@code tenant_id} 条件的
 * 包装器；{@code changed} 校验更新影响行数。私有构造器禁止实例化，仅以
 * 静态工具方法形式使用。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
final class JdbcTenantScope {
    private JdbcTenantScope() {}

    /**
     * 在已校验租户作用域的连接上执行的阻塞操作。
     *
     * @param <T> 操作结果类型
     */
    @FunctionalInterface
    interface Operation<T> {
        /**
         * 执行已经校验租户的阻塞操作。
         * @param tenantId 可信租户标识
         * @return 操作结果
         */
        T execute(String tenantId);
    }

    static <T> Mono<T> call(String requested, Operation<T> operation) {
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(
                    TenantContext.REACTOR_CONTEXT_KEY,
                    TenantContext.currentTenantId().orElse(null));
            boolean missing = tenant == null || tenant.isBlank();
            boolean mismatch = requested != null && !Objects.equals(tenant, requested);
            if (missing || mismatch) {
                return Mono.error(new BusinessException(ResultCode.FORBIDDEN));
            }
            return Mono.fromCallable(() -> {
                        String previous = TenantContext.setTenantId(tenant);
                        try {
                            return operation.execute(tenant);
                        } finally {
                            TenantContext.restoreTenantId(previous);
                        }
                    })
                    .subscribeOn(BlockingIoSchedulers.jdbc());
        });
    }

    static <T> QueryWrapper<T> query(String tenant) {
        return new QueryWrapper<T>().eq("tenant_id", tenant);
    }

    static <T> UpdateWrapper<T> update(String tenant) {
        return new UpdateWrapper<T>().eq("tenant_id", tenant);
    }

    static void changed(int count) {
        if (count != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
    }

    static List<Long> ids(Iterable<Long> values) {
        if (values == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        List<Long> result = new ArrayList<>();
        for (Long value : values) {
            if (value == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR);
            }
            if (!result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }
}
