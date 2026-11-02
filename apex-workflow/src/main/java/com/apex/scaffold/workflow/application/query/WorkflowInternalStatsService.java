package com.apex.scaffold.workflow.application.query;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.ProcessInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 工作流内部统计服务（方案 D5 只读统计）。
 *
 * <p>提供租户维度的进行中流程实例数只读查询，供工作流服务内部统计端点
 * （{@code GET /internal/v1/workflow/stats}，聚合门面消费）使用。
 * 与 {@link WorkflowStatisticsService} 的看板统计相互独立：
 * 本服务仅聚合单一指标，避免内部端点触发全量看板查询。
 *
 * <p>租户口径与看板统计一致：订阅时从可信 Reactor Context 解析租户，
 * 缺失即失败（fail-closed），不退化为跨租户统计。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Service
@RequiredArgsConstructor
public class WorkflowInternalStatsService {

    /** 进行中流程实例状态值（与 {@code wf_process_instance.status} 枚举口径一致）。 */
    private static final String STATUS_RUNNING = "RUNNING";

    private final ProcessInstanceMapper processInstanceMapper;

    /**
     * 统计当前租户进行中的流程实例数。
     *
     * @return 进行中实例数
     * @throws IllegalStateException 当可信租户上下文缺失时
     */
    public Mono<Long> countInProgressInstances() {
        return requireTenant().flatMap(tenant -> Mono.fromCallable(() -> {
                    Integer count = processInstanceMapper.countByStatus(tenant, STATUS_RUNNING);
                    return count == null ? 0L : count.longValue();
                })
                .subscribeOn(BlockingIoSchedulers.jdbc()));
    }

    /**
     * 订阅时解析可信租户，缺失即失败，避免退化为跨租户统计。
     *
     * @return 租户标识
     */
    private Mono<String> requireTenant() {
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            if (tenant == null || tenant.isBlank()) {
                return Mono.error(new IllegalStateException("工作流内部统计缺少有效租户上下文"));
            }
            return Mono.just(tenant);
        });
    }
}
