package com.apex.scaffold.integration.device.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * 设备接入组件入口（方案 M5 / 批次 E3；C7 组件模型标准，与 connector 子域同构）。
 *
 * <p>引入 {@code apex-integration} 依赖即完成装配。开关：
 * {@code apex.component.integration.device.enabled}（默认 true）与
 * {@code apex.component.integration.device.interfaces-enabled}（默认 true）。
 *
 * <p>interfaces 类自身也标注了 interfaces-enabled 条件：宿主若经 connector 子域的
 * 整包组件扫描覆盖到本子域，开关依然生效（双路径装配下行为一致）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "apex.component.integration.device",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IotDeviceComponentAutoConfiguration {

    /** 设备核心（domain/application/infrastructure；interfaces 与 MQTT 由独立开关装配）。 */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = "com.apex.scaffold.integration.device",
            excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "com\\.apex\\.scaffold\\.integration\\.device\\.autoconfigure\\..*"),
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "com\\.apex\\.scaffold\\.integration\\.device\\.interfaces\\..*"),
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "com\\.apex\\.scaffold\\.integration\\.device\\.mqtt\\..*")
            })
    static class IotDeviceCoreConfiguration {}

    /**
     * 设备管理与内部 HTTP 接口（RouterFunction + Handler）。
     *
     * <p>关闭后核心能力（注册、遥测受理、命令、规则）仍可用，仅不暴露 HTTP 端点。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "apex.component.integration.device",
            name = "interfaces-enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ComponentScan(basePackages = "com.apex.scaffold.integration.device.interfaces")
    static class IotDeviceInterfacesConfiguration {}
}
