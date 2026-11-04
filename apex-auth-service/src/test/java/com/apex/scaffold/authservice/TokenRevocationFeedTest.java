package com.apex.scaffold.authservice;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 撤销流内部端点集成测试（统一认证收口 A3）。
 *
 * <p>覆盖 {@code GET /internal/v1/tokens/revocations} 的增量语义：撤销记录按游标拉取、
 * 游标推进、空批幂等；不校验签名（内部端点不经网关）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "security.encryption.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "apex.security.data-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "apex.security.config-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "security.config.encryption.key=01234567890123456789012345678901",
            // 本测试验证撤销流的分页/游标语义，关注业务响应而非内部签名，按配置注释
            // “仅本地单测可显式关闭”的口径关闭内部认证；生产由部署编排注入共享密钥。
            "apex.internal-auth.enabled=false"
        })
@Import(AuthCenterBasicTestConfiguration.class)
@AutoConfigureWebTestClient
@ActiveProfiles("basic")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class TokenRevocationFeedTest extends EmbeddedRedisBootTestSupport {
    private final WebTestClient client;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM auth_token WHERE user_id = 97100");
        jdbc.update("DELETE FROM auth_user WHERE id = 97100");
        jdbc.update(
                "INSERT INTO auth_user(id,tenant_id,username,password_hash,status,failed_login_attempts) VALUES(97100,'default','revocation-user',?,1,0)",
                encoder.encode("secret"));
    }

    /** 已撤销令牌按游标增量返回，游标推进后为空批。 */
    @Test
    void returnsIncrementalRevocationsAndAdvancesCursor() {
        long baseline = now();
        insertRevokedToken("jti-feed-0001", baseline - 10_000);
        insertRevokedToken("jti-feed-0002", baseline - 5_000);
        insertActiveToken("jti-feed-live", baseline - 1_000);

        byte[] first = client.get()
                .uri("/internal/v1/tokens/revocations?since=0&limit=100")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();
        var root = parse(first);
        var revocations = root.get("data").get("revocations");
        org.assertj.core.api.Assertions.assertThat(revocations.size()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(revocations.get(0).get("jti").asText())
                .isEqualTo("jti-feed-0001");
        long cursor = root.get("data").get("cursor").asLong();
        // 游标 = 本批最大撤销时间（相对插入基线比较，避免断言期时钟前进造成抖动）
        org.assertj.core.api.Assertions.assertThat(cursor).isEqualTo(baseline - 5_000);

        // 以推进后的游标再拉：应为空批且游标不回退
        byte[] second = client.get()
                .uri("/internal/v1/tokens/revocations?since=" + cursor + "&limit=100")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();
        var next = parse(second);
        org.assertj.core.api.Assertions.assertThat(
                        next.get("data").get("revocations").size())
                .isZero();
        org.assertj.core.api.Assertions.assertThat(
                        next.get("data").get("cursor").asLong())
                .isEqualTo(cursor);
    }

    /** 缺失 since 游标时拒绝请求。 */
    @Test
    void rejectsMissingCursor() {
        client.get()
                .uri("/internal/v1/tokens/revocations")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    private void insertRevokedToken(String jti, long revokedAtMillis) {
        insertToken(jti, revokedAtMillis, 1);
    }

    private void insertActiveToken(String jti, long createdAtMillis) {
        insertToken(jti, createdAtMillis, 0);
    }

    private void insertToken(String jti, long millis, int revoked) {
        jdbc.update(
                "INSERT INTO auth_token(id,tenant_id,user_id,token_type,token_value,jti,expires_at,revoked,revoked_at,created_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?)",
                System.currentTimeMillis() + jti.hashCode(),
                "default",
                97100L,
                "ACCESS",
                "token-" + jti,
                jti,
                new java.sql.Timestamp(now() + 3_600_000),
                revoked,
                revoked == 1 ? new java.sql.Timestamp(millis) : null,
                new java.sql.Timestamp(millis));
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private com.fasterxml.jackson.databind.JsonNode parse(byte[] body) {
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder()
                    .build()
                    .readTree(body);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
