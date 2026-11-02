package com.apex.scaffold.integration.device.interfaces;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 设备域路由（方案 M5 / 批次 E3，WebFlux 函数式路由）。
 *
 * <p>内部端点与管理端点：
 * <ul>
 *   <li>POST {@code /internal/v1/iot/mqtt/auth}（MQTT 认证钩子，EMQX 调用）</li>
 *   <li>POST {@code /internal/v1/iot/devices/{key}/commands}（命令下行）</li>
 *   <li>GET {@code /internal/v1/iot/devices/stats}（D5 聚合门面消费，契约固定）</li>
 *   <li>GET/POST {@code /integration/v1/iot/devices}、GET/DELETE
 *       {@code /integration/v1/iot/devices/{id}}、GET
 *       {@code /integration/v1/iot/devices/{id}/telemetry}</li>
 *   <li>POST {@code /integration/v1/iot/rules}、GET {@code /integration/v1/iot/rules/{id}}、
 *       PUT {@code /integration/v1/iot/rules/{id}/toggle}</li>
 * </ul>
 *
 * <p>springdoc 元数据（{@code @RouterOperation}）暂未声明：本模块 POM 无
 * springdoc 传递依赖，待依赖补齐后补充。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "apex.component.integration.device",
        name = "interfaces-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IotDeviceRouter {

    /**
     * 注册设备域路由。
     *
     * @param internalHandler 内部端点 Handler
     * @param adminHandler    设备管理 Handler
     * @param ruleHandler     规则管理 Handler
     * @return 路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> iotDeviceRoutes(
            IotInternalHandler internalHandler, DeviceAdminHandler adminHandler, IotRuleHandler ruleHandler) {
        return RouterFunctions.route()
                // 内部端点：MQTT 认证钩子（无租户上下文）、命令下行与聚合统计（可信租户上下文）
                .POST(
                        "/internal/v1/iot/mqtt/auth",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        internalHandler::mqttAuth)
                .POST(
                        "/internal/v1/iot/devices/{key}/commands",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        internalHandler::dispatchCommand)
                .GET(
                        "/internal/v1/iot/devices/stats",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        internalHandler::stats)
                // 设备管理端点
                .GET(
                        "/integration/v1/iot/devices",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        adminHandler::page)
                .POST(
                        "/integration/v1/iot/devices",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        adminHandler::register)
                .GET("/integration/v1/iot/devices/{id}", adminHandler::get)
                .DELETE("/integration/v1/iot/devices/{id}", adminHandler::deregister)
                .GET(
                        "/integration/v1/iot/devices/{id}/telemetry",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        adminHandler::telemetry)
                // 规则管理端点
                .POST(
                        "/integration/v1/iot/rules",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        ruleHandler::create)
                .GET("/integration/v1/iot/rules/{id}", ruleHandler::get)
                .PUT(
                        "/integration/v1/iot/rules/{id}/toggle",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        ruleHandler::toggle)
                .build();
    }
}
