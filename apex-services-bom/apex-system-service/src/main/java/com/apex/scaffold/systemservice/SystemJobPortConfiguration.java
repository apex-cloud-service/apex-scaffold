package com.apex.scaffold.systemservice;

import com.apex.scaffold.application.service.JobService;
import com.apex.scaffold.job.SystemJobPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在系统运行模块适配调度端口，避免增强模块反向依赖业务库。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration(proxyBeanMethods = false)
public class SystemJobPortConfiguration {
    /**
     * 复用既有任务用例及其日志和失败传播链。
     *
     * @param service 系统任务应用服务
     * @return 固定系统任务端口
     */
    @Bean
    SystemJobPort systemJobPort(JobService service) {
        return service::runOnce;
    }
}
