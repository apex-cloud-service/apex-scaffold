package com.apex.scaffold.office.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 办公协同能力组件入口（方案 M3 / 批次 C7）。
 *
 * <p>引入 {@code apex-office} 依赖即完成组件装配，无需消费方手工 {@code scanBasePackages}。
 * 组件开关：{@code apex.component.office.enabled}（默认 true）总开关；
 * {@code apex.component.office.interfaces-enabled}（默认 true）协议层子开关。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "apex.component.office", name = "enabled", havingValue = "true", matchIfMissing = true)
@org.springframework.boot.context.properties.ConfigurationPropertiesScan("com.apex.scaffold.office")
public class OfficeComponentAutoConfiguration {

    /** 组件核心（domain/application/infrastructure）。 */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = "com.apex.scaffold.office",
            excludeFilters = {
                @ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.REGEX,
                        pattern = "com\\.apex\\.scaffold\\.office\\.interfaces\\..*"),
                // 排除组件入口自身，防止 @ComponentScan 扫描到本配置类引发循环 @Import
                @ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.REGEX,
                        pattern = "com\\.apex\\.scaffold\\.office\\.autoconfigure\\..*")
            })
    static class OfficeCoreConfiguration {}

    /** 协议层（interfaces）：可由 interfaces-enabled=false 单独关闭。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "apex.component.office",
            name = "interfaces-enabled",
            havingValue = "true",
            matchIfMissing = true)
    /**
     * 协议层（Router/Handler/Security）归属运行模块而非本业务库：此处不再扫描不存在的
     * {@code com.apex.scaffold.office.interfaces} 包。组合部署由各 service 的协议层装配入口负责，开关仍保留以保持
     * 配置契约兼容。
     */
    static class OfficeInterfacesConfiguration {}
}
