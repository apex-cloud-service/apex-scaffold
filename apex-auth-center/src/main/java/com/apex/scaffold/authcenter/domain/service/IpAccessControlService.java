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
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param ipAddress TODO
     * @param tenantId TODO
     * @param description TODO
     * @param operator TODO
     * @return TODO
     */
    Mono<IpAccessControl> addToWhitelist(String ipAddress, String tenantId, String description, String operator);

    /**
     * 添加IP到黑名单
     *
     * @param ipAddress IP地址或CIDR
     * @param tenantId 租户ID
     * @param description 描述
     * @param durationMinutes 封禁时长(分钟),null表示永久
     * @param operator 操作员
     * @return 创建的黑名单记录
     */
    Mono<IpAccessControl> addToBlacklist(
            String ipAddress, String tenantId, String description, Integer durationMinutes, String operator);

    /**
     * 从白名单移除IP
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param ipAddress TODO
     * @param tenantId TODO
     * @return TODO
     */
    Mono<Void> removeFromWhitelist(String ipAddress, String tenantId);

    /**
     * 从黑名单移除IP
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param ipAddress TODO
     * @param tenantId TODO
     * @return TODO
     */
    Mono<Void> removeFromBlacklist(String ipAddress, String tenantId);

    /**
     * 查询租户的白名单规则
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param tenantId TODO
     * @return TODO
     */
    Flux<IpAccessControl> getWhitelist(String tenantId);

    /**
     * 查询租户的黑名单规则
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param tenantId TODO
     * @return TODO
     */
    Flux<IpAccessControl> getBlacklist(String tenantId);

    /**
     * 清理过期的规则
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    Mono<Long> cleanupExpiredRules();

    /**
     * 启用规则
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param ruleId TODO
     * @param operator TODO
     * @return TODO
     */
    Mono<Void> enableRule(Long ruleId, String operator);

    /**
     * 禁用规则
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param ruleId TODO
     * @param operator TODO
     * @return TODO
     */
    Mono<Void> disableRule(Long ruleId, String operator);

    /**
     * 删除规则
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param ruleId TODO
     * @param operator TODO
     * @return TODO
     */
    Mono<Void> deleteRule(Long ruleId, String operator);

    /**
     * 批量导入规则
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method/**
     * 批量导入访问控制规则
     *
     * @param rules 规则流
     * @param operator 操作员
     * @return 导入成功的规则数量
     */
    Mono<Long> batchImportRules(Flux<IpAccessControl> rules, String operator);
}
