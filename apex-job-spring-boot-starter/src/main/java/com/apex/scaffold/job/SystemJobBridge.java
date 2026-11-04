package com.apex.scaffold.job;

import com.apex.scaffold.common.context.TenantContext;
import com.xxl.job.core.handler.annotation.XxlJob;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 固定 system 应用端口桥接；任务与租户来自可信部署配置，忽略调度参数。
 *
 * @author Apex Scaffold
 * @since 1.3.1
 */
public final class SystemJobBridge {
    private final SystemJobPort service;
    private final String tenant;
    private final Long jobId;

    /**
     * 构造固定任务桥接。
     *
     * @param service system 任务端口
     * @param tenant 固定执行租户
     * @param jobId 固定任务 ID（正数）
     */
    public SystemJobBridge(SystemJobPort service, String tenant, Long jobId) {
        if (tenant == null || tenant.isBlank() || jobId == null || jobId <= 0) {
            throw new IllegalArgumentException("system XXL 桥接需要固定租户和正数任务ID");
        }
        this.service = service;
        this.tenant = tenant;
        this.jobId = jobId;
    }

    /** 使用既有任务查询、日志及失败传播链；不允许调度参数切换租户或任务。 */
    @XxlJob("apexSystemJob")
    public void execute() {
        JobCompletion.await(Mono.defer(() -> {
                    String previous = TenantContext.currentTenantId().orElse(null);
                    TenantContext.setTenantId(tenant);
                    try {
                        return Flux.from(service.runOnce(jobId)).then();
                    } finally {
                        if (previous == null) {
                            TenantContext.clear();
                        } else {
                            TenantContext.setTenantId(previous);
                        }
                    }
                })
                .contextWrite(TenantContext.withTenant(tenant)));
    }
}
