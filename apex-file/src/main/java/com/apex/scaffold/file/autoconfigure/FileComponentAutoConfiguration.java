package com.apex.scaffold.file.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 文件能力组件入口（方案 M3 / 批次 C7）。
 *
 * <p>引入 {@code apex-file} 依赖即完成组件装配（domain/application/infrastructure 全量），
 * 无需消费方手工 {@code scanBasePackages}。组件开关：
 * <ul>
 *   <li>{@code apex.component.file.enabled}（默认 true）——组件总开关；</li>
 *   <li>{@code apex.component.file.interfaces-enabled}（默认 true）——协议层（Router/Handler）子开关，
 *       关闭后仅装配领域/应用/基础设施，用于组合部署时由其它组件承载 HTTP 入口。</li>
 * </ul>
 *
 * <p>配置属性扫描限定本组件根包，避免跨组件误装配。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "apex.component.file", name = "enabled", havingValue = "true", matchIfMissing = true)
@org.springframework.boot.context.properties.ConfigurationPropertiesScan("com.apex.scaffold.file")
public class FileComponentAutoConfiguration {

    /**
     * 组件核心（domain/application/infrastructure）。
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = "com.apex.scaffold.file",
            excludeFilters = {
                @ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.REGEX,
                        pattern = "com\\.apex\\.scaffold\\.file\\.interfaces\\..*"),
                // 排除组件入口自身，防止 @ComponentScan 扫描到本配置类引发循环 @Import
                @ComponentScan.Filter(
                        type = org.springframework.context.annotation.FilterType.REGEX,
                        pattern = "com\\.apex\\.scaffold\\.file\\.autoconfigure\\..*")
            })
    static class FileCoreConfiguration {}

    /**
     * 协议层（interfaces）：可由 {@code interfaces-enabled=false} 单独关闭。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "apex.component.file",
            name = "interfaces-enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ComponentScan(basePackages = "com.apex.scaffold.file.interfaces")
    static class FileInterfacesConfiguration {}
}
