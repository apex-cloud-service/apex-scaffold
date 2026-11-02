package com.apex.scaffold.office.notify.infrastructure.channel;

import com.apex.scaffold.office.notify.domain.NotifyChannelPort;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 短信通知通道（方案 M4 / 批次 D1）。
 *
 * <p>通道与短信服务商解耦：本类只持有"发送函数"（手机号 + 内容 → 结果），
 * 由具体服务商适配器注册（如创蓝）。消费方引入 {@code apex-infrastructure-integration}
 * 后以 Bean 形式提供函数即可启用；未提供时通道不存在（门面不会路由到它）。
 *
 * <p>阻塞 SDK 调用由发送函数自身隔离到 {@code BlockingIoSchedulers.remote()}；
 * 本通道不做线程切换，保持"通道只编排、不承载阻塞"的边界。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
public class SmsNotifyChannel implements NotifyChannelPort {

    /** 通道标识（与 off_message_delivery 白名单一致）。 */
    public static final String CHANNEL = "SMS";

    private final BiFunction<String, String, Mono<String>> sender;

    /**
     * 构造短信通道。
     *
     * @param sender 发送函数（手机号, 内容）→ 服务商消息 ID
     */
    public SmsNotifyChannel(BiFunction<String, String, Mono<String>> sender) {
        this.sender = sender;
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public Mono<String> deliver(String target, String subject, String content, Map<String, Object> params) {
        if (target == null || target.isBlank()) {
            return Mono.error(new IllegalArgumentException("短信接收手机号不能为空"));
        }
        log.debug("短信通道投递: target={}, length={}", maskPhone(target), content == null ? 0 : content.length());
        return sender.apply(target, content);
    }

    /** 手机号脱敏（保留前 3 后 4，用于日志）。 */
    private String maskPhone(String phone) {
        if (phone.length() < 8) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
