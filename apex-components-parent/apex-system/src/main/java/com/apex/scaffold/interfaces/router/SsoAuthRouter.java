package com.apex.scaffold.interfaces.router;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import com.apex.scaffold.interfaces.handler.SsoAuthHandler;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * SSO 认证端路由配置
 *
 * <p>提供 SSO 单点登录的认证端点路由,所有路由使用 {@code /auth/sso} 前缀,
 * 作为公开端点(无需权限校验)供前端和 SSO 提供商回调使用。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>GET /auth/sso/initiate - 发起 SSO 认证(公开端点)</li>
 *   <li>POST /auth/sso/callback - SSO 回调处理(公开端点)</li>
 *   <li>POST /auth/sso/logout - SSO 登出(需认证)</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>initiate 和 callback 端点为公开端点,无需 JWT 认证</li>
 *   <li>callback 端点通过协议适配器验证 SSO 提供商的认证响应</li>
 *   <li>logout 端点需要 JWT 认证,用于撤销当前用户的 SSO 会话</li>
 * </ul>
 *
 * @author Apex Scaffold Generator
 * @since 1.3.1
 */
@Configuration
public class SsoAuthRouter {

    /**
     * 配置 SSO 认证端路由
     *
     * @param authHandler SSO 认证处理器
     * @return 路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(path = "/auth/sso/initiate", beanClass = SsoAuthHandler.class, beanMethod = "initiate"),
        @RouterOperation(path = "/auth/sso/callback", beanClass = SsoAuthHandler.class, beanMethod = "callback"),
        @RouterOperation(path = "/auth/sso/logout", beanClass = SsoAuthHandler.class, beanMethod = "logout")
    })
    public RouterFunction<ServerResponse> ssoAuthRoutes(SsoAuthHandler authHandler) {
        return SpringdocRouteBuilder.route()
                .GET("/auth/sso/initiate", authHandler::initiate, ops -> ops.operationId("initiateSsoAuth")
                        .summary("发起 SSO 认证")
                        .tag("SSO 认证")
                        .parameter(parameterBuilder()
                                .name("providerId")
                                .description("SSO 提供商 ID")
                                .in(ParameterIn.QUERY)
                                .required(true)
                                .example("1"))
                        .response(responseBuilder().responseCode("200").description("生成认证请求成功,返回重定向 URL"))
                        .response(responseBuilder().responseCode("400").description("参数错误"))
                        .response(responseBuilder().responseCode("404").description("SSO 提供商不存在或已禁用")))
                .POST("/auth/sso/callback", authHandler::callback, ops -> ops.operationId("handleSsoCallback")
                        .summary("SSO 回调处理")
                        .tag("SSO 认证")
                        .parameter(parameterBuilder()
                                .name("providerId")
                                .description("SSO 提供商 ID")
                                .in(ParameterIn.QUERY)
                                .required(true))
                        .response(responseBuilder().responseCode("200").description("认证成功,返回 JWT 令牌和用户信息"))
                        .response(responseBuilder().responseCode("400").description("认证响应参数错误或验证失败"))
                        .response(responseBuilder().responseCode("401").description("认证失败,用户身份无效"))
                        .response(responseBuilder().responseCode("404").description("SSO 提供商不存在或已禁用")))
                .POST("/auth/sso/logout", authHandler::logout, ops -> ops.operationId("ssoLogout")
                        .summary("SSO 登出")
                        .tag("SSO 认证")
                        .response(responseBuilder().responseCode("200").description("登出成功"))
                        .response(responseBuilder().responseCode("401").description("未授权,需要先登录"))
                        .response(responseBuilder().responseCode("404").description("SSO 会话不存在")))
                .build();
    }
}
