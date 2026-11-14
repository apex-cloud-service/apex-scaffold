package com.apex.scaffold.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 系统能力组件入口（方案 M3 / 批次 C7）。
 *
 * <p>补齐 {@code apex-system} 缺失的组件自动装配入口，使组合部署与拆分部署对 system
 * 使用同一条装配路径。引入依赖后需显式开启 {@code apex.component.system.enabled=true}
 * 才装配四层（interfaces/application/domain/infrastructure）；默认关闭，避免 test-scope
 * 依赖 apex-system 的服务（如 auth-service）在测试上下文中重复注册同名 Bean。
 *
 * <p>system 的 Router/Handler 按现行约定保留在业务库（AGENTS.md「system 归属」），
 * 因此不提供单独的协议层子开关。组合部署需同时声明
 * {@code apex.component.system.enabled=true}。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "apex.component.system", name = "enabled", havingValue = "true", matchIfMissing = false)
@org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass(
        "com.apex.scaffold.systemservice.SystemServiceApplication")
@org.springframework.boot.context.properties.ConfigurationPropertiesScan("com.apex.scaffold")
public class SystemComponentAutoConfiguration {

    /** 组件核心（system 四层；排除组件入口自身防止循环导入）。 */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = {
                "com.apex.scaffold.interfaces",
                "com.apex.scaffold.application",
                "com.apex.scaffold.domain",
                "com.apex.scaffold.infrastructure"
            })
    static class SystemCoreConfiguration {}
}
