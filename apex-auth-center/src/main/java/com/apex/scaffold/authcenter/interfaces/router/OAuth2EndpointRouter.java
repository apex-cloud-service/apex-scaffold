package com.apex.scaffold.authcenter.interfaces.router;

import com.apex.scaffold.authcenter.interfaces.handler.OAuth2EndpointHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
@SuppressWarnings({"PMD.ClassNamingShouldBeCamelRule", "PMD.ClassMustHaveAuthorRule"})
public class OAuth2EndpointRouter {
    @org.springdoc.core.annotations.RouterOperations({
        @org.springdoc.core.annotations.RouterOperation(
                path = "/oauth2/authorize",
                method = org.springframework.web.bind.annotation.RequestMethod.GET,
                beanClass = OAuth2EndpointHandler.class,
                beanMethod = "authorize",
                operation =
                        @io.swagger.v3.oas.annotations.Operation(
                                operationId = "oauth2Authorize",
                                summary = "签发 PKCE 授权码")),
        @org.springdoc.core.annotations.RouterOperation(
                path = "/oauth2/revoke",
                method = org.springframework.web.bind.annotation.RequestMethod.POST,
                beanClass = OAuth2EndpointHandler.class,
                beanMethod = "revoke",
                operation = @io.swagger.v3.oas.annotations.Operation(operationId = "oauth2Revoke", summary = "撤销授权令牌")),
        @org.springdoc.core.annotations.RouterOperation(
                path = "/oauth2/introspect",
                method = org.springframework.web.bind.annotation.RequestMethod.POST,
                beanClass = OAuth2EndpointHandler.class,
                beanMethod = "introspect",
                operation =
                        @io.swagger.v3.oas.annotations.Operation(operationId = "oauth2Introspect", summary = "查询令牌状态"))
    })
    @Bean
    public RouterFunction<ServerResponse> oauth2EndpointRoutes(OAuth2EndpointHandler handler) {
        return RouterFunctions.route()
                .GET("/oauth2/authorize", handler::authorize)
                .POST("/oauth2/revoke", handler::revoke)
                .POST("/oauth2/introspect", handler::introspect)
                .build();
    }
}
