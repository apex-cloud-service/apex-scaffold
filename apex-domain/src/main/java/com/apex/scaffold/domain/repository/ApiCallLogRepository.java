package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.ApiCallLogEntity;
import reactor.core.publisher.Mono;

/**
 * API 调用日志仓储接口。
 *
 * <p>定义 API 调用日志的持久化契约。实现类负责将 {@link ApiCallLogEntity}
 * 转换为 基础设施持久化对象 并通过 MyBatis-Plus 写入数据库。</p>
 *
 * @author Apex Team
 * @version 1.0.1
 * @since 1.0.0
 * @see ApiCallLogEntity
 */
public interface ApiCallLogRepository {

    /**
     * 异步保存 API 调用日志。
     *
     * <p>写入数据库为异步操作（subscribeOn BlockingIoSchedulers.event()），
     * 不阻塞调用线程。写入失败时仅记录 WARN 级别日志，不向上层传播异常。</p>
     *
     * @param entity API 调用日志实体
     * @return 写入操作完成的空 Mono
     */
    Mono<Void> save(ApiCallLogEntity entity);
}
