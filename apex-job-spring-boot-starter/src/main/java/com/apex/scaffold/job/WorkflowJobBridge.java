package com.apex.scaffold.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 固定工作流超时入口；通过类型化端口等待扫描终态并反馈失败。
 *
 * @author Apex Scaffold
 * @since 1.3.1
 */
@RequiredArgsConstructor
public final class WorkflowJobBridge {
    private final WorkflowJobPort handler;

    /** 不接受 XXL 参数指定 bean、方法或租户。 */
    @XxlJob("apexWorkflowTimeout")
    public void execute() {
        JobCompletion.await(
                Mono.defer(() -> Flux.from(handler.scanTimeoutTasks()).then()));
    }
}
