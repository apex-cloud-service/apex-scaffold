package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

import com.apex.scaffold.application.dto.admin.GenConfigRequest;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.generator.GenConfigEntity;
import com.apex.scaffold.domain.model.generator.GenHistoryEntity;
import com.apex.scaffold.domain.repository.GenConfigRepository;
import com.apex.scaffold.domain.repository.GenDatasourceRepository;
import com.apex.scaffold.domain.repository.GenHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class GeneratorServicesTest {
    @Mock GenConfigRepository configRepository;
    @Mock GenDatasourceRepository datasourceRepository;
    @Mock GenHistoryRepository historyRepository;
    @InjectMocks GenConfigService configService;

    @org.mockito.Spy ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void configQueryReadsPersistedModel() {
        GenConfigEntity entity = new GenConfigEntity();
        entity.setTenantId("tenant-a");
        entity.setTableNames("[\"sys_user\"]");
        lenient().when(configRepository.findById(2L)).thenReturn(Mono.just(entity));
        StepVerifier.create(configService.findById(2L).contextWrite(TenantContext.withTenant("tenant-a")))
                .assertNext(dto -> assertThat(dto.getTableName()).isEqualTo("sys_user"))
                .verifyComplete();
    }

    @Test
    void historyQueryReturnsHistoryRecords() {
        GenConfigEntity config = new GenConfigEntity();
        config.setId(2L);
        config.setTenantId("tenant-a");
        GenHistoryEntity history = new GenHistoryEntity();
        history.setId(7L);
        history.setConfigId(2L);
        history.setTenantId("tenant-a");
        history.setStatus("SUCCESS");
        lenient().when(configRepository.findById(2L)).thenReturn(Mono.just(config));
        lenient().when(historyRepository.findByConfigId(2L)).thenReturn(Mono.just(java.util.List.of(history)));

        StepVerifier.create(configService.listHistory(2L, 1, 20)
                        .contextWrite(TenantContext.withTenant("tenant-a")))
                .assertNext(page -> {
                    assertThat(page.getTotal()).isEqualTo(1L);
                    assertThat(page.getRecords()).singleElement().satisfies(item -> {
                        assertThat(item.getId()).isEqualTo(7L);
                        assertThat(item.getStatus()).isEqualTo("SUCCESS");
                    });
                })
                .verifyComplete();
    }

    @Test
    void historyQueryRejectsInvalidPage() {
        assertThatThrownBy(() -> configService.listHistory(2L, 0, 20))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void outputWhitelistRejectsSiblingPrefix() {
        GenConfigRequest request = new GenConfigRequest();
        request.setOutputPath("/tmp/apex-gen-evil/test");
        StepVerifier.create(configService.create(request).contextWrite(TenantContext.withTenant("tenant-a")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(error.getMessage()).doesNotContain("TODO");
                })
                .verify();
    }
}
