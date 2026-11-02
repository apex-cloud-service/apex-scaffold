package com.apex.scaffold.office.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.office.notify.application.NotifyApplicationService;
import com.apex.scaffold.office.notify.domain.NotifyChannelPort;
import com.apex.scaffold.office.notify.domain.NotifyRequestRecord;
import com.apex.scaffold.office.notify.domain.NotifyRequestRecordRepository;
import com.apex.scaffold.office.notify.domain.NotifyTemplate;
import com.apex.scaffold.office.notify.domain.NotifyTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 统一通知模板与按模板投递测试（统一消息门面 / 批次 D1）。
 *
 * <p>覆盖：模板 CRUD（编码冲突、局部合并、逻辑删除）、按模板投递
 * （模板缺失/停用拒绝、占位符渲染、投递成败均落留痕）。
 *
 * @author Apex Team
 * @version 1.4.0
 * @since 1.4.0
 */
@DisplayName("统一通知模板与按模板投递测试")
class NotifyTemplateFacadeTest {

    private static final String TENANT = "41";

    /** 已启用的短信模板。 */
    private NotifyTemplate enabledSmsTemplate() {
        NotifyTemplate template = new NotifyTemplate();
        template.setTemplateCode("ORDER_SHIPPED");
        template.setChannel("SMS");
        template.setTitle("订单 ${orderId} 已发货");
        template.setBody("你好 ${name}，订单 ${orderId} 已发出");
        template.setStatus(NotifyTemplate.STATUS_ENABLED);
        return template;
    }

    private NotifyApplicationService service(
            NotifyChannelPort channel, NotifyTemplateRepository templates, NotifyRequestRecordRepository records) {
        return new NotifyApplicationService(List.of(channel), templates, records, new ObjectMapper());
    }

    private NotifyChannelPort channel(String name) {
        NotifyChannelPort port = mock(NotifyChannelPort.class);
        when(port.channel()).thenReturn(name);
        return port;
    }

