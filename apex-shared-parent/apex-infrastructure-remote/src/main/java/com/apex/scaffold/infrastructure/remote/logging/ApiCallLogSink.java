package com.apex.scaffold.infrastructure.remote.logging;

import reactor.core.publisher.Mono;

/**
 * 脱敏后出站调用观测数据的写入契约，由服务提供持久化适配。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface ApiCallLogSink {
    /**
     * 写入调用观测数据。
     *
     * @param observation 脱敏后的调用数据
     * @return 操作完成信号，失败时传播错误
     */
    Mono<Void> save(ApiCallObservation observation);
}
