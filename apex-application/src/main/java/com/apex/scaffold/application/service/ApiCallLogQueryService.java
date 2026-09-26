package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.ApiCallLogDTO;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.ApiCallLogEntity;
import com.apex.scaffold.domain.repository.ApiCallLogQueryRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * API 调用日志查询服务。
 *
 * <p>面向管理后台「监控中心-API调用日志」页面提供分页回溯查询，依赖领域层
 * {@link ApiCallLogQueryRepository}（基础设施层实现），将领域实体转换为应用层
 * {@link ApiCallLogDTO}，保持应用层零基础设施依赖（依赖倒置 DIP）。
 * 日志写入由 {@code ApiCallLogFilter} + {@code ApiCallLogService} 在对外部
 * HTTP 调用时自动完成（写读同源），本服务仅负责查询展示。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 * @see ApiCallLogQueryRepository
 */
@Service
public class ApiCallLogQueryService {

    /** API 调用日志查询仓储（领域接口）。 */
    private final ApiCallLogQueryRepository apiCallLogQueryRepository;

    /**
     * 构造查询服务。
     *
     * @param apiCallLogQueryRepository API 调用日志查询仓储
     */
    public ApiCallLogQueryService(ApiCallLogQueryRepository apiCallLogQueryRepository) {
        this.apiCallLogQueryRepository = apiCallLogQueryRepository;
    }

    /**
     * 分页查询 API 调用日志。
     *
     * @param serviceName 目标服务名称（精确匹配），可为 {@code null}
     * @param status      调用状态（0-成功 1-客户端错误 2-服务端错误 3-网络异常），可为 {@code null}
     * @param traceId     链路追踪 ID（模糊匹配），可为 {@code null}
     * @param path        请求路径（模糊匹配），可为 {@code null}
     * @param pageNum     页码，从 1 开始
     * @param pageSize    每页大小，限制在 1-100
     * @return API 调用日志 DTO 分页结果
     */
    public Mono<PageResponse<ApiCallLogDTO>> search(
            String serviceName, Integer status, String traceId, String path, int pageNum, int pageSize) {
        int boundedSize = Math.min(Math.max(pageSize, 1), 100);
        return apiCallLogQueryRepository
                .search(serviceName, status, traceId, path, pageNum, boundedSize)
                .map(page -> PageResponse.of(
                        page.getList().stream().map(this::toDto).toList(), page.getTotal(), pageNum, boundedSize));
    }

    /**
     * 将领域实体转换为应用层 DTO（仅展示概要字段，不随列表返回请求/响应体）。
     *
     * @param entity API 调用日志领域实体
     * @return API 调用日志 DTO
     */
    private ApiCallLogDTO toDto(ApiCallLogEntity entity) {
        ApiCallLogDTO dto = new ApiCallLogDTO();
        dto.setId(entity.getId());
        dto.setServiceName(entity.getServiceName());
        dto.setMethod(entity.getMethod());
        dto.setPath(entity.getPath());
        dto.setUrl(entity.getUrl());
        dto.setResponseStatus(entity.getResponseStatus());
        dto.setExecutionTime(entity.getExecutionTime());
        dto.setStatus(entity.getStatus());
        dto.setErrorMsg(entity.getErrorMsg());
        dto.setTraceId(entity.getTraceId());
        dto.setRequestId(entity.getRequestId());
        dto.setCallTime(entity.getCallTime());
        dto.setCreateTime(entity.getCreateTime());
        return dto;
    }
}
