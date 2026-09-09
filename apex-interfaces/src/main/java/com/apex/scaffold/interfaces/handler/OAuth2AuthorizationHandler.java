package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.OAuth2AuthorizationServiceImpl;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.OAuth2AccessTokenEntity;
import com.apex.scaffold.domain.model.entity.OAuth2AuthorizationCodeEntity;
import com.apex.scaffold.interfaces.security.AuthenticatedUser;
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
 * OAuth2授权端点Handler
 *
 * <p>处理OAuth2授权服务器的核心端点:
 * <ul>
 *   <li>/oauth2/authorize - 授权端点(授权码模式)</li>
 *   <li>/oauth2/token - Token端点(所有授权模式)</li>
 *   <li>/oauth2/revoke - Token撤销端点</li>
 *   <li>/oauth2/introspect - Token自省端点</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthorizationHandler {
    
    private final OAuth2AuthorizationServiceImpl authorizationService;
    
    /**
     * 授权端点 - 授权码模式第一步
     *
     * <p>请求参数:
     * <ul>
     *   <li>response_type - 固定为"code"</li>
     *   <li>client_id - 客户端ID</li>
     *   <li>redirect_uri - 重定向URI</li>
     *   <li>scope - 权限作用域(可选)</li>
     *   <li>state - 状态参数(推荐,防CSRF)</li>
     *   <li>code_challenge - PKCE challenge(PKCE必需)</li>
     *   <li>code_challenge_method - PKCE方法,S256或plain</li>
     * </ul>
     *
     * <p>响应: 重定向到redirect_uri?code=xxx&state=xxx
     *
     * @param request ServerRequest
     * @return 重定向响应
     */
    public Mono<ServerResponse> authorize(ServerRequest request) {
        String responseType = request.queryParam("response_type").orElse("");
        String clientId = request.queryParam("client_id").orElse("");
        String redirectUri = request.queryParam("redirect_uri").orElse("");
        String scope = request.queryParam("scope").orElse("");
        String state = request.queryParam("state").orElse("");
        String codeChallenge = request.queryParam("code_challenge").orElse(null);
        String codeChallengeMethod = request.queryParam("code_challenge_method").orElse("plain");
        
        if (!"code".equals(responseType)) {
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
                .flatMap(userId -> 
                    authorizationService.hasUserConsented(clientId, userId, scope)
                            .flatMap(hasConsented -> {
                                if (hasConsented) {
                                    return authorizationService.generateAuthorizationCode(
                                            clientId, userId, redirectUri, scope, state, codeChallenge, codeChallengeMethod);
                                } else {
                                    return Mono.error(new BusinessException(ResultCode.FORBIDDEN, 
                                            "需要用户授权同意"));
                                }
                            })
                )
                .flatMap(authCode -> {
                    String location = UriComponentsBuilder.fromUriString(redirectUri)
                            .queryParam("code", authCode.getCode())
                            .queryParam("state", state)
                            .build()
                            .toUriString();
                    
                    return ServerResponse.temporaryRedirect(java.net.URI.create(location)).build();
                })
                .onErrorResume(BusinessException.class, e -> 
                        buildErrorResponse(redirectUri, "server_error", e.getMessage(), state));
    }
    
    /**
     * Token端点 - 换取访问令牌
     *
     * <p>支持的授权模式(grant_type):
     * <ul>
     *   <li>authorization_code - 授权码模式</li>
     *   <li>client_credentials - 客户端凭证模式</li>
     *   <li>password - 密码模式</li>
     *   <li>refresh_token - 刷新令牌</li>
     * </ul>
     *
     * @param request ServerRequest
     * @return Token响应
     */
    public Mono<ServerResponse> token(ServerRequest request) {
        return request.formData()
                .flatMap(formData -> {
                    String grantType = formData.getFirst("grant_type");
                    
                    if (grantType == null) {
                        return buildTokenErrorResponse("invalid_request", "缺少grant_type参数");
                    }
                    
                    return switch (grantType) {
                        case "authorization_code" -> handleAuthorizationCodeGrant(formData);
                        case "client_credentials" -> handleClientCredentialsGrant(formData);
                        case "password" -> handlePasswordGrant(formData);
                        case "refresh_token" -> handleRefreshTokenGrant(formData);
                        default -> buildTokenErrorResponse("unsupported_grant_type", "不支持的grant_type");
                    };
                });
    }
    
    /**
     * Token撤销端点
     *
     * @param request ServerRequest
     * @return 撤销结果
     */
    public Mono<ServerResponse> revoke(ServerRequest request) {
        return request.formData()
                .flatMap(formData -> {
                    String token = formData.getFirst("token");
                    String clientId = formData.getFirst("client_id");
                    
                    if (token == null || clientId == null) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Result.error(400, "缺少必需参数"));
                    }
                    
                    return authorizationService.revokeToken(token, clientId)
                            .then(ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .bodyValue(Result.success("Token已撤销")));
                });
    }
    
    /**
     * Token自省端点
     *
     * @param request ServerRequest
     * @return Token信息
     */
    public Mono<ServerResponse> introspect(ServerRequest request) {
        return request.formData()
                .flatMap(formData -> {
                    String token = formData.getFirst("token");
                    
                    if (token == null) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Result.error(400, "缺少token参数"));
                    }
                    
                    return authorizationService.validateToken(token)
                            .map(tokenEntity -> {
                                Map<String, Object> result = new HashMap<>();
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
     * 授权同意端点 - 保存用户授权同意
     *
     * @param request ServerRequest
     * @return 保存结果
     */
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
                                if ("true".equals(approved)) {
                                    return authorizationService.saveConsent(clientId, userId, scope)
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
                .onErrorResume(BusinessException.class, e -> 
                        ServerResponse.status(e.getCode())
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Result.error(e.getCode(), e.getMessage())));
    }
    
    /**
     * 撤销授权同意端点
     *
     * @param request ServerRequest
     * @return 撤销结果
     */
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
                            .flatMap(userId -> authorizationService.revokeConsent(clientId, userId)
                                    .then(ServerResponse.ok()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .bodyValue(Result.success("授权同意已撤销"))));
                })
                .onErrorResume(BusinessException.class, e -> 
                        ServerResponse.status(e.getCode())
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(Result.error(e.getCode(), e.getMessage())));
    }
    
    private Mono<ServerResponse> handleAuthorizationCodeGrant(MultiValueMap<String, String> formData) {
        String code = formData.getFirst("code");
        String clientId = formData.getFirst("client_id");
        String clientSecret = formData.getFirst("client_secret");
        String redirectUri = formData.getFirst("redirect_uri");
        String codeVerifier = formData.getFirst("code_verifier");
        
        return authorizationService.exchangeCodeForToken(code, clientId, clientSecret, redirectUri, codeVerifier)
                .flatMap(this::buildTokenSuccessResponse)
                .onErrorResume(BusinessException.class, e -> 
                        buildTokenErrorResponse("invalid_grant", e.getMessage()));
    }
    
    private Mono<ServerResponse> handleClientCredentialsGrant(MultiValueMap<String, String> formData) {
        String clientId = formData.getFirst("client_id");
        String clientSecret = formData.getFirst("client_secret");
        String scope = formData.getFirst("scope");
        
        return authorizationService.clientCredentialsGrant(clientId, clientSecret, scope)
                .flatMap(this::buildTokenSuccessResponse)
                .onErrorResume(BusinessException.class, e -> 
                        buildTokenErrorResponse("invalid_client", e.getMessage()));
    }
    
    private Mono<ServerResponse> handlePasswordGrant(MultiValueMap<String, String> formData) {
        String clientId = formData.getFirst("client_id");
        String clientSecret = formData.getFirst("client_secret");
        String username = formData.getFirst("username");
        String password = formData.getFirst("password");
        String scope = formData.getFirst("scope");
        
        return authorizationService.passwordGrant(clientId, clientSecret, username, password, scope)
                .flatMap(this::buildTokenSuccessResponse)
                .onErrorResume(BusinessException.class, e -> 
                        buildTokenErrorResponse("invalid_grant", e.getMessage()));
    }
    
    private Mono<ServerResponse> handleRefreshTokenGrant(MultiValueMap<String, String> formData) {
        String refreshToken = formData.getFirst("refresh_token");
        String clientId = formData.getFirst("client_id");
        String clientSecret = formData.getFirst("client_secret");
        
        return authorizationService.refreshToken(refreshToken, clientId, clientSecret)
                .flatMap(this::buildTokenSuccessResponse)
                .onErrorResume(BusinessException.class, e -> 
                        buildTokenErrorResponse("invalid_grant", e.getMessage()));
    }
    
    private Mono<ServerResponse> buildTokenSuccessResponse(OAuth2AccessTokenEntity token) {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", token.getTokenValue());
        response.put("token_type", token.getTokenType());
        response.put("expires_in", token.getExpiresIn());
        response.put("refresh_token", token.getRefreshToken());
        response.put("scope", token.getScope());
        
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(response);
    }
    
    private Mono<ServerResponse> buildTokenErrorResponse(String error, String errorDescription) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", error);
        response.put("error_description", errorDescription);
        
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(response);
    }
    
    private Mono<ServerResponse> buildErrorResponse(String redirectUri, String error, String errorDescription, String state) {
        String location = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", error)
                .queryParam("error_description", errorDescription)
                .queryParam("state", state)
                .build()
                .toUriString();
        
        return ServerResponse.temporaryRedirect(java.net.URI.create(location)).build();
    }
}
