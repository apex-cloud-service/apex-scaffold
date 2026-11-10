package com.apex.scaffold.auth.domain.repository;

import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * 授权快照查询端口。
 *
 * <p>查询目标用户在同租户下的有效权限编码集合。domain 只依赖本端口，
 * 具体查询实现（账号有效性校验、角色与权限聚合）位于应用层。</p>
 *
 * <p>实现必须保证：租户或用户标识非法、账号无效时返回错误信号，
 * 不得降级为放行或空集合。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
public interface AuthorizationQueryPort {

    /**
     * 查询用户在同租户下的有效权限编码集合。
     *
     * @param tenant 租户标识
     * @param userId 用户标识
     * @return 权限编码集合；账号无效时返回错误信号
     */
    Mono<Set<String>> queryPermissions(String tenant, Long userId);
}
