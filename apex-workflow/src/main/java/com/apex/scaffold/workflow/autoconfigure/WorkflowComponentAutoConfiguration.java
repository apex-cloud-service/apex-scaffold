package com.apex.scaffold.workflow.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 工作流能力组件入口（方案 M3 / 批次 C7）。
 *
 * <p>引入 {@code apex-workflow} 依赖即完成组件装配，无需消费方手工 {@code scanBasePackages}。
 * 组件开关：{@code apex.component.workflow.enabled}（默认 true）总开关；
 * {@code apex.component.workflow.interfaces-enabled}（默认 true）协议层子开关。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "apex.component.workflow",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@org.springframework.boot.context.properties.ConfigurationPropertiesScan("com.apex.scaffold.workflow")
public class WorkflowComponentAutoConfiguration {

    /** 组件核心（domain/application/infrastructure）。 */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = "com.apex.scaffold.workflow",
            excludeFilters = {
                @ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.REGEX,
                        pattern = "com\\.apex\\.scaffold\\.workflow\\.interfaces\\..*"),
                // 排除组件入口自身，防止 @ComponentScan 扫描到本配置类引发循环 @Import
                @ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.REGEX,
                        pattern = "com\\.apex\\.scaffold\\.workflow\\.autoconfigure\\..*")
            })
    static class WorkflowCoreConfiguration {}

    /** 协议层（interfaces）：可由 interfaces-enabled=false 单独关闭。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "apex.component.workflow",
            name = "interfaces-enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ComponentScan(basePackages = "com.apex.scaffold.workflow.interfaces")
    static class WorkflowInterfacesConfiguration {}
}
