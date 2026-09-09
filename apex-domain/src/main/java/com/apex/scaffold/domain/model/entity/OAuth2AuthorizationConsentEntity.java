package com.apex.scaffold.domain.model.entity;

import java.time.Instant;
import java.util.Set;
import lombok.Data;

/**
 * OAuth2授权同意实体
 *
 * <p>表示用户对某个OAuth2客户端应用的授权同意记录。
 * 当用户首次授权第三方应用访问其资源时，系统会创建此记录。
 * 后续相同客户端和scope的授权请求将自动通过，无需用户重复授权。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>用户首次授权第三方应用时创建记录</li>
 *   <li>检查用户是否已授权某个应用和scope组合</li>
 *   <li>用户撤销对某个应用的授权</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Data
public class OAuth2AuthorizationConsentEntity {
    
    /**
     * 唯一标识符
     */
    private String id;
    
    /**
     * OAuth2客户端ID
     *
     * <p>关联到 {@code oauth2_registered_client.client_id}
     */
    private String clientId;
    
    /**
     * 用户ID (OpenID)
     *
     * <p>授权的用户唯一标识
     */
    private String userId;
    
    /**
     * 用户已授权的权限作用域集合
     *
     * <p>例如: ["read", "write", "user:profile"]
     * <p>当授权请求的scope是此集合的子集时，自动通过授权
     */
    private Set<String> authorizedScopes;
    
    /**
     * 首次授权时间
     */
    private Instant grantedAt;
    
    /**
     * 最后修改时间
     *
     * <p>当用户更新授权scope时更新此字段
     */
    private Instant lastModifiedAt;
}
