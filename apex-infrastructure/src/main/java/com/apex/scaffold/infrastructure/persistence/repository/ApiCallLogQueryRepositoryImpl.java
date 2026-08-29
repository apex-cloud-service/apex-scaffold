package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.ApiCallLogEntity;
import com.apex.scaffold.domain.repository.ApiCallLogQueryRepository;
import com.apex.scaffold.infrastructure.persistence.mapper.ApiCallLogMapper;
import com.apex.scaffold.infrastructure.persistence.po.ApiCallLogPO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link ApiCallLogQueryRepository} 的 MyBatis-Plus 实现。
 *
 * <p>面向 {@code sys_api_call_log} 表分页查询（写读同源，与
 * {@link ApiCallLogRepositoryImpl} 共用 {@link ApiCallLogMapper}），
 * 支持按目标服务、调用状态、TraceId、请求路径过滤，按主键倒序返回。
 * JDBC 阻塞调用隔离到 boundedElastic 调度器（{@link BlockingIoSchedulers#jdbc()}）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 * @see ApiCallLogPO
 */
@Repository
public class ApiCallLogQueryRepositoryImpl implements ApiCallLogQueryRepository {

    /** API 调用日志 Mapper（{@code sys_api_call_log} 表）。 */
    private final ApiCallLogMapper apiCallLogMapper;

    /**
     * 构造查询仓储实现。
     *
     * @param apiCallLogMapper API 调用日志 Mapper
     */
    public ApiCallLogQueryRepositoryImpl(ApiCallLogMapper apiCallLogMapper) {
        this.apiCallLogMapper = apiCallLogMapper;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<PageResponse<ApiCallLogEntity>> search(
            String serviceName, Integer status, String traceId, String path, int pageNum, int pageSize) {
        int boundedSize = Math.min(Math.max(pageSize, 1), 100);
        return Mono.fromCallable(() -> {
                    Page<ApiCallLogPO> page = new Page<>(pageNum, boundedSize);
                    apiCallLogMapper.selectPage(
                            page,
                            ConditionUtil.lambda(ApiCallLogPO.class)
                                    .eqIfPresent(ApiCallLogPO::getServiceName, serviceName)
                                    .eqIfPresent(ApiCallLogPO::getStatus, status)
                                    .likeIfPresent(ApiCallLogPO::getTraceId, traceId)
                                    .likeIfPresent(ApiCallLogPO::getPath, path)
                                    .orderByDesc(ApiCallLogPO::getId));
                    return PageResponse.of(
                            page.getRecords().stream().map(this::toEntity).collect(Collectors.toList()),
                            page.getTotal(),
                            pageNum,
                            boundedSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** PO → 领域实体转换（{@code sys_api_call_log} 字段映射）。 */
    private ApiCallLogEntity toEntity(ApiCallLogPO po) {
        ApiCallLogEntity e = new ApiCallLogEntity();
        e.setId(po.getId());
        e.setServiceName(po.getServiceName());
        e.setMethod(po.getMethod());
        e.setPath(po.getPath());
        e.setUrl(po.getUrl());
        e.setRequestHeaders(po.getRequestHeaders());
        e.setRequestBody(po.getRequestBody());
        e.setResponseStatus(po.getResponseStatus());
        e.setResponseHeaders(po.getResponseHeaders());
        e.setResponseBody(po.getResponseBody());
        e.setExecutionTime(po.getExecutionTime());
        e.setStatus(po.getStatus());
        e.setErrorMsg(po.getErrorMsg());
        e.setTraceId(po.getTraceId());
        e.setRequestId(po.getRequestId());
        e.setCallTime(po.getCallTime());
        e.setCreateTime(po.getCreateTime());
        return e;
    }
}
