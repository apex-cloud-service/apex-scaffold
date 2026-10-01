package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.SysSsoSessionAssembler;
import com.apex.scaffold.application.dto.admin.SsoSessionDTO;
import com.apex.scaffold.application.dto.admin.SsoStatisticsDTO;
import com.apex.scaffold.application.support.sso.SsoProtocolAdapterFactory;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.SysSsoProviderEntity;
import com.apex.scaffold.domain.model.entity.SysSsoSessionEntity;
import com.apex.scaffold.domain.model.sso.SsoSessionStatus;
import com.apex.scaffold.domain.repository.SysSsoProviderRepository;
import com.apex.scaffold.domain.repository.SysSsoSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * SSO 会话管理服务
 *
 * <p>提供 SSO 单点登录会话的管理功能,包括会话查询、会话撤销、过期会话清理和会话统计等。
 * 支持按提供商、用户、状态等条件查询会话,并提供会话撤销和批量撤销能力。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>会话查询:支持按提供商、用户、状态等条件分页查询会话列表</li>
 *   <li>会话撤销:撤销指定会话,调用协议适配器执行 SSO 登出</li>
 *   <li>批量撤销:撤销指定用户的所有活跃会话</li>
 *   <li>过期清理:定时清理过期会话,释放资源</li>
 *   <li>会话统计:提供会话数量、登录趋势等统计信息</li>
 * </ul>
 *
 * <h2>租户隔离</h2>
 * <ul>
 *   <li>所有操作自动关联当前租户上下文</li>
 *   <li>租户间会话数据完全隔离</li>
 *   <li>统计数据按租户独立计算</li>
 * </ul>
 *
 * <h2>会话状态</h2>
 * <ul>
 *   <li>ACTIVE - 活跃会话</li>
 *   <li>EXPIRED - 已过期</li>
 *   <li>REVOKED - 已撤销</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysSsoSessionService {

    private final SysSsoSessionRepository ssoSessionRepository;
    private final SysSsoProviderRepository ssoProviderRepository;
    private final SysSsoSessionAssembler ssoSessionAssembler;
    private final SsoProtocolAdapterFactory ssoProtocolAdapterFactory;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询 SSO 会话列表
     *
     * <p>支持按提供商、用户、状态等条件查询会话列表,结果自动按租户隔离。
     * 查询结果按登录时间倒序排列。
     *
     * @param providerId SSO 提供商 ID(可选)
     * @param userId 用户 ID(可选)
     * @param status 会话状态(可选,ACTIVE/EXPIRED/REVOKED)
     * @param page 页码(从 0 开始)
     * @param size 每页大小
     * @return 会话 DTO 列表
     * @throws BusinessException 当租户上下文未初始化时
     */
    public Mono<List<SsoSessionDTO>> list(Long providerId, Long userId, String status, int page, int size) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.debug(
                "分页查询 SSO 会话列表: providerId={}, userId={}, status={}, page={}, size={}, tenantId={}",
                providerId,
                userId,
                status,
                page,
                size,
                tenantId);

        return ssoSessionRepository
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
                    if (status != null && !status.trim().isEmpty()) {
                        SsoSessionStatus sessionStatus = SsoSessionStatus.valueOf(status.toUpperCase());
                        entities = entities.stream()
                                .filter(e -> sessionStatus.equals(e.getStatus()))
                                .toList();
                    }

                    entities.sort((a, b) -> b.getLoginTime().compareTo(a.getLoginTime()));

                    int start = Math.min(page * size, entities.size());
                    int end = Math.min(start + size, entities.size());
                    return entities.subList(start, end);
                })
                .map(ssoSessionAssembler::toDTOList);
    }

    /**
     * 撤销指定 SSO 会话
     *
     * <p>撤销指定会话并调用协议适配器执行 SSO 提供商的登出操作。
     * 会话状态更新为 REVOKED,同时记录撤销时间。
     *
     * @param sessionId 会话 ID
     * @return 撤销是否成功
     * @throws BusinessException 当会话不存在、无权操作或撤销失败时
     */
    public Mono<Boolean> revokeSession(Long sessionId) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.info("撤销 SSO 会话: sessionId={}, tenantId={}", sessionId, tenantId);

        return ssoSessionRepository
                .findById(sessionId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "SSO 会话不存在")))
                .flatMap(session -> {
                    if (!tenantId.equals(session.getTenantId())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "无权操作其他租户的 SSO 会话"));
                    }

                    if (SsoSessionStatus.REVOKED.equals(session.getStatus())) {
                        log.warn("SSO 会话已被撤销,跳过: sessionId={}", sessionId);
                        return Mono.just(true);
                    }

                    return ssoProviderRepository
                            .findById(session.getProviderId())
                            .flatMap(provider -> {
                                try {
                                    Map<String, Object> config = parseConfig(provider.getConfig());
                                    String protocolType =
                                            provider.getProviderType().name();
                                    String sessionToken = session.getExternalId();

                                    return ssoProtocolAdapterFactory
                                            .getAdapter(protocolType)
                                            .logout(config, sessionToken)
                                            .flatMap(result -> {
                                                session.setStatus(SsoSessionStatus.LOGOUT);
                                                session.setLogoutTime(LocalDateTime.now());
                                                return ssoSessionRepository
                                                        .updateById(session)
                                                        .thenReturn(true);
                                            });
                                } catch (Exception e) {
                                    log.error("撤销 SSO 会话失败: sessionId={}", sessionId, e);
                                    return Mono.error(
                                            new BusinessException(ResultCode.ERROR, "撤销会话失败: " + e.getMessage()));
                                }
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                log.warn("SSO 提供商不存在,直接更新会话状态: providerId={}", session.getProviderId());
                                session.setStatus(SsoSessionStatus.LOGOUT);
                                session.setLogoutTime(LocalDateTime.now());
                                return ssoSessionRepository.updateById(session).thenReturn(true);
                            }));
                });
    }

    /**
     * 撤销指定用户的所有活跃 SSO 会话
     *
     * <p>批量撤销指定用户的所有活跃会话,用于强制用户下线或安全管理。
     * 对每个会话调用协议适配器执行登出,更新会话状态为 REVOKED。
     *
     * @param userId 用户 ID
     * @return 撤销的会话数量
     * @throws BusinessException 当租户上下文未初始化时
     */
    public Mono<Integer> revokeUserSessions(Long userId) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.info("撤销用户所有 SSO 会话: userId={}, tenantId={}", userId, tenantId);

        return ssoSessionRepository
                .findByUserId(userId)
                .filter(session -> tenantId.equals(session.getTenantId()))
                .filter(session -> session.getStatus() != SsoSessionStatus.LOGOUT
                        && session.getStatus() != SsoSessionStatus.EXPIRED)
                .flatMap(session -> revokeSession(session.getId()).onErrorResume(e -> {
                    log.error("撤销会话失败,继续处理其他会话: sessionId={}", session.getId(), e);
                    return Mono.just(false);
                }))
                .filter(success -> success)
                .count()
                .map(Long::intValue);
    }

    /**
     * 清理过期的 SSO 会话
     *
     * <p>定时任务调用,清理过期时间早于指定时间的会话。
     * 过期会话状态更新为 EXPIRED,不执行 SSO 登出操作。
     *
     * @return 清理的会话数量
     * @throws BusinessException 当租户上下文未初始化时
     */
    public Mono<Integer> cleanExpiredSessions() {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        LocalDateTime now = LocalDateTime.now();
        log.info("清理过期 SSO 会话: tenantId={}, beforeTime={}", tenantId, now);

        return ssoSessionRepository
                .findExpiredSessions(now)
                .filter(session -> tenantId.equals(session.getTenantId()))
                .filter(session -> SsoSessionStatus.ACTIVE.equals(session.getStatus()))
                .flatMap(session -> {
                    session.setStatus(SsoSessionStatus.EXPIRED);
                    return ssoSessionRepository.updateById(session).thenReturn(1);
                })
                .reduce(0, Integer::sum);
    }

    /**
     * 获取 SSO 会话统计信息
     *
     * <p>统计当前租户下的会话数据,包括活跃会话数、近7天登录趋势等。
     * 结果按租户隔离,只返回当前租户的统计数据。
     *
     * @return 会话统计 DTO
     * @throws BusinessException 当租户上下文未初始化时
     */
    public Mono<SsoStatisticsDTO> getStatistics() {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.debug("获取 SSO 会话统计信息: tenantId={}", tenantId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime sevenDaysAgo = now.minusDays(7);

        return ssoSessionRepository
                .findByTenantId(tenantId)
                .collectList()
                .zipWith(ssoProviderRepository.findByTenantId(tenantId).collectList())
                .map(tuple -> {
                    List<SysSsoSessionEntity> sessions = tuple.getT1();
                    List<SysSsoProviderEntity> providers = tuple.getT2();

                    long activeSessionCount = sessions.stream()
                            .filter(s -> SsoSessionStatus.ACTIVE.equals(s.getStatus()))
                            .count();

                    long todayNewSessionCount = sessions.stream()
                            .filter(s ->
                                    s.getLoginTime() != null && s.getLoginTime().isAfter(todayStart))
                            .count();

                    long todayRevokedSessionCount = sessions.stream()
                            .filter(s -> SsoSessionStatus.REVOKED.equals(s.getStatus()))
                            .filter(s -> s.getUpdateTime() != null
                                    && s.getUpdateTime().isAfter(todayStart))
                            .count();

                    long enabledProviderCount = providers.stream()
                            .filter(p -> p.getEnabled() != null && p.getEnabled() == 1)
                            .count();

                    Map<Long, Long> providerUsageMap = sessions.stream()
                            .filter(s ->
                                    s.getLoginTime() != null && s.getLoginTime().isAfter(sevenDaysAgo))
                            .collect(java.util.stream.Collectors.groupingBy(
                                    SysSsoSessionEntity::getProviderId, java.util.stream.Collectors.counting()));

                    Long mostUsedProviderId = providerUsageMap.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse(null);

                    String mostUsedProviderName = null;
                    Long mostUsedProviderCount = 0L;
                    if (mostUsedProviderId != null) {
                        mostUsedProviderName = providers.stream()
                                .filter(p -> mostUsedProviderId.equals(p.getId()))
                                .findFirst()
                                .map(SysSsoProviderEntity::getProviderName)
                                .orElse(null);
                        mostUsedProviderCount = providerUsageMap.get(mostUsedProviderId);
                    }

                    return SsoStatisticsDTO.builder()
                            .activeSessionCount(activeSessionCount)
                            .todayNewSessionCount(todayNewSessionCount)
                            .todayRevokedSessionCount(todayRevokedSessionCount)
                            .todayLoginCount(todayNewSessionCount)
                            .todayLoginSuccessCount(todayNewSessionCount)
                            .todayLoginFailureCount(0L)
                            .todayLoginSuccessRate(100.0)
                            .todayAvgLoginDuration(0L)
                            .todayMaxLoginDuration(0L)
                            .enabledProviderCount((int) enabledProviderCount)
                            .totalProviderCount(providers.size())
                            .mostUsedProviderName(mostUsedProviderName)
                            .mostUsedProviderCount(mostUsedProviderCount)
                            .build();
                });
    }

    /**
     * 解析 JSON 配置字符串为 Map
     *
     * @param configJson JSON 配置字符串
     * @return 配置 Map
     * @throws BusinessException 当 JSON 格式错误时
     */
    private Map<String, Object> parseConfig(String configJson) {
        try {
            if (configJson == null || configJson.trim().isEmpty()) {
                return new HashMap<>(16);
            }
            return objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("解析 JSON 配置失败: config={}", configJson, e);
            throw new BusinessException(ResultCode.PARAM_ERROR, "配置格式错误,请检查 JSON 格式");
        }
    }

    /**
     * 处理 SSO 认证成功
     *
     * <p>在 SSO 认证成功后创建会话记录,记录用户登录信息。
     * 会话记录包含提供商信息、用户信息、登录时间、过期时间等。
     *
     * @param providerId SSO 提供商 ID
     * @param userInfo 用户信息 Map (从协议适配器返回)
     * @param request HTTP 请求对象
     * @return 包含 token 和用户信息的 Map
     * @throws BusinessException 当租户上下文未初始化或创建会话失败时
     */
    public Mono<Map<String, Object>> handleSuccessfulAuth(
            Long providerId, Map<String, Object> userInfo, Object request) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.info("处理 SSO 认证成功: providerId={}, tenantId={}", providerId, tenantId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryTime = now.plusHours(8);

        SysSsoSessionEntity sessionEntity = new SysSsoSessionEntity();
        sessionEntity.setTenantId(tenantId);
        sessionEntity.setProviderId(providerId);
        sessionEntity.setUserId((Long) userInfo.getOrDefault("userId", 0L));
        sessionEntity.setExternalId((String) userInfo.getOrDefault("username", ""));
        sessionEntity.setSessionIndex("SESSION-" + System.currentTimeMillis());
        sessionEntity.setLoginTime(now);
        sessionEntity.setExpireTime(expiryTime);
        sessionEntity.setStatus(SsoSessionStatus.ACTIVE);

        return ssoSessionRepository
                .insert(sessionEntity)
                .map(count -> {
                    Map<String, Object> result = new HashMap<>(16);
                    result.put("sessionId", sessionEntity.getId());
                    result.put("token", "SSO-TOKEN-" + sessionEntity.getId());
                    result.put("userInfo", userInfo);
                    result.put("expiryTime", expiryTime);
                    return result;
                })
                .doOnSuccess(r -> log.info("SSO 会话创建成功: sessionId={}", r.get("sessionId")))
                .doOnError(e -> log.error("SSO 会话创建失败: providerId={}", providerId, e));
    }

    /**
     * 处理 SSO 登出
     *
     * <p>处理 SSO 登出请求,撤销当前用户的会话并可选择性地调用 SSO 提供商的登出端点。
     * 支持单点登出(SLO),确保用户在所有关联系统中都被登出。
     *
     * @param request HTTP 请求对象
     * @return 包含登出结果和重定向 URL 的 Map
     * @throws BusinessException 当租户上下文未初始化或登出失败时
     */
    public Mono<Map<String, Object>> handleLogout(Object request) {
        String tenantId = TenantContext.currentTenantId()
                .orElseThrow(() -> new BusinessException(ResultCode.FORBIDDEN, "租户上下文未初始化"));
        log.info("处理 SSO 登出: tenantId={}", tenantId);

        return ssoSessionRepository
                .findByTenantId(tenantId)
                .filter(session -> SsoSessionStatus.ACTIVE.equals(session.getStatus()))
                .collectList()
                .flatMap(sessions -> {
                    if (sessions.isEmpty()) {
                        Map<String, Object> result = new HashMap<>(16);
                        result.put("success", true);
                        result.put("message", "没有活跃会话需要登出");
                        return Mono.just(result);
                    }

                    return Mono.just(sessions)
                            .flatMapMany(list -> {
                                list.forEach(session -> {
                                    session.setStatus(SsoSessionStatus.REVOKED);
                                    session.setLogoutTime(LocalDateTime.now());
                                });
                                return reactor.core.publisher.Flux.fromIterable(list);
                            })
                            .flatMap(session -> ssoSessionRepository.updateById(session))
                            .collectList()
                            .map(counts -> {
                                int successCount = counts.stream()
                                        .mapToInt(Integer::intValue)
                                        .sum();
                                Map<String, Object> result = new HashMap<>(16);
                                result.put("success", true);
                                result.put("revokedCount", successCount);
                                result.put("message", "登出成功");
                                return result;
                            });
                })
                .doOnSuccess(r -> log.info("SSO 登出成功: {}", r))
                .doOnError(e -> log.error("SSO 登出失败: tenantId={}", tenantId, e));
    }
}
