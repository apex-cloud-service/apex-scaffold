package com.apex.scaffold.gateway.log;

import reactor.core.publisher.Mono;

/**
 * 访问日志仓储接口.
 *
 * <p>抽象访问日志的持久化能力，隔离存储实现（R2DBC）与业务编排
 * （{@link AccessLogService}），便于单元测试以桩替身注入并保证响应式契约。
 *
 * <p><b>响应式契约</b>：方法返回 {@link Mono} 且<b>不阻塞</b>调用线程；
 * 写入失败时以 {@code onError} 信号传播，由上层 {@link AccessLogService} 统一吞掉，
 * 绝不影响网关正常转发。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public interface AccessLogRepository {

    /**
     * 持久化一条访问日志.
     *
     * @param record 已脱敏的访问日志记录
     */
    Mono<Void> save(AccessLogRecord record);
}
