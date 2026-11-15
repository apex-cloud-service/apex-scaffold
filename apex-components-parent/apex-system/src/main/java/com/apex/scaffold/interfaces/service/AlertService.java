package com.apex.scaffold.interfaces.service;

import com.apex.scaffold.common.exception.AlertEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 告警服务。
 *
 * <p>用于发送系统告警信息，目前支持钉钉告警。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Service
public class AlertService {

    /** 钉钉webhook地址 */
    private final String dingdingWebhook;

    /** 钉钉secret */
    private final String dingdingSecret;

    /** Web客户端 */
    private final WebClient webClient;

    /**
     * 构造函数
     *
     * @param webClientBuilder Web客户端构建器
     * @param dingdingWebhook 钉钉 webhook 地址，默认空串表示未配置（不发送告警）
     * @param dingdingSecret 钉钉 secret，默认空串表示未配置
     */
    public AlertService(
            WebClient.Builder webClientBuilder,
            @Value("${alert.dingding.webhook:}") String dingdingWebhook,
            @Value("${alert.dingding.secret:}") String dingdingSecret) {
        this.webClient = webClientBuilder.build();
        this.dingdingWebhook = dingdingWebhook;
        this.dingdingSecret = dingdingSecret;
    }

    /**
     * 发送告警
     *
     * <p>向钉钉发送告警消息
     *
     * @param alertEvent 告警事件
     * @return 是否发送成功
     */
    public Mono<Boolean> sendAlert(AlertEvent alertEvent) {
        return sendAlert(alertEvent, null);
    }

    /**
     * 监听告警事件并异步发送告警。
     *
     * <p>作为 Spring 事件监听器接收 {@link AlertEvent}，触发 {@code sendAlert} 订阅发送，不阻塞事件线程。</p>
     *
     * @param alertEvent 监听到的告警事件
     */
    @EventListener
    public void handleAlertEvent(AlertEvent alertEvent) {
        sendAlert(alertEvent).subscribe();
    }

    private Mono<Boolean> sendAlert(AlertEvent alertEvent, String phone) {
        String webhook = dingdingWebhook;
        if (webhook == null || webhook.isBlank()) {
            return Mono.just(false);
        }
        String webhookUrl = webhook.trim();

        try {
            Map<String, Object> message = buildDingTalkMessage(alertEvent, phone);
            return webClient
                    .post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(message)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .map(response -> {
                        Object errcode = response.get("errcode");
                        return errcode == null || (errcode instanceof Number number && number.intValue() == 0);
                    })
                    .onErrorReturn(false);
        } catch (Exception e) {
            return Mono.just(false);
        }
    }

    /**
     * 发送紧急告警
     *
     * <p>发送钉钉 Markdown 告警并@指定手机号
     *
     * @param alertEvent 告警事件
     * @param phone 电话号码
     * @return 是否发送成功
     */
    public Mono<Boolean> sendUrgentAlert(AlertEvent alertEvent, String phone) {
        if (phone == null || phone.isBlank()) {
            return Mono.just(false);
        }
        return sendAlert(alertEvent, phone.trim());
    }

    /**
     * 构建钉钉消息
     *
     * @param alertEvent 告警事件
     * @param phone 手机号，为 null 时不添加@信息
     * @return 钉钉消息
     */
    private Map<String, Object> buildDingTalkMessage(AlertEvent alertEvent, String phone) {
        Map<String, Object> params = new HashMap<>(3);
        params.put("msgtype", "markdown");

        StringBuilder content = new StringBuilder(512);
        content.append("#### 🔴 系统异常告警\n\n");
        content.append("**异常类型**: ").append(alertEvent.getExceptionType()).append("\n\n");
        content.append("**异常消息**: ").append(alertEvent.getMessage()).append("\n\n");
        content.append("**TraceID**: `").append(alertEvent.getTraceId()).append("`\n\n");
        content.append("**发生时间**: ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("\n\n");
        content.append("**发生次数**: ").append(alertEvent.getCount()).append("\n\n");

        if (alertEvent.getServiceName() != null) {
            content.append("**服务名称**: ").append(alertEvent.getServiceName()).append("\n\n");
        }

        content.append("---\n\n");
        content.append("> 请及时登录系统查看详细信息并处理！\n");

        Map<String, Object> markdown = new HashMap<>(2);
        markdown.put("title", "系统异常告警 - " + alertEvent.getExceptionType());
        markdown.put("text", content.toString());

        params.put("markdown", markdown);
        if (phone != null) {
            params.put("at", Map.of("atMobiles", java.util.List.of(phone), "isAtAll", false));
        }

        return params;
    }
}
