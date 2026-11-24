package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.service.OAuth2Service;
import com.apex.scaffold.auth.application.service.OAuth2Service.OAuthException;
import com.apex.scaffold.auth.domain.service.UnifiedAuditService;
import com.apex.scaffold.common.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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
 * <h2>职责范围</h2>
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
 * <h2>职责范围</h2>
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
@Tag(
        name = "OAuth2-授权端点",
        description = "RFC 6749 授权、撤销、内省与发现端点；响应为 OAuth2 原生协议格式（不包装 Result），" + "统一强制 Cache-Control: no-store")
public class OauthAuthorizationHandler {
    private static final String BASIC_PREFIX = "Basic ";
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String INVALID_CLIENT = "invalid_client";
    private static final String STATE = "state";
    private static final int UNAUTHORIZED = 401;
    private static final int BASIC_PREFIX_LENGTH = 6;

    private final OAuth2Service service;
    private final UnifiedAuditService auditService;

    /**
     * 构造OAuth2授权端点处理器。
     *
     * @param service OAuth2应用服务
     * @param auditService 统一审计服务
     */
    public OauthAuthorizationHandler(OAuth2Service service, UnifiedAuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    /**
     * 签发OAuth2授权码。
     *
     * <p>对应{@code GET /oauth2/authorize}端点，要求请求携带已认证的用户主体。
     *
     * <p><strong>处理流程</strong></p>
     * <ol>
     *   <li>校验查询参数不含重复值</li>
     *   <li>从{@link ServerRequest#principal()}获取当前登录用户</li>
     *   <li>委托{@link OAuth2Service#authorize}签发授权码</li>
     *   <li>记录OAuth2授权审计事件</li>
     *   <li>302重定向回客户端{@code redirect_uri}，附带{@code code}
     *       与可选的{@code state}参数</li>
     * </ol>
     *
     * <p><strong>安全说明</strong></p>
     * <ul>
     *   <li>未认证用户返回401 {@code access_denied}，不签发授权码</li>
     *   <li>重定向响应强制{@code Cache-Control: no-store}</li>
     * </ul>
     *
     * @param request 包含授权请求查询参数的请求
     * @return 302重定向响应或OAuth2标准错误响应
     */
    @AuthorizeParameters
    @OperationLog(
            module = "OauthEndpoint",
            type = OperationLog.OperationType.OTHER,
            description = "authorize",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "authorize",
            summary = "签发OAuth2授权码",
            description = "对应 GET /oauth2/authorize 端点（RFC 6749）。要求请求携带已认证用户主体，"
                    + "校验通过后签发 PKCE 授权码并 302 重定向回客户端 redirect_uri（附带 code 与可选 state）；"
                    + "未认证用户返回 401 access_denied，重定向响应强制 Cache-Control: no-store")
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
     * 处理 {@code POST /oauth2/consent} 端点（统一认证收口 A5）。
     *
     * <p>用户对客户端的授权同意/拒绝：{@code decision=approve} 时按三元键
     * (tenant, principal, client_id) 记录同意，authorize 后续重试即可发码；
     * 其他取值视为拒绝（撤销既有同意记录）。要求请求携带已认证用户主体。
     *
     * <p>表单参数：{@code client_id}（必填）、{@code scope}（可选）、
     * {@code decision}（approve / deny，默认 deny——拒绝优先）。
     *
     * @param request 当前请求
     * @return 同意结果响应（{@code {consented: true|false}}）
     */
    @Operation(
            operationId = "oauth2Consent",
            summary = "OAuth2 授权同意/拒绝",
            description = "用户对客户端的授权同意或拒绝：decision=approve 时按 (tenant, principal, client_id) "
                    + "记录同意，authorize 后续重试即可发码；其他取值视为拒绝并撤销既有同意记录。"
                    + "请求为 form-urlencoded，要求携带已认证用户主体")
    @Parameters({
        @Parameter(
                name = "client_id",
                in = ParameterIn.QUERY,
                required = true,
                description = "客户端标识（form 参数）",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "scope",
                in = ParameterIn.QUERY,
                description = "请求作用域（form 参数，可选）",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "decision",
                in = ParameterIn.QUERY,
                description = "同意决定：approve=同意，其他取值视为拒绝（默认 deny，拒绝优先）",
                schema =
                        @Schema(
                                type = "string",
                                allowableValues = {"approve", "deny"},
                                defaultValue = "deny"))
    })
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "同意结果，data 为 {consented: true|false}"),
        @ApiResponse(responseCode = "401", description = "未认证用户访问，返回 access_denied")
    })
    public Mono<ServerResponse> consent(ServerRequest request) {
        return request.principal()
                .flatMap(principal -> Mono.deferContextual(context -> {
                    String tenant = context.get(com.apex.scaffold.common.context.TenantContext.REACTOR_CONTEXT_KEY);
                    return request.body(org.springframework.web.reactive.function.BodyExtractors.toFormData())
                            .flatMap(formData -> {
                                String clientId = formData.getFirst(CLIENT_ID);
                                if (clientId == null || clientId.isBlank()) {
                                    return error(400, "invalid_request");
                                }
                                String scope = formData.getFirst("scope");
                                boolean approved = "approve".equalsIgnoreCase(formData.getFirst("decision"));
                                return service.recordConsent(tenant, principal.getName(), clientId, scope, approved)
                                        .then(Mono.defer(() -> approved
                                                ? ServerResponse.ok().bodyValue(java.util.Map.of("consented", true))
                                                : ServerResponse.ok().bodyValue(java.util.Map.of("consented", false))));
                            });
                }))
                .switchIfEmpty(error(UNAUTHORIZED, "access_denied"))
                .onErrorResume(OAuthException.class, exception -> error(400, exception.getMessage()));
    }

    /**
     * 声明授权端点的七项查询参数及 S256 PKCE 约束。
     *
     * <p>显式指定字符串模型，避免函数式端点缺少可推导的 Java 参数时生成空 schema。
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Parameters({
        @Parameter(
                name = "client_id",
                in = ParameterIn.QUERY,
                required = true,
                description = "已注册的客户端标识",
                schema = @Schema(type = "string", implementation = String.class)),
        @Parameter(
                name = "response_type",
                in = ParameterIn.QUERY,
                required = true,
                description = "响应类型，仅支持 code",
                schema =
                        @Schema(
                                type = "string",
                                implementation = String.class,
                                allowableValues = {"code"})),
        @Parameter(
                name = "redirect_uri",
                in = ParameterIn.QUERY,
                required = true,
                description = "与客户端注册值匹配的重定向 URI",
                schema = @Schema(type = "string", implementation = String.class, format = "uri")),
        @Parameter(
                name = "scope",
                in = ParameterIn.QUERY,
                description = "空格分隔的授权范围，受客户端配置约束",
                schema = @Schema(type = "string", implementation = String.class)),
        @Parameter(
                name = "state",
                in = ParameterIn.QUERY,
                description = "客户端状态值；传入时在重定向中原样携带",
                schema = @Schema(type = "string", implementation = String.class)),
        @Parameter(
                name = "code_challenge",
                in = ParameterIn.QUERY,
                required = true,
                description = "43 位 Base64URL 编码的 S256 PKCE 挑战值",
                schema = @Schema(type = "string", implementation = String.class, pattern = "[A-Za-z0-9_-]{43}")),
        @Parameter(
                name = "code_challenge_method",
                in = ParameterIn.QUERY,
                required = true,
                description = "PKCE 算法，仅支持 S256",
                schema =
                        @Schema(
                                type = "string",
                                implementation = String.class,
                                allowableValues = {"S256"}))
    })
    private @interface AuthorizeParameters {}

    /**
     * 撤销OAuth2令牌。
     *
     * <p>对应{@code POST /oauth2/revoke}端点，实现RFC 7009令牌撤销。
     * 需通过{@link #withClient}完成客户端凭据校验后才可执行撤销。
     *
     * @param request 包含客户端凭据与待撤销令牌的表单请求
     * @return 撤销结果（空JSON对象）或OAuth2标准错误响应
     */
    @OperationLog(
            module = "OauthEndpoint",
            type = OperationLog.OperationType.UPDATE,
            description = "revoke",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "revoke",
            summary = "撤销OAuth2令牌",
            description = "对应 POST /oauth2/revoke 端点（RFC 7009）。需通过客户端凭据校验"
                    + "（HTTP Basic 或表单 client_secret_post）后方可撤销指定令牌，成功返回空 JSON 对象")
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
    @OperationLog(
            module = "OauthEndpoint",
            type = OperationLog.OperationType.OTHER,
            description = "introspect",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "introspect",
            summary = "查询OAuth2令牌状态",
            description = "对应 POST /oauth2/introspect 端点（RFC 7662）。需通过客户端凭据校验"
                    + "（HTTP Basic 或表单 client_secret_post）后查询令牌当前状态，返回令牌状态 JSON 响应")
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

    /**
     * 返回不经 Result 包装、禁止缓存的 OAuth JSON 成功响应。
     *
     * @param body 协议响应字段；撤销成功时为空对象
     * @return HTTP 200 JSON 响应
     */
    private Mono<ServerResponse> ok(Map<String, Object> body) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .bodyValue(body);
    }

    /**
     * 构造包含 error 字段的原生 OAuth 错误体，并禁止缓存。
     *
     * @param status HTTP 状态码
     * @param code OAuth 协议错误码
     * @return 不经 Result 包装的错误响应
     */
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
