package com.apex.scaffold.knowledgeservice.interfaces.handler;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.knowledge.application.dto.KnowledgeDocumentCleanupDTO;
import com.apex.scaffold.knowledge.application.service.KnowledgeDocumentAppService;
import com.apex.scaffold.knowledge.application.service.KnowledgeDocumentCleanupService;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledgeservice.interfaces.router.KnowledgeDocumentRouter;
import com.apex.scaffold.knowledgeservice.interfaces.util.KnowledgeRequestHandlerUtil;
import jakarta.validation.Validation;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/** 路由/参数/JSON契约测试，不含真实安全链或AOP授权。 */
class KnowledgeDocumentCleanupHandlerTest {
    private KnowledgeDocumentCleanupService service;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        service = mock(KnowledgeDocumentCleanupService.class);
        var util = new KnowledgeRequestHandlerUtil(
                Validation.buildDefaultValidatorFactory().getValidator());
        var handler = new KnowledgeDocumentHandler(
                mock(KnowledgeDocumentAppService.class), util, new KnowledgeProperties(), service);
        client = WebTestClient.bindToRouterFunction(new KnowledgeDocumentRouter().knowledgeDocumentRoutes(handler))
                .build();
    }

    private KnowledgeDocumentCleanupDTO dto() {
        return new KnowledgeDocumentCleanupDTO(
                9L, "文档", "FAILED", "索引未完成", 2, LocalDateTime.of(2026, 11, 15, 10, 0), null);
    }

    @Test
    void pageAndDetailKeepApprovedFrontendContract() {
        when(service.page(1, 20)).thenReturn(Mono.just(PageResponse.of(List.of(dto()), 1L, 1, 20)));
        client.get()
                .uri("/admin-api/v1/kb/document-cleanups")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.list[0].documentId")
                .isEqualTo(9)
                .jsonPath("$.data.list[0].status")
                .isEqualTo("FAILED")
                .jsonPath("$.data.total")
                .isEqualTo(1);
        when(service.detail(9L)).thenReturn(Mono.just(dto()));
        client.get()
                .uri("/admin-api/v1/kb/document-cleanups/9")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.attempts")
                .isEqualTo(2)
                .jsonPath("$.data.errorMessage")
                .isEqualTo("索引未完成")
                .jsonPath("$.data.provider")
                .doesNotExist()
                .jsonPath("$.data.tenantId")
                .doesNotExist();
    }

    @Test
    void retryReturnsAcceptedBooleanAndInvalidParametersRejectBeforeService() {
        when(service.retry(9L)).thenReturn(Mono.just(true));
        client.post()
                .uri("/admin-api/v1/kb/document-cleanups/9/retry")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data")
                .isEqualTo(true);
        for (String path : List.of("?page=0", "?size=101", "?page=bad")) {
            client.get()
                    .uri("/admin-api/v1/kb/document-cleanups" + path)
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
        }
        client.get()
                .uri("/admin-api/v1/kb/document-cleanups/invalid")
                .exchange()
                .expectStatus()
                .isBadRequest();
        client.post()
                .uri("/admin-api/v1/kb/document-cleanups/0/retry")
                .exchange()
                .expectStatus()
                .isBadRequest();
        verify(service, never()).page(anyInt(), anyInt());
    }
}
