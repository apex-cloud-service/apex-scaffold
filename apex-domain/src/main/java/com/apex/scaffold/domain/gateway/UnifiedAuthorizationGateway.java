package com.apex.scaffold.domain.gateway;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 查询可信主应用身份对应的统一授权。
 *
 * @author Apex Scaffold Team
 */
public interface UnifiedAuthorizationGateway {
    /**
     * 查询显式映射的中心授权并返回主应用身份。
     *
     * @param tenantId 可信主应用租户
     * @param userId 主应用用户标识
     * @return 授权快照，缺失映射或查询失败时拒绝授权
     */
    Mono<Authorization> query(String tenantId, Long userId);

    record Authorization(String tenantId, Long userId, List<String> permissions, List<Role> roles) {}

    record Role(String code, String dataScope) {}
}
