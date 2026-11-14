package com.apex.scaffold.authservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.application.service.InternalAuthService;
import com.apex.scaffold.authservice.interfaces.util.RequestHandlerUtil;
import com.apex.scaffold.contract.dto.internal.TokenValidationDTO;
import com.apex.scaffold.contract.dto.internal.TokenValidationRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link InternalAuthHandler} 单元测试（迁移方案 Phase 2.3）.
 *
 * <p>覆盖内部契约接口的参数解析与错误兜底：
 * <ul>
 *   <li>权限查询：合法参数透传、非法 accountId / 空 tenantId 返回 400；</li>
 *   <li>Token 校验：请求体空返回 400，正常透传服务返回 200。</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.1
 */
class InternalAuthHandlerTest {

    private final InternalAuthService service = mock(InternalAuthService.class);
    private final RequestHandlerUtil responses = mock(RequestHandlerUtil.class);
    private final InternalAuthHandler handler = new InternalAuthHandler(service, responses);

    @Test
    void 合法权限查询返回200() {
        when(service.getUserPermissions(1001L, 100L)).thenReturn(Mono.just(List.of("sys:user:view")));
        when(responses.buildSuccessResponse(any()))
                .thenReturn(ServerResponse.ok().build());

        StepVerifier.create(handler.getUserPermissions(MockServerRequest.builder()
                        .pathVariable("accountId", "1001")
                        .queryParam("tenantId", "100")
                        .build()))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(200))
                .verifyComplete();
        verify(service).getUserPermissions(1001L, 100L);
    }

    @Test
    void 非数字accountId返回400() {
        StepVerifier.create(handler.getUserPermissions(MockServerRequest.builder()
                        .pathVariable("accountId", "abc")
                        .queryParam("tenantId", "100")
                        .build()))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(400))
                .verifyComplete();
    }

    @Test
    void 非正数accountId返回400() {
        StepVerifier.create(handler.getUserPermissions(MockServerRequest.builder()
                        .pathVariable("accountId", "0")
                        .queryParam("tenantId", "100")
                        .build()))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(400))
                .verifyComplete();
    }

    @Test
    void 缺失tenantId返回400() {
        StepVerifier.create(handler.getUserPermissions(MockServerRequest.builder()
                        .pathVariable("accountId", "1001")
                        .build()))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(400))
                .verifyComplete();
    }

    @Test
    void 空请求体返回400() {
        when(responses.validateAndMap(any(org.springframework.web.reactive.function.server.ServerRequest.class), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(handler.validateToken(MockServerRequest.builder().build()))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(400))
                .verifyComplete();
    }

    @Test
    void 合法校验请求返回200() {
        TokenValidationDTO dto = TokenValidationDTO.builder()
                .valid(true)
                .accountId(1001L)
                .tenantId(100L)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(responses.validateAndMap(any(org.springframework.web.reactive.function.server.ServerRequest.class), any()))
                .thenReturn(Mono.just(TokenValidationRequest.builder()
                        .token("raw-token")
                        .tenantId(100L)
                        .build()));
        when(service.validateToken(any(TokenValidationRequest.class))).thenReturn(Mono.just(dto));
        when(responses.buildSuccessResponse(any()))
                .thenReturn(ServerResponse.ok().build());

        StepVerifier.create(handler.validateToken(MockServerRequest.builder().build()))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(200))
                .verifyComplete();
        verify(service).validateToken(any(TokenValidationRequest.class));
    }

    @Test
    void 服务异常兜底为500() {
        when(service.getUserPermissions(anyLong(), anyLong()))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(handler.getUserPermissions(MockServerRequest.builder()
                        .pathVariable("accountId", "1001")
                        .queryParam("tenantId", "100")
                        .build()))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(500))
                .verifyComplete();
    }
}
