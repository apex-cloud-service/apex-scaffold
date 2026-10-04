package com.apex.scaffold;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 应用程序启动类。
 *
 * <p>Apex Scaffold 应用程序入口，基于 Spring Boot 3 和 WebFlux 响应式编程模型。
 * 启用异步任务调度、定时任务调度和 Nacos 服务发现。
 *
 * <p>数据库 schema 迁移由 Flyway 统一管理，所有环境均使用 Flyway
 * 迁移脚本（{@code classpath:db/migration/}）进行 DDL 变更，
 * 确保各环境 schema 的一致性。
 *
 * <p>主应用显式排除认证中心包的组件扫描，避免独立认证中心应用的 Bean 被重复装配。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@SpringBootApplication(exclude = {R2dbcAutoConfiguration.class})
@ComponentScan(
        basePackages = "com.apex.scaffold",
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.apex\\.scaffold\\.authcenter\\..*")
        })
@ConfigurationPropertiesScan(basePackages = "com.apex.scaffold")
@EnableAsync
@EnableDiscoveryClient
public class Application {

    /**
     * 构造应用程序启动类。
     */
    public Application() {}

    /**
     * 应用程序入口方法。
     *
     * <p>启动 Spring Boot 应用程序，加载所有配置和 Bean，
     * Flyway 将在应用启动过程中自动执行数据库迁移。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        LogDirectoryConfigurer.configure();
        SpringApplication.run(Application.class, args);
    }
}
