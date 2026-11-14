package com.apex.scaffold.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 能力调用用量只读查询端口（方案 D4 计量）。
 *
 * <p>基于既有操作审计日志（{@code sys_operation_log}，V14 起携带租户列）按
 * 租户维度聚合接口调用次数，供能力用量查询 API（{@code /api/v2/capability-usage}）
 * 消费；只读统计，不做计费与配额扣减。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public interface CapabilityUsageQueryRepository {

    /**
     * 按请求 URI 前缀清单聚合各租户调用次数。
     *
     * @param uriPrefixes 请求 URI 前缀清单（非空，表示单一能力的路由口径）
     * @param from        起始时间（含）
     * @param to          结束时间（含）
     * @param tenantId    租户标识；为空时聚合全部租户（平台视角）
     * @return 租户维度调用计数，按租户标识升序
     */
    Mono<List<TenantUsageCount>> countCallsByUriPrefixes(
            List<String> uriPrefixes, LocalDateTime from, LocalDateTime to, String tenantId);

    /**
     * 单租户调用计数行。
     *
     * @param tenantId  租户标识
     * @param callCount 调用次数
     */
    record TenantUsageCount(String tenantId, long callCount) {}
}
