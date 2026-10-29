package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.OAuth2AuthorizationServiceImpl;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.OAuth2AccessTokenEntity;
import com.apex.scaffold.interfaces.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * OAuth2 授权服务器处理器（函数式端点）。
 *
 * <p>实现标准 OAuth2 授权服务器核心端点，支持授权码模式、客户端凭证模式、
 * 密码模式和刷新令牌等授权流程，支持 PKCE 扩展以增强安全性。提供令牌颁发、
 * 令牌撤销、令牌自省、用户授权同意管理等完整的 OAuth2 服务端能力。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /oauth2/authorize - OAuth2 授权端点，处理授权码模式的授权请求</li>
 *   <li>POST /oauth2/token - OAuth2 令牌端点，根据不同 grant_type 换取访问令牌</li>
 *   <li>POST /oauth2/revoke - OAuth2 令牌撤销端点，撤销指定的访问令牌或刷新令牌</li>
 *   <li>POST /oauth2/introspect - OAuth2 令牌自省端点，验证令牌有效性并返回元数据</li>
 *   <li>POST /oauth2/consent - 保存用户授权同意</li>
 *   <li>POST /oauth2/consent/revoke - 撤销用户授权同意</li>
 * </ul>
 *
 * <h2>支持的授权模式</h2>
 * <ul>
 *   <li>authorization_code：授权码模式，适用于服务端应用，支持 PKCE</li>
 *   <li>client_credentials：客户端凭证模式，适用于服务间调用</li>
 *   <li>password：密码模式，适用于受信任的第一方应用</li>
 *   <li>refresh_token：刷新令牌模式，用于延长访问令牌有效期</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see OAuth2AuthorizationServiceImpl
 */
@SuppressWarnings({"checkstyle:TypeName", "PMD.ClassNamingShouldBeCamelRule"})
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "OAuth2授权服务器", description = "OAuth2授权服务器核心端点,支持授权码、客户端凭证、密码和刷新令牌等授权模式")
public class OAuth2AuthorizationHandler {

    private static final String RESPONSE_TYPE_CODE = "code";
    private static final String GRANT_TYPE_AUTH_CODE = "authorization_code";
    private static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    private static final String GRANT_TYPE_PASSWORD = "password";
    private static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";
    private static final String APPROVED_VALUE_TRUE = "true";
    private static final int INITIAL_MAP_CAPACITY = 8;

    private final OAuth2AuthorizationServiceImpl authorizationService;

