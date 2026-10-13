package com.apex.scaffold.application.support.sso;

import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * SSO 协议适配器接口
 *
 * <p>定义 SSO 单点登录协议的通用接口,支持多种协议(SAML、CAS、LDAP、OIDC)的统一抽象。
 * 各具体协议实现此接口以提供协议特定的认证、登出和元数据获取功能。
 *
 * <h2>支持的协议</h2>
 * <ul>
 *   <li>SAML 2.0 - 企业级 SSO 标准协议</li>
 *   <li>CAS - Central Authentication Service 协议</li>
 *   <li>LDAP - 轻量级目录访问协议</li>
 *   <li>OIDC - OpenID Connect 协议</li>
 * </ul>
 *
 * <h2>核心流程</h2>
 * <ul>
 *   <li>认证流程: initiate() → callback() → authenticate()</li>
 *   <li>登出流程: logout()</li>
 *   <li>元数据: getMetadata()</li>
 * </ul>
 *
 * @author Apex Scaffold Generator
 * @since 2026-09-19
 */
public interface SsoProtocolAdapter {

    /**
     * 发起 SSO 认证请求
     *
     * <p>生成认证请求 URL 或数据,用于重定向用户到 SSO 提供商进行身份验证。
     * 不同协议返回不同格式的数据:
     * <ul>
     *   <li>SAML: 返回 SAMLRequest 和跳转 URL</li>
     *   <li>CAS: 返回 CAS 登录 URL</li>
     *   <li>OIDC: 返回 Authorization Endpoint URL</li>
     * </ul>
     *
     * @param config 提供商配置信息,包含端点 URL、证书等
     * @param callbackUrl 认证成功后的回调 URL
     * @return 认证请求数据,包含跳转 URL 和必要参数
     */
    Mono<Map<String, Object>> initiate(Map<String, Object> config, String callbackUrl);

    /**
     * 处理 SSO 回调并完成认证
     *
     * <p>验证 SSO 提供商返回的认证响应,提取用户身份信息。
     * 返回的用户信息包含:
     * <ul>
     *   <li>userId - 用户唯一标识</li>
     *   <li>username - 用户名</li>
     *   <li>email - 邮箱</li>
     *   <li>attributes - 其他属性(Map)</li>
     * </ul>
     *
     * @param config 提供商配置信息
     * @param callbackParams 回调参数(如 SAMLResponse、ticket、code 等)
     * @return 用户身份信息
     */
    Mono<Map<String, Object>> authenticate(Map<String, Object> config, Map<String, String> callbackParams);

    /**
     * 执行 SSO 登出
     *
     * <p>注销 SSO 会话,并可选择性地重定向到 SSO 提供商的登出端点。
     * 返回数据可能包含:
     * <ul>
     *   <li>logoutUrl - SSO 提供商的登出 URL(可选)</li>
     *   <li>success - 登出是否成功</li>
     * </ul>
     *
     * @param config 提供商配置信息
     * @param sessionToken SSO 会话令牌
     * @return 登出结果,可能包含重定向 URL
     */
    Mono<Map<String, Object>> logout(Map<String, Object> config, String sessionToken);

    /**
     * 获取 SSO 提供商的元数据
     *
     * <p>获取协议相关的元数据信息,用于配置验证或客户端集成。
     * 不同协议返回不同内容:
     * <ul>
     *   <li>SAML: SP 元数据 XML</li>
     *   <li>OIDC: Discovery 端点信息</li>
     *   <li>CAS: 服务配置信息</li>
     * </ul>
     *
     * @param config 提供商配置信息
     * @return 元数据信息
     */
    Mono<Map<String, Object>> getMetadata(Map<String, Object> config);

    /**
     * 测试 SSO 连接
     *
     * <p>验证 SSO 提供商配置是否正确,端点是否可访问,证书是否有效等。
     * 返回测试结果:
     * <ul>
     *   <li>success - 测试是否成功</li>
     *   <li>message - 测试结果消息</li>
     *   <li>details - 详细信息(可选)</li>
     * </ul>
     *
     * @param config 提供商配置信息
     * @return 测试结果
     */
    Mono<Map<String, Object>> testConnection(Map<String, Object> config);
}
