package com.apex.scaffold.infrastructure.persistence.repository;

import static com.apex.scaffold.common.scheduler.BlockingIoSchedulers.event;

import com.apex.scaffold.domain.model.entity.ApiCallLogEntity;
import com.apex.scaffold.domain.repository.ApiCallLogRepository;
import com.apex.scaffold.infrastructure.persistence.mapper.ApiCallLogMapper;
import com.apex.scaffold.infrastructure.persistence.po.ApiCallLogPO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link ApiCallLogRepository} 的 MyBatis-Plus 实现。
 *
 * <p>使用 {@code BlockingIoSchedulers#event()} 专用调度器执行阻塞的 API 调用日志入库写入，
 * 确保不阻塞 Reactor 主线程（如 Netty EventLoop）。
 * EVENT 专用池采用"低线程 + 大队列"配置（默认 2 线程 / 2048 队列），
 * 可在 RocketMQ 消费端回调风暴等瞬时峰值场景下吸收突发流量而不丢失日志数据，
 * 同时与 JDBC/CACHE/STORAGE 等核心业务池实现线程级隔离。
 * 写入失败时仅输出 WARN 日志，不影响主业务流程。</p>
 *
 * @author Apex Team
 * @version 1.0.1
 * @since 1.0.0
 * @see ApiCallLogMapper
 * @see com.apex.scaffold.common.scheduler.BlockingIoSchedulers#event()
 */
@Slf4j
@Repository
public class ApiCallLogRepositoryImpl
        implements ApiCallLogRepository, com.apex.scaffold.infrastructure.remote.logging.ApiCallLogSink {

    /**
     * 将共享调用观测数据转换为 system 日志实体后写入。
     *
     * @param observation 脱敏后的调用观测数据
     */
    @Override
    public Mono<Void> save(com.apex.scaffold.infrastructure.remote.logging.ApiCallObservation observation) {
        ApiCallLogEntity entity = new ApiCallLogEntity();
        entity.setServiceName(observation.getServiceName());
        entity.setMethod(observation.getMethod());
        entity.setPath(observation.getPath());
        entity.setUrl(observation.getUrl());
        entity.setRequestHeaders(observation.getRequestHeaders());
        entity.setRequestBody(observation.getRequestBody());
        entity.setResponseStatus(observation.getResponseStatus());
        entity.setResponseHeaders(observation.getResponseHeaders());
        entity.setResponseBody(observation.getResponseBody());
        entity.setExecutionTime(observation.getExecutionTime());
        entity.setStatus(observation.getStatus());
        entity.setErrorMsg(observation.getErrorMsg());
        entity.setTraceId(observation.getTraceId());
        entity.setRequestId(observation.getRequestId());
        entity.setCallTime(observation.getCallTime());
        return save(entity);
    }

    /** 调用日志 Mapper。 */
    private final ApiCallLogMapper mapper;

    /**
     * 构造仓储实现。
     *
     * @param mapper MyBatis-Plus Mapper
     */
    public ApiCallLogRepositoryImpl(ApiCallLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>保存 API 调用日志到持久化存储。实现流程：</p>
     * <ol>
     *   <li>将领域实体 {@link ApiCallLogEntity} 转换为持久化对象 {@link ApiCallLogPO}</li>
     *   <li>通过 Snowflake ID 生成器（{@code IdWorker}）赋分布式主键</li>
     *   <li>在 {@code BlockingIoSchedulers#event()} 专用调度器上执行 MyBatis-Plus insert 操作</li>
     *   <li>写入失败时输出 WARN 级别日志（含 serviceName、path 和异常栈），但<em>不</em>对上游抛出异常
     *       （调用日志写入属于旁路观测，不应阻塞主业务链路）</li>
     * </ol>
     *
     * <p>使用 EVENT 专用池而非通用 {@code boundedElastic} 的原因：</p>
     * <ul>
     *   <li>大队列（默认 2048）吸收 MQ 消费端或网关层的瞬时写入风暴</li>
     *   <li>低线程数（默认 2）减少上下文切换，兼顾写入吞吐量与资源占用</li>
     *   <li>独立池隔离避免 JDBC/缓存等核心业务慢操作挤占日志写入能力</li>
     * </ul>
     *
     * @param entity API 调用日志领域实体（包含请求/响应头体、执行耗时、状态、TraceId 等）
     */
    @Override
    public Mono<Void> save(ApiCallLogEntity entity) {
        return Mono.fromRunnable(() -> {
                    try {
                        ApiCallLogPO po = toPo(entity);
                        po.setId(nextId());
                        mapper.insert(po);
                    } catch (Exception e) {
                        log.warn("保存API调用日志失败, serviceName={}, path={}", entity.getServiceName(), entity.getPath(), e);
                    }
                })
                .subscribeOn(event())
                .then();
    }

    /**
     * 将领域实体转换为持久化对象。
     *
     * @param entity 领域实体
     * @return 持久化对象
     */
    private ApiCallLogPO toPo(ApiCallLogEntity entity) {
        ApiCallLogPO po = new ApiCallLogPO();
        po.setServiceName(entity.getServiceName());
        po.setMethod(entity.getMethod());
        po.setPath(entity.getPath());
        po.setUrl(entity.getUrl());
        po.setRequestHeaders(entity.getRequestHeaders());
        po.setRequestBody(entity.getRequestBody());
        po.setResponseStatus(entity.getResponseStatus());
        po.setResponseHeaders(entity.getResponseHeaders());
        po.setResponseBody(entity.getResponseBody());
        po.setExecutionTime(entity.getExecutionTime());
        po.setStatus(entity.getStatus());
        po.setErrorMsg(entity.getErrorMsg());
        po.setTraceId(entity.getTraceId());
        po.setRequestId(entity.getRequestId());
        po.setCallTime(entity.getCallTime());
        return po;
    }

    /**
     * 生成下一个 Snowflake ID。
     *
     * @return 分布式唯一ID
     */
    private long nextId() {
        return com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
    }
}
