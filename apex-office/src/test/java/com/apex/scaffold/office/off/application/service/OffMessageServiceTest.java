package com.apex.scaffold.office.off.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.office.off.application.assembler.OffMessageAssembler;
import com.apex.scaffold.office.off.application.assembler.OffMessageAssemblerImpl;
import com.apex.scaffold.office.off.domain.entity.OffMessageDeliveryEntity;
import com.apex.scaffold.office.off.domain.entity.OffMessageEntity;
import com.apex.scaffold.office.off.domain.entity.OffMessageRecipientEntity;
import com.apex.scaffold.office.off.domain.repository.MessageDeliveryRepository;
import com.apex.scaffold.office.off.domain.repository.OffMessageDeliveryRepository;
import com.apex.scaffold.office.off.domain.repository.OffMessageRecipientRepository;
import com.apex.scaffold.office.off.domain.repository.OffMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

/**
 * OffMessageService 单元测试：收件箱增量游标、投递状态查询与投递记录。
 */
class OffMessageServiceTest {

    private OffMessageRepository messageRepository;
    private OffMessageRecipientRepository recipientRepository;
    private OffMessageDeliveryRepository channelDeliveryRepository;
    private OffMessageService service;

    @BeforeEach
    void setUp() {
        messageRepository = mock(OffMessageRepository.class);
        recipientRepository = mock(OffMessageRecipientRepository.class);
        MessageDeliveryRepository deliveryRepository = mock(MessageDeliveryRepository.class);
        channelDeliveryRepository = mock(OffMessageDeliveryRepository.class);
        OffMessageAssembler assembler = new OffMessageAssemblerImpl();
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        service = new OffMessageService(
                messageRepository,
                recipientRepository,
                assembler,
                deliveryRepository,
                channelDeliveryRepository,
                events);
    }

    private OffMessageEntity message(long id, long senderId) {
        OffMessageEntity entity = new OffMessageEntity();
        entity.setId(id);
        entity.setMessageCode("MSG-" + id);
        entity.setSubject("主题");
        entity.setSenderId(senderId);
        entity.setTenantId("t1");
        return entity;
    }

    private OffMessageRecipientEntity recipient(long id, long messageId, long recipientId) {
        OffMessageRecipientEntity entity = new OffMessageRecipientEntity();
        entity.setId(id);
        entity.setMessageId(messageId);
        entity.setRecipientId(recipientId);
        entity.setReadStatus(false);
        entity.setTenantId("t1");
        return entity;
    }

    private OffMessageDeliveryEntity delivery(long messageId, long recipientId, String channel, String status) {
        OffMessageDeliveryEntity entity = new OffMessageDeliveryEntity();
        entity.setMessageId(messageId);
        entity.setRecipientId(recipientId);
        entity.setChannel(channel);
        entity.setDeliveryStatus(status);
        entity.setTenantId("t1");
        return entity;
    }

