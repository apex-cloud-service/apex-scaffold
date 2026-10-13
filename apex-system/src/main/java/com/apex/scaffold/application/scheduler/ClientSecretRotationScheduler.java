package com.apex.scaffold.application.scheduler;

import com.apex.scaffold.application.service.OAuth2ClientSecretRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * OAuth2 客户端密钥轮换调度器。
 *
 * <p>基于 Spring {@code @Scheduled} 定时任务对 OAuth2 客户端密钥有效期进行每日巡检：
 * 每天 02:00（cron {@code 0 0 2 * * ?}）筛查密钥已过期的客户端并记录告警日志；
 * 每天 03:00（cron {@code 0 0 3 * * ?}）筛查密钥即将过期的客户端并触发到期通知。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientSecretRotationScheduler {

    private final OAuth2ClientSecretRotationService rotationService;

    /**
     * 每日 02:00 定时巡检密钥已过期的 OAuth2 客户端，发现后记录 ERROR 告警日志。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void checkExpiredSecrets() {
        log.info("Starting daily check for expired client secrets");

        rotationService
                .findClientsWithExpiredSecrets()
                .collectList()
                .flatMap(expiredClients -> {
                    if (!expiredClients.isEmpty()) {
                        log.error("Found {} clients with expired secrets: {}", expiredClients.size(), expiredClients);
                    }
                    return Mono.empty();
                })
                .subscribe(
                        unused -> log.info("Completed expired secrets check"),
                        error -> log.error("Error checking expired secrets", error));
    }

    /**
     * 每日 03:00 定时巡检密钥即将到期的 OAuth2 客户端，并逐个触发到期通知。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void checkExpiringSoonSecrets() {
        log.info("Starting daily check for expiring soon client secrets");

        rotationService
                .findClientsWithExpiringSoonSecrets()
                .flatMap(clientId ->
                        rotationService.notifyClientSecretExpiring(clientId).thenReturn(clientId))
                .collectList()
                .subscribe(
                        notifiedClients -> {
                            if (!notifiedClients.isEmpty()) {
                                log.warn(
                                        "Notified {} clients about expiring secrets: {}",
                                        notifiedClients.size(),
                                        notifiedClients);
                            }
                            log.info("Completed expiring soon secrets check");
                        },
                        error -> log.error("Error checking expiring soon secrets", error));
    }
}
