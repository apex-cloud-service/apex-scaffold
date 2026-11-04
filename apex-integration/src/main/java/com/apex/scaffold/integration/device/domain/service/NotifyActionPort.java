package com.apex.scaffold.integration.device.domain.service;

import reactor.core.publisher.Mono;

/**
 * 通知动作可选端口（方案 M5 / 批次 E3）。
 *
 * <p>规则引擎 NOTIFY 动作经本端口对接 D1 消息门面；本任务只定义端口不提供实现，
 * 规则引擎以 {@code ObjectProvider} 注入，端口缺失时记日志跳过该动作。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface NotifyActionPort {

    /**
     * 发送设备规则命中通知。
     *
     * @param tenantId  租户编码
     * @param ruleName  规则名
     * @param deviceKey 设备标识
     * @param content   通知内容（含命中指标摘要）
     */
    Mono<Void> notify(String tenantId, String ruleName, String deviceKey, String content);
}
