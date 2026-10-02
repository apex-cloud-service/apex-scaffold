package com.apex.scaffold.authcenter.interfaces.router;

import com.apex.scaffold.authcenter.application.service.OAuth2MetadataService;
import com.apex.scaffold.authcenter.config.OAuth2Properties;
import io.swagger.v3.oas.annotations.Operation;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
@EnableConfigurationProperties(OAuth2Properties.class)
@SuppressWarnings({"PMD.ClassNamingShouldBeCamelRule", "PMD.ClassMustHaveAuthorRule"})
public class OAuth2MetadataRouter {
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/.well-known/openid-configuration",
                method = RequestMethod.GET,
                operation = @Operation(operationId = "oauth2Discovery", summary = "OAuth2 discovery")),
        @RouterOperation(
                path = "/.well-known/jwks.json",
                method = RequestMethod.GET,
                operation = @Operation(operationId = "oauth2Jwks", summary = "OAuth2 public signing keys"))
    })
    public RouterFunction<ServerResponse> oauth2MetadataRoutes(OAuth2MetadataService metadata) {
        return RouterFunctions.route()
                .GET("/.well-known/openid-configuration", request -> ServerResponse.ok()
                        .bodyValue(metadata.discovery()))
                .GET("/.well-known/jwks.json", request -> ServerResponse.ok().bodyValue(metadata.jwks()))
                .build();
    }
}
