package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2 客户端创建/更新请求
 *
 * <p>用于管理 OAuth2 客户端（第三方应用）的注册、配置和更新。
 * OAuth2 客户端是指接入本系统授权服务的第三方应用，需要预先注册并配置授权参数。
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>注册新的 OAuth2 客户端应用</li>
 *   <li>更新已有客户端的配置（如回调地址、授权范围）</li>
 *   <li>配置客户端的安全策略（PKCE、授权同意等）</li>
 * </ul>
 *
 * <h2>OAuth2 授权模式</h2>
 * <p>支持的授权类型（authorizationGrantTypes）：
 * <ul>
 *   <li>{@code authorization_code}：授权码模式（最常用、最安全）</li>
 *   <li>{@code refresh_token}：刷新令牌</li>
 *   <li>{@code client_credentials}：客户端凭证模式</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Schema(description = "OAuth2客户端创建/更新请求")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class OAuth2ClientRequest {

    /**
     * 客户端 ID
     *
     * <p>OAuth2 客户端的唯一标识，全局唯一。
     * 创建时如果不指定，系统将自动生成；更新时必须指定现有客户端 ID。
     *
     * <p>命名建议：使用小写字母、数字和连字符，如 {@code apex-admin-web}
     */
    @Schema(description = "客户端ID，全局唯一标识", example = "apex-admin-web")
    private String clientId;

    /**
     * 客户端密钥
     *
     * <p>用于客户端身份验证的密钥，类似于密码。
     * 创建时如果不指定，系统将自动生成；更新时如果不提供则保持原值。
     *
     * <p>安全要求：
     * <ul>
     *   <li>必须妥善保管，不能泄露给第三方</li>
     *   <li>建议定期轮换</li>
     *   <li>仅在服务端使用，不能嵌入前端代码</li>
     * </ul>
     */
    @Schema(description = "客户端密钥，用于身份验证", example = "secret-abc123xyz")
    private String clientSecret;

    /**
     * 客户端名称
     *
     * <p>面向用户的可读名称，用于授权同意页面展示。
     *
     * <p>示例：{@code Apex 管理后台}、{@code 第三方数据分析平台}
     */
    @Schema(description = "客户端名称，用于授权页面展示", example = "Apex管理后台")
    private String clientName;

    /**
     * 客户端认证方式
     *
     * <p>支持的客户端认证方法集合。常用值：
     * <ul>
     *   <li>{@code client_secret_basic}：HTTP Basic 认证（推荐）</li>
     *   <li>{@code client_secret_post}：POST 参数认证</li>
     *   <li>{@code none}：公开客户端（不推荐用于生产环境）</li>
     * </ul>
     */
    @Schema(description = "客户端认证方式集合", example = "[\"client_secret_basic\", \"client_secret_post\"]")
    private Set<String> clientAuthenticationMethods;

    /**
     * 授权类型
     *
     * <p>客户端支持的 OAuth2 授权模式集合。常用值：
     * <ul>
     *   <li>{@code authorization_code}：授权码模式</li>
     *   <li>{@code refresh_token}：刷新令牌</li>
     *   <li>{@code client_credentials}：客户端凭证模式</li>
     * </ul>
     *
     * <p>注意：授权码模式通常需要配合 {@code refresh_token} 使用
     */
    @Schema(description = "授权类型集合", example = "[\"authorization_code\", \"refresh_token\"]")
    private Set<String> authorizationGrantTypes;

    /**
     * 授权成功后的重定向 URI 列表
     *
     * <p>用户授权成功后，授权服务器将用户重定向到的地址集合。
     * 必须是完整的 HTTPS URL（开发环境可使用 HTTP localhost）。
     *
     * <p>安全要求：
     * <ul>
     *   <li>必须预先配置，不能动态指定</li>
     *   <li>授权请求中的 redirect_uri 必须完全匹配其中一个</li>
     *   <li>生产环境必须使用 HTTPS</li>
     * </ul>
     *
     * <p>示例：{@code https://admin.example.com/oauth/callback}
     */
    @Schema(description = "授权成功后的重定向URI列表", example = "[\"https://admin.example.com/oauth/callback\"]")
    private Set<String> redirectUris;

    /**
     * 登出后的重定向 URI 列表
     *
     * <p>用户退出登录后，系统将用户重定向到的地址集合。
     * 用于实现单点登出（Single Logout）功能。
     *
     * <p>示例：{@code https://admin.example.com/logout-success}
     */
    @Schema(description = "登出后的重定向URI列表", example = "[\"https://admin.example.com/logout-success\"]")
    private Set<String> postLogoutRedirectUris;

    /**
     * 授权范围（Scope）
     *
     * <p>客户端可以请求的权限范围集合。常用值：
     * <ul>
     *   <li>{@code read}：读取权限</li>
     *   <li>{@code write}：写入权限</li>
     *   <li>{@code openid}：OpenID Connect 基本信息</li>
     *   <li>{@code profile}：用户资料</li>
     *   <li>{@code email}：邮箱地址</li>
     * </ul>
     *
     * <p>实际授权时，用户可以选择授予部分或全部 scope
     */
    @Schema(description = "授权范围集合", example = "[\"read\", \"write\", \"openid\", \"profile\"]")
    private Set<String> scopes;

    /**
     * 是否要求 PKCE（Proof Key for Code Exchange）
     *
     * <p>PKCE 是一种增强授权码模式安全性的扩展，推荐所有客户端启用。
     *
     * <p>安全建议：
     * <ul>
     *   <li>移动应用和单页应用（SPA）：必须启用</li>
     *   <li>服务端渲染应用：建议启用</li>
     *   <li>纯后端应用：可选</li>
     * </ul>
     *
     * <p>默认值：{@code false}
     */
    @Schema(description = "是否要求PKCE验证", example = "true")
    private Boolean requireProofKey;

    /**
     * 是否需要用户授权同意
     *
     * <p>如果启用，用户首次授权时需要在同意页面确认授予的权限。
     * 如果禁用，系统将自动授予权限（适用于受信任的第一方应用）。
     *
     * <p>推荐配置：
     * <ul>
     *   <li>第三方应用：必须启用（{@code true}）</li>
     *   <li>第一方应用：可以禁用（{@code false}）</li>
     * </ul>
     *
     * <p>默认值：{@code true}
     */
    @Schema(description = "是否需要用户授权同意", example = "true")
    private Boolean requireAuthorizationConsent;

    /**
     * 访问令牌有效期（秒）
     *
     * <p>Access Token 的有效时长，过期后需要使用 Refresh Token 刷新。
     *
     * <p>推荐配置：
     * <ul>
     *   <li>短期令牌：3600（1 小时）</li>
     *   <li>中期令牌：7200（2 小时）</li>
     *   <li>长期令牌：不推荐超过 86400（24 小时）</li>
     * </ul>
     *
     * <p>默认值：{@code 3600L}（1 小时）
     */
    @Schema(description = "访问令牌有效期（秒）", example = "3600")
    private Long accessTokenTimeToLive;

    /**
     * 刷新令牌有效期（秒）
     *
     * <p>Refresh Token 的有效时长，用于获取新的 Access Token。
     *
     * <p>推荐配置：
     * <ul>
     *   <li>Web 应用：2592000（30 天）</li>
     *   <li>移动应用：7776000（90 天）</li>
     * </ul>
     *
     * <p>默认值：{@code 2592000L}（30 天）
     */
    @Schema(description = "刷新令牌有效期（秒）", example = "2592000")
    private Long refreshTokenTimeToLive;

    /**
     * 授权码有效期（秒）
     *
     * <p>Authorization Code 的有效时长，授权码是一次性的，获取后立即交换 Token。
     *
     * <p>安全要求：
     * <ul>
     *   <li>有效期应尽可能短，通常 300 秒（5 分钟）已足够</li>
     *   <li>授权码使用后立即失效</li>
     *   <li>不能重复使用同一个授权码</li>
     * </ul>
     *
     * <p>默认值：{@code 300L}（5 分钟）
     */
    @Schema(description = "授权码有效期（秒）", example = "300")
    private Long authorizationCodeTimeToLive;
}
