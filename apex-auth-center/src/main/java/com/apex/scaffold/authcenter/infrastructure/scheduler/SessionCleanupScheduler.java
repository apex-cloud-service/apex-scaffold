package com.apex.scaffold.authcenter.infrastructure.scheduler;

import com.apex.scaffold.authcenter.domain.repository.SessionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 会话清理定时任务
 *
 * <p>定期清理过期会话，释放存储空间并维护会话表数据质量。
 *
 * <p>执行策略：
 * <ul>
 *   <li>执行频率：每小时执行一次（可配置）</li>
 *   <li>清理范围：expiresAt < 当前时间的会话记录</li>
 *   <li>批量删除：避免大事务，使用响应式批处理</li>
 *   <li>日志记录：记录每次清理的会话数量和耗时</li>
 * </ul>
 *
 * <p>配置方式：
 * <pre>
 * auth.session.cleanup:
 *   enabled: true
 *   cron: "0 0 * * * ?"  # 每小时整点执行
 * </pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCleanupScheduler {

    private final SessionRepository sessionRepository;

    /**
     * 定时清理过期会话
     *
     * <p>扫描并删除所有已过期的会话记录。
     *
     * <p>执行流程：
     * <ol>
     *   <li>查询所有 expiresAt < 当前时间的会话</li>
     *   <li>批量删除过期会话</li>
     *   <li>记录清理数量和耗时</li>
     *   <li>异常情况下记录错误日志但不中断调度</li>
     * </ol>
     */
    @Scheduled(cron = "${auth.session.cleanup.cron:0 0 * * * ?}")
    public void cleanupExpiredSessions() {
        log.info("开始执行会话清理任务");
        long startTime = System.currentTimeMillis();

        sessionRepository
                .findExpiredSessions()
                .flatMap(session -> {
                    log.debug(
                            "清理过期会话: sessionId={}, userId={}, expiresAt={}",
                            session.getSessionId(),
                            session.getUserId(),
                            session.getExpiresAt());
                    return sessionRepository.deleteById(session.getId());
                })
                .count()
                .doOnSuccess(count -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("会话清理任务完成: 清理{}条记录, 耗时{}ms", count, duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("会话清理任务失败: 耗时{}ms", duration, error);
                })
                .onErrorResume(error -> {
                    return Mono.empty();
                })
                .subscribe();
    }

    /**
     * 定时清理孤立会话（可选任务）
     *
     * <p>清理创建超过7天但未正常过期的异常会话。
     *
     * <p>执行频率：每天凌晨3点执行
     */
    @Scheduled(cron = "${auth.session.cleanup.orphan.cron:0 0 3 * * ?}")
    public void cleanupOrphanSessions() {
        log.info("开始执行孤立会话清理任务");
        long startTime = System.currentTimeMillis();
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);

        sessionRepository
                .findAll()
                .filter(session -> session.getCreatedAt() != null
                        && session.getCreatedAt().isBefore(threshold)
                        && (session.getExpiresAt() == null
                                || session.getExpiresAt().isBefore(LocalDateTime.now())))
                .flatMap(session -> {
                    log.debug(
                            "清理孤立会话: sessionId={}, userId={}, createdAt={}",
                            session.getSessionId(),
                            session.getUserId(),
                            session.getCreatedAt());
                    return sessionRepository.deleteById(session.getId());
                })
                .count()
                .doOnSuccess(count -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("孤立会话清理任务完成: 清理{}条记录, 耗时{}ms", count, duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("孤立会话清理任务失败: 耗时{}ms", duration, error);
                })
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }

    /**
     * 定时清理长时间未活跃会话（可选任务）
     *
     * <p>清理超过24小时未活跃的会话，防止会话泄露。
     *
     * <p>执行频率：每6小时执行一次
     */
    @Scheduled(cron = "${auth.session.cleanup.inactive.cron:0 0 */6 * * ?}")
    public void cleanupInactiveSessions() {
        log.info("开始执行未活跃会话清理任务");
        long startTime = System.currentTimeMillis();
        LocalDateTime inactiveThreshold = LocalDateTime.now().minusHours(24);

        sessionRepository
                .findAll()
                .filter(session -> session.getLastActiveAt() != null
                        && session.getLastActiveAt().isBefore(inactiveThreshold))
                .flatMap(session -> {
                    log.debug(
                            "清理未活跃会话: sessionId={}, userId={}, lastActiveAt={}",
                            session.getSessionId(),
                            session.getUserId(),
                            session.getLastActiveAt());
                    return sessionRepository.deleteById(session.getId());
                })
                .count()
                .doOnSuccess(count -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("未活跃会话清理任务完成: 清理{}条记录, 耗时{}ms", count, duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("未活跃会话清理任务失败: 耗时{}ms", duration, error);
                })
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }
}
