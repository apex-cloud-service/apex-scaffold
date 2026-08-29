package com.apex.scaffold.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.ApiCallLogEntity;
import reactor.core.publisher.Mono;

/**
 * API 调用日志查询仓储接口。
 *
 * <p>定义对外部 HTTP 调用日志（{@code sys_api_call_log} 表）的分页回溯查询契约，
 * 支撑管理后台「监控中心-API调用日志」页面。写入契约见 {@link ApiCallLogRepository}，
 * 本接口与写路径分离，避免查询副作用混入旁路写入链路。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 * @see ApiCallLogEntity
 */
public interface ApiCallLogQueryRepository {

    /**
     * 分页查询 API 调用日志，按主键倒序（最新优先）。
     *
     * @param serviceName 目标服务名称（精确匹配），可为 {@code null}
     * @param status      调用状态（0-成功 1-客户端错误 2-服务端错误 3-网络异常），可为 {@code null}
     * @param traceId     链路追踪 ID（模糊匹配），可为 {@code null}
     * @param path        请求路径（模糊匹配），可为 {@code null}
     * @param pageNum     页码，从 1 开始
     * @param pageSize    每页大小，上限 100
     * @return 日志实体分页结果
     */
    Mono<PageResponse<ApiCallLogEntity>> search(
            String serviceName, Integer status, String traceId, String path, int pageNum, int pageSize);
}
