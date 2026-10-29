package com.apex.scaffold.workflowservice;

import com.apex.scaffold.job.WorkflowJobPort;
import com.apex.scaffold.workflow.application.scheduler.WorkflowTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在工作流运行模块适配固定超时扫描端口。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration(proxyBeanMethods = false)
public class WorkflowJobPortConfiguration {
    /**
     * 将任务代理调用交由既有扫描器处理，保留扫描互斥与异步失败传播语义。
     *
     * @param handler 工作流超时扫描器
     * @return 固定超时扫描任务端口
     */
    @Bean
    WorkflowJobPort workflowJobPort(WorkflowTimeoutHandler handler) {
        return handler::scanTimeoutTasks;
    }
}
