package com.apex.scaffold.infrastructure.mq.alert;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 告警通知发送器
 *
 * <p>支持多种告警通知渠道。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "apex.mq.alert.notification", name = "enabled", havingValue = "true")
public class AlertNotificationSender {

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 发送钉钉通知
     *
     * @param webhookUrl 钉钉 Webhook URL
     * @param title      标题
     * @param message    消息内容
     */
    public void sendDingTalk(String webhookUrl, String title, String message) {
        try {
            Map<String, Object> payload = new HashMap<>(16);
            payload.put("msgtype", "markdown");

            Map<String, Object> markdown = new HashMap<>(16);
            markdown.put("title", title);
            markdown.put("text", String.format("### %s\n\n%s", title, message));
            payload.put("markdown", markdown);

            restTemplate.postForEntity(webhookUrl, payload, String.class);
            log.info("已发送钉钉通知: {}", title);

        } catch (Exception e) {
            log.error("发送钉钉通知失败", e);
        }
    }

    /**
     * 发送企业微信通知
     *
     * @param webhookUrl 企业微信 Webhook URL
     * @param message    消息内容
     */
    public void sendWeChat(String webhookUrl, String message) {
        try {
            Map<String, Object> payload = new HashMap<>(16);
            payload.put("msgtype", "text");

            Map<String, Object> text = new HashMap<>(16);
            text.put("content", message);
            payload.put("text", text);

            restTemplate.postForEntity(webhookUrl, payload, String.class);
            log.info("已发送企业微信通知");

        } catch (Exception e) {
            log.error("发送企业微信通知失败", e);
        }
    }
}
