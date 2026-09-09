package com.apex.scaffold.application.dto;

import java.util.Set;
import lombok.Data;

/**
 * OAuth2客户端创建/更新请求DTO
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Data
public class OAuth2ClientRequest {

    private String clientId;

    private String clientSecret;

    private String clientName;

    private Set<String> clientAuthenticationMethods;

    private Set<String> authorizationGrantTypes;

    private Set<String> redirectUris;

    private Set<String> postLogoutRedirectUris;

    private Set<String> scopes;

    private Boolean requireProofKey;

    private Boolean requireAuthorizationConsent;

    private Long accessTokenTimeToLive;

    private Long refreshTokenTimeToLive;

    private Long authorizationCodeTimeToLive;
}
