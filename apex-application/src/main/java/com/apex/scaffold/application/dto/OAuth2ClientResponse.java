package com.apex.scaffold.application.dto;

import java.time.Instant;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2客户端响应DTO
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**n * OAuth2ClientResponsen *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
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
