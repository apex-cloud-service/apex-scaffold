package com.apex.scaffold.knowledgeservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 知识库服务上下文回归测试：安全链 401/403 fail-closed 与已认证只读 200。
 *
 * <p>权限中心未配置时 {@code KnowledgePermissionClient} fail-closed 拒绝一切受
 * {@code @RequirePermission} 保护的端点；未配置 reader 凭据属于部署校验失败路径，
 * 与 office/workflow 服务的口径一致。写路径的成功用例由单测与联调环境覆盖。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@SpringBootTest(classes = KnowledgeServiceApplication.class)
@AutoConfigureWebTestClient
class KnowledgeServiceContextTest {

    /** 与测试 application.yml 中 HMAC 过渡密钥保持一致。 */
    private static final String HMAC_SECRET = "test-knowledge-hmac-secret-32-bytes!!";

    private final WebTestClient client;

    /**
     * 构造器注入 WebTestClient（spring.test.constructor.autowire.mode=all，禁用字段 @Autowired）。
     *
     * @param client 测试客户端
     */
    public KnowledgeServiceContextTest(WebTestClient client) {
        this.client = client;
    }

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
     * 未携带令牌的请求返回 401。
     */
    @Test
    void unauthenticatedRequestIsRejected() {
        client.get().uri("/api/v1/kb/spaces").exchange().expectStatus().isEqualTo(401);
    }

    /**
     * 无效令牌（错误密钥签名）返回 401，不因验签失败放行。
     */
    @Test
    void invalidTokenIsRejected() {
        SecretKey other = Keys.hmacShaKeyFor("another-secret-key-with-32-bytes!".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .claims(Map.of("userId", "1", "tenantId", "t1", "tokenType", "ACCESS"))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(other)
                .compact();
        client.get()
                .uri("/api/v1/kb/spaces")
                .header("Authorization", "Bearer " + forged)
                .exchange()
                .expectStatus()
                .isEqualTo(401);
    }

    /**
     * 已认证访问无权限注解的业务端点：空间列表返回成功包装。
     */
    @Test
    void authenticatedSpacesListReturnsSuccess() {
        client.get()
                .uri("/api/v1/kb/spaces")
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
     * 已认证访问会话分页（无权限注解，按用户归属隔离）。
     */
    @Test
    void authenticatedConversationsReturnsSuccess() {
        client.get()
                .uri("/api/v1/kb/chat/conversations")
                .header("Authorization", "Bearer " + bearerToken())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    /**
     * 权限中心未配置时管理端空间创建 fail-closed 拒绝（403）。
     */
    @Test
    void adminWriteIsDeniedWhenAuthorizationUnconfigured() {
        client.post()
                .uri("/admin-api/v1/kb/spaces")
                .header("Authorization", "Bearer " + bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"测试空间\"}")
                .exchange()
                .expectStatus()
                .isEqualTo(403);
    }

    /**
     * 知识检索端点同样受权限注解保护（fail-closed 403）。
     */
    @Test
    void searchEndpointIsDeniedWhenAuthorizationUnconfigured() {
        client.get()
                .uri("/api/v1/kb/search?keyword=质量")
                .header("Authorization", "Bearer " + bearerToken())
                .exchange()
                .expectStatus()
                .isEqualTo(403);
    }

    /**
     * 刷新令牌（tokenType=REFRESH）不得用于业务访问。
     */
    @Test
    void refreshTokenIsRejected() {
        SecretKey key = Keys.hmacShaKeyFor(HMAC_SECRET.getBytes(StandardCharsets.UTF_8));
        String refresh = Jwts.builder()
                .claims(Map.of("userId", "1", "tenantId", "t1", "tokenType", "REFRESH"))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();
        client.get()
                .uri("/api/v1/kb/spaces")
                .header("Authorization", "Bearer " + refresh)
                .exchange()
                .expectStatus()
                .isEqualTo(401);
    }
}
