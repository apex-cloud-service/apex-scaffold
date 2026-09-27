package com.apex.scaffold.authcenter.infrastructure.scheduler;

import com.apex.scaffold.authcenter.infrastructure.crypto.JwtKeyRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * JWT密钥自动轮换调度器
 *
 * <p>定期检查JWT密钥是否需要轮换，如需要则自动执行轮换。
 *
 * <h2>调度策略</h2>
 * <ul>
 *   <li>每天凌晨3点检查一次</li>
 *   <li>检测到密钥接近过期时自动轮换</li>
 *   <li>轮换失败会记录错误但不影响系统运行</li>
 * </ul>
 *
 * @author Apex Security Team
 * @version 1.0.0
 * @since 2026-09-25
 */
@Slf4j
@RequiredArgsConstructor
public class JwtKeyRotationScheduler {

    private final JwtKeyRotationService keyRotationService;

    /**
     * 定时检查并轮换JWT密钥
     *
     * <p>每天凌晨3点执行，避免业务高峰期
     */
    @Scheduled(cron = "${security.jwt.rotation.cron:0 0 3 * * ?}")
    public void checkAndRotateKey() {
        log.debug("开始检查JWT密钥是否需要轮换");

        keyRotationService
                .needsRotation()
                .flatMap(needsRotation -> {
                    if (needsRotation) {
                        log.info("检测到JWT密钥需要轮换，开始执行自动轮换");
                        return keyRotationService
                                .rotateKey()
                                .doOnSuccess(version -> log.info("JWT密钥自动轮换成功: newVersion={}", version))
                                .doOnError(e -> log.error("JWT密钥自动轮换失败", e))
                                .then();
                    } else {
                        log.debug("JWT密钥尚未到期，无需轮换");
                        return null;
                    }
                })
                .onErrorResume(e -> {
                    log.error("JWT密钥轮换检查失败", e);
                    return null;
                })
                .subscribe();
    }
}
