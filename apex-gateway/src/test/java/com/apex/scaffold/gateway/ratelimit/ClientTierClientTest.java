package com.apex.scaffold.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.util.JsonUtil;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link ClientTierClient} 单元测试（D2 按应用分档限流）。
 *
 * <p>覆盖：正常档位解析与缓存、缺省档兜底、未知客户端 fail-open、内部端点不可达
 * fail-open（限流是保护性手段，档位查询失败只允许退化为默认档）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("ClientTierClient 限流档查询测试")
class ClientTierClientTest {

    private OpenAppTierProperties properties;

    private ClientTierClient client;

    /** 桩响应：null 表示端点不可达（抛错），否则返回该 JSON 体。 */
    private volatile String stubBody;

    private volatile boolean failFetch;

    @BeforeEach
    void setUp() {
        properties = new OpenAppTierProperties();
        client =
                new ClientTierClient(properties, new com.apex.scaffold.common.internal.InternalAuthProperties(), null) {
                    @Override
                    Mono<String> fetch(String clientId) {
                        if (failFetch) {
                            return Mono.error(new IllegalStateException("connection refused"));
                        }
                        return stubBody == null
                                ? Mono.error(new IllegalStateException("no stub"))
                                : Mono.just(stubBody);
                    }
                };
        stubBody = tierBody("partner-app", "premium");
        failFetch = false;
    }

    private static String tierBody(String clientId, String tier) {
        return JsonUtil.toJson(Map.of("code", 0, "data", Map.of("clientId", clientId, "rateLimitTier", tier)));
    }

    @Test
    @DisplayName("正常解析开放应用档位并短 TTL 缓存")
    void shouldParseTierAndCache() {
        StepVerifier.create(client.tierOf("partner-app")).expectNext("premium").verifyComplete();

        stubBody = tierBody("partner-app", "default");
        StepVerifier.create(client.tierOf("partner-app")).expectNext("premium").verifyComplete();
    }

    @Test
    @DisplayName("未声明档位的数据节点回退 default")
    void shouldFallbackWhenTierMissing() {
        stubBody = JsonUtil.toJson(Map.of("code", 0, "data", Map.of("clientId", "app-1")));

        StepVerifier.create(client.tierOf("app-1")).expectNext("default").verifyComplete();
    }

    @Test
    @DisplayName("内部端点不可达时 fail-open 返回 default")
    void shouldFailOpenWhenLookupFails() {
        failFetch = true;

        StepVerifier.create(client.tierOf("app-2")).expectNext("default").verifyComplete();
    }

    @Test
    @DisplayName("响应体不可解析时 fail-open 返回 default")
    void shouldFailOpenWhenBodyUnparsable() {
        stubBody = "not-json";

        StepVerifier.create(client.tierOf("app-3")).expectNext("default").verifyComplete();
    }

    @Test
    @DisplayName("分档开关关闭或 clientId 缺失时直接返回 default")
    void shouldBypassWhenDisabledOrBlank() {
        properties.setEnabled(false);
        assertThat(client.tierOf("app-4").block()).isEqualTo("default");

        properties.setEnabled(true);
        assertThat(client.tierOf(" ").block()).isEqualTo("default");
    }
}
