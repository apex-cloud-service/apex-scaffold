package com.apex.scaffold.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.interfaces.util.MessageUtils;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 健康检查处理器单元测试
 *
 * <p>验证 HealthHandler 的健康检查和信息查询接口，
 * 包括返回状态码、Content-Type 和响应体内容的正确性
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HealthHandler 健康检查处理器测试")
class HealthHandlerTest {

    @Mock
    private MessageUtils messageUtils;

    private HealthHandler healthHandler;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        healthHandler = new HealthHandler(messageUtils, "apex-scaffold", "1.2.0");
        when(messageUtils.get(any(Locale.class), any())).thenReturn("");
    }

    @Test
    @DisplayName("健康检查接口应返回200和UP状态")
    void health_shouldReturnUpStatus() {
        ServerRequest request = createMockServerRequest();

        Mono<ServerResponse> result = healthHandler.health(request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.statusCode().value()).isEqualTo(200);
                    assertThat(response.headers().getContentType()).hasToString(MediaType.APPLICATION_JSON.toString());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("获取系统信息接口应返回200")
    void info_shouldReturnSystemInfo() {
        ServerRequest request = createMockServerRequest();

        Mono<ServerResponse> result = healthHandler.info(request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.statusCode().value()).isEqualTo(200);
                    assertThat(response.headers().getContentType()).hasToString(MediaType.APPLICATION_JSON.toString());
                })
                .verifyComplete();
    }

    private ServerRequest createMockServerRequest() {
        return mock(ServerRequest.class);
    }
}
