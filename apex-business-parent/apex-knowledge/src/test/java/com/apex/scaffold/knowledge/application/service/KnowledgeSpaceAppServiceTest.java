package com.apex.scaffold.knowledge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.knowledge.application.assembler.KnowledgeAssembler;
import com.apex.scaffold.knowledge.application.dto.KnowledgeSpaceDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeSpaceSaveRequest;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeSpaceRepository;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import com.apex.scaffold.knowledge.domain.model.KnowledgeSpace;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 知识空间应用服务测试：唯一性约束与删除守卫。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeSpaceAppServiceTest {

    @Mock
    private KnowledgeSpaceRepository spaceRepository;

    @Mock
    private KnowledgeDocumentRepository documentRepository;

    private KnowledgeSpaceAppService service;

    /**
     * 组装被测服务（@Mock 字段由 MockitoExtension 在每个测试前注入）。
     */
    @BeforeEach
    void setUpService() {
        service = new KnowledgeSpaceAppService(spaceRepository, documentRepository, new KnowledgeAssembler());
    }

    @Test
    void createRejectsDuplicateName() {
        when(spaceRepository.existsByName("重复空间", null)).thenReturn(Mono.just(true));

        StepVerifier.create(service.create(new KnowledgeSpaceSaveRequest("重复空间", null, null)))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void createDefaultsStatusToOne() {
        when(spaceRepository.existsByName("新空间", null)).thenReturn(Mono.just(false));
        when(spaceRepository.insert(any(KnowledgeSpace.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.create(new KnowledgeSpaceSaveRequest("新空间", "描述", null)))
                .assertNext(dto -> assertThat(dto.status()).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void updateRejectsDuplicateNameAndMissingSpace() {
        when(spaceRepository.findById(1L)).thenReturn(Mono.just(activeSpace()));
        when(spaceRepository.existsByName("新名", 1L)).thenReturn(Mono.just(true));

        StepVerifier.create(service.update(1L, new KnowledgeSpaceSaveRequest("新名", null, null)))
                .expectError(BusinessException.class)
                .verify();

        when(spaceRepository.findById(404L)).thenReturn(Mono.empty());
        StepVerifier.create(service.update(404L, new KnowledgeSpaceSaveRequest("任意", null, null)))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void updatePreservesStatusWhenRequestOmitsIt() {
        when(spaceRepository.findById(1L)).thenReturn(Mono.just(activeSpace()));
        when(spaceRepository.existsByName("新名", 1L)).thenReturn(Mono.just(false));
        when(spaceRepository.update(any(KnowledgeSpace.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.update(1L, new KnowledgeSpaceSaveRequest("新名", "描述", null)))
                .assertNext(dto -> assertThat(dto.status()).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void deleteRejectsSpaceWithDocuments() {
        when(spaceRepository.findById(1L)).thenReturn(Mono.just(activeSpace()));
        when(documentRepository.findPage(1L, null, null, 1, 1))
                .thenReturn(Mono.just(PageResponse.of(List.of(documentStub()), 1L, 1, 1)));

        StepVerifier.create(service.delete(1L))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    void deleteProceedsWhenSpaceEmpty() {
        when(spaceRepository.findById(1L)).thenReturn(Mono.just(activeSpace()));
        when(documentRepository.findPage(1L, null, null, 1, 1))
                .thenReturn(Mono.just(PageResponse.of(List.of(), 0L, 1, 1)));
        when(spaceRepository.deleteById(1L)).thenReturn(Mono.just(true));

        StepVerifier.create(service.delete(1L)).expectNext(true).verifyComplete();
    }

    @Test
    void findPageAndListActiveMapToDto() {
        when(spaceRepository.findPage("关键词", 1, 10))
                .thenReturn(Mono.just(PageResponse.of(List.of(activeSpace()), 1L, 1, 10)));
        StepVerifier.create(service.findPage("关键词", 1, 10))
                .assertNext(page -> assertThat(page.getList().getFirst().name()).isEqualTo("正常空间"))
                .verifyComplete();

        when(spaceRepository.findAllActive()).thenReturn(Mono.just(List.of(activeSpace())));
        StepVerifier.create(service.listActive())
                .assertNext(spaces -> assertThat(spaces).hasSize(1))
                .verifyComplete();

        when(spaceRepository.findById(1L)).thenReturn(Mono.just(activeSpace()));
        StepVerifier.create(service.findById(1L))
                .assertNext(dto -> assertThat(dto).isInstanceOf(KnowledgeSpaceDTO.class))
                .verifyComplete();

        when(spaceRepository.findById(99L)).thenReturn(Mono.empty());
        StepVerifier.create(service.findById(99L))
                .expectError(BusinessException.class)
                .verify();
    }

    /**
     * 构建正常状态空间。
     *
     * @return 空间模型
     */
    private KnowledgeSpace activeSpace() {
        return KnowledgeSpace.builder()
                .id(1L)
                .name("正常空间")
                .description("描述")
                .status(1)
                .build();
    }

    /**
     * 构建文档占位（分页非空判断用）。
     *
     * @return 文档模型
     */
    private KnowledgeDocument documentStub() {
        return KnowledgeDocument.builder()
                .id(2L)
                .spaceId(1L)
                .title("文档")
                .sourceType(KnowledgeDocument.SourceType.MANUAL)
                .parseStatus(KnowledgeDocument.ParseStatus.PARSED)
                .indexStatus(KnowledgeDocument.IndexStatus.NONE)
                .chunkCount(0)
                .build();
    }
}
