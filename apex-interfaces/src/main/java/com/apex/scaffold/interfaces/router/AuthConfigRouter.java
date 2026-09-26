package com.apex.scaffold.interfaces.router;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import com.apex.scaffold.interfaces.handler.AuthConfigHandler;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springdoc.core.fn.builders.content.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 认证配置管理路由配置。
 *
 * <p>基于 WebFlux 函数式编程模型注册认证配置相关的管理端点，路由前缀为
 * {@code /admin-api/v1/auth/config}，覆盖 JWT 配置的查询、更新与连通性测试、
 * OAuth2 客户端管理以及认证侧登录日志查询，业务处理统一委托 {@link AuthConfigHandler}。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see AuthConfigHandler
 */
@Configuration
public class AuthConfigRouter {

    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/auth/config/jwt",
                method = RequestMethod.GET,
                beanClass = AuthConfigHandler.class,
                beanMethod = "getJwtConfig"),
        @RouterOperation(
                path = "/admin-api/v1/auth/config/jwt",
                method = RequestMethod.PUT,
                beanClass = AuthConfigHandler.class,
                beanMethod = "updateJwtConfig"),
        @RouterOperation(
                path = "/admin-api/v1/auth/config/session",
                method = RequestMethod.GET,
                beanClass = AuthConfigHandler.class,
                beanMethod = "getSessionConfig"),
        @RouterOperation(
                path = "/admin-api/v1/auth/config/session",
                method = RequestMethod.PUT,
                beanClass = AuthConfigHandler.class,
                beanMethod = "updateSessionConfig"),
        @RouterOperation(
                path = "/admin-api/v1/auth/config/security",
                method = RequestMethod.GET,
                beanClass = AuthConfigHandler.class,
                beanMethod = "getSecurityPolicy"),
        @RouterOperation(
                path = "/admin-api/v1/auth/config/security",
                method = RequestMethod.PUT,
                beanClass = AuthConfigHandler.class,
                beanMethod = "updateSecurityPolicy"),
        @RouterOperation(
                path = "/admin-api/v1/auth/config/permissions/sync",
                method = RequestMethod.POST,
                beanClass = AuthConfigHandler.class,
                beanMethod = "syncPermissions"),
        @RouterOperation(
                path = "/admin-api/v1/auth/config/oauth2/clients",
                method = RequestMethod.GET,
                beanClass = AuthConfigHandler.class,
                beanMethod = "listOAuth2Clients"),
        @RouterOperation(
                path = "/admin-api/v1/auth/config/sessions",
                method = RequestMethod.GET,
                beanClass = AuthConfigHandler.class,
                beanMethod = "listOnlineSessions"),
        @RouterOperation(
                path = "/admin-api/v1/auth/config/sessions/kickout",
                method = RequestMethod.POST,
                beanClass = AuthConfigHandler.class,
                beanMethod = "kickoutSession"),
        @RouterOperation(
                path = "/admin-api/v1/auth/config/login-logs",
                method = RequestMethod.GET,
                beanClass = AuthConfigHandler.class,
                beanMethod = "listLoginLogs"),
        @RouterOperation(
                path = "/admin-api/v1/auth/config/permissions",
                method = RequestMethod.GET,
                beanClass = AuthConfigHandler.class,
                beanMethod = "listPermissions"),
        @RouterOperation(
                path = "/admin-api/v1/auth/config/jwt/test",
                method = RequestMethod.POST,
                beanClass = AuthConfigHandler.class,
                beanMethod = "testJwtConfig")
    })
    public RouterFunction<ServerResponse> authConfigRoutes(AuthConfigHandler handler) {
        return SpringdocRouteBuilder.route()
                .GET("/admin-api/v1/auth/config/jwt", handler::getJwtConfig, ops -> ops.operationId("getJwtConfig")
                        .summary("查询 JWT 配置")
                        .tag("后台-认证授权配置")
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .PUT("/admin-api/v1/auth/config/jwt", handler::updateJwtConfig, ops -> ops.operationId(
                                "updateJwtConfig")
                        .summary("更新 JWT 配置")
                        .tag("后台-认证授权配置")
                        .requestBody(requestBodyBuilder()
                                .description("JWT 配置更新请求")
                                .required(true)
                                .content(Builder.contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)))
                        .response(responseBuilder().responseCode("200").description("更新成功")))
                .GET("/admin-api/v1/auth/config/session", handler::getSessionConfig, ops -> ops.operationId(
                                "getSessionConfig")
                        .summary("查询会话配置")
                        .tag("后台-认证授权配置")
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .PUT("/admin-api/v1/auth/config/session", handler::updateSessionConfig, ops -> ops.operationId(
                                "updateSessionConfig")
                        .summary("更新会话配置")
                        .tag("后台-认证授权配置")
                        .requestBody(requestBodyBuilder()
                                .description("会话配置更新请求")
                                .required(true)
                                .content(Builder.contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)))
                        .response(responseBuilder().responseCode("200").description("更新成功")))
                .GET("/admin-api/v1/auth/config/security", handler::getSecurityPolicy, ops -> ops.operationId(
                                "getSecurityPolicy")
                        .summary("查询安全策略")
                        .tag("后台-认证授权配置")
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .PUT("/admin-api/v1/auth/config/security", handler::updateSecurityPolicy, ops -> ops.operationId(
                                "updateSecurityPolicy")
                        .summary("更新安全策略")
                        .tag("后台-认证授权配置")
                        .requestBody(requestBodyBuilder()
                                .description("安全策略配置更新请求")
                                .required(true)
                                .content(Builder.contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)))
                        .response(responseBuilder().responseCode("200").description("更新成功")))
                .POST("/admin-api/v1/auth/config/permissions/sync", handler::syncPermissions, ops -> ops.operationId(
                                "syncPermissions")
                        .summary("同步权限码")
                        .tag("后台-认证授权配置")
                        .response(responseBuilder().responseCode("200").description("同步成功")))
                .GET("/admin-api/v1/auth/config/oauth2/clients", handler::listOAuth2Clients, ops -> ops.operationId(
                                "listOAuth2ClientsAlias")
                        .summary("查询 OAuth2 客户端列表")
                        .tag("后台-认证授权配置")
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
                                .name("clientId")
                                .description("客户端 ID")
                                .in(ParameterIn.QUERY))
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .GET("/admin-api/v1/auth/config/sessions", handler::listOnlineSessions, ops -> ops.operationId(
                                "listOnlineSessionsAlias")
                        .summary("查询在线会话列表")
                        .tag("后台-认证授权配置")
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
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .POST("/admin-api/v1/auth/config/sessions/kickout", handler::kickoutSession, ops -> ops.operationId(
                                "kickoutSessionAlias")
                        .summary("踢出会话")
                        .tag("后台-认证授权配置")
                        .response(responseBuilder().responseCode("200").description("踢出成功")))
                .GET("/admin-api/v1/auth/config/login-logs", handler::listLoginLogs, ops -> ops.operationId(
                                "listLoginLogsAlias")
                        .summary("查询登录日志列表")
                        .tag("后台-认证授权配置")
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
                                .description("登录账号")
                                .in(ParameterIn.QUERY))
                        .parameter(parameterBuilder()
                                .name("status")
                                .description("登录状态")
                                .in(ParameterIn.QUERY))
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .GET("/admin-api/v1/auth/config/permissions", handler::listPermissions, ops -> ops.operationId(
                                "listPermissionsAlias")
                        .summary("查询权限列表")
                        .tag("后台-认证授权配置")
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .POST("/admin-api/v1/auth/config/jwt/test", handler::testJwtConfig, ops -> ops.operationId(
                                "testJwtConfig")
                        .summary("测试 JWT 配置")
                        .description("根据指定的 JWT 配置生成、验证和解码 Token，用于测试 JWT 配置的正确性")
                        .tag("后台-认证授权配置")
                        .requestBody(requestBodyBuilder()
                                .description("JWT 测试请求参数")
                                .required(true)
                                .content(Builder.contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)))
                        .response(responseBuilder().responseCode("200").description("测试成功，返回生成的 Token 及验证结果"))
                        .response(responseBuilder().responseCode("400").description("测试失败，配置错误或参数无效")))
                .build();
    }
}
