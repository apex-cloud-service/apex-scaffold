package com.apex.scaffold.infrastructure.remote.config;

import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在消费者装配之前绑定配置并注册容器所有的 HTTP 资源。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExternalHttpClientPoolProperties.class)
public class ManagedWebClientInitializer {
    /**
     * 可选日志服务在客户端创建前注入，销毁回调负责释放连接池。
     *
     * @param properties 外部 HTTP 连接与连接池配置
     * @param logService 可选的 API 调用日志服务提供者
     * @return 由当前容器管理生命周期的客户端工厂
     */
    @Bean(destroyMethod = "close")
    public ManagedWebClientFactory managedWebClientFactory(
            ExternalHttpClientPoolProperties properties, ObjectProvider<ApiCallLogService> logService) {
        return new ManagedWebClientFactory(properties, logService.getIfAvailable());
    }
}
