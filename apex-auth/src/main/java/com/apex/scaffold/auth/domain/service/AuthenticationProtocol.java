package com.apex.scaffold.auth.domain.service;

import com.apex.scaffold.auth.domain.model.vo.AuthenticationRequest;
import com.apex.scaffold.auth.domain.model.vo.AuthenticationResult;
import reactor.core.publisher.Mono;

/**
 * 认证协议接口
 *
 * <p>定义统一的认证协议规范，所有认证协议适配器必须实现此接口。
 * 支持JWT、OAuth2、SSO、LDAP等多种认证协议的插件化扩展。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>用户身份认证</li>
 *   <li>Token验证</li>
 *   <li>登出处理</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * AuthenticationProtocol protocol = new JwtAuthenticationProtocol();
 * AuthenticationResult result = protocol.authenticate(request).block();
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 * @see JwtAuthenticationProtocol
 * @see OAuth2AuthenticationProtocol
 */
public interface AuthenticationProtocol {

    /**
     * 获取协议名称
     *
     * <p>返回协议的唯一标识符，用于协议选择和路由
     *
     * @return 协议名称，如：jwt、oauth2、sso、ldap
     */
    String getProtocolName();

    /**
     * 执行用户身份认证
     *
     * <p>根据认证请求验证用户身份，验证通过后返回认证结果，包含用户信息和Token。
     * 支持多种认证方式：用户名密码、手机验证码、第三方授权等。
     *
     * <p>认证流程：
     * <ol>
     *   <li>提取认证凭证（用户名密码、授权码等）</li>
     *   <li>调用对应的认证逻辑验证凭证有效性</li>
     *   <li>验证通过后签发Token</li>
     *   <li>记录审计日志</li>
     * </ol>
     *
     * @param request 认证请求，包含认证凭证、设备信息、IP地址等
     * @return 认证结果的Mono，包含Token、用户信息和过期时间
     */
    Mono<AuthenticationResult> authenticate(AuthenticationRequest request);

    /**
     * 验证Token有效性
     *
     * <p>验证Token的签名、过期时间、撤销状态等
     *
     * @param token Token字符串
     * @return 验证结果的Mono，true-有效，false-无效
     */
    Mono<Boolean> validateToken(String token);

    /**
     * 登出处理
     *
     * <p>撤销Token，清理会话信息
     *
     * @param token Token字符串
     */
    Mono<Void> logout(String token);
}
