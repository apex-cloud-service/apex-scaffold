package com.apex.scaffold.integration.device.interfaces;

import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
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
 *       {@code /integration/v1/iot/devices/{id}/telemetry}、GET
 *       {@code /integration/v1/iot/devices/{id}/shadow}、GET/POST
 *       {@code /integration/v1/iot/devices/{id}/commands}</li>
 *   <li>GET/POST {@code /integration/v1/iot/rules}、GET {@code /integration/v1/iot/rules/{id}}、
 *       PUT {@code /integration/v1/iot/rules/{id}/toggle}</li>
 * </ul>
 *
 * <p>{@code @RouterOperation} 只声明路由绑定；操作说明、参数、请求体与响应模型统一由
 * {@link IotInternalHandler}、{@link DeviceAdminHandler}、{@link IotRuleHandler}
 * 方法上的 SpringDoc 注解决定。本模块 POM 已声明 springdoc-webflux-api（optional），
 * 无该依赖时不注册文档元数据，路由行为不变。
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
    @RouterOperations({
        @RouterOperation(
                path = "/internal/v1/iot/mqtt/auth",
                method = RequestMethod.POST,
                beanClass = IotInternalHandler.class,
                beanMethod = "mqttAuth"),
        @RouterOperation(
                path = "/internal/v1/iot/mqtt/acl",
                method = RequestMethod.POST,
                beanClass = IotInternalHandler.class,
                beanMethod = "mqttAcl"),
        @RouterOperation(
                path = "/internal/v1/iot/devices/{key}/commands",
                method = RequestMethod.POST,
                beanClass = IotInternalHandler.class,
                beanMethod = "dispatchCommand"),
        @RouterOperation(
                path = "/internal/v1/iot/devices/stats",
                method = RequestMethod.GET,
                beanClass = IotInternalHandler.class,
                beanMethod = "stats"),
        @RouterOperation(
                path = "/integration/v1/iot/devices",
                method = RequestMethod.GET,
                beanClass = DeviceAdminHandler.class,
                beanMethod = "page"),
        @RouterOperation(
                path = "/integration/v1/iot/devices",
                method = RequestMethod.POST,
                beanClass = DeviceAdminHandler.class,
                beanMethod = "register"),
        @RouterOperation(
                path = "/integration/v1/iot/devices/{id}",
                method = RequestMethod.GET,
                beanClass = DeviceAdminHandler.class,
                beanMethod = "get"),
        @RouterOperation(
                path = "/integration/v1/iot/devices/{id}",
                method = RequestMethod.DELETE,
                beanClass = DeviceAdminHandler.class,
                beanMethod = "deregister"),
        @RouterOperation(
                path = "/integration/v1/iot/devices/{id}/telemetry",
                method = RequestMethod.GET,
                beanClass = DeviceAdminHandler.class,
                beanMethod = "telemetry"),
        @RouterOperation(
                path = "/integration/v1/iot/devices/{id}/shadow",
                method = RequestMethod.GET,
                beanClass = DeviceAdminHandler.class,
                beanMethod = "shadow"),
        @RouterOperation(
                path = "/integration/v1/iot/devices/{id}/commands",
                method = RequestMethod.GET,
                beanClass = DeviceAdminHandler.class,
                beanMethod = "commands"),
        @RouterOperation(
                path = "/integration/v1/iot/devices/{id}/commands",
                method = RequestMethod.POST,
                beanClass = DeviceAdminHandler.class,
                beanMethod = "dispatchCommand"),
        @RouterOperation(
                path = "/integration/v1/iot/rules",
                method = RequestMethod.GET,
                beanClass = IotRuleHandler.class,
                beanMethod = "page"),
        @RouterOperation(
                path = "/integration/v1/iot/rules",
                method = RequestMethod.POST,
                beanClass = IotRuleHandler.class,
                beanMethod = "create"),
        @RouterOperation(
                path = "/integration/v1/iot/rules/{id}",
                method = RequestMethod.GET,
                beanClass = IotRuleHandler.class,
                beanMethod = "get"),
        @RouterOperation(
                path = "/integration/v1/iot/rules/{id}/toggle",
                method = RequestMethod.PUT,
                beanClass = IotRuleHandler.class,
                beanMethod = "toggle")
    })
    public RouterFunction<ServerResponse> iotDeviceRoutes(
            IotInternalHandler internalHandler, DeviceAdminHandler adminHandler, IotRuleHandler ruleHandler) {
        return RouterFunctions.route()
                // 内部端点：MQTT 认证钩子（无租户上下文）、命令下行与聚合统计（可信租户上下文）
                .POST(
                        "/internal/v1/iot/mqtt/auth",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        internalHandler::mqttAuth)
                // EMQX HTTP Authorize 钩子：pub/sub 时按租户命名空间返回 allow/deny
                .POST(
                        "/internal/v1/iot/mqtt/acl",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        internalHandler::mqttAcl)
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
                // 设备影子查询与命令下发/历史（E3 管理面补强）
                .GET(
                        "/integration/v1/iot/devices/{id}/shadow",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        adminHandler::shadow)
                .GET(
                        "/integration/v1/iot/devices/{id}/commands",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        adminHandler::commands)
                .POST(
                        "/integration/v1/iot/devices/{id}/commands",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        adminHandler::dispatchCommand)
                // 规则管理端点
                .GET(
                        "/integration/v1/iot/rules",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        ruleHandler::page)
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
