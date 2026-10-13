package com.apex.scaffold.domain.model.sso;

import lombok.Getter;

/**
 * SSO 认证协议类型枚举
 *
 * <p>定义 SSO 单点登录系统支持的认证协议类型。不同协议在认证流程、令牌格式和集成方式上存在差异，
 * 系统需要根据协议类型执行不同的认证逻辑和令牌解析策略。
 *
 * <h2>支持的协议</h2>
 * <ul>
 *   <li>{@link #SAML} - Security Assertion Markup Language 2.0，基于 XML 的企业级 SSO 标准</li>
 *   <li>{@link #CAS} - Central Authentication Service，由 Apereo 维护的开源 SSO 协议</li>
 *   <li>{@link #LDAP} - Lightweight Directory Access Protocol，目录服务认证协议</li>
 *   <li>{@link #OIDC} - OpenID Connect，基于 OAuth 2.0 的现代身份认证层</li>
 * </ul>
 *
 * <h2>协议特点对比</h2>
 * <table border="1">
 *   <tr>
 *     <th>协议</th>
 *     <th>数据格式</th>
 *     <th>传输方式</th>
 *     <th>适用场景</th>
 *   </tr>
 *   <tr>
 *     <td>SAML</td>
 *     <td>XML</td>
 *     <td>HTTP POST/Redirect</td>
 *     <td>企业级应用、跨域信任、联邦身份</td>
 *   </tr>
 *   <tr>
 *     <td>CAS</td>
 *     <td>Ticket/XML</td>
 *     <td>HTTP Redirect</td>
 *     <td>高校、政府、传统企业应用</td>
 *   </tr>
 *   <tr>
 *     <td>LDAP</td>
 *     <td>LDAP Entry</td>
 *     <td>TCP 389/636</td>
 *     <td>企业内网、AD 域集成、组织架构同步</td>
 *   </tr>
 *   <tr>
 *     <td>OIDC</td>
 *     <td>JWT</td>
 *     <td>HTTP REST</td>
 *     <td>现代 Web/移动应用、微服务、云原生</td>
 *   </tr>
 * </table>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>SSO 提供商配置时指定认证协议类型</li>
 *   <li>认证请求处理时选择对应的协议适配器</li>
 *   <li>令牌解析时应用协议特定的验证规则</li>
 *   <li>用户属性映射时使用协议特定的字段名称</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public enum SsoProtocolType {
    /**
     * SAML 2.0 协议
     *
     * <p>特点：
     * <ul>
     *   <li>数据格式：XML 断言（Assertion）</li>
     *   <li>传输方式：HTTP POST Binding / HTTP Redirect Binding</li>
     *   <li>认证流程：SP-initiated / IdP-initiated</li>
     *   <li>安全机制：XML 签名、XML 加密</li>
     *   <li>元数据：支持 SAML Metadata XML 自动配置</li>
     * </ul>
     *
     * <p>配置要点：
     * <ul>
     *   <li>Entity ID：SP 和 IdP 的唯一标识</li>
     *   <li>Metadata URL：IdP 元数据地址</li>
     *   <li>ACS URL：Assertion Consumer Service 地址</li>
     *   <li>证书：用于签名验证和加密的 X.509 证书</li>
     * </ul>
     */
    SAML("SAML 2.0", "基于 XML 的企业级 SSO 标准协议"),

    /**
     * CAS 协议（Central Authentication Service）
     *
     * <p>特点：
     * <ul>
     *   <li>数据格式：Ticket（ST/TGT）和 XML 验证响应</li>
     *   <li>传输方式：HTTP Redirect + 服务端验证</li>
     *   <li>认证流程：中心化票据颁发和验证</li>
     *   <li>安全机制：一次性票据、服务端验证</li>
     *   <li>协议版本：支持 CAS 1.0/2.0/3.0</li>
     * </ul>
     *
     * <p>配置要点：
     * <ul>
     *   <li>CAS Server URL：CAS 认证服务器地址</li>
     *   <li>Service URL：应用的回调地址</li>
     *   <li>Ticket Validation URL：票据验证端点</li>
     *   <li>Logout URL：登出端点（可选）</li>
     * </ul>
     */
    CAS("CAS", "中心化认证服务协议"),

    /**
     * LDAP 协议（Lightweight Directory Access Protocol）
     *
     * <p>特点：
     * <ul>
     *   <li>数据格式：LDAP Entry（DN、属性集合）</li>
     *   <li>传输方式：TCP 389（明文）/ 636（SSL/TLS）</li>
     *   <li>认证流程：Bind 操作验证用户凭证</li>
     *   <li>安全机制：Simple Bind / SASL 认证</li>
     *   <li>目录服务：支持 AD、OpenLDAP、389 Directory Server</li>
     * </ul>
     *
     * <p>配置要点：
     * <ul>
     *   <li>LDAP URL：目录服务器地址（ldap://或ldaps://）</li>
     *   <li>Base DN：搜索基准 DN</li>
     *   <li>Bind DN：管理员 DN（用于搜索）</li>
     *   <li>User Filter：用户搜索过滤器（如 uid={0}）</li>
     *   <li>属性映射：username、email、displayName 等</li>
     * </ul>
     */
    LDAP("LDAP", "轻量级目录访问协议"),

    /**
     * OpenID Connect (OIDC) 协议
     *
     * <p>特点：
     * <ul>
     *   <li>数据格式：JWT ID Token + OAuth 2.0 Access Token</li>
     *   <li>传输方式：HTTP REST + OAuth 2.0 流程</li>
     *   <li>认证流程：Authorization Code / Implicit / Hybrid Flow</li>
     *   <li>安全机制：JWT 签名、PKCE、State 参数</li>
     *   <li>发现机制：支持 .well-known/openid-configuration 自动发现</li>
     * </ul>
     *
     * <p>配置要点：
     * <ul>
     *   <li>Issuer URL：OIDC 提供商的 Issuer 地址</li>
     *   <li>Client ID / Secret：OAuth 2.0 客户端凭证</li>
     *   <li>Authorization Endpoint：授权端点</li>
     *   <li>Token Endpoint：令牌端点</li>
     *   <li>UserInfo Endpoint：用户信息端点</li>
     *   <li>JWK Set URI：公钥集合地址（用于验证 JWT）</li>
     * </ul>
     */
    OIDC("OpenID Connect", "基于 OAuth 2.0 的现代身份认证层");

    private final String displayName;

    private final String description;

    SsoProtocolType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 根据名称查找 SSO 协议类型
     *
     * @param name 协议类型名称（不区分大小写）
     * @return 匹配的协议类型
     * @throws IllegalArgumentException 如果找不到匹配的类型
     */
    public static SsoProtocolType fromName(String name) {
        for (SsoProtocolType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported SSO protocol type: " + name);
    }

    /**
     * 检查是否支持元数据自动配置
     *
     * <p>SAML 和 OIDC 支持通过元数据 URL 自动获取配置信息，
     * 而 CAS 和 LDAP 通常需要手动配置各个端点。
     *
     * @return true 表示支持元数据自动配置
     */
    public boolean supportsMetadata() {
        return this == SAML || this == OIDC;
    }

    /**
     * 检查是否需要证书配置
     *
     * <p>SAML 协议通常需要配置 X.509 证书用于签名验证和加密，
     * OIDC 使用 JWK Set 验证 JWT 签名，
     * CAS 和 LDAP 一般不需要额外的证书配置。
     *
     * @return true 表示需要证书配置
     */
    public boolean requiresCertificate() {
        return this == SAML;
    }
}
