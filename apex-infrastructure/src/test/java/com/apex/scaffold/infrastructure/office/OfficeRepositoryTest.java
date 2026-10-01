package com.apex.scaffold.infrastructure.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.domain.office.message.CardMessageContent;
import com.apex.scaffold.domain.office.message.MarkdownMessageContent;
import com.apex.scaffold.domain.office.message.MessageType;
import com.apex.scaffold.domain.office.message.OfficeMessage;
import com.apex.scaffold.domain.office.message.TextMessageContent;
import com.apex.scaffold.infrastructure.office.persistence.*;
import com.apex.scaffold.infrastructure.office.persistence.converter.OfficeMessageConverter;
import com.apex.scaffold.infrastructure.office.persistence.mapper.OfficeMessageMapper;
import com.apex.scaffold.infrastructure.office.persistence.po.OfficeMessagePO;
import com.apex.scaffold.infrastructure.office.persistence.repository.OfficeMessageRepositoryImpl;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import reactor.test.StepVerifier;

class OfficeRepositoryTest {
    private final OfficeAppMapper apps = mock(OfficeAppMapper.class);
    private final OfficeBindingMapper bindings = mock(OfficeBindingMapper.class);
    private final OfficeOrganizationMappingMapper organizationMappings = mock(OfficeOrganizationMappingMapper.class);
    private final OfficeSyncTaskMapper syncTasks = mock(OfficeSyncTaskMapper.class);
    private final OfficeRepositoryImpl repository = new OfficeRepositoryImpl(
            apps, bindings, organizationMappings, syncTasks, Mappers.getMapper(OfficePersistenceConverter.class));

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void officeMessageConverterMapsFailureReasonToMigratedColumn() {
        OfficeMessageConverter converter = Mappers.getMapper(OfficeMessageConverter.class);
        OfficeMessage message = OfficeMessage.builder()
                .tenantId("41")
                .failureReason("platform rejected")
                .sendStatus("FAILED")
                .build();

        OfficeMessagePO po = converter.toPO(message);

        assertThat(po.getResultMessage()).isEqualTo("platform rejected");
        assertThat(po.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void officeMessageConverterRestoresTextContent() {
        OfficeMessageConverter converter = Mappers.getMapper(OfficeMessageConverter.class);
        OfficeMessage source = OfficeMessage.builder()
                .messageType(MessageType.TEXT)
                .content(TextMessageContent.builder().text("hello").build())
                .build();

        OfficeMessage restored = converter.toEntity(converter.toPO(source));

        assertThat(restored.getContent()).isInstanceOf(TextMessageContent.class);
        assertThat(((TextMessageContent) restored.getContent()).getText()).isEqualTo("hello");
    }

    @Test
    void officeMessageConverterRestoresMarkdownContent() {
        OfficeMessageConverter converter = Mappers.getMapper(OfficeMessageConverter.class);
        OfficeMessage source = OfficeMessage.builder()
                .messageType(MessageType.MARKDOWN)
                .content(MarkdownMessageContent.builder()
                        .markdown("**hello**")
                        .title("title")
                        .build())
                .build();

        OfficeMessage restored = converter.toEntity(converter.toPO(source));

        assertThat(restored.getContent()).isInstanceOf(MarkdownMessageContent.class);
        assertThat(((MarkdownMessageContent) restored.getContent()).getMarkdown())
                .isEqualTo("**hello**");
        assertThat(((MarkdownMessageContent) restored.getContent()).getTitle()).isEqualTo("title");
    }

    @Test
    void officeMessageConverterRestoresCardContent() {
        OfficeMessageConverter converter = Mappers.getMapper(OfficeMessageConverter.class);
        OfficeMessage source = OfficeMessage.builder()
                .messageType(MessageType.CARD)
                .content(CardMessageContent.builder()
                        .title("title")
                        .cardJson("{\"button\":\"approve\"}")
                        .cardType("interactive")
                        .build())
                .build();

        OfficeMessage restored = converter.toEntity(converter.toPO(source));

        assertThat(restored.getContent()).isInstanceOf(CardMessageContent.class);
        assertThat(((CardMessageContent) restored.getContent()).getTitle()).isEqualTo("title");
        assertThat(((CardMessageContent) restored.getContent()).getCardJson()).isEqualTo("{\"button\":\"approve\"}");
        assertThat(((CardMessageContent) restored.getContent()).getCardType()).isEqualTo("interactive");
    }

    @Test
    void officeMessageConverterRejectsMalformedContentJson() {
        OfficeMessageConverter converter = Mappers.getMapper(OfficeMessageConverter.class);
        OfficeMessagePO po = new OfficeMessagePO();
        po.setMessageType(MessageType.TEXT.name());
        po.setContent("{");

        assertThatThrownBy(() -> converter.toEntity(po)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void messageLookupUsesCurrentTenantAndJdbcScheduler() {
        OfficeMessageMapper messageMapper = mock(OfficeMessageMapper.class);
        OfficeMessageConverter messageConverter = mock(OfficeMessageConverter.class);
        OfficeMessageRepositoryImpl messageRepository =
                new OfficeMessageRepositoryImpl(messageMapper, messageConverter, mock(BlockingIoSchedulers.class));
        OfficeMessagePO po = new OfficeMessagePO();
        po.setId(7L);
        po.setTenantId(41L);
        OfficeMessage entity = OfficeMessage.builder().id(7L).tenantId("41").build();
        when(messageMapper.selectOne(any(Wrapper.class))).thenAnswer(call -> {
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OfficeMessagePO> query = call.getArgument(0);
            assertThat(query.getSqlSegment()).contains("tenant_id", "id");
            assertThat(query.getParamNameValuePairs().values()).contains(41L, 7L);
            return po;
        });
        when(messageConverter.toEntity(po)).thenReturn(entity);
        TenantContext.setTenantId("41");

        StepVerifier.create(messageRepository.findById(7L)).expectNext(entity).verifyComplete();
        verify(messageMapper, never()).selectById(any());
    }

    @Test
    void messageStatusUpdateRejectsOptimisticLockConflict() {
        OfficeMessageMapper messageMapper = mock(OfficeMessageMapper.class);
        OfficeMessageConverter messageConverter = mock(OfficeMessageConverter.class);
        OfficeMessageRepositoryImpl messageRepository =
                new OfficeMessageRepositoryImpl(messageMapper, messageConverter, mock(BlockingIoSchedulers.class));
        OfficeMessage entity = OfficeMessage.builder()
                .id(7L)
                .tenantId("41")
                .sendStatus("FAILED")
                .version(2)
                .build();
        OfficeMessagePO po = new OfficeMessagePO();
        po.setId(7L);
        po.setTenantId(41L);
        po.setVersion(2);
        po.setStatus("FAILED");
        when(messageConverter.toPO(entity)).thenReturn(po);
        when(messageMapper.update(isNull(), any(Wrapper.class))).thenAnswer(call -> {
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<OfficeMessagePO> update = call.getArgument(1);
            assertThat(update.getSqlSegment()).contains("tenant_id", "id", "version");
            assertThat(update.getSqlSet()).contains("send_status", "version = version + 1");
            assertThat(update.getParamNameValuePairs().values()).contains(41L, 7L, 2, "FAILED");
            return 0;
        });
        TenantContext.setTenantId("41");

        StepVerifier.create(messageRepository.updateStatus(entity))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("消息状态更新冲突"))
                .verify();
        verify(messageMapper, never()).selectById(any());
    }

    @Test
    void officeMessagePoUsesMigratedColumnNames() throws NoSuchFieldException {
        assertThat(columnName("recipientType")).isEqualTo("target_type");
        assertThat(columnName("recipientIds")).isEqualTo("target_user_ids");
        assertThat(columnName("status")).isEqualTo("send_status");
        assertThat(columnName("resultMessage")).isEqualTo("failure_reason");
    }

    private String columnName(String fieldName) throws NoSuchFieldException {
        Field field = OfficeMessagePO.class.getDeclaredField(fieldName);
        TableField mapping = field.getAnnotation(TableField.class);
        return mapping == null || mapping.value().isBlank() ? fieldName : mapping.value();
    }

    @Test
    void rejectsMissingOrDifferentTenantBeforeJdbc() {
        StepVerifier.create(repository.findBinding("tenant-a", OfficePlatform.FEISHU, "external"))
                .expectError(BusinessException.class)
                .verify();
        TenantContext.setTenantId("tenant-b");
        StepVerifier.create(repository.findApp("tenant-a", OfficePlatform.FEISHU))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(apps, bindings);
    }

    @Test
    void bindingQueryUsesTenantPlatformIdentityAndJdbcScheduler() {
        TenantContext.setTenantId("tenant-a");
        when(bindings.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            Wrapper<?> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("tenant_id", "platform", "external_user_id", "deleted");
            OfficeBindingPO po = new OfficeBindingPO();
            po.setTenantId("tenant-a");
            po.setPlatform(OfficePlatform.FEISHU);
            po.setExternalUserId("external");
            po.setUserId(7L);
            po.setStatus("APPROVED");
            return po;
        });
        StepVerifier.create(repository.findBinding("tenant-a", OfficePlatform.FEISHU, "external"))
                .assertNext(binding -> {
                    assertThat(binding.getUserId()).isEqualTo(7L);
                    assertThat(binding.getTenantId()).isEqualTo("tenant-a");
                    assertThat(binding.getStatus()).isEqualTo("APPROVED");
                })
                .verifyComplete();
    }

    @Test
    void statusChangeUsesTenantVersionAndPreviousStatus() {
        TenantContext.setTenantId("tenant-a");
        when(bindings.update(isNull(), any(Wrapper.class))).thenAnswer(call -> {
            assertThat(Thread.currentThread().getName()).contains("jdbc");
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<?> query = call.getArgument(1);
            assertThat(query.getSqlSegment()).contains("tenant_id", "id", "version", "status", "deleted");
            assertThat(query.getSqlSet()).contains("version = version + 1", "enabled", "status");
            assertThat(query.getParamNameValuePairs().values()).contains("tenant-a", 9L, "PENDING", "APPROVED");
            return 1;
        });
        StepVerifier.create(repository.changeBindingStatus("tenant-a", 9L, 0, "PENDING", "APPROVED"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void lostApprovalRaceReturnsBusinessError() {
        TenantContext.setTenantId("tenant-a");
        when(bindings.update(isNull(), any(Wrapper.class))).thenReturn(0);
        StepVerifier.create(repository.changeBindingStatus("tenant-a", 9L, 0, "PENDING", "APPROVED"))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void adminWritesRejectCrossTenantBeforeJdbc() {
        TenantContext.setTenantId("tenant-b");
        StepVerifier.create(repository.deleteApp("tenant-a", OfficePlatform.FEISHU, 0))
                .expectError(BusinessException.class)
                .verify();
        StepVerifier.create(repository.changeBindingStatus("tenant-a", 9L, 0, "APPROVED", "REVOKED"))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(apps, bindings);
    }

    @Test
    void listsUseTenantAndBoundedPagination() {
        TenantContext.setTenantId("tenant-a");
        when(bindings.selectList(any(Wrapper.class))).thenAnswer(call -> {
            Wrapper<?> query = call.getArgument(0);
            assertThat(query.getSqlSegment())
                    .contains("tenant_id", "platform", "deleted", "ORDER BY id", "LIMIT 20 OFFSET 20");
            return java.util.List.of();
        });
        StepVerifier.create(repository.listBindings("tenant-a", OfficePlatform.FEISHU, 2, 20))
                .expectNext(java.util.List.of())
                .verifyComplete();
        StepVerifier.create(repository.listBindings("tenant-a", OfficePlatform.FEISHU, 1, 101))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void deleteIsVersionedLogicalDeletion() {
        TenantContext.setTenantId("tenant-a");
        when(apps.update(isNull(), any(Wrapper.class))).thenAnswer(call -> {
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<?> query = call.getArgument(1);
            assertThat(query.getSqlSegment()).contains("tenant_id", "platform", "version", "deleted");
            assertThat(query.getSqlSet()).contains("deleted", "enabled", "version = version + 1");
            return 1;
        });
        StepVerifier.create(repository.deleteApp("tenant-a", OfficePlatform.FEISHU, 0))
                .expectNext(true)
                .verifyComplete();
    }
}