    /**
     * 处理 OAuth2 授权码模式的授权请求。
     *
     * <p>校验 response_type、client_id、redirect_uri 等参数后，检查用户是否已对该客户端授权同意：
     * 已同意则生成授权码并 307 重定向回客户端 redirect_uri（携带 code 与 state），
     * 未同意或校验失败时重定向携带错误信息，缺少必需参数时返回 400。
     *
     * @param request HTTP 请求对象，查询参数包含 response_type、client_id、redirect_uri、scope、state、
     *                code_challenge、code_challenge_method
     * @return 授权成功后的重定向响应，参数错误时返回 400 错误响应
     */
    @Operation(
            operationId = "oauth2Authorize",
            summary = "OAuth2授权端点",
            description = "处理授权码模式的授权请求,支持PKCE扩展。用户授权后重定向到客户端并携带授权码")
    @ApiResponses({
        @ApiResponse(
                responseCode = "307",
                description = "重定向到客户端redirect_uri，携带code/state或error/error_description/state"),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "用户未登录",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "403",
                description = "用户拒绝授权或未授予权限",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OAuth2Authorization",
            type = OperationLog.OperationType.OTHER,
            description = "authorize",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> authorize(ServerRequest request) {
        String responseType = request.queryParam("response_type").orElse("");
        String clientId = request.queryParam("client_id").orElse("");
        String redirectUri = request.queryParam("redirect_uri").orElse("");
        String scope = request.queryParam("scope").orElse("");
        String state = request.queryParam("state").orElse("");
        String codeChallenge = request.queryParam("code_challenge").orElse(null);
        String codeChallengeMethod = request.queryParam("code_challenge_method").orElse("plain");

        if (!RESPONSE_TYPE_CODE.equals(responseType)) {
            return buildErrorResponse(redirectUri, "unsupported_response_type", "不支持的response_type", state);
        }

        if (clientId.isEmpty() || redirectUri.isEmpty()) {
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Result.error(ResultCode.PARAM_ERROR.getCode(), "缺少必需参数"));
        }

        return request.principal()
                .cast(Authentication.class)
                .map(auth -> {
                    AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
                    return user.openId();
                })
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "用户未登录")))
                .flatMap(userId -> authorizationService
                        .hasUserConsented(clientId, userId, scope)
                        .flatMap(hasConsented -> {
                            if (hasConsented) {
                                return authorizationService.generateAuthorizationCode(
                                        clientId,
                                        userId,
                                        redirectUri,
                                        scope,
                                        state,
                                        codeChallenge,
                                        codeChallengeMethod);
                            } else {
                                return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "需要用户授权同意"));
                            }
                        }))
                .flatMap(authCode -> {
                    String location = UriComponentsBuilder.fromUriString(redirectUri)
                            .queryParam("code", authCode.getCode())
                            .queryParam("state", state)
                            .build()
                            .toUriString();

                    return ServerResponse.temporaryRedirect(java.net.URI.create(location))
                            .build();
                })
                .onErrorResume(
                        BusinessException.class,
                        e -> buildErrorResponse(redirectUri, "server_error", e.getMessage(), state));
    }

    /**
     * 处理 OAuth2 令牌端点请求。
     *
     * <p>解析表单参数中的 grant_type，分发到授权码、客户端凭证、密码或刷新令牌
     * 等对应模式换取访问令牌；缺少 grant_type 或模式不支持时返回错误响应。
     *
     * @param request HTTP 请求对象，表单参数包含 grant_type 及各模式所需字段
     * @return 包含 access_token、token_type、expires_in 等信息的令牌响应，失败时返回错误响应
     */
    @Operation(
            operationId = "oauth2Token",
            summary = "OAuth2令牌端点",
            description = "根据不同的grant_type换取访问令牌,支持授权码、客户端凭证、密码和刷新令牌模式")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "令牌颁发成功,返回access_token、token_type、expires_in等信息"),
        @ApiResponse(responseCode = "400", description = "请求参数错误、grant_type不支持、客户端认证失败或授权码无效")
    })
    @RequestBody(
            description = "令牌请求参数(表单格式)",
            required = true,
            content = @Content(mediaType = "application/x-www-form-urlencoded"))
    @OperationLog(
            module = "OAuth2Authorization",
            type = OperationLog.OperationType.OTHER,
            description = "token",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> token(ServerRequest request) {
        return request.formData().flatMap(formData -> {
            String grantType = formData.getFirst("grant_type");

            if (grantType == null) {
                return buildTokenErrorResponse("invalid_request", "缺少grant_type参数");
            }

            return switch (grantType) {
                case GRANT_TYPE_AUTH_CODE -> handleAuthorizationCodeGrant(formData);
                case GRANT_TYPE_CLIENT_CREDENTIALS -> handleClientCredentialsGrant(formData);
                case GRANT_TYPE_PASSWORD -> handlePasswordGrant(formData);
                case GRANT_TYPE_REFRESH_TOKEN -> handleRefreshTokenGrant(formData);
                default -> buildTokenErrorResponse("unsupported_grant_type", "不支持的grant_type");
            };
        });
    }

    /**
     * 处理 OAuth2 令牌撤销请求。
     *
     * <p>撤销指定的访问令牌或刷新令牌，使其立即失效；缺少 token 或 client_id 参数时返回 400。
     *
     * @param request HTTP 请求对象，表单参数包含 token、client_id
     * @return 撤销成功的统一成功响应，缺少必需参数时返回 400 错误响应
     */
    @Operation(operationId = "oauth2Revoke", summary = "OAuth2令牌撤销端点", description = "撤销指定的访问令牌或刷新令牌,使其立即失效")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "令牌撤销成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "缺少必需参数",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequestBody(
            description = "撤销请求参数:token(必需)、client_id(必需)",
            required = true,
            content = @Content(mediaType = "application/x-www-form-urlencoded"))
    @OperationLog(
            module = "OAuth2Authorization",
            type = OperationLog.OperationType.UPDATE,
            description = "revoke",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> revoke(ServerRequest request) {
        return request.formData().flatMap(formData -> {
            String token = formData.getFirst("token");
            String clientId = formData.getFirst("client_id");

            if (token == null || clientId == null) {
                return ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.error(400, "缺少必需参数"));
            }

            return authorizationService
                    .revokeToken(token, clientId)
                    .then(ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.success("Token已撤销")));
        });
    }

    /**
     * 处理 OAuth2 令牌自省请求。
     *
     * <p>验证令牌的有效性并返回令牌元数据（active、client_id、user_id、scope、exp）；
     * 令牌无效或不存在时返回 active=false，缺少 token 参数时返回 400。
     *
     * @param request HTTP 请求对象，表单参数包含 token
     * @return 包含令牌元数据信息的响应，缺少 token 参数时返回 400 错误响应
     */
    @Operation(operationId = "oauth2Introspect", summary = "OAuth2令牌自省端点", description = "验证令牌的有效性并返回令牌的元数据信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "返回令牌信息,active字段指示令牌是否有效"),
        @ApiResponse(responseCode = "400", description = "缺少token参数")
    })
    @RequestBody(
            description = "自省请求参数:token(必需)",
            required = true,
            content = @Content(mediaType = "application/x-www-form-urlencoded"))
    @OperationLog(
            module = "OAuth2Authorization",
            type = OperationLog.OperationType.OTHER,
            description = "introspect",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> introspect(ServerRequest request) {
        return request.formData().flatMap(formData -> {
            String token = formData.getFirst("token");

            if (token == null) {
                return ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.error(400, "缺少token参数"));
            }

            return authorizationService
                    .validateToken(token)
                    .map(tokenEntity -> {
                        Map<String, Object> result = new HashMap<>(INITIAL_MAP_CAPACITY);
                        result.put("active", true);
                        result.put("client_id", tokenEntity.getClientId());
                        result.put("user_id", tokenEntity.getUserId());
                        result.put("scope", tokenEntity.getScope());
                        result.put("exp", tokenEntity.getExpiresAt().getEpochSecond());
                        return result;
                    })
                    .defaultIfEmpty(Map.of("active", false))
                    .flatMap(result -> ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(result));
        });
    }

    /**
     * 处理保存用户授权同意请求。
     *
     * <p>用户在授权页面同意或拒绝授权后保存同意记录：approved 为 true 时保存
     * 当前用户对指定客户端的授权同意；缺少必需参数时返回 400，用户未登录时返回 401，
     * 拒绝授权时返回 403。
     *
     * @param request HTTP 请求对象，表单参数包含 client_id、scope、approved
     * @return 保存成功的统一成功响应，参数缺失或未登录时返回对应错误响应
     */
    @Operation(operationId = "oauth2Consent", summary = "保存用户授权同意", description = "用户在授权页面同意或拒绝授权后调用此端点保存同意记录")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "同意记录保存成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "缺少必需参数",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "用户未登录",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "403",
                description = "用户无权执行操作；拒绝授权本身返回 HTTP 200，Result.code=403",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequestBody(
            description = "同意请求参数:client_id(必需)、scope(可选)、approved(必需,true或false)",
            required = true,
            content = @Content(mediaType = "application/x-www-form-urlencoded"))
    @OperationLog(
            module = "OAuth2Authorization",
            type = OperationLog.OperationType.OTHER,
            description = "consent",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> consent(ServerRequest request) {
        return request.formData()
                .flatMap(formData -> {
                    String clientId = formData.getFirst("client_id");
                    String scope = formData.getFirst("scope");
                    String approved = formData.getFirst("approved");

                    if (clientId == null || approved == null) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Result.error(400, "缺少必需参数"));
                    }

                    return request.principal()
                            .cast(Authentication.class)
                            .map(auth -> {
                                AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
                                return user.openId();
                            })
                            .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "用户未登录")))
                            .flatMap(userId -> {
                                if (APPROVED_VALUE_TRUE.equals(approved)) {
                                    return authorizationService
                                            .saveConsent(clientId, userId, scope)
                                            .then(ServerResponse.ok()
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .bodyValue(Result.success("授权同意已保存")));
                                } else {
                                    return ServerResponse.ok()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .bodyValue(Result.error(403, "用户拒绝授权"));
                                }
                            });
                })
                .onErrorResume(BusinessException.class, e -> ServerResponse.status(e.getCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.error(e.getCode(), e.getMessage())));
    }

    /**
     * 处理撤销用户授权同意请求。
     *
     * <p>撤销当前登录用户对指定客户端的授权同意记录；缺少 client_id 参数时返回 400，
     * 用户未登录时返回 401。
     *
     * @param request HTTP 请求对象，表单参数包含 client_id
     * @return 撤销成功的统一成功响应，参数缺失或未登录时返回对应错误响应
     */
    @Operation(operationId = "oauth2RevokeConsent", summary = "撤销用户授权同意", description = "用户撤销之前对某个客户端的授权同意")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "授权同意撤销成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "缺少client_id参数",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "401",
                description = "用户未登录",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequestBody(
            description = "撤销请求参数:client_id(必需)",
            required = true,
            content = @Content(mediaType = "application/x-www-form-urlencoded"))
    @OperationLog(
            module = "OAuth2Authorization",
            type = OperationLog.OperationType.UPDATE,
            description = "revokeConsent",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> revokeConsent(ServerRequest request) {
        return request.formData()
                .flatMap(formData -> {
                    String clientId = formData.getFirst("client_id");

                    if (clientId == null) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Result.error(400, "缺少client_id参数"));
                    }

                    return request.principal()
                            .cast(Authentication.class)
                            .map(auth -> {
                                AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
                                return user.openId();
                            })
                            .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "用户未登录")))
                            .flatMap(userId -> authorizationService
                                    .revokeConsent(clientId, userId)
                                    .then(ServerResponse.ok()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .bodyValue(Result.success("授权同意已撤销"))));
                })
                .onErrorResume(BusinessException.class, e -> ServerResponse.status(e.getCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.error(e.getCode(), e.getMessage())));
    }

    private Mono<ServerResponse> handleAuthorizationCodeGrant(MultiValueMap<String, String> formData) {
        String code = formData.getFirst("code");
        String clientId = formData.getFirst("client_id");
        String clientSecret = formData.getFirst("client_secret");
        String redirectUri = formData.getFirst("redirect_uri");
        String codeVerifier = formData.getFirst("code_verifier");

        return authorizationService
                .exchangeCodeForToken(code, clientId, clientSecret, redirectUri, codeVerifier)
                .flatMap(this::buildTokenSuccessResponse)
                .onErrorResume(BusinessException.class, e -> buildTokenErrorResponse("invalid_grant", e.getMessage()));
    }

    private Mono<ServerResponse> handleClientCredentialsGrant(MultiValueMap<String, String> formData) {
        String clientId = formData.getFirst("client_id");
        String clientSecret = formData.getFirst("client_secret");
        String scope = formData.getFirst("scope");

        return authorizationService
                .clientCredentialsGrant(clientId, clientSecret, scope)
                .flatMap(this::buildTokenSuccessResponse)
                .onErrorResume(BusinessException.class, e -> buildTokenErrorResponse("invalid_client", e.getMessage()));
    }

    private Mono<ServerResponse> handlePasswordGrant(MultiValueMap<String, String> formData) {
        String clientId = formData.getFirst("client_id");
        String clientSecret = formData.getFirst("client_secret");
        String username = formData.getFirst("username");
        String password = formData.getFirst("password");
        String scope = formData.getFirst("scope");

        return authorizationService
                .passwordGrant(clientId, clientSecret, username, password, scope)
                .flatMap(this::buildTokenSuccessResponse)
                .onErrorResume(BusinessException.class, e -> buildTokenErrorResponse("invalid_grant", e.getMessage()));
    }

    private Mono<ServerResponse> handleRefreshTokenGrant(MultiValueMap<String, String> formData) {
        String refreshToken = formData.getFirst("refresh_token");
        String clientId = formData.getFirst("client_id");
        String clientSecret = formData.getFirst("client_secret");

        return authorizationService
                .refreshToken(refreshToken, clientId, clientSecret)
                .flatMap(this::buildTokenSuccessResponse)
                .onErrorResume(BusinessException.class, e -> buildTokenErrorResponse("invalid_grant", e.getMessage()));
    }

    private Mono<ServerResponse> buildTokenSuccessResponse(OAuth2AccessTokenEntity token) {
        Map<String, Object> response = new HashMap<>(INITIAL_MAP_CAPACITY);
        response.put("access_token", token.getTokenValue());
        response.put("token_type", token.getTokenType());
        response.put("expires_in", token.getExpiresIn());
        response.put("refresh_token", token.getRefreshToken());
        response.put("scope", token.getScope());

        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(response);
    }

    private Mono<ServerResponse> buildTokenErrorResponse(String error, String errorDescription) {
        Map<String, Object> response = new HashMap<>(4);
        response.put("error", error);
        response.put("error_description", errorDescription);

        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(response);
    }

    private Mono<ServerResponse> buildErrorResponse(
            String redirectUri, String error, String errorDescription, String state) {
        String location = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", error)
                .queryParam("error_description", errorDescription)
                .queryParam("state", state)
                .build()
                .toUriString();

        return ServerResponse.temporaryRedirect(java.net.URI.create(location)).build();
    }
}
