package com.apex.scaffold.authcenter.domain.service;

import com.apex.scaffold.authcenter.domain.model.IpAccessControl;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * IP访问控制领域服务接口
 * @author Apex Team
 * @since 1.2.0
 */
public interface IpAccessControlService {

    /**
     * 检查IP是否被允许访问
     * 
     * @param ipAddress 客户端IP地址
     * @param tenantId 租户ID
     * @return true-允许访问, false-拒绝访问
     */
    Mono<Boolean> isAccessAllowed(String ipAddress, String tenantId);

    /**
     * 添加IP到白名单
     */
    Mono<IpAccessControl> addToWhitelist(String ipAddress, String tenantId, String description, String operator);

    /**
     * 添加IP到黑名单
     */
    Mono<IpAccessControl> addToBlacklist(String ipAddress, String tenantId, String description, 
                                          Integer durationMinutes, String operator);

    /**
     * 从白名单移除IP
     */
    Mono<Void> removeFromWhitelist(String ipAddress, String tenantId);

    /**
     * 从黑名单移除IP
     */
    Mono<Void> removeFromBlacklist(String ipAddress, String tenantId);

    /**
     * 查询租户的白名单规则
     */
    Flux<IpAccessControl> getWhitelist(String tenantId);

    /**
     * 查询租户的黑名单规则
     */
    Flux<IpAccessControl> getBlacklist(String tenantId);

    /**
     * 清理过期的规则
     */
    Mono<Long> cleanupExpiredRules();

    /**
     * 启用规则
     */
    Mono<Void> enableRule(Long ruleId, String operator);

    /**
     * 禁用规则
     */
    Mono<Void> disableRule(Long ruleId, String operator);

    /**
     * 删除规则
     */
    Mono<Void> deleteRule(Long ruleId, String operator);

    /**
     * 批量导入规则
     */
    Mono<Long> batchImportRules(Flux<IpAccessControl> rules, String operator);
}
