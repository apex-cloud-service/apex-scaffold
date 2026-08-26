package com.apex.scaffold.interfaces.router;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.interfaces.handler.AuthHandler;
import com.apex.scaffold.interfaces.handler.AuthorizationHandler;
import com.apex.scaffold.interfaces.handler.FileHandler;
import com.apex.scaffold.interfaces.handler.HealthHandler;
import com.apex.scaffold.interfaces.handler.UserHandler;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Multipart 路由内容协商回归测试。
 *
 * <p>上传接口应根据请求体的 Content-Type 匹配路由，不能要求客户端通过 Accept 声明
 * multipart/form-data 响应。客户端使用 Accept: application/json 时仍应进入 Handler。</p>
 *
 * @author weiqiang.ling
 */
@DisplayName("Multipart 路由内容协商测试")
class MultipartRouteContentNegotiationTest {

    /**
     * 验证 {@code shouldRouteGenericUploadWhenClientAcceptsJson} 场景。
     */
    @Test
    @DisplayName("通用上传在 Accept application/json 时应进入 FileHandler")
    void shouldRouteGenericUploadWhenClientAcceptsJson() {
        FileHandler fileHandler = mock(FileHandler.class);
        when(fileHandler.uploadGenericFile(any())).thenReturn(Mono.defer(() -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("code", 0))));

        RouterFunction<ServerResponse> routes = routes(fileHandler);
        WebTestClient.bindToRouterFunction(routes)
                .build()
                .post()
                .uri("/api/v1/files/generic")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_JSON);

        verify(fileHandler).uploadGenericFile(any());
    }

    /**
     * 执行 {@code routes} 逻辑。
     *
     * @param fileHandler fileHandler 参数
     *
     * @return 执行结果
     */
    private RouterFunction<ServerResponse> routes(FileHandler fileHandler) {
        return new ApiRouter()
                .apiRoutes(
                        mock(AuthHandler.class),
                        mock(AuthorizationHandler.class),
                        mock(UserHandler.class),
                        fileHandler,
                        mock(HealthHandler.class));
    }
}
