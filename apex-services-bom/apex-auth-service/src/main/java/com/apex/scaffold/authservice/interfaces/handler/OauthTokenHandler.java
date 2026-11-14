package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.service.OAuth2Service;
import com.apex.scaffold.auth.application.service.OAuth2Service.OAuthException;
import com.apex.scaffold.auth.domain.service.UnifiedAuditService;
import com.apex.scaffold.common.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
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

/**
 * OAuth2令牌端点处理器。
 *
 * <p>实现RFC 6749定义的Token Endpoint，支持{@code authorization_code}、
 * {@code password}、{@code client_credentials}、{@code refresh_token}
 * 四种授权类型的令牌签发。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>解析并校验表单请求参数（禁止重复参数）</li>
 *   <li>支持HTTP Basic（{@code client_secret_basic}）与表单
 *       （{@code client_secret_post}）两种客户端认证方式，互斥使用</li>
 *   <li>委托{@link OAuth2Service}完成具体授权类型的令牌签发逻辑</li>
 *   <li>按RFC 6749规范返回标准错误码（invalid_client/invalid_request/
 *       unsupported_grant_type）</li>
 *   <li>记录OAuth2令牌签发审计事件</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>响应头强制{@code Cache-Control: no-store}与{@code Pragma: no-cache}，
 *       防止令牌被中间缓存持久化</li>
 *   <li>客户端认证失败时返回{@code WWW-Authenticate: Basic realm=oauth2}
 *       挑战头，符合HTTP Basic认证协议要求</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see OAuth2Service
 * @see OAuthEndpointHandler
 */
@Component
public class OauthTokenHandler {
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
    private final UnifiedAuditService auditService;

    /**
     * 构造OAuth2令牌端点处理器。
     *
     * @param service OAuth2应用服务
     * @param auditService 统一审计服务
     */
    public OauthTokenHandler(OAuth2Service service, UnifiedAuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    /**
     * 签发OAuth2访问令牌。
     *
     * <p>对应{@code POST /oauth2/token}端点，是OAuth2授权流程的最终环节。
     *
     * <p><strong>处理流程</strong></p>
     * <ol>
     *   <li>解析表单参数，拒绝重复参数（防止请求走私/歧义攻击）</li>
     *   <li>校验{@code grant_type}是否属于受支持的四种授权类型</li>
     *   <li>提取客户端凭据：HTTP Basic头或表单参数（{@code client_id}/
     *       {@code client_secret}），二者只能二选一</li>
     *   <li>委托{@link OAuth2Service#token}完成对应授权类型的令牌签发</li>
     *   <li>记录OAuth2令牌签发审计事件（成功或失败均记录）</li>
     *   <li>成功时返回包含访问令牌的JSON响应，失败时按RFC 6749返回标准错误体</li>
     * </ol>
     *
     * <p><strong>安全说明</strong></p>
     * <ul>
     *   <li>响应头固定{@code Cache-Control: no-store}，避免令牌被缓存</li>
     *   <li>客户端认证失败（{@code invalid_client}）返回401并附带
     *       {@code WWW-Authenticate}挑战头；其余校验失败返回400</li>
     * </ul>
     *
     * @param request 包含表单编码的令牌请求
     * @return 令牌响应或OAuth2标准错误响应
     */
    @OperationLog(
            module = "OauthToken",
            type = OperationLog.OperationType.OTHER,
            description = "token",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "token",
            summary = "签发OAuth2访问令牌",
            description = "对应 POST /oauth2/token 端点（RFC 6749）。表单编码请求，支持 authorization_code、password、"
                    + "client_credentials、refresh_token 四种授权类型；客户端凭据支持 HTTP Basic 与表单两种互斥方式。"
                    + "响应头固定 Cache-Control: no-store；客户端认证失败返回 401 并附带 WWW-Authenticate 挑战头，"
                    + "其余校验失败按 RFC 6749 返回 400 标准错误体")
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
            return issueToken(request, values, grant, finalClientId, finalSecret, authorization);
        });
    }

    /**
     * 在可信租户上下文中签发令牌并记录服务处理结果，返回原生 OAuth JSON。
     *
     * @param request 用于获取客户端地址的请求
     * @param values 已解析的单值表单
     * @param grant 已校验的授权类型
     * @param finalClientId 解析出的客户端标识
     * @param finalSecret 解析出的客户端密钥
     * @param authorization 原始认证头；为空时使用表单客户端认证
     * @return HTTP 200 令牌响应或 HTTP 400/401 协议错误响应
     */
    private Mono<ServerResponse> issueToken(
            ServerRequest request,
            Map<String, String> values,
            String grant,
            String finalClientId,
            String finalSecret,
            String authorization) {
        String clientIp = request.remoteAddress()
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");
        return Mono.deferContextual(context -> {
            String tenantId = context.get(com.apex.scaffold.common.context.TenantContext.REACTOR_CONTEXT_KEY);
            return service.token(
                            values,
                            finalClientId,
                            finalSecret,
                            authorization == null ? "client_secret_post" : "client_secret_basic",
                            tenantId)
                    .flatMap(body -> auditService
                            .logOAuth2Event(
                                    "TOKEN",
                                    null,
                                    tenantId,
                                    finalClientId,
                                    clientIp,
                                    true,
                                    new LinkedHashMap<>(Map.of("grantType", grant)))
                            .thenReturn(body))
                    .flatMap(body -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.CACHE_CONTROL, "no-store")
                            .header(HttpHeaders.PRAGMA, "no-cache")
                            .bodyValue(body))
                    .onErrorResume(OAuthException.class, exception -> auditService
                            .logOAuth2Event(
                                    "TOKEN",
                                    null,
                                    tenantId,
                                    finalClientId,
                                    clientIp,
                                    false,
                                    new LinkedHashMap<>(Map.of("grantType", grant, "error", exception.getMessage())))
                            .then(error(status(exception), exception.getMessage())));
        });
    }

    /**
     * 将客户端认证失败映射为 401，其余 OAuth 校验失败映射为 400。
     *
     * @param exception OAuth 业务异常
     * @return HTTP 状态码
     */
    private int status(OAuthException exception) {
        return INVALID_CLIENT.equals(exception.getMessage()) ? UNAUTHORIZED : BAD_REQUEST;
    }

    /**
     * 返回原生 OAuth 错误体；401 响应附带 Basic 客户端认证挑战头。
     *
     * @param status HTTP 状态码
     * @param error OAuth 协议错误码
     * @return 禁止缓存且不经 Result 包装的 JSON 响应
     */
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
