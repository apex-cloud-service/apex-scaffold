package com.apex.scaffold.office.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.office.notify.application.NotifyApplicationService;
import com.apex.scaffold.office.notify.domain.NotifyChannelPort;
import com.apex.scaffold.office.notify.domain.NotifyTemplateRenderer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 统一消息门面测试（方案 M4 / 批次 D1）。
 *
 * <p>覆盖：模板渲染（占位符替换/缺失保留）、单通道投递、未知通道拒绝、
 * 通道失败收敛为结果、多通道扇出独立成败。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("统一消息门面测试")
class NotifyFacadeTest {

    private NotifyChannelPort stubChannel(String name, boolean fail) {
        NotifyChannelPort port = mock(NotifyChannelPort.class);
        when(port.channel()).thenReturn(name);
        if (fail) {
            when(port.deliver(anyString(), any(), anyString(), any()))
                    .thenReturn(Mono.error(new IllegalStateException("channel down")));
        } else {
            when(port.deliver(anyString(), any(), anyString(), any())).thenReturn(Mono.just("msg-" + name));
        }
        return port;
    }

    @Test
    @DisplayName("模板渲染：替换占位符，缺失参数原样保留")
    void rendersTemplate() {
        assertThat(NotifyTemplateRenderer.render("你好 ${name}，订单 ${orderId} 已发货", Map.of("name", "张三")))
                .isEqualTo("你好 张三，订单 ${orderId} 已发货");
        assertThat(NotifyTemplateRenderer.declaredParams("你好 ${name}，订单 ${orderId}"))
                .containsExactlyInAnyOrder("name", "orderId");
        assertThat(NotifyTemplateRenderer.render("无占位符", Map.of())).isEqualTo("无占位符");
        assertThat(NotifyTemplateRenderer.render(null, Map.of())).isEmpty();
    }

    @Test
    @DisplayName("单通道投递成功并回报缺失参数")
    void sendsToChannel() {
        NotifyApplicationService service = new NotifyApplicationService(List.of(stubChannel("SMS", false)));
        StepVerifier.create(service.send(new NotifyApplicationService.NotifyRequest(
                        "sms", "13800138000", "通知", "你好 ${name}，${missing}", Map.of("name", "张三"))))
                .assertNext(result -> {
                    assertThat(result.delivered()).isTrue();
                    assertThat(result.messageId()).isEqualTo("msg-SMS");
                    assertThat(result.missingParams()).containsExactly("missing");
                })
                .verifyComplete();
        assertThat(service.availableChannels()).containsExactly("SMS");
    }

    @Test
    @DisplayName("未知通道与空目标拒绝")
    void rejectsUnknownChannelAndBlankTarget() {
        NotifyApplicationService service = new NotifyApplicationService(List.of(stubChannel("SMS", false)));
        StepVerifier.create(
                        service.send(new NotifyApplicationService.NotifyRequest("PIGEON", "x", null, "c", Map.of())))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(service.send(new NotifyApplicationService.NotifyRequest("SMS", " ", null, "c", Map.of())))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("通道失败收敛为失败结果，不中断调用链")
    void convergesChannelFailure() {
        NotifyApplicationService service = new NotifyApplicationService(List.of(stubChannel("EMAIL", true)));
        StepVerifier.create(service.send(
                        new NotifyApplicationService.NotifyRequest("EMAIL", "a@example.com", "主题", "正文", Map.of())))
                .assertNext(result -> {
                    assertThat(result.delivered()).isFalse();
                    assertThat(result.error()).contains("channel down");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("多通道扇出：逐通道独立成败")
    void fansOutAcrossChannels() {
        NotifyApplicationService service =
                new NotifyApplicationService(List.of(stubChannel("INBOX", false), stubChannel("SMS", true)));
        StepVerifier.create(service.sendFanout(Map.of("INBOX", "42", "SMS", "13800138000"), "主题", "正文", Map.of()))
                .assertNext(first -> assertThat(first.delivered()).isTrue())
                .assertNext(second -> assertThat(second.delivered()).isFalse())
                .verifyComplete();
    }
}
