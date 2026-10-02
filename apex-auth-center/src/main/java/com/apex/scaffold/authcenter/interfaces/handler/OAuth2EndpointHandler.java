package com.apex.scaffold.authcenter.interfaces.handler;

import com.apex.scaffold.authcenter.application.service.OAuth2Service;
import com.apex.scaffold.authcenter.application.service.OAuth2Service.OAuthException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@SuppressWarnings({"PMD.ClassNamingShouldBeCamelRule", "PMD.ClassMustHaveAuthorRule"})
public class OAuth2EndpointHandler {
    private static final String BASIC_PREFIX = "Basic ";
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String INVALID_CLIENT = "invalid_client";
    private static final String STATE = "state";
    private static final int UNAUTHORIZED = 401;
    private static final int BASIC_PREFIX_LENGTH = 6;

    private final OAuth2Service service;

    public OAuth2EndpointHandler(OAuth2Service service) {
        this.service = service;
    }

    public Mono<ServerResponse> authorize(ServerRequest request) {
        if (request.queryParams().values().stream().anyMatch(values -> values.size() != 1)) {
            return error(400, "invalid_request");
        }
        Map<String, String> values = request.queryParams().toSingleValueMap();
        return request.principal()
                .flatMap(principal -> Mono.deferContextual(context -> service.authorize(
                        values,
                        values.get(CLIENT_ID),
                        principal.getName(),
                        context.get(com.apex.scaffold.common.context.TenantContext.REACTOR_CONTEXT_KEY))))
                .flatMap(result -> {
                    org.springframework.web.util.UriComponentsBuilder redirect =
                            org.springframework.web.util.UriComponentsBuilder.fromUriString(
                                            result.get("redirect_uri").toString())
                                    .queryParam("code", result.get("code"));
                    if (values.containsKey(STATE)) {
                        redirect.queryParam(STATE, values.get(STATE));
                    }
                    return ServerResponse.status(302)
                            .location(redirect.build().encode().toUri())
                            .header(HttpHeaders.CACHE_CONTROL, "no-store")
                            .build();
                })
                .switchIfEmpty(error(UNAUTHORIZED, "access_denied"))
                .onErrorResume(OAuthException.class, exception -> error(400, exception.getMessage()));
    }

    public Mono<ServerResponse> revoke(ServerRequest request) {
        return withClient(request, (values, clientId, tenant) -> service.revoke(values.get("token"), clientId, tenant)
                .then(ok(Map.of())));
    }

    public Mono<ServerResponse> introspect(ServerRequest request) {
        return withClient(
                request, (values, clientId, tenant) -> service.introspect(values.get("token"), clientId, tenant)
                        .flatMap(this::ok));
    }

    private Mono<ServerResponse> withClient(ServerRequest request, Endpoint endpoint) {
        return request.formData().flatMap(form -> {
            if (form.values().stream().anyMatch(value -> value.size() != 1)
                    || request.headers().header(HttpHeaders.AUTHORIZATION).size() > 1) {
                return error(400, "invalid_request");
            }
            Map<String, String> values = new LinkedHashMap<>();
            form.forEach((key, value) -> {
                if (!value.isEmpty()) {
                    values.put(key, value.get(0));
                }
            });
            String authorization = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
            boolean formCredentials = values.containsKey(CLIENT_ID) || values.containsKey(CLIENT_SECRET);
            if (authorization != null && formCredentials) {
                return error(400, "invalid_request");
            }
            String clientId = values.get(CLIENT_ID);
            String secret = values.get(CLIENT_SECRET);
            if (authorization != null) {
                if (!authorization.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX_LENGTH)) {
                    return error(UNAUTHORIZED, INVALID_CLIENT);
                }
                try {
                    String decoded = new String(
                            Base64.getDecoder().decode(authorization.substring(BASIC_PREFIX_LENGTH)),
                            StandardCharsets.UTF_8);
                    int separator = decoded.indexOf(':');
                    if (separator < 0) {
                        return error(UNAUTHORIZED, INVALID_CLIENT);
                    }
                    clientId = URLDecoder.decode(decoded.substring(0, separator), StandardCharsets.UTF_8);
                    secret = URLDecoder.decode(decoded.substring(separator + 1), StandardCharsets.UTF_8);
                } catch (IllegalArgumentException exception) {
                    return error(UNAUTHORIZED, INVALID_CLIENT);
                }
            }
            if (clientId == null || secret == null) {
                return error(UNAUTHORIZED, INVALID_CLIENT);
            }
            String authenticatedClient = clientId;
            String clientSecret = secret;
            return Mono.deferContextual(context -> {
                        String tenant = context.get(com.apex.scaffold.common.context.TenantContext.REACTOR_CONTEXT_KEY);
                        return service.validateClient(
                                        authenticatedClient,
                                        clientSecret,
                                        authorization == null ? "client_secret_post" : "client_secret_basic",
                                        tenant)
                                .flatMap(valid -> endpoint.handle(values, authenticatedClient, tenant));
                    })
                    .onErrorResume(
                            OAuthException.class,
                            exception -> error(
                                    INVALID_CLIENT.equals(exception.getMessage()) ? UNAUTHORIZED : 400,
                                    exception.getMessage()));
        });
    }

    private Mono<ServerResponse> ok(Map<String, Object> body) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .bodyValue(body);
    }

    private Mono<ServerResponse> error(int status, String code) {
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .bodyValue(Map.of("error", code));
    }

    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    @FunctionalInterface
    private interface Endpoint {
        Mono<ServerResponse> handle(Map<String, String> values, String clientId, String tenant);
    }
}
