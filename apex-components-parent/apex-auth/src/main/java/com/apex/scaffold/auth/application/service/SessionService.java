package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.domain.model.entity.SessionEntity;
import com.apex.scaffold.auth.domain.repository.SessionRepository;
import com.apex.scaffold.auth.domain.repository.TokenRepository;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 会话服务
 *
 * <p>提供会话管理的核心业务逻辑，包括会话创建、查询、更新和终止。
 * 支持在线用户管理、并发登录控制和会话过期清理。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;

    /** 令牌仓储：会话终止/踢出时联动撤销关联访问令牌（统一认证收口 A4 安全加固）。 */
    private final TokenRepository tokenRepository;

    /**
     * 会话默认有效期（小时）
     */
    private static final int DEFAULT_SESSION_HOURS = 24;

    /**
     * 最大并发会话数（默认 5，部署可经 auth-center.session.max-concurrent 覆盖；下限 1）
     */
    @org.springframework.beans.factory.annotation.Value("${auth-center.session.max-concurrent:5}")
    private int maxConcurrentSessions = 5;

    /**
     * 创建会话
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param tokenId Token ID
     * @param deviceType 设备类型
     * @param deviceId 设备ID
     * @param ipAddress IP地址
     * @param userAgent User-Agent
     * @return 创建的会话实体
     */
    public Mono<SessionEntity> createSession(
            Long userId,
            String tenantId,
            Long tokenId,
            String deviceType,
            String deviceId,
            String ipAddress,
            String userAgent) {

        return checkConcurrentSessions(userId, tenantId)
                .then(Mono.defer(() -> {
                    SessionEntity session = SessionEntity.builder()
                            .sessionId(UUID.randomUUID().toString())
                            .userId(userId)
                            .tenantId(tenantId)
                            .tokenId(tokenId)
                            .deviceType(deviceType)
                            .deviceId(deviceId)
                            .ipAddress(ipAddress)
                            .userAgent(userAgent)
                            .status("ACTIVE")
                            .createdAt(LocalDateTime.now())
                            .lastActiveAt(LocalDateTime.now())
                            .expiresAt(LocalDateTime.now().plusHours(DEFAULT_SESSION_HOURS))
                            .deleted(false)
                            .build();

                    return sessionRepository.save(session);
                }))
                .doOnNext(s -> log.debug("会话创建成功: userId={}", userId));
    }

    /**
     * 检查并发会话数限制
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     */
    private Mono<Void> checkConcurrentSessions(Long userId, String tenantId) {
        return sessionRepository.findActiveByUserId(userId, tenantId).count().flatMap(count -> {
            int limit = Math.max(1, maxConcurrentSessions);
            if (count >= limit) {
                return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "超过最大并发会话数限制: " + limit));
            }
            return Mono.empty();
        });
    }

    /**
     * 根据会话ID获取会话
     *
     * @param sessionId 会话唯一标识
     * @return 会话实体
     */
    public Mono<SessionEntity> getSessionBySessionId(String sessionId) {
        return sessionRepository
                .findBySessionId(sessionId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "会话不存在")));
    }

    /**
     * 根据Token ID获取会话
     *
     * @param tokenId Token ID
     * @return 会话实体
     */
    public Mono<SessionEntity> getSessionByTokenId(Long tokenId) {
        return sessionRepository
                .findByTokenId(tokenId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "会话不存在")));
    }

    /**
     * 获取用户的所有会话
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 会话列表
     */
    public Flux<SessionEntity> getUserSessions(Long userId, String tenantId) {
        return sessionRepository.findByUserId(userId, tenantId);
    }

    /**
     * 获取用户的活跃会话
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 活跃会话列表
     */
    public Flux<SessionEntity> getActiveSessions(Long userId, String tenantId) {
        return sessionRepository.findActiveByUserId(userId, tenantId);
    }

    /**
     * 获取在线用户数量
     *
     * @param tenantId 租户ID
     * @return 在线用户数
     */
    public Mono<Long> getOnlineUserCount(String tenantId) {
        return sessionRepository
                .findByUserId(null, tenantId)
                .filter(SessionEntity::isActive)
                .map(SessionEntity::getUserId)
                .distinct()
                .count();
    }

    /**
     * 更新会话活跃时间
     *
     * @param sessionId 会话唯一标识
     */
    public Mono<Void> updateSessionActivity(String sessionId) {
        return sessionRepository
                .updateLastActive(sessionId)
                .flatMap(success -> {
                    if (!success) {
                        return Mono.error(new BusinessException(ResultCode.ERROR, "会话活跃时间更新失败"));
                    }
                    return Mono.<Void>empty();
                })
                .then();
    }

    /**
     * 终止会话
     *
     * <p>终止前联动撤销会话关联的访问令牌，旧令牌不再有效至自然过期。
     *
     * @param sessionId 会话唯一标识
     */
    public Mono<Void> terminateSession(String sessionId) {
        return sessionRepository
                .findBySessionId(sessionId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "会话不存在")))
                .flatMap(session -> revokeSessionToken(session, "SESSION_TERMINATED")
                        .then(sessionRepository.terminateBySessionId(sessionId)))
                .flatMap(success -> {
                    if (!success) {
                        return Mono.error(new BusinessException(ResultCode.ERROR, "会话终止失败"));
                    }
                    return Mono.<Void>empty();
                })
                .then()
                .doOnSuccess(v -> log.debug("会话终止成功"));
    }

    /**
     * 终止用户的所有会话
     *
     * <p>终止前联动撤销该用户在租户下的全部令牌（幂等，重复调用安全）。
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     */
    public Mono<Void> terminateUserSessions(Long userId, String tenantId) {
        return tokenRepository
                .revokeByUserId(userId, tenantId, "SESSION", "SESSION_TERMINATED")
                .then(sessionRepository.terminateByUserId(userId, tenantId))
                .flatMap(success -> {
                    if (!success) {
                        return Mono.error(new BusinessException(ResultCode.ERROR, "用户会话终止失败"));
                    }
                    return Mono.<Void>empty();
                })
                .doOnSuccess(v -> log.debug("用户所有会话终止成功: userId={}", userId));
    }

    /**
     * 踢出指定会话
     *
     * <p>踢出前联动撤销会话关联的访问令牌，被踢端的既有令牌立即失效。
     *
     * @param sessionId 会话唯一标识
     */
    public Mono<Void> kickOutSession(String sessionId) {
        return sessionRepository
                .findBySessionId(sessionId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "会话不存在")))
                .flatMap(session -> revokeSessionToken(session, "KICKED_OUT").then(Mono.defer(() -> {
                    session.kickOut();
                    return sessionRepository.update(session);
                })))
                .then()
                .doOnSuccess(v -> log.debug("会话踢出成功"));
    }

    /**
     * 撤销会话关联的访问令牌（按会话记录的令牌持久化 ID 定位，再按 jti 撤销）。
     *
     * <p>令牌记录缺失或已被清理时静默跳过（会话终止语义不受影响）。
     *
     * @param session 会话实体
     * @param reason  撤销原因
     */
    private Mono<Void> revokeSessionToken(SessionEntity session, String reason) {
        if (session.getTokenId() == null) {
            return Mono.empty();
        }
        return tokenRepository
                .findById(session.getTokenId())
                .flatMap(token -> token.getJti() == null
                        ? Mono.just(Boolean.FALSE)
                        : tokenRepository.revokeByJti(token.getJti(), "SESSION", reason))
                .then();
    }

    /**
     * 清理过期会话
     *
     * @return 清理的会话数量
     */
    public Mono<Long> cleanupExpiredSessions() {
        return sessionRepository.cleanupExpiredSessions().doOnSuccess(count -> log.info("过期会话清理完成: count={}", count));
    }

    /**
     * 验证会话有效性
     *
     * @param sessionId 会话唯一标识
     * @return 验证结果
     */
    public Mono<Boolean> validateSession(String sessionId) {
        return sessionRepository
                .findBySessionId(sessionId)
                .map(SessionEntity::isActive)
                .defaultIfEmpty(false);
    }

    /**
     * 获取所有在线用户
     *
     * @return 在线用户会话列表
     */
    public Flux<SessionEntity> getOnlineUsers() {
        return sessionRepository.findAll().filter(SessionEntity::isActive);
    }

    /**
     * 统计在线用户数
     *
     * @return 在线用户数
     */
    public Mono<Long> countOnlineUsers() {
        return sessionRepository
                .findAll()
                .filter(SessionEntity::isActive)
                .map(SessionEntity::getUserId)
                .distinct()
                .count();
    }

    /**
     * 根据ID获取会话
     *
     * @param id 会话ID
     * @return 会话实体
     */
    public Mono<SessionEntity> getSessionById(Long id) {
        return sessionRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "会话不存在")));
    }
}
