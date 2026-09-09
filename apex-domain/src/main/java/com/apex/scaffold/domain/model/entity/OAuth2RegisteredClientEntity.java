package com.apex.scaffold.domain.model.entity;

import java.time.Instant;
import java.util.Set;
import lombok.Data;

/**
 * OAuth2注册客户端领域实体
 *
 * <p>表示一个注册到授权服务器的OAuth2客户端应用。客户端可以是：
 * <ul>
 *   <li>Web应用(需要client_secret,使用授权码模式)</li>
 *   <li>移动应用(公共客户端,使用PKCE增强的授权码模式)</li>
 *   <li>服务应用(M2M,使用客户端凭证模式)</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Data
public class OAuth2RegisteredClientEntity {
    
    private String id;
    
    private String clientId;
    
    private Instant clientIdIssuedAt;
    
    private String clientSecret;
    
    private Instant clientSecretExpiresAt;
    
    private String clientName;
    
    private Set<String> clientAuthenticationMethods;
    
    private Set<String> authorizationGrantTypes;
    
    private Set<String> redirectUris;
    
    private Set<String> postLogoutRedirectUris;
    
    private Set<String> scopes;
    
    private String clientSettings;
    
    private String tokenSettings;
    
    /**
     * 判断是否为公共客户端(无密钥)
     *
     * @return true-公共客户端, false-机密客户端
     */
    public boolean isPublicClient() {
        return clientSecret == null || clientSecret.isEmpty();
    }
    
    /**
     * 判断是否支持指定的授权类型
     *
     * @param grantType 授权类型
     * @return true-支持, false-不支持
     */
    public boolean supportsGrantType(String grantType) {
        return authorizationGrantTypes != null && authorizationGrantTypes.contains(grantType);
    }
    
    /**
     * 判断是否支持指定的作用域
     *
     * @param scope 作用域
     * @return true-支持, false-不支持
     */
    public boolean supportsScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }
}
