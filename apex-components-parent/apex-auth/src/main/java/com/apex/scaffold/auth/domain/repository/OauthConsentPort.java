package com.apex.scaffold.auth.domain.repository;

import reactor.core.publisher.Mono;

/**
 * OAuth 用户授权同意端口。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public interface OauthConsentPort {
    /**
     * 查询主体对客户端的有效同意范围。
     *
     * @param tenant 租户
     * @param principal 已认证主体
     * @param clientId 客户端 ID
     * @return 有效同意范围；未命中时为空 Mono
     */
    Mono<String> findActiveScope(String tenant, String principal, String clientId);

    /**
     * 保存主体对客户端的授权同意。
     *
     * @param tenant 租户
     * @param principal 已认证主体
     * @param clientId 客户端 ID
     * @param scope 授权范围
     * @return 写入完成信号
     */
    Mono<Void> save(String tenant, String principal, String clientId, String scope);

    /**
     * 撤销主体对客户端的授权同意。
     *
     * @param tenant 租户
     * @param principal 已认证主体
     * @param clientId 客户端 ID
     * @return 撤销完成信号
     */
    Mono<Void> revoke(String tenant, String principal, String clientId);
}
