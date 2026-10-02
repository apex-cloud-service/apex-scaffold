package com.apex.scaffold.authcenter.interfaces.handler;

import com.apex.scaffold.authcenter.application.service.OAuth2Service;
import com.apex.scaffold.authcenter.application.service.OAuth2Service.OAuthException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@SuppressWarnings({"PMD.ClassNamingShouldBeCamelRule", "PMD.ClassMustHaveAuthorRule"})
public class OAuth2TokenHandler {
    private static final String BASIC_PREFIX = "Basic ";
    private static final String BASIC_CHALLENGE = "Basic realm=oauth2";
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String INVALID_CLIENT = "invalid_client";
    private static final String INVALID_REQUEST = "invalid_request";
    private static final String UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type";
    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int BASIC_PREFIX_LENGTH = 6;

    private static final Set<String> GRANTS =
            Set.of("authorization_code", "password", "client_credentials", "refresh_token");
    private final OAuth2Service service;

    public OAuth2TokenHandler(OAuth2Service service) {
        this.service = service;
    }

    public Mono<ServerResponse> token(ServerRequest request) {
        return request.formData().flatMap(form -> {
            if (form.values().stream().anyMatch(value -> value.size() != 1)
                    || request.headers().header(HttpHeaders.AUTHORIZATION).size() > 1) {
                return error(BAD_REQUEST, INVALID_REQUEST);
            }
            Map<String, String> values = new LinkedHashMap<>();
            form.forEach((key, value) -> {
                if (!value.isEmpty()) {
                    values.put(key, value.get(0));
                }
            });
            String grant = values.get("grant_type");
            if (grant == null) {
                return error(BAD_REQUEST, INVALID_REQUEST);
            }
            if (!GRANTS.contains(grant)) {
                return error(BAD_REQUEST, UNSUPPORTED_GRANT_TYPE);
            }
            String authorization = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
            boolean formCredentials = values.containsKey(CLIENT_ID) || values.containsKey(CLIENT_SECRET);
            if (authorization != null && formCredentials) {
                return error(BAD_REQUEST, INVALID_REQUEST);
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
            String finalClientId = clientId;
            String finalSecret = secret;
            return Mono.deferContextual(context -> service.token(
                            values,
                            finalClientId,
                            finalSecret,
                            authorization == null ? "client_secret_post" : "client_secret_basic",
                            context.get(com.apex.scaffold.common.context.TenantContext.REACTOR_CONTEXT_KEY)))
                    .flatMap(body -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.CACHE_CONTROL, "no-store")
                            .header(HttpHeaders.PRAGMA, "no-cache")
                            .bodyValue(body))
                    .onErrorResume(OAuthException.class, exception -> error(status(exception), exception.getMessage()));
        });
    }

    private int status(OAuthException exception) {
        return INVALID_CLIENT.equals(exception.getMessage()) ? UNAUTHORIZED : BAD_REQUEST;
    }

    private Mono<ServerResponse> error(int status, String error) {
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .headers(headers -> {
                    if (status == UNAUTHORIZED) {
                        headers.set(HttpHeaders.WWW_AUTHENTICATE, BASIC_CHALLENGE);
                    }
                })
                .bodyValue(Map.of("error", error));
    }
}
