package com.apex.scaffold.domain.model.entity;

import java.time.Instant;
import lombok.Data;

/**
 * OAuth2授权码实体
 *
 * <p>表示OAuth2授权码模式中生成的授权码，有效期通常为5分钟。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Data
public class OAuth2AuthorizationCodeEntity {
    
    private String code;
    
    private String clientId;
    
    private String userId;
    
    private String redirectUri;
    
    private String scope;
    
    private String state;
    
    private String codeChallenge;
    
    private String codeChallengeMethod;
    
    private Instant issuedAt;
    
    private Instant expiresAt;
    
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
