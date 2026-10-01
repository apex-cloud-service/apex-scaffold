package com.apex.scaffold.authcenter.application.scheduler;

import com.apex.scaffold.authcenter.application.service.JwtKeyRotationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * JWT密钥轮换定时任务
 *
 * <p>定期检查密钥状态并执行以下维护操作：
 * <ul>
 *   <li>检查即将过期的密钥（30天内），发送告警通知</li>
 *   <li>废弃过期的ROTATING状态密钥（超过24小时）</li>
 *   <li>清理已吊销且超过保留期的密钥（365天）</li>
 * </ul>
 *
 * <h2>执行频率</h2>
 * <ul>
 *   <li>密钥过期检查：每天凌晨2点执行</li>
 *   <li>ROTATING密钥废弃：每小时执行一次</li>
 *   <li>已吊销密钥清理：每周日凌晨3点执行</li>
 * </ul>
 *
 * @author Apex Team
 * @since 2026-09-25
 */
@Component
@RequiredArgsConstructor
public class JwtKeyRotationScheduler {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyRotationScheduler.class);

    private final JwtKeyRotationService jwtKeyRotationService;

    /**
     * 检查即将过期的密钥
     *
     * <p>每天凌晨2点执行，检查30天内即将过期的ACTIVE密钥，
     * 记录告警日志并通知运维团队准备密钥轮换。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void checkExpiringSoonKeys() {
        log.info("开始执行JWT密钥过期检查任务");

        jwtKeyRotationService
                .findExpiringSoonKeys()
                .doOnNext(key -> {
                    long daysUntilExpiry = java.time.temporal.ChronoUnit.DAYS.between(
                            java.time.LocalDateTime.now(), key.getExpiresAt());
                    log.warn(
                            "告警：JWT密钥即将过期！版本={}, 剩余天数={}, 过期时间={}",
                            key.getKeyVersion(),
                            daysUntilExpiry,
                            key.getExpiresAt());

                    // TODO: 集成告警系统，发送邮件/短信/钉钉通知
                    // alertService.sendKeyExpiryAlert(key.getKeyVersion(), daysUntilExpiry);
                })
                .count()
                .subscribe(
                        count -> {
                            if (count > 0) {
                                log.warn("发现{}个即将过期的JWT密钥，请尽快安排密钥轮换", count);
                            } else {
                                log.info("JWT密钥状态正常，没有即将过期的密钥");
                            }
                        },
                        error -> log.error("JWT密钥过期检查任务执行失败", error));
    }

    /**
     * 废弃过期的ROTATING状态密钥
     *
     * <p>每小时执行一次，将ROTATING状态超过24小时的密钥设置为DEPRECATED。
     * DEPRECATED密钥不再用于Token验证，仅保留用于审计和历史查询。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void deprecateExpiredRotatingKeys() {
        log.info("开始执行ROTATING密钥废弃任务");

        jwtKeyRotationService
                .deprecateExpiredRotatingKeys()
                .subscribe(
                        count -> {
                            if (count > 0) {
                                log.info("已废弃{}个过期的ROTATING密钥", count);
                            } else {
                                log.debug("没有需要废弃的ROTATING密钥");
                            }
                        },
                        error -> log.error("ROTATING密钥废弃任务执行失败", error));
    }

    /**
     * 清理已吊销的过期密钥
     *
     * <p>每周日凌晨3点执行，删除已吊销超过365天的密钥记录。
     * 保留1年的历史记录用于安全审计和事件追溯。
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void cleanupRevokedKeys() {
        log.info("开始执行已吊销密钥清理任务");

        jwtKeyRotationService
                .cleanupRevokedKeys()
                .subscribe(
                        count -> {
                            if (count > 0) {
                                log.info("已清理{}个超过保留期的已吊销密钥", count);
                            } else {
                                log.info("没有需要清理的已吊销密钥");
                            }
                        },
                        error -> log.error("已吊销密钥清理任务执行失败", error));
    }

    /**
     * 自动密钥轮换（可选，默认禁用）
     *
     * <p>每90天自动执行一次密钥轮换（每季度）。
     * 生产环境建议通过监控告警提醒人工审核后手动执行轮换。
     *
     * <p><b>注意</b>：此任务默认注释掉，需要启用自动轮换时取消注释。
     */
    public void autoRotateKey() {
        log.info("开始执行JWT密钥自动轮换任务");

        jwtKeyRotationService
                .rotateKey("SCHEDULED", 0L, "SYSTEM_SCHEDULER")
                .subscribe(
                        newKey -> log.info("JWT密钥自动轮换成功，新版本: {}", newKey.getKeyVersion()),
                        error -> log.error("JWT密钥自动轮换失败", error));
    }
}
