package com.apex.scaffold.interfaces.test;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 集成测试专用引导类（仅测试作用域）。
 *
 * <p>旧单体启动类 {@code com.apex.scaffold.Application} 已随 Phase 6 单体退役移除，
 * apex-interfaces 降级为纯库模块（不再产出可执行 fat jar）。本类仅在测试源码树中提供
 * 与旧单体等价的组件扫描边界，供集成测试加载完整 Spring 上下文使用，不会被打包进
 * 任何生产产物。
 *
 * <p>装配边界与旧单体保持一致：
 * <ul>
 *   <li>扫描 {@code com.apex.scaffold}，排除 {@code auth} / {@code authservice} 包；</li>
 *   <li>{@code @ConfigurationPropertiesScan} 覆盖 {@code com.apex.scaffold}；</li>
 *   <li>启用异步执行与 Nacos 服务发现（测试 profile 下服务发现被禁用）。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SpringBootApplication(exclude = {R2dbcAutoConfiguration.class})
@ComponentScan(
        basePackages = "com.apex.scaffold",
        excludeFilters = {
            @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.apex\\.scaffold\\.auth\\..*"),
            @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.apex\\.scaffold\\.authservice\\..*")
        })
@ConfigurationPropertiesScan(basePackages = "com.apex.scaffold")
@EnableAsync
@EnableDiscoveryClient
public class IntegrationTestApplication {

    /**
     * 构造集成测试引导类。
     */
    public IntegrationTestApplication() {}
}
