package com.apex.scaffold.job;

import org.reactivestreams.Publisher;

/**
 * 工作流超时扫描端口，由工作流运行模块提供适配。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@FunctionalInterface
public interface WorkflowJobPort {
    /**
     * 扫描超时任务，异步失败应传播至调度中心。
     *
     * @return 扫描完成信号，扫描失败通过错误信号传播至调度中心
     */
    Publisher<Void> scanTimeoutTasks();
}
