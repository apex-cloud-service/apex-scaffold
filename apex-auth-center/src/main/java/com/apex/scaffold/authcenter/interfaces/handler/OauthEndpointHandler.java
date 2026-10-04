package com.apex.scaffold.authcenter.interfaces.handler;

import com.apex.scaffold.authcenter.application.service.OAuth2Service;
import com.apex.scaffold.authcenter.application.service.OAuth2Service.OAuthException;
import com.apex.scaffold.authcenter.domain.service.UnifiedAuditService;
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

/**
 * OAuth2授权端点处理器。
 *
 * <p>实现RFC 6749定义的Authorization Endpoint以及令牌撤销（RFC 7009）、
 * 令牌内省（RFC 7662）端点，覆盖OAuth2授权码流程的发码、撤销与状态查询。
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>{@link #authorize}：面向已登录用户签发PKCE授权码，302重定向回
 *       客户端{@code redirect_uri}</li>
 *   <li>{@link #revoke}：撤销指定令牌，需客户端凭据认证</li>
 *   <li>{@link #introspect}：查询令牌当前状态，需客户端凭据认证</li>
 *   <li>统一的客户端凭据校验逻辑（{@link #withClient}），支持HTTP Basic
 *       与表单两种认证方式</li>
 *   <li>记录OAuth2授权/撤销/内省审计事件</li>
 * </ul>
 *
 * <h2>安全特性</h2>
 * <ul>
 *   <li>所有响应强制{@code Cache-Control: no-store}，避免授权码/令牌状态
 *       被中间缓存持久化</li>
 *   <li>未认证用户访问授权端点时返回{@code access_denied}而非直接抛出异常，
 *       避免泄露授权码签发的内部状态</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see OAuth2Service
 * @see OauthTokenHandler
 */
@Component
public class OauthEndpointHandler {
    private static final String BASIC_PREFIX = "Basic ";
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String INVALID_CLIENT = "invalid_client";
    private static final String STATE = "state";
    private static final int UNAUTHORIZED = 401;
    private static final int BASIC_PREFIX_LENGTH = 6;

    private final OAuth2Service service;
    private final UnifiedAuditService auditService;

