package com.apex.scaffold.infrastructure.ratelimit.distributed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 分布式限流预热器
 *
 * <p>应用启动时自动预热所有配置的分布式限流规则，
 * 将令牌桶初始化到合适的状态，避免冷启动时的流量突增被误判为限流。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>将每个令牌桶预热到容量的 50%</li>
 *   <li>仅预热启用的规则</li>
 *   <li>预热失败不影响应用启动</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "apex.ratelimit.distributed", name = "enabled", havingValue = "true")
@Order(100)
public class DistributedRateLimitWarmer {

    private final DistributedRateLimitService rateLimitService;
    private final DistributedRateLimitProperties properties;

    /**
     * 应用启动完成后自动预热
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmupOnStartup() {
        if (!properties.isEnabled()) {
            log.info("分布式限流未启用，跳过预热");
            return;
        }

        if (!properties.hasEnabledRules()) {
            log.info("没有配置分布式限流规则，跳过预热");
            return;
        }

        log.info("开始预热分布式限流规则，总数: {}", properties.getRules().size());

        long startTime = System.currentTimeMillis();

        try {
            Long successCount = rateLimitService.warmupAll().block();
            long elapsed = System.currentTimeMillis() - startTime;

            log.info(
                    "分布式限流预热完成，成功: {}/{}, 耗时: {} ms",
                    successCount,
                    properties.getRules().size(),
                    elapsed);

            // 逐条打印启用规则的预热详情（预热令牌数为容量的一半）
            properties.getRules().forEach(rule -> {
                if (rule.isEnabled()) {
                    log.info(
                            "  ✓ {} - 容量: {}, 补充速率: {}/s, 预热: {} 令牌",
                            rule.getResource(),
                            rule.getCapacity(),
                            rule.getRefillRate(),
                            rule.getCapacity() / 2);
                }
            });

        } catch (Exception e) {
            log.error("分布式限流预热失败（不影响应用启动）", e);
        }
    }
}
