package com.apex.scaffold.integration.connector.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 集成能力组件入口（方案 M5 / 批次 E1；C7 组件模型标准）。
 *
 * <p>引入 {@code apex-integration} 依赖即完成装配。开关：
 * {@code apex.component.integration.enabled}（默认 true）与
 * {@code apex.component.integration.interfaces-enabled}（默认 true）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "apex.component.integration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@org.springframework.boot.context.properties.ConfigurationPropertiesScan("com.apex.scaffold.integration")
public class IntegrationComponentAutoConfiguration {

    /** 连接器子域核心；设备子域由 {@code apex.component.integration.device.enabled} 独立控制。 */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = "com.apex.scaffold.integration.connector",
            excludeFilters = {
                @ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.REGEX,
                        pattern = "com\\.apex\\.scaffold\\.integration\\.connector\\.autoconfigure\\..*"),
                @ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.REGEX,
                        pattern = "com\\.apex\\.scaffold\\.integration\\.connector\\.interfaces\\..*")
            })
    static class IntegrationCoreConfiguration {}

    /**
     * 管理与 webhook HTTP 接口（RouterFunction + Handler）。
     *
     * <p>关闭后核心能力（连接器、出站调用、入站受理服务）仍可用，
     * 仅不暴露 HTTP 端点。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "apex.component.integration",
            name = "interfaces-enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ComponentScan(basePackages = "com.apex.scaffold.integration.connector.interfaces")
    static class IntegrationInterfacesConfiguration {}
}