    @Test
    @DisplayName("按模板投递：缺省通道取模板默认通道，渲染占位符并落 SUCCESS 留痕")
    void sendsByTemplateAndRecordsSuccess() {
        NotifyChannelPort sms = channel("SMS");
        when(sms.deliver(anyString(), any(), anyString(), any())).thenReturn(Mono.just("msg-1"));
        NotifyTemplateRepository templates = mock(NotifyTemplateRepository.class);
        when(templates.findByCode(TENANT, "ORDER_SHIPPED")).thenReturn(Mono.just(enabledSmsTemplate()));
        NotifyRequestRecordRepository records = mock(NotifyRequestRecordRepository.class);
        when(records.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        NotifyApplicationService facade = service(sms, templates, records);

        StepVerifier.create(facade.sendByTemplate(
                        TENANT, "ORDER_SHIPPED", null, "13800138000", Map.of("name", "张三", "orderId", "1001")))
                .assertNext(result -> {
                    assertThat(result.delivered()).isTrue();
                    assertThat(result.channel()).isEqualTo("SMS");
                    assertThat(result.messageId()).isEqualTo("msg-1");
                })
                .verifyComplete();

        verify(sms).deliver(eq("13800138000"), eq("订单 1001 已发货"), eq("你好 张三，订单 1001 已发出"), any());
        ArgumentCaptor<NotifyRequestRecord> captor = ArgumentCaptor.forClass(NotifyRequestRecord.class);
        verify(records).insert(captor.capture());
        NotifyRequestRecord record = captor.getValue();
        assertThat(record.getTenantId()).isEqualTo(TENANT);
        assertThat(record.getChannel()).isEqualTo("SMS");
        assertThat(record.getTemplateCode()).isEqualTo("ORDER_SHIPPED");
        assertThat(record.getRecipient()).isEqualTo("[\"13800138000\"]");
        assertThat(record.getStatus()).isEqualTo(NotifyRequestRecord.STATUS_SUCCESS);
        assertThat(record.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("按模板投递：通道失败收敛为失败结果并落 FAILED 留痕")
    void recordsFailureWhenChannelFails() {
        NotifyChannelPort sms = channel("SMS");
        when(sms.deliver(anyString(), any(), anyString(), any()))
                .thenReturn(Mono.error(new IllegalStateException("channel down")));
        NotifyTemplateRepository templates = mock(NotifyTemplateRepository.class);
        when(templates.findByCode(TENANT, "ORDER_SHIPPED")).thenReturn(Mono.just(enabledSmsTemplate()));
        NotifyRequestRecordRepository records = mock(NotifyRequestRecordRepository.class);
        when(records.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        NotifyApplicationService facade = service(sms, templates, records);

        StepVerifier.create(facade.sendByTemplate(
                        TENANT, "ORDER_SHIPPED", null, "13800138000", Map.of("name", "张三", "orderId", "1001")))
                .assertNext(result -> {
                    assertThat(result.delivered()).isFalse();
                    assertThat(result.error()).contains("channel down");
                })
                .verifyComplete();

        ArgumentCaptor<NotifyRequestRecord> captor = ArgumentCaptor.forClass(NotifyRequestRecord.class);
        verify(records).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotifyRequestRecord.STATUS_FAILED);
        assertThat(captor.getValue().getErrorMessage()).contains("channel down");
    }

    @Test
    @DisplayName("按模板投递：模板缺失报业务异常且不投递、不留痕")
    void rejectsMissingTemplate() {
        NotifyTemplateRepository templates = mock(NotifyTemplateRepository.class);
        when(templates.findByCode(TENANT, "MISSING")).thenReturn(Mono.empty());
        NotifyRequestRecordRepository records = mock(NotifyRequestRecordRepository.class);
        NotifyChannelPort sms = channel("SMS");
        NotifyApplicationService facade = service(sms, templates, records);

        StepVerifier.create(facade.sendByTemplate(TENANT, "MISSING", null, "13800138000", Map.of()))
                .expectError(BusinessException.class)
                .verify();

        verifyNoInteractions(records);
        verify(sms, never()).deliver(anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("按模板投递：停用模板拒绝投递且不留痕")
    void rejectsDisabledTemplate() {
        NotifyTemplate template = enabledSmsTemplate();
        template.setStatus(NotifyTemplate.STATUS_DISABLED);
        NotifyTemplateRepository templates = mock(NotifyTemplateRepository.class);
        when(templates.findByCode(TENANT, "ORDER_SHIPPED")).thenReturn(Mono.just(template));
        NotifyRequestRecordRepository records = mock(NotifyRequestRecordRepository.class);
        NotifyChannelPort sms = channel("SMS");
        NotifyApplicationService facade = service(sms, templates, records);

        StepVerifier.create(facade.sendByTemplate(
                        TENANT, "ORDER_SHIPPED", null, "13800138000", Map.of("name", "张三", "orderId", "1001")))
                .expectError(BusinessException.class)
                .verify();

        verifyNoInteractions(records);
        verify(sms, never()).deliver(anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("按模板投递：显式通道优先于模板默认通道，留痕记录实际通道")
    void explicitChannelOverridesTemplateDefault() {
        NotifyChannelPort email = channel("EMAIL");
        when(email.deliver(anyString(), any(), anyString(), any())).thenReturn(Mono.just("msg-2"));
        NotifyTemplateRepository templates = mock(NotifyTemplateRepository.class);
        when(templates.findByCode(TENANT, "ORDER_SHIPPED")).thenReturn(Mono.just(enabledSmsTemplate()));
        NotifyRequestRecordRepository records = mock(NotifyRequestRecordRepository.class);
        when(records.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        NotifyApplicationService facade = service(email, templates, records);

        StepVerifier.create(facade.sendByTemplate(
                        TENANT, "ORDER_SHIPPED", "email", "a@example.com", Map.of("name", "张三", "orderId", "1001")))
                .assertNext(result -> assertThat(result.channel()).isEqualTo("EMAIL"))
                .verifyComplete();

        verify(email).deliver(eq("a@example.com"), any(), anyString(), any());
        ArgumentCaptor<NotifyRequestRecord> captor = ArgumentCaptor.forClass(NotifyRequestRecord.class);
        verify(records).insert(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo("EMAIL");
    }

    @Test
    @DisplayName("创建模板：编码冲突报业务异常，缺省状态按启用处理")
    void createTemplateRejectsDuplicateCode() {
        NotifyTemplateRepository templates = mock(NotifyTemplateRepository.class);
        NotifyRequestRecordRepository records = mock(NotifyRequestRecordRepository.class);
        NotifyApplicationService facade = service(channel("SMS"), templates, records);

        NotifyTemplate duplicate = new NotifyTemplate();
        duplicate.setTemplateCode("ORDER_SHIPPED");
        duplicate.setChannel("SMS");
        duplicate.setBody("正文");
        when(templates.findByCode(TENANT, "ORDER_SHIPPED")).thenReturn(Mono.just(enabledSmsTemplate()));
        StepVerifier.create(facade.createTemplate(TENANT, duplicate))
                .expectError(BusinessException.class)
                .verify();

        NotifyTemplate fresh = new NotifyTemplate();
        fresh.setTemplateCode("ORDER_DELIVERED");
        fresh.setChannel("SMS");
        fresh.setBody("正文");
        when(templates.findByCode(TENANT, "ORDER_DELIVERED")).thenReturn(Mono.empty());
        when(templates.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        StepVerifier.create(facade.createTemplate(TENANT, fresh))
                .assertNext(saved -> {
                    assertThat(saved.getTenantId()).isEqualTo(TENANT);
                    assertThat(saved.getStatus()).isEqualTo(NotifyTemplate.STATUS_ENABLED);
                })
                .verifyComplete();
        verify(templates).insert(any());
    }

    @Test
    @DisplayName("更新模板：仅非空字段生效，编码变更校验冲突")
    void updateTemplateMergesPartialChanges() {
        NotifyTemplate existing = enabledSmsTemplate();
        existing.setId(1L);
        NotifyTemplateRepository templates = mock(NotifyTemplateRepository.class);
        when(templates.findById(TENANT, 1L)).thenReturn(Mono.just(existing));
        when(templates.update(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        NotifyRequestRecordRepository records = mock(NotifyRequestRecordRepository.class);
        NotifyApplicationService facade = service(channel("SMS"), templates, records);

        NotifyTemplate changes = new NotifyTemplate();
        changes.setTitle("新主题");
        StepVerifier.create(facade.updateTemplate(TENANT, 1L, changes))
                .assertNext(updated -> {
                    assertThat(updated.getTitle()).isEqualTo("新主题");
                    assertThat(updated.getChannel()).isEqualTo("SMS");
                    assertThat(updated.getTemplateCode()).isEqualTo("ORDER_SHIPPED");
                })
                .verifyComplete();

        NotifyTemplate rename = new NotifyTemplate();
        rename.setTemplateCode("OTHER");
        when(templates.findByCode(TENANT, "OTHER")).thenReturn(Mono.just(new NotifyTemplate()));
        StepVerifier.create(facade.updateTemplate(TENANT, 1L, rename))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("删除模板：先加载后逻辑删除，未命中报业务异常")
    void deleteTemplateRequiresExisting() {
        NotifyTemplate existing = enabledSmsTemplate();
        existing.setId(1L);
        NotifyTemplateRepository templates = mock(NotifyTemplateRepository.class);
        when(templates.findById(TENANT, 1L)).thenReturn(Mono.just(existing));
        when(templates.deleteById(TENANT, 1L)).thenReturn(Mono.just(true));
        NotifyRequestRecordRepository records = mock(NotifyRequestRecordRepository.class);
        NotifyApplicationService facade = service(channel("SMS"), templates, records);

        StepVerifier.create(facade.deleteTemplate(TENANT, 1L)).expectNext(true).verifyComplete();
        verify(templates).deleteById(TENANT, 1L);

        when(templates.findById(TENANT, 2L)).thenReturn(Mono.empty());
        StepVerifier.create(facade.deleteTemplate(TENANT, 2L))
                .expectError(BusinessException.class)
                .verify();
    }
}
