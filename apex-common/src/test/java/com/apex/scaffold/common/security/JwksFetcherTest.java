package com.apex.scaffold.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.common.config.JwksFetchProperties;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * {@link JwksFetcher} 共享 JWKS 拉取客户端单元测试（统一认证收口 A2）。
 *
 * <p>覆盖 JWK Set 解析（含非 RSA 跳过、不完整条目跳过）、快照更新与失败保留、
 * 禁用态行为；网络层以覆写 {@code fetch} 的替身模拟，不发起真实请求。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("JwksFetcher 共享 JWKS 拉取客户端测试")
class JwksFetcherTest {

    private record KeyMaterial(String kid, KeyPair pair) {}

    private static KeyMaterial rsaKey(String kid) {
        return new KeyMaterial(kid, Jwts.SIG.RS256.keyPair().build());
    }

    /** 以 kid/n/e 投影构造 JWKS JSON（模拟认证中心聚合端点响应）。 */
    private static String jwksJson(KeyMaterial... keys) {
        StringBuilder sb = new StringBuilder("{\"keys\":[");
        for (int i = 0; i < keys.length; i++) {
            KeyMaterial key = keys[i];
            java.security.interfaces.RSAPublicKey rsa =
                    (java.security.interfaces.RSAPublicKey) key.pair().getPublic();
            BigInteger modulus = rsa.getModulus();
            BigInteger exponent = rsa.getPublicExponent();
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"")
                    .append(key.kid())
                    .append("\",\"n\":\"")
                    .append(Base64.getUrlEncoder().withoutPadding().encodeToString(modulus.toByteArray()))
                    .append("\",\"e\":\"")
                    .append(Base64.getUrlEncoder().withoutPadding().encodeToString(exponent.toByteArray()))
                    .append("\"}");
        }
        return sb.append("]}").toString();
    }

    @Test
    @DisplayName("解析 JWKS：kid → RSA 公钥，非 RSA 与不完整条目跳过")
    void parsesJwks() {
        KeyMaterial active = rsaKey("key-1");
        String body = "{\"keys\":["
                + "{\"kty\":\"oct\",\"kid\":\"symmetric\"},"
                + "{\"kty\":\"RSA\",\"kid\":\"\"},"
                + jwksJson(active).substring("{\"keys\":[".length());
        Map<String, PublicKey> parsed = JwksFetcher.parseJwks(body);
        assertThat(parsed).containsOnlyKeys("key-1");
        assertThat(parsed.get("key-1")).isEqualTo(active.pair().getPublic());
    }

    @Test
    @DisplayName("非法 JWKS 响应抛出参数异常")
    void rejectsMalformedJwks() {
        assertThatThrownBy(() -> JwksFetcher.parseJwks("{\"not_keys\":[]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keys");
    }

    @Test
    @DisplayName("未配置 remote-url 时处于禁用态，快照恒为空")
    void disabledWithoutRemoteUrl() {
        JwksFetcher fetcher = new JwksFetcher(new JwksFetchProperties());
        assertThat(fetcher.enabled()).isFalse();
        assertThat(fetcher.snapshot()).isEmpty();
    }

    @Test
    @DisplayName("拉取成功更新快照，空结果与失败保留上一次快照")
    void refreshSemantics() {
        KeyMaterial first = rsaKey("key-1");
        KeyMaterial second = rsaKey("key-2");
        JwksFetchProperties properties = new JwksFetchProperties();
        properties.setRemoteUrl("http://auth.example/.well-known/jwks.json");
        JwksFetcher fetcher = new JwksFetcher(properties) {
            private int round;

            @Override
            Mono<Map<String, PublicKey>> fetch(String url) {
                return switch (round++) {
                    case 0 -> Mono.just(JwksFetcher.parseJwks(jwksJson(first)));
                    case 1 -> Mono.just(Map.of()); // 空结果：保留快照
                    case 2 -> Mono.error(new IllegalStateException("auth unreachable")); // 失败：保留快照
                    default -> Mono.just(JwksFetcher.parseJwks(jwksJson(second))); // 恢复后更新
                };
            }
        };
        fetcher.fetchAndStore().block();
        assertThat(fetcher.snapshot()).containsOnlyKeys("key-1");
        fetcher.fetchAndStore().onErrorResume(e -> Mono.empty()).block();
        assertThat(fetcher.snapshot()).containsOnlyKeys("key-1");
        fetcher.fetchAndStore().onErrorResume(e -> Mono.empty()).block();
        assertThat(fetcher.snapshot()).containsOnlyKeys("key-1");
        fetcher.fetchAndStore().block();
        assertThat(fetcher.snapshot()).containsOnlyKeys("key-2");
    }
}
