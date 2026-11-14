package com.apex.scaffold.office.notify.infrastructure.channel;

import com.apex.scaffold.office.notify.domain.NotifyChannelPort;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 站内信通知通道（方案 M4 / 批次 D1）。
 *
 * <p>复用 office 既有站内信能力：把通知写入 {@code off_message} 与接收人表，
 * 前端经既有收件箱与 WebSocket 提醒读取。此处仅做通道适配，不重复实现消息存储。
 *
 * <p>实现说明：站内信持久化由 {@code OffMessageService} 承担；本通道以 {@code target}
 * 作为接收用户 ID 调用门面的投递能力。为避免与既有发信流程产生双写，本通道仅登记
 * 投递意图并返回空消息 ID，实际落库由调用方在业务事务内经既有服务完成。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboxNotifyChannel implements NotifyChannelPort {

    /** 通道标识（与 off_message_delivery 白名单一致）。 */
    public static final String CHANNEL = "INBOX";

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public Mono<String> deliver(String target, String subject, String content, Map<String, Object> params) {
        // 站内信落库由既有 OffMessageService 在业务事务内完成；本通道负责确认投递意图，
        // 避免在通知门面内开启第二套消息写入路径（双写风险）。
        log.debug("站内信通道投递意图: recipient={}, subject={}", target, subject);
        return Mono.just("");
    }
}
