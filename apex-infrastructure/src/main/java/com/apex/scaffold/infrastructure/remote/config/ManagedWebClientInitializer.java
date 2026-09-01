package com.apex.scaffold.infrastructure.remote.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 外部 HTTP 客户端工厂初始化配置。
 *
 * <p>通过 {@link ApplicationRunner} 在 Spring 容器完全启动后将 YAML 中 {@code remote.api.pool-*} 配置
 * 注入到 {@link ManagedWebClientFactory} 中，使连接池参数可通过配置文件调整。
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 2.1.0
 */
@Configuration
@EnableConfigurationProperties(ExternalHttpClientPoolProperties.class)
public class ManagedWebClientInitializer {
    private static final Logger log = LoggerFactory.getLogger(ManagedWebClientInitializer.class);

    /**
     * 容器启动后立即初始化受管 WebClient 工厂的连接池参数。
     *
     * @param properties 外部 HTTP 连接池配置属性
     * @return ApplicationRunner 实例
     */
    @Bean
    public ApplicationRunner managedWebClientPoolRunner(ExternalHttpClientPoolProperties properties) {
        return args -> ManagedWebClientFactory.initialize(properties);
    }
}