    public OauthEndpointHandler(OAuth2Service service, UnifiedAuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    /**
     * 签发OAuth2授权码。
     *
     * <p>对应{@code GET /oauth2/authorize}端点，要求请求携带已认证的用户主体。
     *
     * <h3>处理流程</h3>
     * <ol>
     *   <li>校验查询参数不含重复值</li>
     *   <li>从{@link ServerRequest#principal()}获取当前登录用户</li>
     *   <li>委托{@link OAuth2Service#authorize}签发授权码</li>
     *   <li>记录OAuth2授权审计事件</li>
     *   <li>302重定向回客户端{@code redirect_uri}，附带{@code code}
     *       与可选的{@code state}参数</li>
     * </ol>
     *
     * <h3>安全说明</h3>
     * <ul>
     *   <li>未认证用户返回401 {@code access_denied}，不签发授权码</li>
     *   <li>重定向响应强制{@code Cache-Control: no-store}</li>
     * </ul>
     *
     * @param request 包含授权请求查询参数的请求
     * @return 302重定向响应或OAuth2标准错误响应
     */
    public Mono<ServerResponse> authorize(ServerRequest request) {
        if (request.queryParams().values().stream().anyMatch(values -> values.size() != 1)) {
            return error(400, "invalid_request");
        }
        Map<String, String> values = request.queryParams().toSingleValueMap();
        String clientIp = request.remoteAddress()
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");
        return request.principal()
                .flatMap(principal -> Mono.deferContextual(context -> {
                    String tenant = context.get(com.apex.scaffold.common.context.TenantContext.REACTOR_CONTEXT_KEY);
                    return service.authorize(values, values.get(CLIENT_ID), principal.getName(), tenant)
                            .flatMap(result -> auditService
                                    .logOAuth2Event(
                                            "AUTHORIZE",
                                            principal.getName(),
                                            tenant,
                                            values.get(CLIENT_ID),
                                            clientIp,
                                            true,
                                            null)
                                    .thenReturn(result));
                }))
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

    /**
     * 撤销OAuth2令牌。
     *
     * <p>对应{@code POST /oauth2/revoke}端点，实现RFC 7009令牌撤销。
     * 需通过{@link #withClient}完成客户端凭据校验后才可执行撤销。
     *
     * @param request 包含客户端凭据与待撤销令牌的表单请求
     * @return 撤销结果（空JSON对象）或OAuth2标准错误响应
     */
    public Mono<ServerResponse> revoke(ServerRequest request) {
        return withClient(
                "REVOKE", request, (values, clientId, tenant) -> service.revoke(values.get("token"), clientId, tenant)
                        .then(ok(Map.of())));
    }

    /**
     * 查询OAuth2令牌状态。
     *
     * <p>对应{@code POST /oauth2/introspect}端点，实现RFC 7662令牌内省。
     * 需通过{@link #withClient}完成客户端凭据校验后才可查询。
     *
     * @param request 包含客户端凭据与待查询令牌的表单请求
     * @return 令牌状态JSON响应或OAuth2标准错误响应
     */
    public Mono<ServerResponse> introspect(ServerRequest request) {
        return withClient("INTROSPECT", request, (values, clientId, tenant) -> service.introspect(
                        values.get("token"), clientId, tenant)
                .flatMap(this::ok));
    }

    /**
     * 校验客户端凭据并委托{@link Endpoint}完成具体业务处理，统一记录审计事件。
     *
     * <p>支持HTTP Basic（{@code client_secret_basic}）与表单
     * （{@code client_secret_post}）两种客户端认证方式，二者互斥。
     *
     * @param action 用于审计记录的操作类型（REVOKE/INTROSPECT）
     * @param request 原始请求
     * @param endpoint 客户端认证通过后执行的具体业务逻辑
     * @return 业务处理结果或OAuth2标准错误响应
     */
    private Mono<ServerResponse> withClient(String action, ServerRequest request, Endpoint endpoint) {
        String clientIp = request.remoteAddress()
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");
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
            final String authenticatedClient = clientId;
            final String clientSecret = secret;
            return Mono.deferContextual(context -> {
                        String tenant = context.get(com.apex.scaffold.common.context.TenantContext.REACTOR_CONTEXT_KEY);
                        return service.validateClient(
                                        authenticatedClient,
                                        clientSecret,
                                        authorization == null ? "client_secret_post" : "client_secret_basic",
                                        tenant)
                                .flatMap(valid -> endpoint.handle(values, authenticatedClient, tenant))
                                .flatMap(response -> auditService
                                        .logOAuth2Event(action, null, tenant, authenticatedClient, clientIp, true, null)
                                        .thenReturn(response));
                    })
                    .onErrorResume(OAuthException.class, exception -> {
                        int status = INVALID_CLIENT.equals(exception.getMessage()) ? UNAUTHORIZED : 400;
                        return Mono.deferContextual(context -> auditService
                                .logOAuth2Event(
                                        action,
                                        null,
                                        context.get(com.apex.scaffold.common.context.TenantContext.REACTOR_CONTEXT_KEY),
                                        authenticatedClient,
                                        clientIp,
                                        false,
                                        new LinkedHashMap<>(Map.of("error", exception.getMessage())))
                                .then(error(status, exception.getMessage())));
                    });
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

    /**
     * 客户端凭据校验通过后执行的业务处理逻辑。
     *
     * <p>由{@link #withClient}在完成客户端认证后回调，用于承载
     * {@link #revoke}、{@link #introspect}各自的差异化业务逻辑。
     */
    @FunctionalInterface
    private interface Endpoint {
        /**
         * 执行业务处理。
         *
         * @param values 表单请求参数
         * @param clientId 已通过认证的客户端ID
         * @param tenant 当前租户ID
         * @return 业务处理结果响应
         */
        Mono<ServerResponse> handle(Map<String, String> values, String clientId, String tenant);
    }
}
