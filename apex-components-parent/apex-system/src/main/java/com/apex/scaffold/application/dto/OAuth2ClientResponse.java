package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2 客户端响应 DTO。
 *
 * <p>返回 OAuth2 客户端（第三方应用）的注册信息与配置详情，用于管理后台的
 * 客户端列表展示、详情查询和更新表单回显。除密钥轮换接口外，响应中不包含
 * 客户端密钥明文（密钥采用 BCrypt 加密存储，仅在创建与重置时返回一次）。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>管理后台 OAuth2 客户端列表分页展示</li>
 *   <li>客户端详情查询与更新表单数据回显</li>
 *   <li>即将过期客户端（密钥轮换提醒）列表展示</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code authorizationGrantTypes}：客户端支持的授权模式（authorization_code、refresh_token 等）</li>
 *   <li>{@code clientAuthenticationMethods}：客户端认证方式（client_secret_basic、client_secret_post 等）</li>
 *   <li>{@code clientSecretExpiresAt}：密钥过期时间，用于驱动密钥轮换提醒</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 * @see OAuth2ClientRequest
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
@Schema(description = "OAuth2客户端响应数据传输对象")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2ClientResponse {

    @Schema(description = "客户端记录ID", example = "1001")
    private String id;

    @Schema(description = "客户端ID，OAuth2标准标识", example = "client-app-001")
    private String clientId;

    @Schema(description = "客户端名称", example = "移动应用客户端")
    private String clientName;

    @Schema(description = "客户端ID签发时间", example = "2026-09-23T10:00:00Z")
    private Instant clientIdIssuedAt;

    @Schema(description = "客户端密钥过期时间", example = "2027-09-23T10:00:00Z")
    private Instant clientSecretExpiresAt;

    @Schema(description = "客户端认证方法集合", example = "[\"client_secret_basic\", \"client_secret_post\"]")
    private Set<String> clientAuthenticationMethods;

    @Schema(description = "授权类型集合", example = "[\"authorization_code\", \"refresh_token\"]")
    private Set<String> authorizationGrantTypes;

    @Schema(
            description = "重定向URI集合",
            example = "[\"https://example.com/callback\", \"https://app.example.com/oauth/callback\"]")
    private Set<String> redirectUris;

    @Schema(
            description = "登出后重定向URI集合",
            example = "[\"https://example.com/logout\", \"https://app.example.com/logout\"]")
    private Set<String> postLogoutRedirectUris;

    @Schema(description = "授权范围集合", example = "[\"read\", \"write\", \"openid\", \"profile\"]")
    private Set<String> scopes;

    @Schema(description = "是否为公共客户端（无需客户端密钥）", example = "false")
    private Boolean isPublicClient;
}
