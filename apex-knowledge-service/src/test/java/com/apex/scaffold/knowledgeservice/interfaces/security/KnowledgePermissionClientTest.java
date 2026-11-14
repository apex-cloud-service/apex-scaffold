package com.apex.scaffold.knowledgeservice.interfaces.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.knowledgeservice.interfaces.security.KnowledgePermissionClient.Snapshot;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/**
 * 授权快照客户端测试：fail-closed 拒绝组合与快照契约（未配置 HTTPS 基址时
 * 全部走订阅时拒绝分支，成功路径由联调环境覆盖）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
class KnowledgePermissionClientTest {

    /**
     * 构建客户端与配置。
     *
     * @return 客户端与属性二元组
     */
    private record ClientFixture(KnowledgePermissionClient client, KnowledgePermissionProperties properties) {}

    /**
     * 构建默认（未确认共享命名空间）的客户端。
     *
     * @return fixture
     */
    private ClientFixture fixture() {
        KnowledgePermissionProperties properties = new KnowledgePermissionProperties();
        KnowledgePermissionClient client = new KnowledgePermissionClient(properties, WebClient.builder());
        return new ClientFixture(client, properties);
    }

    /**
     * 构建已确认命名空间但配置不完整的客户端。
     *
     * @param baseUrl 基址
     * @param tokens reader 凭据映射
     * @param timeout 超时
     * @return fixture
     */
    private ClientFixture httpsFixture(String baseUrl, Map<String, String> tokens, Duration timeout) {
        KnowledgePermissionProperties properties = new KnowledgePermissionProperties();
        properties.setSharedUserNamespace(true);
        properties.setBaseUrl(baseUrl);
        properties.setReaderTokens(tokens);
        properties.setTimeout(timeout);
        KnowledgePermissionClient client = new KnowledgePermissionClient(properties, WebClient.builder());
        return new ClientFixture(client, properties);
    }

    /**
     * 断言查询被统一拒绝。
     *
     * @param client 客户端
     * @param user 用户
     */
    private void assertDenied(KnowledgePermissionClient client, AuthenticatedUser user) {
        StepVerifier.create(client.permissions(user))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void unconfirmedNamespaceIsDenied() {
        ClientFixture fixture = fixture();
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "t1"));
    }

    @Test
    void httpBaseUrlIsDenied() {
        ClientFixture fixture = httpsFixture("http://127.0.0.1:8081", Map.of("t1", "reader"), Duration.ofSeconds(3));
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "t1"));
    }

    @Test
    void missingReaderTokenIsDenied() {
        ClientFixture fixture = httpsFixture("https://127.0.0.1:8081", new HashMap<>(), Duration.ofSeconds(3));
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "tenant-missing"));
    }

    @Test
    void blankReaderTokenIsDenied() {
        ClientFixture fixture = httpsFixture("https://127.0.0.1:8081", Map.of("t1", "  "), Duration.ofSeconds(3));
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "t1"));
    }

    @Test
    void oversizedTimeoutIsDenied() {
        ClientFixture fixture = httpsFixture("https://127.0.0.1:8081", Map.of("t1", "reader"), Duration.ofSeconds(31));
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "t1"));
    }

    @Test
    void zeroTimeoutIsDenied() {
        ClientFixture fixture = httpsFixture("https://127.0.0.1:8081", Map.of("t1", "reader"), Duration.ZERO);
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "t1"));
    }

    @Test
    void invalidUserIdIsDenied() {
        ClientFixture fixture = httpsFixture("https://127.0.0.1:8081", Map.of("t1", "reader"), Duration.ofSeconds(3));
        assertDenied(fixture.client(), new AuthenticatedUser(0L, "t1"));
    }

    @Test
    void blankTenantIsDenied() {
        ClientFixture fixture = httpsFixture("https://127.0.0.1:8081", Map.of("t1", "reader"), Duration.ofSeconds(3));
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "  "));
    }

    @Test
    void malformedBaseUrlIsDenied() {
        ClientFixture fixture =
                httpsFixture("https://user:pass@127.0.0.1", Map.of("t1", "reader"), Duration.ofSeconds(3));
        assertDenied(fixture.client(), new AuthenticatedUser(1L, "t1"));

        ClientFixture withQuery = httpsFixture("https://127.0.0.1?x=1", Map.of("t1", "reader"), Duration.ofSeconds(3));
        assertDenied(withQuery.client(), new AuthenticatedUser(1L, "t1"));
    }

    @Test
    void snapshotRecordCarriesFields() {
        Snapshot snapshot = new Snapshot("t1", 1L, List.of("kb:space:read"));
        assertThat(snapshot.tenantId()).isEqualTo("t1");
        assertThat(snapshot.permissions()).containsExactly("kb:space:read");
    }
}
