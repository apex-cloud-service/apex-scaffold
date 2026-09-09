package com.apex.scaffold.domain.model.entity;

import java.time.Instant;
import lombok.Data;

/**
 * OAuth2访问令牌实体
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Data
public class OAuth2AccessTokenEntity {

    private String tokenValue;

    private String tokenType = "Bearer";

    private String clientId;

    private String userId;

    private String scope;

    private Instant issuedAt;

    private Instant expiresAt;

    private String refreshToken;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public long getExpiresIn() {
        return java.time.Duration.between(Instant.now(), expiresAt).getSeconds();
    }
}
