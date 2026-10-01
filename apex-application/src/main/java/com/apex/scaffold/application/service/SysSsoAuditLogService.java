package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.SysSsoAuditLogAssembler;
import com.apex.scaffold.application.dto.admin.SsoAuditLogDTO;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.SysSsoAuditLogEntity;
import com.apex.scaffold.domain.repository.SysSsoAuditLogRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * SSO 审计日志服务
 *
 * <p>提供 SSO 单点登录相关操作的审计日志查询功能,记录所有 SSO 认证、会话管理等操作的详细信息。
 * 包含操作类型、操作结果、失败原因等,用于安全审计和问题排查。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>日志查询:支持按提供商、用户、操作类型、时间范围等条件分页查询审计日志</li>
 *   <li>日志创建:记录 SSO 相关操作的审计日志(内部方法,由认证流程调用)</li>
 *   <li>统计分析:获取指定提供商的统计数据,包括成功率和失败趋势</li>
 * </ul>
 *
 * <h2>租户隔离</h2>
 * <ul>
 *   <li>所有操作自动关联当前租户上下文</li>
 *   <li>租户间审计日志完全隔离</li>
 *   <li>统计数据按租户独立计算</li>
 * </ul>
 *
 * <h2>审计范围</h2>
 * <ul>
 *   <li>LOGIN - SSO 登录</li>
 *   <li>LOGOUT - SSO 登出</li>
 *   <li>REVOKE - 会话撤销</li>
 *   <li>TEST_CONNECTION - 连接测试</li>
 *   <li>CONFIG_UPDATE - 配置更新</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysSsoAuditLogService {

    private final SysSsoAuditLogRepository ssoAuditLogRepository;
    private final SysSsoAuditLogAssembler ssoAuditLogAssembler;

    /**
     * 分页查询 SSO 审计日志
     *
     * <p>支持按提供商、用户、操作类型、时间范围等条件查询审计日志,结果自动按租户隔离。
     * 查询结果按创建时间倒序排列。
     *
     * @param providerId SSO 提供商 ID(可选)
     * @param userId 用户 ID(可选)
     * @param action 操作类型(可选,LOGIN/LOGOUT/REVOKE/TEST_CONNECTION/CONFIG_UPDATE)
     * @param startTime 开始时间(可选)
     * @param endTime 结束时间(可选)
     * @param page 页码(从 0 开始)
     * @param size 每页大小
     * @return 审计日志 DTO 列表
     * @throws BusinessException 当租户上下文未初始化时
     */
    public Mono<List<SsoAuditLogDTO>> list(
            Long providerId,
            Long userId,
            String action,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int page,
            int size) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.debug(
                "分页查询 SSO 审计日志: providerId={}, userId={}, action={}, startTime={}, endTime={}, page={}, size={}, tenantId={}",
                providerId,
                userId,
                action,
                startTime,
                endTime,
                page,
                size,
                tenantId);

        return ssoAuditLogRepository
                .findByTenantId(tenantId)
                .collectList()
                .map(entities -> {
                    if (providerId != null) {
                        entities = entities.stream()
                                .filter(e -> providerId.equals(e.getProviderId()))
                                .toList();
                    }
                    if (userId != null) {
                        entities = entities.stream()
                                .filter(e -> userId.equals(e.getUserId()))
                                .toList();
                    }
                    if (action != null && !action.trim().isEmpty()) {
                        entities = entities.stream()
                                .filter(e -> action.equalsIgnoreCase(e.getOperation()))
                                .toList();
                    }
                    if (startTime != null) {
                        entities = entities.stream()
                                .filter(e -> e.getCreateTime() != null
                                        && !e.getCreateTime().isBefore(startTime))
                                .toList();
                    }
                    if (endTime != null) {
                        entities = entities.stream()
                                .filter(e -> e.getCreateTime() != null
                                        && !e.getCreateTime().isAfter(endTime))
                                .toList();
                    }

                    entities.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));

                    int start = Math.min(page * size, entities.size());
                    int end = Math.min(start + size, entities.size());
                    return entities.subList(start, end);
                })
                .map(ssoAuditLogAssembler::toDTOList);
    }

    /**
     * 创建 SSO 审计日志
     *
     * <p>记录 SSO 相关操作的审计日志,包含操作类型、操作结果、失败原因、执行耗时等信息。
     * 此方法为内部方法,由认证流程、会话管理等模块调用。
     *
     * @param entity 审计日志实体
     * @return 创建是否成功
     * @throws BusinessException 当租户上下文未初始化时
     */
    public Mono<Boolean> create(SysSsoAuditLogEntity entity) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.debug(
                "创建 SSO 审计日志: operation={}, status={}, tenantId={}",
                entity.getOperation(),
                entity.getStatus(),
                tenantId);

        entity.setTenantId(tenantId);
        return ssoAuditLogRepository.insert(entity).map(count -> count > 0);
    }

    /**
     * 获取指定提供商的统计数据
     *
     * <p>统计指定提供商在最近指定天数内的操作成功率和失败趋势。
     * 返回数据包括总操作次数、成功次数、失败次数、成功率等。
     *
     * @param providerId 提供商 ID
     * @param days 统计天数
     * @return 统计数据 Map
     * @throws BusinessException 当租户上下文未初始化时
     */
    public Mono<Map<String, Object>> getStatisticsByProvider(Long providerId, int days) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.debug("获取 SSO 提供商统计数据: providerId={}, days={}, tenantId={}", providerId, days, tenantId);

        LocalDateTime startTime = LocalDateTime.now().minusDays(days);

        return ssoAuditLogRepository
                .findByProviderId(providerId)
                .filter(log -> log.getTenantId().equals(tenantId))
                .filter(log ->
                        log.getCreateTime() != null && log.getCreateTime().isAfter(startTime))
                .collectList()
                .map(logs -> {
                    long totalCount = logs.size();
                    long successCount = logs.stream()
                            .filter(log -> "SUCCESS".equalsIgnoreCase(log.getStatus()))
                            .count();
                    long failureCount = totalCount - successCount;

                    double successRate = totalCount > 0 ? (successCount * 100.0 / totalCount) : 0.0;

                    Map<String, Long> actionCounts = logs.stream()
                            .collect(java.util.stream.Collectors.groupingBy(
                                    SysSsoAuditLogEntity::getOperation, java.util.stream.Collectors.counting()));

                    Map<String, Object> result = new HashMap<>(16);
                    result.put("providerId", providerId);
                    result.put("days", days);
                    result.put("totalCount", totalCount);
                    result.put("successCount", successCount);
                    result.put("failureCount", failureCount);
                    result.put("successRate", String.format("%.2f", successRate));
                    result.put("actionCounts", actionCounts);
                    result.put("startTime", startTime);
                    result.put("endTime", LocalDateTime.now());

                    return result;
                });
    }
}
