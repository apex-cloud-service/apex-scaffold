package com.apex.scaffold.knowledgeservice.interfaces.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.knowledge.application.dto.KbAskRequest;
import com.apex.scaffold.knowledge.application.dto.KbSearchResultDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeChunkDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeDocumentDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeDocumentDetailDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeManualDocumentRequest;
import com.apex.scaffold.knowledge.application.dto.KnowledgeSpaceDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeSpaceSaveRequest;
import com.apex.scaffold.knowledge.application.service.KnowledgeChatAppService;
import com.apex.scaffold.knowledge.application.service.KnowledgeDocumentAppService;
import com.apex.scaffold.knowledge.application.service.KnowledgeSearchAppService;
import com.apex.scaffold.knowledge.application.service.KnowledgeSpaceAppService;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledgeservice.interfaces.router.KnowledgeChatRouter;
import com.apex.scaffold.knowledgeservice.interfaces.router.KnowledgeDocumentRouter;
import com.apex.scaffold.knowledgeservice.interfaces.router.KnowledgeSearchRouter;
import com.apex.scaffold.knowledgeservice.interfaces.router.KnowledgeSpaceRouter;
import com.apex.scaffold.knowledgeservice.interfaces.util.KnowledgeRequestHandlerUtil;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 知识库 Handler 单元测试：经真实 RouterFunction 分发（bindToRouterFunction，
 * 不含安全链与 AOP 切面），覆盖参数解析分支、multipart 上传与错误映射。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeHandlersUnitTest {

    @Mock
    private KnowledgeSpaceAppService spaceAppService;

    @Mock
    private KnowledgeDocumentAppService documentAppService;

    @Mock
    private KnowledgeSearchAppService searchAppService;

    @Mock
    private KnowledgeChatAppService chatAppService;

    @Mock
    private FilePart filePart;

    private WebTestClient client;

    private KnowledgeProperties properties = new KnowledgeProperties();

    @BeforeEach
    void setUpClient() {
        KnowledgeRequestHandlerUtil requestUtil = new KnowledgeRequestHandlerUtil(
                Validation.buildDefaultValidatorFactory().getValidator());
        KnowledgeSpaceHandler spaceHandler = new KnowledgeSpaceHandler(spaceAppService, requestUtil);
        KnowledgeDocumentHandler documentHandler =
                new KnowledgeDocumentHandler(documentAppService, requestUtil, properties);
        KnowledgeSearchHandler searchHandler =
                new KnowledgeSearchHandler(searchAppService, spaceAppService, requestUtil);
        KnowledgeChatHandler chatHandler = new KnowledgeChatHandler(chatAppService, requestUtil);
        // 伪身份过滤器：模拟独立验签过滤器写入身份与租户（bindToRouterFunction 不含安全链）
        org.springframework.web.server.WebFilter fakeIdentity = (exchange, chain) -> {
            var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    new com.apex.scaffold.knowledgeservice.interfaces.security.AuthenticatedUser(7L, "t1"),
                    null,
                    List.of());
            return com.apex.scaffold.common.context.TenantContext.withTenant(chain.filter(exchange))
                    .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant("t1"))
                    .contextWrite(
                            org.springframework.security.core.context.ReactiveSecurityContextHolder.withAuthentication(
                                    authentication));
        };
        client = WebTestClient.bindToRouterFunction(new KnowledgeSpaceRouter()
                        .knowledgeSpaceRoutes(spaceHandler)
                        .and(new KnowledgeDocumentRouter().knowledgeDocumentRoutes(documentHandler))
                        .and(new KnowledgeSearchRouter().knowledgeSearchRoutes(searchHandler))
                        .and(new KnowledgeChatRouter().knowledgeChatRoutes(chatHandler)))
                .webFilter(fakeIdentity)
                .build();
    }

    /**
     * 构建空间 DTO。
     *
     * @param id ID
     * @param name 名称
     * @return 空间 DTO
     */
    private KnowledgeSpaceDTO space(long id, String name) {
        return new KnowledgeSpaceDTO(id, name, "描述", 1, null, null);
    }

    /**
     * 构建文档 DTO。
     *
     * @return 文档 DTO
     */
    private KnowledgeDocumentDTO document() {
        return new KnowledgeDocumentDTO(
                9L, 1L, "文档", "UPLOAD", "application/pdf", 10L, "PARSED", null, 2, "INDEXED", null, null);
    }

    @Test
    void spaceEndpointsMapThroughRouter() {
        when(spaceAppService.findPage(eq("产品"), eq(2), eq(50)))
                .thenReturn(
                        Mono.just(com.apex.scaffold.common.dto.PageResponse.of(List.of(space(1L, "空间")), 1L, 2, 50)));
        client.get()
                .uri("/admin-api/v1/kb/spaces?page=2&size=50&keyword=产品")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.list[0].name")
                .isEqualTo("空间");

        // 分页参数缺省时走默认值
        when(spaceAppService.findPage(eq(null), eq(1), eq(20)))
                .thenReturn(Mono.just(com.apex.scaffold.common.dto.PageResponse.of(List.of(), 0L, 1, 20)));
        client.get().uri("/admin-api/v1/kb/spaces").exchange().expectStatus().isOk();

        when(spaceAppService.findById(1L)).thenReturn(Mono.just(space(1L, "空间")));
        client.get()
                .uri("/admin-api/v1/kb/spaces/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.id")
                .isEqualTo(1);

        when(spaceAppService.create(any(KnowledgeSpaceSaveRequest.class))).thenReturn(Mono.just(space(2L, "新建")));
        client.post()
                .uri("/admin-api/v1/kb/spaces")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"新建空间\"}")
                .exchange()
                .expectStatus()
                .isOk();

        when(spaceAppService.update(eq(1L), any(KnowledgeSpaceSaveRequest.class)))
                .thenReturn(Mono.just(space(1L, "更新")));
        client.put()
                .uri("/admin-api/v1/kb/spaces/1")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"更新空间\"}")
                .exchange()
                .expectStatus()
                .isOk();

        when(spaceAppService.delete(1L)).thenReturn(Mono.just(true));
        client.delete()
                .uri("/admin-api/v1/kb/spaces/1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data")
                .isEqualTo(true);
    }

    @Test
    void spaceErrorMapsToBadRequest() {
        when(spaceAppService.findById(404L)).thenReturn(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "不存在")));
        client.get()
                .uri("/admin-api/v1/kb/spaces/404")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    void documentEndpointsMapThroughRouter() {
        when(documentAppService.findPage(eq(1L), eq("PARSED"), eq("文档"), anyInt(), anyInt()))
                .thenReturn(Mono.just(com.apex.scaffold.common.dto.PageResponse.of(List.of(document()), 1L, 1, 20)));
        when(documentAppService.findPage(eq(null), eq(null), eq(null), anyInt(), anyInt()))
                .thenReturn(Mono.just(com.apex.scaffold.common.dto.PageResponse.of(List.of(), 0L, 1, 20)));
        client.get()
                .uri("/admin-api/v1/kb/documents?spaceId=1&parseStatus=PARSED&keyword=文档&page=1&size=20")
                .exchange()
                .expectStatus()
                .isOk();
        client.get().uri("/admin-api/v1/kb/documents").exchange().expectStatus().isOk();

        when(documentAppService.findById(9L))
                .thenReturn(Mono.just(
                        new KnowledgeDocumentDetailDTO(document(), List.of(new KnowledgeChunkDTO(3L, 0, "分块", 2)))));
        client.get()
                .uri("/admin-api/v1/kb/documents/9")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.chunks[0].seq")
                .isEqualTo(0);

        when(documentAppService.reindex(9L))
                .thenReturn(Mono.just(new KnowledgeDocumentDTO(
                        9L, 1L, "文档", "UPLOAD", "application/pdf", 10L, "PENDING", null, 0, "PENDING", null, null)));
        client.post()
                .uri("/admin-api/v1/kb/documents/9/reindex")
                .exchange()
                .expectStatus()
                .isOk();

        when(documentAppService.delete(9L)).thenReturn(Mono.just(true));
        client.delete()
                .uri("/admin-api/v1/kb/documents/9")
                .exchange()
                .expectStatus()
                .isOk();

        when(documentAppService.registerManual(any(KnowledgeManualDocumentRequest.class)))
                .thenReturn(Mono.just(new KnowledgeDocumentDTO(
                        11L, 1L, "录入", "MANUAL", "text/plain", 8L, "PARSED", null, 0, "PENDING", null, null)));
        client.post()
                .uri("/admin-api/v1/kb/manual-documents")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"spaceId\":1,\"title\":\"录入\",\"content\":\"正文\"}")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void documentValidationFailureMapsToBadRequest() {
        client.post()
                .uri("/admin-api/v1/kb/manual-documents")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"spaceId\":1,\"title\":\"标题\",\"content\":\"\"}")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void uploadRegistersFilePartAndRejectsMissingField() {
        // 手工构造标准 multipart 字节（ASCII 文件名），规避 Part 序列化往返的不确定性
        String boundary = "test-boundary";
        String body = "--"
                + boundary
                + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"manual.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n"
                + "\r\n"
                + "manual-bytes"
                + "\r\n"
                + "--"
                + boundary
                + "--\r\n";
        when(documentAppService.registerUpload(eq(1L), eq("manual.pdf"), eq("application/pdf"), any(byte[].class)))
                .thenReturn(Mono.just(new KnowledgeDocumentDTO(
                        10L, 1L, "手册", "UPLOAD", "application/pdf", 4L, "PENDING", null, 0, "NONE", null, null)));

        client.post()
                .uri("/admin-api/v1/kb/spaces/1/documents")
                .contentType(
                        org.springframework.http.MediaType.parseMediaType("multipart/form-data; boundary=" + boundary))
                .bodyValue(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.id")
                .isEqualTo(10);
    }

    @Test
    void uploadWithoutFileFieldIsRejected() {
        client.post()
                .uri("/admin-api/v1/kb/spaces/1/documents")
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .bodyValue("no-parts")
                .exchange()
                .expectStatus()
                .value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isGreaterThanOrEqualTo(400));
    }

    @Test
    void searchRequiresKeywordAndParsesSpaceIds() {
        when(searchAppService.search(any())).thenReturn(Mono.just(new KbSearchResultDTO(1L, false, List.of())));
        client.get()
                .uri("/api/v1/kb/search?keyword=质量&spaceIds=1,2&page=1&size=5")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.total")
                .isEqualTo(1);

        // 无 spaceIds / page/size 缺省分支
        client.get()
                .uri("/api/v1/kb/search?keyword=质量")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void searchMissingKeywordMapsToBadRequest() {
        client.get()
                .uri("/api/v1/kb/search")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());
    }

    @Test
    void searchSpacesListMapsThrough() {
        when(spaceAppService.listActive()).thenReturn(Mono.just(List.of(space(1L, "空间"))));
        client.get()
                .uri("/api/v1/kb/spaces")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data[0].name")
                .isEqualTo("空间");
    }

    @Test
    void chatEndpointsMapThroughRouter() {
        when(chatAppService.listConversations(anyLong(), anyInt(), anyInt()))
                .thenReturn(Mono.just(com.apex.scaffold.common.dto.PageResponse.of(List.of(), 0L, 1, 20)));
        client.get()
                .uri("/api/v1/kb/chat/conversations?page=1&size=10")
                .exchange()
                .expectStatus()
                .isOk();

        when(chatAppService.listMessages(anyLong(), anyLong(), anyInt())).thenReturn(Mono.just(List.of()));
        client.get()
                .uri("/api/v1/kb/chat/conversations/5/messages?limit=10")
                .exchange()
                .expectStatus()
                .isOk();

        when(chatAppService.deleteConversation(anyLong(), anyLong())).thenReturn(Mono.just(true));
        client.delete()
                .uri("/api/v1/kb/chat/conversations/5")
                .exchange()
                .expectStatus()
                .isOk();

        when(chatAppService.ask(anyLong(), any(KbAskRequest.class)))
                .thenReturn(Flux.just(
                        ServerSentEvent.builder("[]").event("citations").build()));
        client.post()
                .uri("/api/v1/kb/chat/ask")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"question\":\"提问\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(org.springframework.http.MediaType.TEXT_EVENT_STREAM);
    }
}
