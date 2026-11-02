package com.apex.scaffold.office.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.office.notify.application.NotifyApplicationService;
import com.apex.scaffold.office.notify.domain.NotifyRequestRecord;
import com.apex.scaffold.office.notify.domain.NotifyRequestRecordRepository;
import com.apex.scaffold.office.notify.domain.NotifyTemplateRepository;
import com.apex.scaffold.office.notify.infrastructure.channel.EmailNotifyChannel;
import com.apex.scaffold.office.notify.infrastructure.channel.SmsNotifyChannel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * SMS / EMAIL 通道与 Outbox 异步投递测试（方案 M4 / 批次 D1 补齐）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("SMS/EMAIL 通道与异步投递测试")
class NotifyChannelExtensionTest {

    private SmsNotifyChannel smsChannel() {
        return new SmsNotifyChannel((phone, content) -> Mono.just("sms-" + phone));
    }

    private EmailNotifyChannel emailChannel() {
        return new EmailNotifyChannel((to, subject, body) -> Mono.just("mail-" + subject));
    }

    @Test
    @DisplayName("短信通道：投递并回报服务商消息 ID；空手机号拒绝")
    void smsChannelDelivers() {
        StepVerifier.create(smsChannel().deliver("13800138000", null, "验证码 123456", Map.of()))
                .assertNext(id -> assertThat(id).isEqualTo("sms-13800138000"))
                .verifyComplete();

        StepVerifier.create(smsChannel().deliver(" ", null, "x", Map.of()))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("邮件通道：主题缺省为「通知」；空收件人拒绝")
    void emailChannelDelivers() {
        StepVerifier.create(emailChannel().deliver("a@example.com", null, "<p>hi</p>", Map.of()))
                .assertNext(id -> assertThat(id).isEqualTo("mail-通知"))
                .verifyComplete();

        StepVerifier.create(emailChannel().deliver("", "s", "b", Map.of()))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    @DisplayName("门面可路由到 SMS/EMAIL 通道（通道扩展点生效）")
    void facadeRoutesToExtendedChannels() {
        NotifyTemplateRepository templates = mock(NotifyTemplateRepository.class);
        NotifyRequestRecordRepository records = mock(NotifyRequestRecordRepository.class);
        NotifyApplicationService service = new NotifyApplicationService(
                List.of(smsChannel(), emailChannel()),
                templates,
                records,
                new com.fasterxml.jackson.databind.ObjectMapper());

        StepVerifier.create(service.send(new NotifyApplicationService.NotifyRequest(
                        "SMS", "13800138000", null, "验证码 ${code}", Map.of("code", "123456"))))
                .assertNext(result -> {
                    assertThat(result.channel()).isEqualTo("SMS");
                    assertThat(result.delivered()).isTrue();
                })
                .verifyComplete();

        StepVerifier.create(service.send(
                        new NotifyApplicationService.NotifyRequest("EMAIL", "a@example.com", "主题", "正文", Map.of())))
                .assertNext(result -> assertThat(result.channel()).isEqualTo("EMAIL"))
                .verifyComplete();

        assertThat(service.availableChannels()).containsExactlyInAnyOrder("SMS", "EMAIL");
    }

    @Test
    @DisplayName("异步模式：先落留痕（PENDING）再投递，返回留痕 ID")
    void asyncModeRecordsThenDelivers() {
        NotifyTemplateRepository templates = mock(NotifyTemplateRepository.class);
        NotifyRequestRecordRepository records = mock(NotifyRequestRecordRepository.class);
        NotifyRequestRecord saved = new NotifyRequestRecord();
        saved.setId(99L);
        when(records.insert(any(NotifyRequestRecord.class))).thenReturn(Mono.just(saved));

        NotifyApplicationService service = new NotifyApplicationService(
                List.of(smsChannel()), templates, records, new com.fasterxml.jackson.databind.ObjectMapper());
        service.setAsyncOutboxEnabled(true);
        assertThat(service.asyncOutboxEnabled()).isTrue();

        StepVerifier.create(service.sendAsync(
                        new NotifyApplicationService.NotifyRequest("SMS", "13800138000", null, "正文", Map.of())))
                .assertNext(result -> {
                    assertThat(result.delivered()).isTrue();
                    assertThat(result.messageId()).isEqualTo("99");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("异步模式关闭时退化为同步投递（行为不变）")
    void asyncModeDisabledFallsBackToSync() {
        NotifyTemplateRepository templates = mock(NotifyTemplateRepository.class);
        NotifyRequestRecordRepository records = mock(NotifyRequestRecordRepository.class);
        NotifyApplicationService service = new NotifyApplicationService(
                List.of(smsChannel()), templates, records, new com.fasterxml.jackson.databind.ObjectMapper());
        service.setAsyncOutboxEnabled(false);

        StepVerifier.create(service.sendAsync(
                        new NotifyApplicationService.NotifyRequest("SMS", "13800138000", null, "正文", Map.of())))
                .assertNext(result -> {
                    assertThat(result.delivered()).isTrue();
                    // 同步路径返回通道消息 ID 而非留痕 ID
                    assertThat(result.messageId()).startsWith("sms-");
                })
                .verifyComplete();
    }
}
