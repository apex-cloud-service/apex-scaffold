package com.apex.scaffold.job;

import org.reactivestreams.Publisher;

/**
 * 系统任务执行端口，由业务运行模块适配，调度增强不依赖业务实现。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@FunctionalInterface
public interface SystemJobPort {
    /**
     * 执行可信配置指定的任务，返回值的终态决定调度结果。
     *
     * @param jobId 可信部署配置指定的系统任务 ID
     * @return 任务执行完成信号，执行失败通过错误信号传播至调度中心
     */
    Publisher<Void> runOnce(Long jobId);
}
