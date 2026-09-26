package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * OAuth2注册客户端持久化对象
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@TableName("oauth2_registered_client")
public class OAuth2RegisteredClientPO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("client_id")
    private String clientId;

    @TableField("client_id_issued_at")
    private LocalDateTime clientIdIssuedAt;

    @TableField("client_secret")
    private String clientSecret;

    @TableField("client_secret_expires_at")
    private LocalDateTime clientSecretExpiresAt;

    @TableField("client_name")
    private String clientName;

    @TableField("client_authentication_methods")
    private String clientAuthenticationMethods;

    @TableField("authorization_grant_types")
    private String authorizationGrantTypes;

    @TableField("redirect_uris")
    private String redirectUris;

    @TableField("post_logout_redirect_uris")
    private String postLogoutRedirectUris;

    @TableField("scopes")
    private String scopes;

    @TableField("client_settings")
    private String clientSettings;

    @TableField("token_settings")
    private String tokenSettings;

    /**
     * 上一个客户端密钥（已加密）
     *
     * <p>密钥轮换后的旧密钥，用于宽限期认证（对应表列 {@code previous_client_secret}）。
     */
    @TableField("previous_client_secret")
    private String previousClientSecret;

    /**
     * 密钥轮换时间
     *
     * <p>最近一次密钥轮换时间戳，从未轮换时为 {@code null}（对应表列 {@code secret_rotated_at}）。
     */
    @TableField("secret_rotated_at")
    private LocalDateTime secretRotatedAt;

    /**
     * 密钥轮换次数
     *
     * <p>累计轮换计数，默认 0（对应表列 {@code secret_rotation_count}）。
     */
    @TableField("secret_rotation_count")
    private Integer secretRotationCount;
}
