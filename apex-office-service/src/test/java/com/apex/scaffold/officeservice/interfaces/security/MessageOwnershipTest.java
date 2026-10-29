package com.apex.scaffold.officeservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.off.application.assembler.OffMessageAssembler;
import com.apex.scaffold.office.off.application.dto.MessageDTO;
import com.apex.scaffold.office.off.application.service.OffMessageService;
import com.apex.scaffold.office.off.domain.entity.OffMessageEntity;
import com.apex.scaffold.office.off.domain.entity.OffMessageRecipientEntity;
import com.apex.scaffold.office.off.domain.repository.MessageDeliveryRepository;
import com.apex.scaffold.office.off.domain.repository.OffMessageDeliveryRepository;
import com.apex.scaffold.office.off.domain.repository.OffMessageRecipientRepository;
import com.apex.scaffold.office.off.domain.repository.OffMessageRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 验证详情查询必须同时满足可信租户与用户归属。
 * @author Apex Team
 * @since 1.3.1
 */
class MessageOwnershipTest {
    /** 相同用户编号不能跨租户读取，非收件人不能读取同租户详情。 */
    @Test
    void requiresTenantAndOwnership() {
        var messages = mock(OffMessageRepository.class);
        var recipients = mock(OffMessageRecipientRepository.class);
        var assembler = mock(OffMessageAssembler.class);
        var entity = new OffMessageEntity();
        entity.setTenantId("a");
        entity.setSenderId(7L);
        var dto = new MessageDTO();
        when(messages.findById(99L)).thenReturn(Mono.just(entity));
        when(recipients.findByMessageIdAndRecipientId(99L, 8L)).thenReturn(Mono.empty());
        when(assembler.toDTO(entity)).thenReturn(dto);
        var service = new OffMessageService(
                messages,
                recipients,
                assembler,
                mock(MessageDeliveryRepository.class),
                mock(OffMessageDeliveryRepository.class),
                mock(ApplicationEventPublisher.class));
        StepVerifier.create(service.getMessageDetail(99L, 7L).contextWrite(TenantContext.withTenant("a")))
                .expectNext(dto)
                .verifyComplete();
        StepVerifier.create(service.getMessageDetail(99L, 7L).contextWrite(TenantContext.withTenant("b")))
                .verifyComplete();
        StepVerifier.create(service.getMessageDetail(99L, 8L).contextWrite(TenantContext.withTenant("a")))
                .verifyComplete();
    }

    /** 即使第二阶段仓储订阅前线程租户已丢失，也必须从 Reactor Context 重新装配。 */
    @Test
    void restoresTenantForRecipientLookupAfterAsyncBoundary() {
        var messages = mock(OffMessageRepository.class);
        var recipients = mock(OffMessageRecipientRepository.class);
        var assembler = mock(OffMessageAssembler.class);
        var entity = new OffMessageEntity();
        entity.setTenantId("a");
        entity.setSenderId(7L);
        var recipient = new OffMessageRecipientEntity();
        recipient.setTenantId("a");
        recipient.setRecipientId(8L);
        var dto = new MessageDTO();
        when(messages.findById(99L))
                .thenReturn(Mono.delay(Duration.ofMillis(1))
                        .doOnNext(ignored -> TenantContext.clear())
                        .thenReturn(entity));
        when(recipients.findByMessageIdAndRecipientId(99L, 8L))
                .thenReturn(Mono.fromCallable(() -> {
                            assertThat(TenantContext.currentTenantId()).contains("a");
                            return recipient;
                        })
                        .subscribeOn(BlockingIoSchedulers.jdbc()));
        when(assembler.toDTO(entity)).thenReturn(dto);
        var service = new OffMessageService(
                messages,
                recipients,
                assembler,
                mock(MessageDeliveryRepository.class),
                mock(OffMessageDeliveryRepository.class),
                mock(ApplicationEventPublisher.class));
        StepVerifier.create(service.getMessageDetail(99L, 8L).contextWrite(TenantContext.withTenant("a")))
                .expectNext(dto)
                .expectComplete()
                .verify(Duration.ofSeconds(5));
        recipient.setTenantId("b");
        StepVerifier.create(service.getMessageDetail(99L, 8L).contextWrite(TenantContext.withTenant("a")))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    /** HTTP 补偿查询与已读更新在延迟订阅后仍保留可信租户。 */
    @Test
    void restoresTenantForUnreadQueryAndReadUpdates() {
        var recipients = mock(OffMessageRecipientRepository.class);
        when(recipients.countUnreadByRecipientId(7L))
                .thenReturn(Mono.fromCallable(() -> {
                            assertThat(TenantContext.currentTenantId()).contains("a");
                            return 2L;
                        })
                        .subscribeOn(BlockingIoSchedulers.jdbc()));
        when(recipients.markAsRead(99L, 7L))
                .thenReturn(Mono.fromCallable(() -> {
                            assertThat(TenantContext.currentTenantId()).contains("a");
                            return 1;
                        })
                        .subscribeOn(BlockingIoSchedulers.jdbc()));
        when(recipients.markAllAsRead(7L))
                .thenReturn(Mono.fromCallable(() -> {
                            assertThat(TenantContext.currentTenantId()).contains("a");
                            return 2;
                        })
                        .subscribeOn(BlockingIoSchedulers.jdbc()));
        var service = new OffMessageService(
                mock(OffMessageRepository.class),
                recipients,
                mock(OffMessageAssembler.class),
                mock(MessageDeliveryRepository.class),
                mock(OffMessageDeliveryRepository.class),
                mock(ApplicationEventPublisher.class));
        StepVerifier.create(Mono.delay(Duration.ofMillis(1))
                        .doOnNext(ignored -> TenantContext.clear())
                        .then(service.markAsRead(99L, 7L))
                        .then(service.markAllAsRead(7L))
                        .then(service.countUnread(7L))
                        .contextWrite(TenantContext.withTenant("a")))
                .expectNext(2L)
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }
}
