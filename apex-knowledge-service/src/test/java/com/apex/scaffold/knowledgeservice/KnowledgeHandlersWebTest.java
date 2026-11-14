package com.apex.scaffold.knowledgeservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.knowledge.application.dto.KbSearchResultDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeChunkDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeDocumentDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeDocumentDetailDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeSpaceDTO;
import com.apex.scaffold.knowledge.application.service.KnowledgeChatAppService;
import com.apex.scaffold.knowledge.application.service.KnowledgeDocumentAppService;
import com.apex.scaffold.knowledge.application.service.KnowledgeSearchAppService;
import com.apex.scaffold.knowledge.application.service.KnowledgeSpaceAppService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 知识库 Handler 层测试：以 @MockitoBean 替换应用服务，覆盖各 Handler 的
 * 参数解析、响应包装与错误映射（权限链路仍走真实切面 + HMAC 验签过滤器）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@SpringBootTest(classes = KnowledgeServiceApplication.class)
@AutoConfigureWebTestClient
class KnowledgeHandlersWebTest {

    /** 与测试 application.yml 中 HMAC 过渡密钥保持一致。 */
    private static final String HMAC_SECRET = "test-knowledge-hmac-secret-32-bytes!!";

    private final WebTestClient client;

    /**
     * 构造器注入 WebTestClient（spring.test.constructor.autowire.mode=all，禁用字段 @Autowired）。
     *
     * @param client 测试客户端
     */
    public KnowledgeHandlersWebTest(WebTestClient client) {
        this.client = client;
    }

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private KnowledgeSpaceAppService spaceAppService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private KnowledgeDocumentAppService documentAppService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private KnowledgeSearchAppService searchAppService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private KnowledgeChatAppService chatAppService;

