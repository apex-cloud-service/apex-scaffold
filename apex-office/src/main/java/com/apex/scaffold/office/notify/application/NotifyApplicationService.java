package com.apex.scaffold.office.notify.application;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.notify.domain.NotifyChannelPort;
import com.apex.scaffold.office.notify.domain.NotifyTemplateRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 统一消息门面（方案 M4 / 批次 D1）。
 *
 * <p>把站内信、办公平台 IM、短信、邮件收敛为同一投递入口：调用方只给出
 * <b>通道 + 目标 + 模板 + 参数</b>，门面负责模板渲染、通道路由与失败收敛。
 * 通道由 {@link NotifyChannelPort} 适配器提供（新增通道免改本类）。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>通道路由</b>：请求显式 {@code channel} 优先；未指定时按模板默认通道；</li>
 *   <li><b>模板校验</b>：模板缺失参数保留占位符（不静默输出空串），并在响应中回报缺失项；</li>
 *   <li><b>失败收敛</b>：单通道投递失败不影响其它通道（多通道扇出），逐通道回报状态；</li>
 *   <li><b>租户隔离</b>：租户取自可信上下文，由通道适配器与持久化层各自落实。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyApplicationService {

    /** 通道适配器集合（按 channel() 索引）。 */
    private final List<NotifyChannelPort> channels;

    /**
     * 单通道投递请求。
     *
     * @param channel 通道标识（INBOX / FEISHU / DINGTALK / WECOM / SMS / EMAIL）
     * @param target  目标（用户 ID / 外部 ID / 手机号 / 邮箱）
     * @param subject 主题（可空）
     * @param content 正文模板（含 {@code ${key}} 占位符）
     * @param params  渲染参数
     */
    public record NotifyRequest(
            String channel, String target, String subject, String content, Map<String, Object> params) {}

    /**
     * 单通道投递结果。
     *
     * @param channel    通道标识
     * @param delivered  是否投递成功
     * @param messageId  通道侧消息 ID（无则空串）
     * @param missingParams 模板中未提供的参数名（渲染后仍保留占位符）
     * @param error      失败原因（成功时为 null）
     */
    public record NotifyResult(
            String channel, boolean delivered, String messageId, List<String> missingParams, String error) {}

    /**
     * 投递单条通知。
     *
     * @param request 投递请求
     * @return 投递结果
     */
    public Mono<NotifyResult> send(NotifyRequest request) {
        if (request == null || request.channel() == null || request.channel().isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "通知通道不能为空"));
        }
        if (request.target() == null || request.target().isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "通知目标不能为空"));
        }
        String channelKey = request.channel().trim().toUpperCase(Locale.ROOT);
        NotifyChannelPort port = findChannel(channelKey);
        if (port == null) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "通知通道未启用或不支持: " + channelKey));
        }
        String rendered = NotifyTemplateRenderer.render(request.content(), request.params());
        List<String> missing = new ArrayList<>();
        NotifyTemplateRenderer.declaredParams(request.content()).forEach(name -> {
            if (request.params() == null || !request.params().containsKey(name)) {
                missing.add(name);
            }
        });
        return port.deliver(request.target(), request.subject(), rendered, request.params())
                .map(messageId -> new NotifyResult(channelKey, true, messageId == null ? "" : messageId, missing, null))
                .onErrorResume(error -> {
                    log.warn(
                            "通知投递失败: channel={}, target={}, reason={}",
                            channelKey,
                            request.target(),
                            error.getMessage());
                    return Mono.just(new NotifyResult(channelKey, false, "", missing, error.getMessage()));
                });
    }

    /**
     * 多通道扇出投递（同一模板投递到多个通道，逐通道独立成败）。
     *
     * @param channelTargets 通道 → 目标 的映射
     * @param subject        主题
     * @param content        正文模板
     * @param params         渲染参数
     * @return 各通道投递结果
     */
    public Flux<NotifyResult> sendFanout(
            Map<String, String> channelTargets, String subject, String content, Map<String, Object> params) {
        if (channelTargets == null || channelTargets.isEmpty()) {
            return Flux.error(new BusinessException(ResultCode.PARAM_ERROR, "至少需要一个通知通道"));
        }
        return Flux.fromIterable(channelTargets.entrySet())
                .concatMap(
                        entry -> send(new NotifyRequest(entry.getKey(), entry.getValue(), subject, content, params)));
    }

    /**
     * 已启用的通道标识（运维/管理端展示）。
     *
     * @return 通道标识列表
     */
    public List<String> availableChannels() {
        return channels == null
                ? List.of()
                : channels.stream().map(NotifyChannelPort::channel).distinct().toList();
    }

    /**
     * 按通道标识查找适配器（大小写不敏感）。
     *
     * @param channelKey 已大写化的通道标识
     * @return 适配器；未装配时为 null
     */
    private NotifyChannelPort findChannel(String channelKey) {
        if (channels == null) {
            return null;
        }
        return channels.stream()
                .filter(port -> channelKey.equalsIgnoreCase(port.channel()))
                .findFirst()
                .orElse(null);
    }
}
