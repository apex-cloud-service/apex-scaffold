package com.apex.scaffold.auth.domain.service;

import com.apex.scaffold.auth.domain.model.IpAccessControl;
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
     *
     * @param ipAddress IP地址或CIDR
     * @param tenantId 租户ID
     * @param description 描述
     * @param operator 操作员
     * @return 创建的白名单记录
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
     *
     * @param ipAddress IP地址或CIDR
     * @param tenantId 租户ID
     * @return 操作完成信号
     */
    Mono<Void> removeFromWhitelist(String ipAddress, String tenantId);

    /**
     * 从黑名单移除IP
     *
     * @param ipAddress IP地址或CIDR
     * @param tenantId 租户ID
     * @return 操作完成信号
     */
    Mono<Void> removeFromBlacklist(String ipAddress, String tenantId);

    /**
     * 查询租户的白名单规则
     *
     * @param tenantId 租户ID
     * @return 白名单规则列表
     */
    Flux<IpAccessControl> getWhitelist(String tenantId);

    /**
     * 查询租户的黑名单规则
     *
     * @param tenantId 租户ID
     * @return 黑名单规则列表
     */
    Flux<IpAccessControl> getBlacklist(String tenantId);

    /**
     * 清理过期的规则
     *
     * @return 清理的规则数量
     */
    Mono<Long> cleanupExpiredRules();

    /**
     * 启用规则
     *
     * @param ruleId 规则ID
     * @param tenantId 租户ID
     * @param operator 操作员
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Mono<Void> enableRule(Long ruleId, String tenantId, String operator);

    /**
     * 禁用规则
     *
     * @param ruleId 规则ID
     * @param tenantId 租户ID
     * @param operator 操作员
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Mono<Void> disableRule(Long ruleId, String tenantId, String operator);

    /**
     * 删除规则
     *
     * @param ruleId 规则ID
     * @param tenantId 租户ID
     * @param operator 操作员
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Mono<Void> deleteRule(Long ruleId, String tenantId, String operator);

    /**
     * 批量导入访问控制规则
     *
     * @param rules 规则流
     * @param operator 操作员
     * @return 导入成功的规则数量
     */
    Mono<Long> batchImportRules(Flux<IpAccessControl> rules, String operator);
}
