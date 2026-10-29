package com.apex.scaffold.domain.service;

import com.apex.scaffold.domain.model.entity.OAuth2AccessTokenEntity;
import com.apex.scaffold.domain.model.entity.OAuth2AuthorizationCodeEntity;
import com.apex.scaffold.domain.model.entity.OAuth2AuthorizationConsentEntity;
import reactor.core.publisher.Mono;

/**
 * OAuth2授权服务接口
 *
 * <p>定义OAuth2授权流程的核心业务逻辑。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
public interface OAuth2AuthorizationService {

    /**
     * 生成授权码
     *
     * @param clientId 客户端ID
     * @param userId 用户ID(OpenID)
     * @param redirectUri 重定向URI
     * @param scope 权限作用域
     * @param state 状态参数(防CSRF)
     * @param codeChallenge PKCE code challenge
     * @param codeChallengeMethod PKCE方法(S256或plain)
     * @return 授权码实体
     */
    Mono<OAuth2AuthorizationCodeEntity> generateAuthorizationCode(
            String clientId,
            String userId,
            String redirectUri,
            String scope,
            String state,
            String codeChallenge,
            String codeChallengeMethod);

    /**
     * 验证授权码并生成访问令牌(授权码模式)
     *
     * @param code 授权码
     * @param clientId 客户端ID
     * @param clientSecret 客户端密钥
     * @param redirectUri 重定向URI
     * @param codeVerifier PKCE code verifier
     * @return 访问令牌实体
     */
    Mono<OAuth2AccessTokenEntity> exchangeCodeForToken(
            String code, String clientId, String clientSecret, String redirectUri, String codeVerifier);

    /**
     * 客户端凭证模式获取访问令牌
     *
     * @param clientId 客户端ID
     * @param clientSecret 客户端密钥
     * @param scope 权限作用域
     * @return 访问令牌实体
     */
    Mono<OAuth2AccessTokenEntity> clientCredentialsGrant(String clientId, String clientSecret, String scope);

    /**
     * 密码模式获取访问令牌
     *
     * @param clientId 客户端ID
     * @param clientSecret 客户端密钥
     * @param username 用户名
     * @param password 密码
     * @param scope 权限作用域
     * @return 访问令牌实体
     */
    Mono<OAuth2AccessTokenEntity> passwordGrant(
            String clientId, String clientSecret, String username, String password, String scope);

    /**
     * 刷新令牌
     *
     * @param refreshToken 刷新令牌
     * @param clientId 客户端ID
     * @param clientSecret 客户端密钥
     * @return 新的访问令牌实体
     */
    Mono<OAuth2AccessTokenEntity> refreshToken(String refreshToken, String clientId, String clientSecret);

    /**
     * 撤销令牌
     *
     * @param token 令牌值
     * @param clientId 客户端ID
     */
    Mono<Void> revokeToken(String token, String clientId);

    /**
     * 验证访问令牌
     *
     * @param token 访问令牌
     * @return 令牌实体,无效则返回empty
     */
    Mono<OAuth2AccessTokenEntity> validateToken(String token);

    /**
     * 检查用户是否已授权给客户端指定的scope
     *
     * @param clientId 客户端ID
     * @param userId 用户ID(OpenID)
     * @param requestedScopes 请求的权限作用域
     * @return 如果用户已授权所有请求的scope则返回true
     */
    Mono<Boolean> hasUserConsented(String clientId, String userId, String requestedScopes);

    /**
     * 保存用户授权同意
     *
     * @param clientId 客户端ID
     * @param userId 用户ID(OpenID)
     * @param authorizedScopes 用户授权的权限作用域
     * @return 授权同意实体
     */
    Mono<OAuth2AuthorizationConsentEntity> saveConsent(String clientId, String userId, String authorizedScopes);

    /**
     * 撤销用户对客户端的授权同意
     *
     * @param clientId 客户端ID
     * @param userId 用户ID(OpenID)
     */
    Mono<Void> revokeConsent(String clientId, String userId);
}
