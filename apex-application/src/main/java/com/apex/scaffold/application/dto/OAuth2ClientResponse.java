package com.apex.scaffold.application.dto;

import java.time.Instant;
import java.util.Set;
import lombok.Data;

/**
 * OAuth2客户端响应DTO
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Data
public class OAuth2ClientResponse {
    
    private String id;
    
    private String clientId;
    
    private String clientName;
    
    private Instant clientIdIssuedAt;
    
    private Instant clientSecretExpiresAt;
    
    private Set<String> clientAuthenticationMethods;
    
    private Set<String> authorizationGrantTypes;
    
    private Set<String> redirectUris;
    
    private Set<String> postLogoutRedirectUris;
    
    private Set<String> scopes;
    
    private Boolean isPublicClient;
}
