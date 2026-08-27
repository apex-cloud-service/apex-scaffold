package com.apex.scaffold.infrastructure.remote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * {@link RemoteRequest} Builder 与不可变快照测试。
 *
 * @author Apex Team
 * @since 3.3.0
 */
@DisplayName("远程请求描述")
class RemoteRequestTest {

    /**
     * 验证 Builder 会完整表达请求，并隔离调用方对原 Map 的后续修改。
     */
    @Test
    @DisplayName("构建后应保存不可变请求快照")
    void shouldBuildImmutableSnapshot() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page", "1");

        RemoteRequest<String> request = RemoteRequest.builder(String.class)
                .service("face-analysis")
                .path("/analyze")
                .method(HttpMethod.POST)
                .queryParams(query)
                .headers(Map.of("X-Trace-Id", "trace-id"))
                .jsonBody(Map.of("photoUrl", "https://example.test/photo.jpg"))
                .build();
        query.put("page", "2");

        assertThat(request.serviceName()).isEqualTo("face-analysis");
        assertThat(request.queryParams()).containsEntry("page", "1");
        assertThat(request.bodyKind()).isEqualTo(RemoteRequest.BodyKind.JSON);
        assertThatThrownBy(() -> request.headers().put("X-New", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 验证执行请求所需的服务名和路径不能为空白。
     */
    @Test
    @DisplayName("缺少必填地址信息时应拒绝构建")
    void shouldRejectMissingAddress() {
        assertThatThrownBy(() -> RemoteRequest.builder(String.class)
                        .service(" ")
                        .path("/analyze")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceName");
    }

    /**
     * 验证 {@code shouldAllowSuppressingRequestBodyLogging} 场景下的预期行为。
     *
     */
    @Test
    @DisplayName("大体积请求体可关闭请求体日志")
    void shouldAllowSuppressingRequestBodyLogging() {
        RemoteRequest<String> request = RemoteRequest.builder(String.class)
                .service("image-processor")
                .path("/openapi/image/submit")
                .method(HttpMethod.POST)
                .jsonBody(Map.of("resource", "data:image/jpeg;base64,AAAA"))
                .logRequestBody(false)
                .build();

        assertThat(request.logRequestBody()).isFalse();
    }
}
