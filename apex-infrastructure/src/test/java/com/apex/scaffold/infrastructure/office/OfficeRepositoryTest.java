package com.apex.scaffold.infrastructure.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.infrastructure.office.persistence.*;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import reactor.test.StepVerifier;

class OfficeRepositoryTest {
    private final OfficeAppMapper apps = mock(OfficeAppMapper.class);
    private final OfficeBindingMapper bindings = mock(OfficeBindingMapper.class);
    private final OfficeRepositoryImpl repository =
            new OfficeRepositoryImpl(apps, bindings, Mappers.getMapper(OfficePersistenceConverter.class));

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
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