    @Test
    @DisplayName("getInboxCursor 组装消息与已读状态，recipientRecordId 作游标")
    void inboxCursorAssemblesItems() {
        when(recipientRepository.findInboxAfterId(100L, 0L, 20)).thenReturn(Flux.just(recipient(11L, 1L, 100L)));
        when(messageRepository.findById(1L)).thenReturn(Mono.just(message(1L, 200L)));

        StepVerifier.create(service.getInboxCursor(100L, null, null)
                        .contextWrite(Context.of(TenantContext.REACTOR_CONTEXT_KEY, "t1")))
                .assertNext(item -> {
                    assertThat(item.getRecipientRecordId()).isEqualTo(11L);
                    assertThat(item.getMessageId()).isEqualTo(1L);
                    assertThat(item.getSubject()).isEqualTo("主题");
                    assertThat(item.getReadStatus()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getInboxCursor 限制 limit 最大 100")
    void inboxCursorClampsLimit() {
        when(recipientRepository.findInboxAfterId(100L, 5L, 100)).thenReturn(Flux.empty());

        StepVerifier.create(service.getInboxCursor(100L, 5L, 500)
                        .contextWrite(Context.of(TenantContext.REACTOR_CONTEXT_KEY, "t1")))
                .verifyComplete();
        verify(recipientRepository).findInboxAfterId(100L, 5L, 100);
    }

    @Test
    @DisplayName("getMessageDeliveries 发送人可见全量投递")
    void deliveriesForSender() {
        when(messageRepository.findById(9L)).thenReturn(Mono.just(message(9L, 100L)));
        when(channelDeliveryRepository.findByMessageId(9L))
                .thenReturn(Flux.just(delivery(9L, 100L, "INBOX", "SUCCESS"), delivery(9L, 200L, "FEISHU", "PENDING")));

        StepVerifier.create(service.getMessageDeliveries(9L, 100L)
                        .contextWrite(Context.of(TenantContext.REACTOR_CONTEXT_KEY, "t1")))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("getMessageDeliveries 接收人仅可见本人投递")
    void deliveriesForRecipient() {
        when(messageRepository.findById(9L)).thenReturn(Mono.just(message(9L, 100L)));
        when(channelDeliveryRepository.findByMessageIdAndRecipientId(9L, 200L))
                .thenReturn(Flux.just(delivery(9L, 200L, "INBOX", "SUCCESS")));

        StepVerifier.create(service.getMessageDeliveries(9L, 200L)
                        .contextWrite(Context.of(TenantContext.REACTOR_CONTEXT_KEY, "t1")))
                .assertNext(dto -> assertThat(dto.getRecipientId()).isEqualTo(200L))
                .verifyComplete();
    }

    @Test
    @DisplayName("getMessageDeliveries 跨租户消息返回空")
    void deliveriesCrossTenant() {
        when(messageRepository.findById(9L)).thenReturn(Mono.just(message(9L, 100L)));

        StepVerifier.create(service.getMessageDeliveries(9L, 100L)
                        .contextWrite(Context.of(TenantContext.REACTOR_CONTEXT_KEY, "other")))
                .verifyComplete();
    }

    @Test
    @DisplayName("recordDelivery 拒绝非法渠道")
    void recordDeliveryRejectsBadChannel() {
        StepVerifier.create(service.recordDelivery(9L, 100L, "SMS", "SUCCESS", null, null)
                        .contextWrite(Context.of(TenantContext.REACTOR_CONTEXT_KEY, "t1")))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("recordDelivery 拒绝非法状态")
    void recordDeliveryRejectsBadStatus() {
        StepVerifier.create(service.recordDelivery(9L, 100L, "INBOX", "DONE", null, null)
                        .contextWrite(Context.of(TenantContext.REACTOR_CONTEXT_KEY, "t1")))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("recordDelivery 成功时写入送达时间并 upsert")
    void recordDeliverySuccess() {
        when(recipientRepository.findByMessageIdAndRecipientId(9L, 100L))
                .thenReturn(Mono.just(recipient(11L, 9L, 100L)));
        when(channelDeliveryRepository.upsert(any(OffMessageDeliveryEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.recordDelivery(9L, 100L, "FEISHU", "SUCCESS", "pm-1", null)
                        .contextWrite(Context.of(TenantContext.REACTOR_CONTEXT_KEY, "t1")))
                .assertNext(dto -> {
                    assertThat(dto.getChannel()).isEqualTo("FEISHU");
                    assertThat(dto.getDeliveryStatus()).isEqualTo("SUCCESS");
                    assertThat(dto.getDeliveredAt()).isNotNull();
                    assertThat(dto.getPlatformMessageId()).isEqualTo("pm-1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("recordDelivery 接收记录不存在时报业务异常")
    void recordDeliveryRecipientMissing() {
        when(recipientRepository.findByMessageIdAndRecipientId(9L, 100L)).thenReturn(Mono.empty());

        StepVerifier.create(service.recordDelivery(9L, 100L, "INBOX", "SUCCESS", null, null)
                        .contextWrite(Context.of(TenantContext.REACTOR_CONTEXT_KEY, "t1")))
                .expectError(BusinessException.class)
                .verify();
    }
}
