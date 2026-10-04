package com.apex.scaffold.interfaces.router;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import com.apex.scaffold.interfaces.handler.SsoAuditLogHandler;
import com.apex.scaffold.interfaces.handler.SsoProviderHandler;
import com.apex.scaffold.interfaces.handler.SsoSessionHandler;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springdoc.core.fn.builders.content.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * SSO 管理路由配置。
 *
 * <p>基于 WebFlux 函数式编程模型注册单点登录管理端点，路由前缀为
 * {@code /admin-api/v1/sso}，覆盖 SSO 提供方（Provider）的增删改查与启停、
 * 在线会话查询与强制下线、审计日志查询与统计，业务处理分别委托
 * {@link SsoProviderHandler}、{@link SsoSessionHandler} 与 {@link SsoAuditLogHandler}。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see SsoProviderHandler
 * @see SsoSessionHandler
 * @see SsoAuditLogHandler
 */
@Configuration
public class SsoAdminRouter {

    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/sso/providers",
                beanClass = SsoProviderHandler.class,
                beanMethod = "list"),
        @RouterOperation(
                path = "/admin-api/v1/sso/providers",
                beanClass = SsoProviderHandler.class,
                beanMethod = "create"),
        @RouterOperation(
                path = "/admin-api/v1/sso/providers/{id}",
                beanClass = SsoProviderHandler.class,
                beanMethod = "update"),
        @RouterOperation(
                path = "/admin-api/v1/sso/providers/{id}",
                beanClass = SsoProviderHandler.class,
                beanMethod = "delete"),
        @RouterOperation(
                path = "/admin-api/v1/sso/providers/{id}",
                beanClass = SsoProviderHandler.class,
                beanMethod = "get"),
        @RouterOperation(
                path = "/admin-api/v1/sso/providers/{id}/test",
                beanClass = SsoProviderHandler.class,
                beanMethod = "testConnection"),
        @RouterOperation(
                path = "/admin-api/v1/sso/providers/{id}/metadata",
                beanClass = SsoProviderHandler.class,
                beanMethod = "getMetadata"),
        @RouterOperation(path = "/admin-api/v1/sso/sessions", beanClass = SsoSessionHandler.class, beanMethod = "list"),
        @RouterOperation(
                path = "/admin-api/v1/sso/sessions/{id}/revoke",
                beanClass = SsoSessionHandler.class,
                beanMethod = "revoke"),
        @RouterOperation(
                path = "/admin-api/v1/sso/sessions/users/{userId}/revoke",
                beanClass = SsoSessionHandler.class,
                beanMethod = "revokeUserSessions"),
        @RouterOperation(
                path = "/admin-api/v1/sso/sessions/statistics",
                beanClass = SsoSessionHandler.class,
                beanMethod = "getStatistics"),
        @RouterOperation(
                path = "/admin-api/v1/sso/audit-logs",
                beanClass = SsoAuditLogHandler.class,
                beanMethod = "list"),
        @RouterOperation(
                path = "/admin-api/v1/sso/audit-logs/statistics",
                beanClass = SsoAuditLogHandler.class,
                beanMethod = "getStatistics")
    })
    @Bean
    @SuppressWarnings("PMD.MethodTooLongRule")
    public RouterFunction<ServerResponse> ssoAdminRoutes(
            SsoProviderHandler providerHandler, SsoSessionHandler sessionHandler, SsoAuditLogHandler auditLogHandler) {
        return SpringdocRouteBuilder.route()
                .GET("/admin-api/v1/sso/providers", providerHandler::list, ops -> ops.operationId("listSsoProviders")
                        .summary("查询 SSO 提供商列表")
                        .tag("后台-SSO 管理")
                        .parameter(parameterBuilder()
                                .name("page")
                                .description("页码")
                                .in(ParameterIn.QUERY)
                                .example("1"))
                        .parameter(parameterBuilder()
                                .name("size")
                                .description("每页数量")
                                .in(ParameterIn.QUERY)
                                .example("20"))
                        .parameter(parameterBuilder()
                                .name("providerName")
                                .description("提供商名称")
                                .in(ParameterIn.QUERY))
                        .parameter(parameterBuilder()
                                .name("protocolType")
                                .description("协议类型")
                                .in(ParameterIn.QUERY))
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .POST("/admin-api/v1/sso/providers", providerHandler::create, ops -> ops.operationId(
                                "createSsoProvider")
                        .summary("创建 SSO 提供商")
                        .tag("后台-SSO 管理")
                        .requestBody(requestBodyBuilder()
                                .description("SSO 提供商配置信息")
                                .required(true)
                                .content(Builder.contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)))
                        .response(responseBuilder().responseCode("200").description("创建成功")))
                .PUT("/admin-api/v1/sso/providers/{id}", providerHandler::update, ops -> ops.operationId(
                                "updateSsoProvider")
                        .summary("更新 SSO 提供商")
                        .tag("后台-SSO 管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("提供商 ID")
                                .in(ParameterIn.PATH)
                                .required(true))
                        .requestBody(requestBodyBuilder()
                                .description("SSO 提供商更新信息")
                                .required(true)
                                .content(Builder.contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)))
                        .response(responseBuilder().responseCode("200").description("更新成功")))
                .DELETE("/admin-api/v1/sso/providers/{id}", providerHandler::delete, ops -> ops.operationId(
                                "deleteSsoProvider")
                        .summary("删除 SSO 提供商")
                        .tag("后台-SSO 管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("提供商 ID")
                                .in(ParameterIn.PATH)
                                .required(true))
                        .response(responseBuilder().responseCode("200").description("删除成功")))
                .GET("/admin-api/v1/sso/providers/{id}", providerHandler::get, ops -> ops.operationId("getSsoProvider")
                        .summary("查询 SSO 提供商详情")
                        .tag("后台-SSO 管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("提供商 ID")
                                .in(ParameterIn.PATH)
                                .required(true))
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .POST("/admin-api/v1/sso/providers/{id}/test", providerHandler::testConnection, ops -> ops.operationId(
                                "testSsoProviderConnection")
                        .summary("测试 SSO 提供商连接")
                        .tag("后台-SSO 管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("提供商 ID")
                                .in(ParameterIn.PATH)
                                .required(true))
                        .response(responseBuilder().responseCode("200").description("测试完成")))
                .GET("/admin-api/v1/sso/providers/{id}/metadata", providerHandler::getMetadata, ops -> ops.operationId(
                                "getSsoProviderMetadata")
                        .summary("获取 SSO 提供商元数据")
                        .tag("后台-SSO 管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("提供商 ID")
                                .in(ParameterIn.PATH)
                                .required(true))
                        .response(responseBuilder().responseCode("200").description("获取成功")))
                .GET("/admin-api/v1/sso/sessions", sessionHandler::list, ops -> ops.operationId("listSsoSessions")
                        .summary("查询 SSO 会话列表")
                        .tag("后台-SSO 管理")
                        .parameter(parameterBuilder()
                                .name("page")
                                .description("页码")
                                .in(ParameterIn.QUERY)
                                .example("1"))
                        .parameter(parameterBuilder()
                                .name("size")
                                .description("每页数量")
                                .in(ParameterIn.QUERY)
                                .example("20"))
                        .parameter(parameterBuilder()
                                .name("username")
                                .description("用户名")
                                .in(ParameterIn.QUERY))
                        .parameter(parameterBuilder()
                                .name("providerId")
                                .description("提供商 ID")
                                .in(ParameterIn.QUERY))
                        .parameter(parameterBuilder()
                                .name("status")
                                .description("会话状态")
                                .in(ParameterIn.QUERY))
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .POST("/admin-api/v1/sso/sessions/{id}/revoke", sessionHandler::revoke, ops -> ops.operationId(
                                "revokeSsoSession")
                        .summary("撤销 SSO 会话")
                        .tag("后台-SSO 管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("会话 ID")
                                .in(ParameterIn.PATH)
                                .required(true))
                        .response(responseBuilder().responseCode("200").description("撤销成功")))
                .POST(
                        "/admin-api/v1/sso/sessions/users/{userId}/revoke",
                        sessionHandler::revokeUserSessions,
                        ops -> ops.operationId("revokeUserSsoSessions")
                                .summary("撤销用户所有 SSO 会话")
                                .tag("后台-SSO 管理")
                                .parameter(parameterBuilder()
                                        .name("userId")
                                        .description("用户 ID")
                                        .in(ParameterIn.PATH)
                                        .required(true))
                                .response(responseBuilder().responseCode("200").description("撤销成功")))
                .GET("/admin-api/v1/sso/sessions/statistics", sessionHandler::getStatistics, ops -> ops.operationId(
                                "getSsoSessionStatistics")
                        .summary("获取 SSO 会话统计")
                        .tag("后台-SSO 管理")
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .GET("/admin-api/v1/sso/audit-logs", auditLogHandler::list, ops -> ops.operationId("listSsoAuditLogs")
                        .summary("查询 SSO 审计日志列表")
                        .tag("后台-SSO 管理")
                        .parameter(parameterBuilder()
                                .name("page")
                                .description("页码")
                                .in(ParameterIn.QUERY)
                                .example("1"))
                        .parameter(parameterBuilder()
                                .name("size")
                                .description("每页数量")
                                .in(ParameterIn.QUERY)
                                .example("20"))
                        .parameter(parameterBuilder()
                                .name("username")
                                .description("用户名")
                                .in(ParameterIn.QUERY))
                        .parameter(parameterBuilder()
                                .name("providerId")
                                .description("提供商 ID")
                                .in(ParameterIn.QUERY))
                        .parameter(parameterBuilder()
                                .name("operationType")
                                .description("操作类型")
                                .in(ParameterIn.QUERY))
                        .parameter(parameterBuilder()
                                .name("result")
                                .description("操作结果")
                                .in(ParameterIn.QUERY))
                        .parameter(parameterBuilder()
                                .name("startTime")
                                .description("开始时间")
                                .in(ParameterIn.QUERY))
                        .parameter(parameterBuilder()
                                .name("endTime")
                                .description("结束时间")
                                .in(ParameterIn.QUERY))
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .GET("/admin-api/v1/sso/audit-logs/statistics", auditLogHandler::getStatistics, ops -> ops.operationId(
                                "getSsoAuditLogStatistics")
                        .summary("获取 SSO 审计日志统计")
                        .tag("后台-SSO 管理")
                        .parameter(parameterBuilder()
                                .name("startTime")
                                .description("开始时间")
                                .in(ParameterIn.QUERY))
                        .parameter(parameterBuilder()
                                .name("endTime")
                                .description("结束时间")
                                .in(ParameterIn.QUERY))
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .build();
    }
}
