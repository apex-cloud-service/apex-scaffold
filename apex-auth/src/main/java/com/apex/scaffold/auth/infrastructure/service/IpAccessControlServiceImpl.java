package com.apex.scaffold.auth.infrastructure.service;

import com.apex.scaffold.auth.domain.model.IpAccessControl;
import com.apex.scaffold.auth.domain.service.IpAccessControlService;
import com.apex.scaffold.auth.infrastructure.repository.IpAccessControlRepository;
import com.apex.scaffold.common.audit.AuditLogService;
import com.apex.scaffold.common.util.LogSafetyUtil;
import java.time.LocalDateTime;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * IP访问控制服务实现
 *
 * 安全策略：
 * 1. 黑名单优先：先检查黑名单，在黑名单中直接拒绝
 * 2. 白名单生效：如果配置了白名单，只允许白名单中的IP
 * 3. 无白名单时：默认允许所有未在黑名单中的IP
 * 4. 支持CIDR和通配符匹配
 * 5. 规则优先级：数字越小优先级越高
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IpAccessControlServiceImpl implements IpAccessControlService {

    private final IpAccessControlRepository repository;
    private final AuditLogService auditLogService;

    /**
     * 检查IP是否被允许访问
     *
     * 策略优先级：
     * 1. 黑名单匹配 -> 拒绝
     * 2. 白名单存在 && 白名单不匹配 -> 拒绝
     * 3. 其他情况 -> 允许
     *
     * @param ipAddress 客户端IP地址
     * @param tenantId 租户ID
     * @return 允许访问返回true，拒绝访问或参数为空返回false
     */
    @Override
    @Cacheable(value = "ip-access-control", key = "#ipAddress + ':' + #tenantId", unless = "#result == null")
    public Mono<Boolean> isAccessAllowed(String ipAddress, String tenantId) {
        if (ipAddress == null || tenantId == null) {
            return Mono.just(false);
        }

        return Mono.zip(
                        // 检查黑名单
                        checkBlacklist(ipAddress, tenantId),
                        // 检查白名单
                        checkWhitelist(ipAddress, tenantId),
                        // 判断是否存在白名单规则
                        hasWhitelistRules(tenantId))
                .map(tuple -> {
                    boolean inBlacklist = tuple.getT1();
                    boolean inWhitelist = tuple.getT2();
                    boolean hasWhitelist = tuple.getT3();

                    // 1. 黑名单中直接拒绝
                    if (inBlacklist) {
                        log.warn(
                                "IP访问被拒绝(黑名单): ip={}, tenantId={}",
                                LogSafetyUtil.text(ipAddress, 64),
                                LogSafetyUtil.text(tenantId, 64));
                        return false;
                    }

                    // 2. 如果配置了白名单，必须在白名单中
                    if (hasWhitelist && !inWhitelist) {
                        log.warn(
                                "IP访问被拒绝(不在白名单): ip={}, tenantId={}",
                                LogSafetyUtil.text(ipAddress, 64),
                                LogSafetyUtil.text(tenantId, 64));
                        return false;
                    }

                    // 3. 其他情况允许
                    return true;
                });
    }

    /**
     * 检查IP是否在黑名单中
     */
    private Mono<Boolean> checkBlacklist(String ipAddress, String tenantId) {
        return repository
                .findByTenantIdAndType(tenantId, IpAccessControl.AccessControlType.BLACKLIST)
                .filter(IpAccessControl::isActive)
                .sort(Comparator.comparing(IpAccessControl::getPriority))
                .filter(rule -> rule.matches(ipAddress))
                .hasElements();
    }

    /**
     * 检查IP是否在白名单中
     */
    private Mono<Boolean> checkWhitelist(String ipAddress, String tenantId) {
        return repository
                .findByTenantIdAndType(tenantId, IpAccessControl.AccessControlType.WHITELIST)
                .filter(IpAccessControl::isActive)
                .sort(Comparator.comparing(IpAccessControl::getPriority))
                .filter(rule -> rule.matches(ipAddress))
                .hasElements();
    }

    /**
     * 判断是否存在白名单规则
     */
    private Mono<Boolean> hasWhitelistRules(String tenantId) {
        return repository
                .findByTenantIdAndType(tenantId, IpAccessControl.AccessControlType.WHITELIST)
                .filter(IpAccessControl::isActive)
                .hasElements();
    }

    /**
     * 添加IP到租户白名单并记录审计日志
     *
     * @param ipAddress 要放行的IP地址
     * @param tenantId 租户ID
     * @param description 规则描述
     * @param operator 执行操作的运维人员标识
     * @return 新创建的白名单规则
     */
    @Override
    @CacheEvict(value = "ip-access-control", allEntries = true)
    public Mono<IpAccessControl> addToWhitelist(
            String ipAddress, String tenantId, String description, String operator) {
        IpAccessControl rule = IpAccessControl.builder()
                .tenantId(tenantId)
                .ipAddress(ipAddress)
                .type(IpAccessControl.AccessControlType.WHITELIST)
                .description(description)
                .enabled(true)
                .priority(100)
                .createdAt(LocalDateTime.now())
                .createdBy(operator)
                .build();

        return repository
                .save(rule)
                .flatMap(saved -> auditLogService
                        .log(operator, tenantId, "添加IP白名单: " + ipAddress, "IP_WHITELIST_ADD", "SUCCESS", null)
                        .thenReturn(saved))
                .doOnNext(saved -> log.info("添加IP白名单: ip={}, tenantId={}, operator={}", ipAddress, tenantId, operator));
    }

    /**
     * 添加IP到租户黑名单并记录审计日志，可指定过期时长
     *
     * @param ipAddress 要封禁的IP地址
     * @param tenantId 租户ID
     * @param description 规则描述
     * @param durationMinutes 封禁持续时长（分钟），为null表示永久封禁
     * @param operator 执行操作的运维人员标识
     * @return 新创建的黑名单规则
     */
    @Override
    @CacheEvict(value = "ip-access-control", allEntries = true)
    public Mono<IpAccessControl> addToBlacklist(
            String ipAddress, String tenantId, String description, Integer durationMinutes, String operator) {
        LocalDateTime expiresAt = durationMinutes != null ? LocalDateTime.now().plusMinutes(durationMinutes) : null;

        IpAccessControl rule = IpAccessControl.builder()
                .tenantId(tenantId)
                .ipAddress(ipAddress)
                .type(IpAccessControl.AccessControlType.BLACKLIST)
                .description(description)
                .enabled(true)
                .priority(100)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .createdBy(operator)
                .build();

        return repository
                .save(rule)
                .flatMap(saved -> auditLogService
                        .log(
                                operator,
                                tenantId,
                                "添加IP黑名单: "
                                        + ipAddress
                                        + (durationMinutes != null ? ", 持续时间: " + durationMinutes + "分钟" : ""),
                                "IP_BLACKLIST_ADD",
                                "SUCCESS",
                                null)
                        .thenReturn(saved))
                .doOnNext(saved -> log.warn(
                        "添加IP黑名单: ip={}, tenantId={}, duration={}, operator={}",
                        ipAddress,
                        tenantId,
                        durationMinutes,
                        operator));
    }

    /**
     * 从租户白名单中移除指定IP的全部规则
     *
     * @param ipAddress 要移除的IP地址
     * @param tenantId 租户ID
     * @return 完成信号
     */
    @Override
    @CacheEvict(value = "ip-access-control", allEntries = true)
    public Mono<Void> removeFromWhitelist(String ipAddress, String tenantId) {
        return repository
                .deleteByIpAddressAndTenantIdAndType(ipAddress, tenantId, IpAccessControl.AccessControlType.WHITELIST)
                .doOnSuccess(count -> log.info("从白名单移除IP: ip={}, tenantId={}, count={}", ipAddress, tenantId, count))
                .then();
    }

    /**
     * 从租户黑名单中移除指定IP的全部规则
     *
     * @param ipAddress 要解除封禁的IP地址
     * @param tenantId 租户ID
     * @return 完成信号
     */
    @Override
    @CacheEvict(value = "ip-access-control", allEntries = true)
    public Mono<Void> removeFromBlacklist(String ipAddress, String tenantId) {
        return repository
                .deleteByIpAddressAndTenantIdAndType(ipAddress, tenantId, IpAccessControl.AccessControlType.BLACKLIST)
                .doOnSuccess(count -> log.info("从黑名单移除IP: ip={}, tenantId={}, count={}", ipAddress, tenantId, count))
                .then();
    }

    /**
     * 查询租户的白名单规则，按优先级升序排列
     *
     * @param tenantId 租户ID
     * @return 白名单规则流
     */
    @Override
    public Flux<IpAccessControl> getWhitelist(String tenantId) {
        return repository
                .findByTenantIdAndType(tenantId, IpAccessControl.AccessControlType.WHITELIST)
                .sort(Comparator.comparing(IpAccessControl::getPriority));
    }

    /**
     * 查询租户的黑名单规则，按优先级升序排列
     *
     * @param tenantId 租户ID
     * @return 黑名单规则流
     */
    @Override
    public Flux<IpAccessControl> getBlacklist(String tenantId) {
        return repository
                .findByTenantIdAndType(tenantId, IpAccessControl.AccessControlType.BLACKLIST)
                .sort(Comparator.comparing(IpAccessControl::getPriority));
    }

    /**
     * 清理已过有效期的IP访问控制规则
     *
     * @return 删除的过期规则数量
     */
    @Override
    @CacheEvict(value = "ip-access-control", allEntries = true)
    public Mono<Long> cleanupExpiredRules() {
        return repository
                .deleteExpired(LocalDateTime.now())
                .doOnSuccess(count -> log.info("清理过期IP访问规则: count={}", count));
    }

    /**
     * 启用指定的IP访问控制规则
     *
     * @param ruleId 规则ID
     * @param operator 执行操作的运维人员标识
     * @return 完成信号
     */
    @Override
    @CacheEvict(value = "ip-access-control", allEntries = true)
    public Mono<Void> enableRule(Long ruleId, String tenantId, String operator) {
        return repository
                .findByIdAndTenantId(ruleId, tenantId)
                .flatMap(rule -> {
                    rule.setEnabled(true);
                    rule.setUpdatedAt(LocalDateTime.now());
                    rule.setUpdatedBy(operator);
                    return repository.save(rule);
                })
                .doOnSuccess(rule -> log.info("启用IP访问规则: ruleId={}, operator={}", ruleId, operator))
                .then();
    }

    /**
     * 禁用指定的IP访问控制规则
     *
     * @param ruleId 规则ID
     * @param operator 执行操作的运维人员标识
     * @return 完成信号
     */
    @Override
    @CacheEvict(value = "ip-access-control", allEntries = true)
    public Mono<Void> disableRule(Long ruleId, String tenantId, String operator) {
        return repository
                .findByIdAndTenantId(ruleId, tenantId)
                .flatMap(rule -> {
                    rule.setEnabled(false);
                    rule.setUpdatedAt(LocalDateTime.now());
                    rule.setUpdatedBy(operator);
                    return repository.save(rule);
                })
                .doOnSuccess(rule -> log.info("禁用IP访问规则: ruleId={}, operator={}", ruleId, operator))
                .then();
    }

    /**
     * 删除指定的IP访问控制规则并记录审计日志
     *
     * @param ruleId 规则ID
     * @param operator 执行操作的运维人员标识
     * @return 完成信号
     */
    @Override
    @CacheEvict(value = "ip-access-control", allEntries = true)
    public Mono<Void> deleteRule(Long ruleId, String tenantId, String operator) {
        return repository
                .findByIdAndTenantId(ruleId, tenantId)
                .flatMap(rule -> repository
                        .deleteByIdAndTenantId(ruleId, tenantId)
                        .then(auditLogService.log(
                                operator,
                                rule.getTenantId(),
                                "删除IP访问规则: " + rule.getIpAddress(),
                                "IP_RULE_DELETE",
                                "SUCCESS",
                                null)))
                .doOnSuccess(v -> log.info("删除IP访问规则: ruleId={}, operator={}", ruleId, operator));
    }

    /**
     * 批量导入IP访问控制规则，自动补充创建时间和创建人
     *
     * @param rules 待导入的规则流
     * @param operator 执行操作的运维人员标识
     * @return 成功导入的规则数量
     */
    @Override
    @CacheEvict(value = "ip-access-control", allEntries = true)
    public Mono<Long> batchImportRules(Flux<IpAccessControl> rules, String operator) {
        return rules.map(rule -> {
                    rule.setCreatedAt(LocalDateTime.now());
                    rule.setCreatedBy(operator);
                    return rule;
                })
                .flatMap(repository::save)
                .count()
                .doOnSuccess(count -> log.info("批量导入IP访问规则: count={}, operator={}", count, operator));
    }
}
