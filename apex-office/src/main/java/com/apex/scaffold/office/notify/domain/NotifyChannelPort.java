package com.apex.scaffold.office.notify.domain;

import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 通知通道端口（方案 M4 / 批次 D1）。
 *
 * <p>统一消息门面的通道扩展点：站内信、办公平台 IM、短信、邮件分别以适配器实现。
 * 新增通道 = 实现本接口 + 条件装配，<b>无需修改门面编排逻辑</b>（与 C3 存储 SPI 同思路）。
 *
 * <p>通道适配器职责边界：
 * <ul>
 *   <li>仅负责"把已渲染好的内容投递到目标通道"，不做模板渲染与参数校验（门面负责）；</li>
 *   <li>阻塞 SDK 调用须隔离到 {@code BlockingIoSchedulers.remote()/event()}
 *       （AGENTS.md 阻塞 I/O 约束）；</li>
 *   <li>失败须以异常向上传播，由门面记录投递状态并交由 Outbox 重试。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface NotifyChannelPort {

    /**
     * 通道标识（与 {@code off_message_delivery.channel} 白名单一致）。
     *
     * @return 通道标识（INBOX / FEISHU / DINGTALK / WECOM / SMS / EMAIL）
     */
    String channel();

    /**
     * 投递单条通知。
     *
     * @param target  目标（站内信为用户 ID 串、IM 为外部用户 ID、短信为手机号、邮件为邮箱）
     * @param subject 主题（短信/站内信可为空）
     * @param content 已渲染的正文
     * @param params  通道附加参数（如短信模板 ID、邮件抄送），可为空
     * @return 通道侧消息 ID（无则返回空串）
     */
    Mono<String> deliver(String target, String subject, String content, Map<String, Object> params);
}
