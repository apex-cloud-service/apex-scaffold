package com.apex.scaffold.auth.domain.repository;

import reactor.core.publisher.Mono;

/**
 * OAuth2 用户授权同意端口（RFC 6749 §4.1.2 语义的持久化）。
 *
 * <p>同意记录按三元键 (租户, 主体, 客户端) 唯一，撤销为逻辑删除。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since  1.3.1
 */
public interface OAuth2ConsentPort {

    /**
     * 查询主体对客户端的有效同意 scope。
     *
     * @param tenant    租户
     * @param principal 已认证主体
     * @param clientId  客户端 ID
     * @return 有效同意的 scope（空串表示同意记录未限定范围）；无有效同意时为空 Mono
     */
    Mono<String> findActiveScope(String tenant, String principal, String clientId);

    /**
     * 记录或刷新同意（撤销后重新同意、扩大范围须更新既有记录）。
     *
     * @param tenant    租户
     * @param principal 已认证主体
     * @param clientId  客户端 ID
     * @param scope     同意的授权范围
     * @return 写入完成后发出完成信号
     */
    Mono<Void> save(String tenant, String principal, String clientId, String scope);

    /**
     * 撤销同意（逻辑删除）。
     *
     * @param tenant    租户
     * @param principal 已认证主体
     * @param clientId  客户端 ID
     * @return 写入完成后发出完成信号
     */
    Mono<Void> revoke(String tenant, String principal, String clientId);
}
