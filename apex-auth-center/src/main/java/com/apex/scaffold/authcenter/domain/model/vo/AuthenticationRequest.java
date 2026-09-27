package com.apex.scaffold.authcenter.domain.model.vo;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证请求值对象
 *
 * <p>封装认证请求的所有参数，支持多种认证协议和授权模式。
 *
 * <p>支持的授权模式：
 * <ul>
 *   <li>password - 密码模式</li>
 *   <li>authorization_code - 授权码模式</li>
 *   <li>client_credentials - 客户端凭证模式</li>
 * </ul>
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationRequest {

    private String protocol;

    private String username;

    private String password;

    private String clientId;

    private String clientSecret;

    private String grantType;

    private String authorizationCode;

    private String redirectUri;

    private String scope;

    private String deviceType;

    private String deviceId;

    private String deviceName;

    private String ipAddress;

    private String userAgent;

    private Map<String, Object> metadata;

    /**
     * 判断是否为密码模式认证请求
     *
     * @return 授权类型为password时返回true
     */
    public boolean isPasswordGrant() {
        return "password".equals(grantType);
    }

    /**
     * 判断是否为授权码模式认证请求
     *
     * @return 授权类型为authorization_code时返回true
     */
    public boolean isAuthorizationCodeGrant() {
        return "authorization_code".equals(grantType);
    }

    /**
     * 判断是否为客户端凭证模式认证请求
     *
     * @return 授权类型为client_credentials时返回true
     */
    public boolean isClientCredentialsGrant() {
        return "client_credentials".equals(grantType);
    }

    /**
     * 判断请求中是否同时携带用户名和密码凭证
     *
     * @return 用户名和密码均非空时返回true
     */
    public boolean hasCredentials() {
        return username != null && password != null;
    }
}
