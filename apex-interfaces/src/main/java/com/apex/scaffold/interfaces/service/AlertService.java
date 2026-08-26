package com.apex.scaffold.interfaces.service;

import com.apex.scaffold.common.exception.AlertEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${alert.dingding.webhook:}")
    private String dingdingWebhook;

    /** 钉钉secret */
    @Value("${alert.dingding.secret:}")
    private String dingdingSecret;

    /** Web客户端 */
    private final WebClient webClient;

    /**
     * 构造函数
     *
     * @param webClientBuilder Web客户端构建器
     */
    public AlertService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
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
        String webhook = dingdingWebhook;
        if (webhook == null || webhook.isBlank()) {
            return Mono.just(false);
        }
        String webhookUrl = webhook.trim();

        try {
            Map<String, Object> message = buildDingTalkMessage(alertEvent);
            return webClient
                    .post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(message)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .map(response -> {
                        Integer errcode = (Integer) response.get("errcode");
                        return errcode == null || Objects.equals(errcode, 0);
                    })
                    .onErrorResume(e -> Mono.just(false));
        } catch (Exception e) {
            return Mono.just(false);
        }
    }

    /**
     * 发送紧急告警
     *
     * <p>目前未实现
     *
     * @param alertEvent 告警事件
     * @param phone 电话号码
     * @return 是否发送成功
     */
    public Mono<Boolean> sendUrgentAlert(AlertEvent alertEvent, String phone) {
        return Mono.just(false);
    }

    /**
     * 构建钉钉消息
     *
     * @param alertEvent 告警事件
     * @return 钉钉消息
     */
    private Map<String, Object> buildDingTalkMessage(AlertEvent alertEvent) {
        Map<String, Object> params = new HashMap<>(2);
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

        return params;
    }
}
