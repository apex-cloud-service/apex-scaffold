package com.apex.scaffold.authcenter.interfaces.router;

import com.apex.scaffold.authcenter.interfaces.handler.OAuth2TokenHandler;
import io.swagger.v3.oas.annotations.Operation;
import org.springdoc.core.annotations.RouterOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
@SuppressWarnings({"PMD.ClassNamingShouldBeCamelRule", "PMD.ClassMustHaveAuthorRule"})
public class OAuth2TokenRouter {
    @Bean
    @RouterOperation(
            path = "/oauth2/token",
            method = RequestMethod.POST,
            beanClass = OAuth2TokenHandler.class,
            beanMethod = "token",
            operation = @Operation(operationId = "oauth2Token", summary = "OAuth2 token"))
    public RouterFunction<ServerResponse> oauth2TokenRoutes(OAuth2TokenHandler handler) {
        return RouterFunctions.route().POST("/oauth2/token", handler::token).build();
    }
}
