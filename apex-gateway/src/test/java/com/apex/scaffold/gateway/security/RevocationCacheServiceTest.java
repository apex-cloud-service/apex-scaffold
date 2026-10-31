package com.apex.scaffold.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RevocationCacheService} 撤销缓存单元测试（统一认证收口 A3）。
 *
 * <p>覆盖 {@code Result} 封装解析、增量入缓存与游标单调推进、空批与非法响应容错、禁用态。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("RevocationCacheService 网关撤销缓存测试")
class RevocationCacheServiceTest {

    private RevocationProperties enabledProperties() {
        RevocationProperties properties = new RevocationProperties();
        properties.setEnabled(true);
        properties.setUrl("http://auth.internal/internal/v1/tokens/revocations");
        return properties;
    }

    @Test
    @DisplayName("撤销增量入缓存并按 jti 命中，游标单调推进")
    void recordsRevocationsAndAdvancesCursor() {
        RevocationCacheService service = new RevocationCacheService(enabledProperties());
        service.consume(
                """
                {"code":0,"data":{"revocations":[
                  {"jti":"jti-a","revokedAt":1000},
                  {"jti":"jti-b","revokedAt":2000}],
                  "cursor":2000}}""");

        assertThat(service.isRevoked("jti-a")).isTrue();
        assertThat(service.isRevoked("jti-b")).isTrue();
        assertThat(service.isRevoked("jti-c")).isFalse();
        assertThat(service.isRevoked(null)).isFalse();
        assertThat(service.size()).isEqualTo(2);

        // 后批游标更小/相同时不回退
        service.consume("{\"code\":0,\"data\":{\"revocations\":[],\"cursor\":1500}}");
        assertThat(service.isRevoked("jti-a")).isTrue();
    }

    @Test
    @DisplayName("空批与非法响应容错，不破坏既有缓存")
    void toleratesEmptyAndMalformedResponses() {
        RevocationCacheService service = new RevocationCacheService(enabledProperties());
        service.consume(
                "{\"code\":0,\"data\":{\"revocations\":[{\"jti\":\"jti-a\",\"revokedAt\":1000}],\"cursor\":1000}}");
        service.consume("{\"code\":0,\"data\":{\"revocations\":[]}}");
        service.consume("not-json");
        service.consume("{\"code\":0}");
        assertThat(service.isRevoked("jti-a")).isTrue();
        assertThat(service.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("未启用时 isRevoked 语义仍可用但不拉取")
    void disabledState() {
        RevocationCacheService service = new RevocationCacheService(new RevocationProperties());
        assertThat(service.enabled()).isFalse();
        service.consume("{\"code\":0,\"data\":{\"revocations\":[{\"jti\":\"jti-x\",\"revokedAt\":1}],\"cursor\":1}}");
        // consume 为纯缓存操作，禁用态仅影响拉取调度
        assertThat(service.isRevoked("jti-x")).isTrue();
    }
}
