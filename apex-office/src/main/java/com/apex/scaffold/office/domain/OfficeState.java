package com.apex.scaffold.office.domain;

import java.time.Duration;
import reactor.core.publisher.Mono;

/**
 * 办公平台授权状态端口，提供租户范围内的限时签发与一次性消费。
 *
 * @author Apex Team
 * @since 1.2.0
 */
public interface OfficeState {
    /**
     * 签发绑定租户、平台及回调地址的随机状态，订阅时执行。
     * @param tenantId 服务端租户标识
     * @param platform 平台标识
     * @param redirectUri 回调地址，允许为空字符串
     * @param ttl 有效期，至少一毫秒
     * @return 状态值
     * @throws IllegalArgumentException 参数无效时通过响应式错误信号返回
     */
    Mono<String> issue(String tenantId, String platform, String redirectUri, Duration ttl);

    /**
     * 原子校验并消费状态；不匹配时保留状态，过期或已消费时拒绝。
     * @param state 待消费状态
     * @param tenantId 服务端租户标识
     * @param platform 平台标识
     * @param redirectUri 签发时绑定的回调地址
     * @return 仅首次匹配且未过期的消费返回 true
     */
    Mono<Boolean> consume(String state, String tenantId, String platform, String redirectUri);
}