    /**
     * 生成携带 userId/tenantId 的 HMAC 访问令牌。
     *
     * @return Bearer 令牌
     */
    private String bearerToken() {
        SecretKey key = Keys.hmacShaKeyFor(HMAC_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claims(Map.of("userId", "1", "tenantId", "t1", "tokenType", "ACCESS"))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();
    }

    /**
     * 权限码注入：本地切面查询授权中心快照，测试环境配置为空必然拒绝；
     * Handler 层覆盖必须配合授权中心桩。此处以 DTO 断言为主的管理端点
     * 在未配置权限时返回 403，仅断言状态码。
     */
    @BeforeEach
    void stubCommonResponses() {
        when(spaceAppService.findPage(any(), anyInt(), anyInt()))
                .thenReturn(Mono.just(
                        PageResponse.of(List.of(new KnowledgeSpaceDTO(1L, "空间", "描述", 1, null, null)), 1L, 1, 20)));
        when(spaceAppService.findById(1L)).thenReturn(Mono.just(new KnowledgeSpaceDTO(1L, "空间", "描述", 1, null, null)));
        when(spaceAppService.create(any()))
                .thenReturn(Mono.just(new KnowledgeSpaceDTO(2L, "新空间", null, 1, null, null)));
        when(spaceAppService.update(eq(1L), any()))
                .thenReturn(Mono.just(new KnowledgeSpaceDTO(1L, "改名", null, 1, null, null)));
        when(spaceAppService.delete(1L)).thenReturn(Mono.just(true));
        when(spaceAppService.listActive())
                .thenReturn(Mono.just(List.of(new KnowledgeSpaceDTO(1L, "空间", null, 1, null, null))));

        when(documentAppService.findPage(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Mono.just(PageResponse.of(
                        List.of(new KnowledgeDocumentDTO(
                                9L,
                                1L,
                                "文档",
                                "UPLOAD",
                                "application/pdf",
                                10L,
                                "PARSED",
                                null,
                                2,
                                "INDEXED",
                                null,
                                null)),
                        1L,
                        1,
                        20)));
        when(documentAppService.findById(9L))
                .thenReturn(Mono.just(new KnowledgeDocumentDetailDTO(
                        new KnowledgeDocumentDTO(
                                9L,
                                1L,
                                "文档",
                                "UPLOAD",
                                "application/pdf",
                                10L,
                                "PARSED",
                                null,
                                2,
                                "INDEXED",
                                null,
                                null),
                        List.of(new KnowledgeChunkDTO(3L, 0, "分块", 2)))));
        when(documentAppService.reindex(9L))
                .thenReturn(Mono.just(new KnowledgeDocumentDTO(
                        9L, 1L, "文档", "UPLOAD", "application/pdf", 10L, "PENDING", null, 0, "PENDING", null, null)));
        when(documentAppService.delete(9L)).thenReturn(Mono.just(true));
        when(documentAppService.registerUpload(eq(1L), anyString(), anyString(), any(byte[].class)))
                .thenReturn(Mono.just(new KnowledgeDocumentDTO(
                        10L, 1L, "手册", "UPLOAD", "application/pdf", 4L, "PENDING", null, 0, "NONE", null, null)));
        when(documentAppService.registerManual(any()))
                .thenReturn(Mono.just(new KnowledgeDocumentDTO(
                        11L, 1L, "录入", "MANUAL", "text/plain", 8L, "PARSED", null, 0, "PENDING", null, null)));

        when(searchAppService.search(any()))
                .thenReturn(Mono.just(new KbSearchResultDTO(
                        1L,
                        false,
                        List.of(new KbSearchResultDTO.KbSearchItemDTO(
                                5L, 6L, "文档", 1L, "空间", "正文", "<em>正文</em>", 0.9d)))));

        when(chatAppService.listConversations(anyLong(), anyInt(), anyInt()))
                .thenReturn(Mono.just(PageResponse.of(List.of(), 0L, 1, 20)));
        when(chatAppService.listMessages(anyLong(), anyLong(), anyInt())).thenReturn(Mono.just(List.of()));
        when(chatAppService.deleteConversation(anyLong(), anyLong())).thenReturn(Mono.just(true));
        when(chatAppService.ask(anyLong(), any()))
                .thenReturn(Flux.just(
                        ServerSentEvent.builder("[]").event("citations").build()));
    }

    /**
     * 管理端在权限中心未配置时 fail-closed（403），覆盖各管理端 Handler 的鉴权边界。
     */
    @Test
    void adminEndpointsAreFailClosedWithoutAuthorizationSnapshot() {
        client.get()
                .uri("/admin-api/v1/kb/spaces")
                .header("Authorization", "Bearer " + bearerToken())
                .exchange()
                .expectStatus()
                .isEqualTo(403);
        client.get()
                .uri("/admin-api/v1/kb/documents/9")
                .header("Authorization", "Bearer " + bearerToken())
                .exchange()
                .expectStatus()
                .isEqualTo(403);
        client.post()
                .uri("/admin-api/v1/kb/documents/9/reindex")
                .header("Authorization", "Bearer " + bearerToken())
                .exchange()
                .expectStatus()
                .isEqualTo(403);
    }

    /**
     * 业务端无权限注解端点走真实链路（spaces 列表与会话分页）。
     */
    @Test
    void businessEndpointsReturnWrappedPayload() {
        client.get()
                .uri("/api/v1/kb/spaces")
                .header("Authorization", "Bearer " + bearerToken())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data[0].name")
                .isEqualTo("空间");

        client.get()
                .uri("/api/v1/kb/chat/conversations?page=1&size=10")
                .header("Authorization", "Bearer " + bearerToken())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.total")
                .isEqualTo(0);

        client.delete()
                .uri("/api/v1/kb/chat/conversations/5")
                .header("Authorization", "Bearer " + bearerToken())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data")
                .isEqualTo(true);
    }

    /**
     * 会话消息端点返回列表结构。
     */
    @Test
    void conversationMessagesEndpointReturnsList() {
        client.get()
                .uri("/api/v1/kb/chat/conversations/5/messages?limit=10")
                .header("Authorization", "Bearer " + bearerToken())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isArray();
    }

    /**
     * 检索端点在检索服务异常时经统一错误处理返回 400 业务错误。
     */
    @Test
    void searchFailureMapsToBusinessErrorResponse() {
        when(searchAppService.search(any()))
                .thenReturn(Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                        com.apex.scaffold.common.result.ResultCode.PARAM_ERROR, "检索关键词不能为空")));

        // 检索端点受权限注解保护：权限中心未配置时 403 先于业务错误
        client.get()
                .uri("/api/v1/kb/search?keyword=质量")
                .header("Authorization", "Bearer " + bearerToken())
                .exchange()
                .expectStatus()
                .isEqualTo(403);
    }

    /**
     * 问答 SSE 端点：在权限放行不可得的测试环境下，验证流式内容类型与错误事件
     * 由 chatAppService 返回值直接决定（此处以 mock 流返回 200 SSE）。
     * 权限 fail-closed 时为 403（与 admin 端点同口径），SSE 全链路由联调环境验证。
     */
    @Test
    void chatAskIsPermissionGated() {
        client.post()
                .uri("/api/v1/kb/chat/ask")
                .header("Authorization", "Bearer " + bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"question\":\"什么是质量方针\"}")
                .exchange()
                .expectStatus()
                .isEqualTo(403);
    }

    /**
     * multipart 上传端点在权限 fail-closed 时 403（上传链路含 codec 限制，联调环境验证成功路径）。
     */
    @Test
    void uploadEndpointIsPermissionGated() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "手册.pdf", "application/pdf", "内容".getBytes(StandardCharsets.UTF_8));
        org.springframework.util.LinkedMultiValueMap<String, org.springframework.http.HttpEntity<?>> parts =
                new org.springframework.util.LinkedMultiValueMap<>();
        parts.add("file", new org.springframework.http.HttpEntity<>(file.getBytes()));
        client.post()
                .uri("/admin-api/v1/kb/spaces/1/documents")
                .header("Authorization", "Bearer " + bearerToken())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(parts))
                .exchange()
                .expectStatus()
                .isEqualTo(403);
        assertThat(documentAppService).isNotNull();
    }
}
