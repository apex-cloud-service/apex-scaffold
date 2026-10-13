package com.apex.scaffold.office.domain;

import reactor.core.publisher.Mono;

/**
 * 办公平台 OAuth 认证端口
 *
 * <p>定义办公平台 OAuth 2.0 认证流程的统一契约，由基础设施层的平台适配器实现。
 * 支持基于全局配置和租户级应用配置两种模式。
 *
 * <p>OAuth 流程：
 * <ol>
 *   <li>调用 {@link #authorizationUrl} 生成授权地址，引导用户跳转到平台授权页</li>
 *   <li>用户授权后，平台回调携带 code 和 state</li>
 *   <li>调用 {@link #authenticate} 使用 code 兑换平台 access token</li>
 *   <li>使用 access token 获取外部用户标识</li>
 *   <li>查询绑定关系，签发 Apex JWT</li>
 * </ol>
 *
 * <p>安全要点：
 * <ul>
 *   <li>state 必须在服务端生成并绑定租户、平台和回调地址</li>
 *   <li>平台 access token 不得返回给浏览器</li>
 *   <li>密钥通过 secretReference 引用环境变量，不存储明文</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.2.0
 */
public interface OfficeOAuthClient {
    /**
     * 获取当前客户端支持的办公平台类型
     *
     * @return 办公平台类型
     */
    OfficePlatform platform();

    /**
     * 生成 OAuth 授权地址（已废弃，使用租户级应用配置方法）
     *
     * @param state 服务端生成的状态值
     * @return 授权地址
     * @deprecated 使用 {@link #authorizationUrl(OfficePlatformApp, String)}
     */
    @Deprecated
    Mono<String> authorizationUrl(String state);

    /**
     * 使用授权码换取访问令牌（已废弃，使用租户级应用配置方法）
     *
     * @param code 平台授权码
     * @return 访问令牌
     * @deprecated 使用 {@link #authenticate(OfficePlatformApp, String)}
     */
    @Deprecated
    Mono<String> exchange(String code);

    /**
     * 生成 OAuth 授权地址（租户级应用配置）
     *
     * @param app 租户的平台应用配置
     * @param state 服务端生成的状态值
     * @return 平台授权地址，用户点击后跳转到平台授权页
     */
    Mono<String> authorizationUrl(OfficePlatformApp app, String state);

    /**
     * 使用授权码完成认证并返回外部用户标识（租户级应用配置）
     *
     * <p>该方法完成以下步骤：
     * <ol>
     *   <li>使用 code 和应用密钥向平台换取 access token</li>
     *   <li>使用 access token 查询外部用户信息</li>
     *   <li>提取并返回外部用户唯一标识（OpenId/UnionId/UserId）</li>
     * </ol>
     *
     * @param app 租户的平台应用配置
     * @param code 平台授权码（一次性有效）
     * @return 外部用户唯一标识
     * @throws com.apex.scaffold.common.exception.BusinessException 认证失败或平台返回错误时
     */
    Mono<String> authenticate(OfficePlatformApp app, String code);
}
