package com.apex.scaffold.interfaces.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document;
import static org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.documentationConfiguration;

import com.apex.scaffold.application.assembler.UserAssembler;
import com.apex.scaffold.application.dto.AccountDeletionAcceptedResponse;
import com.apex.scaffold.application.dto.AccountDeletionRequest;
import com.apex.scaffold.application.service.AccountDeletionService;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.RequestHandlerUtil;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunctions;
import reactor.core.publisher.Mono;

/**
 * 账号注销两个函数式接口的 Spring REST Docs 契约。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(RestDocumentationExtension.class)
@DisplayName("账号注销接口 REST Docs")
class AccountDeletionSpringRestDocsTest {

    private static final String OPEN_ID = "rest-docs-open-id";
    private static final String CLIENT_IP = "127.0.0.1";

    private AccountDeletionService accountDeletionService;
    private RequestHandlerUtil requestHandlerUtil;
    private WebTestClient client;

    /**
     * 装配 UserHandler 的模拟依赖，绑定注销两个函数式路由，并配置固定 openId
     * principal 的 WebTestClient 与 REST Docs 文档过滤器。
     *
     * @param documentation REST Docs 上下文提供者
     */
    @BeforeEach
    void setUp(RestDocumentationContextProvider documentation) {
        accountDeletionService = mock(AccountDeletionService.class);
        requestHandlerUtil = mock(RequestHandlerUtil.class);
        MessageUtils messageUtils = mock(MessageUtils.class);
        UserHandler handler = new UserHandler(
                mock(UserService.class),
                mock(UserAssembler.class),
                requestHandlerUtil,
                messageUtils,
                accountDeletionService);

        client = WebTestClient.bindToRouterFunction(RouterFunctions.route()
                        .POST("/api/v1/user/me/deletion-code", handler::sendAccountDeletionCode)
                        .DELETE("/api/v1/user/me", handler::deleteCurrentUser)
                        .build())
                .webFilter((exchange, chain) -> chain.filter(exchange.mutate()
                        .principal(Mono.just((Principal) () -> OPEN_ID))
                        .build()))
                .configureClient()
                .filter(documentationConfiguration(documentation))
                .build();
    }

    /**
     * 验证发送注销验证码接口生成 200 响应文档：统一响应结构完整、手机号不进入
     * 请求参数，且服务以认证 openId 被调用。
     */
    @Test
    @DisplayName("发送注销验证码应记录手机号不入参、统一响应和认证要求")
    void documentsDeletionCodeEndpoint() {
        when(accountDeletionService.sendDeletionCode(OPEN_ID)).thenReturn(Mono.empty());

        client.post()
                .uri("/api/v1/user/me/deletion-code")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .consumeWith(document(
                        "account-deletion-code",
                        preprocessRequest(),
                        preprocessResponse(),
                        responseFields(
                                fieldWithPath("code").description("业务状态码，成功为 0"),
                                fieldWithPath("msg").description("结果说明"),
                                fieldWithPath("data").optional().description("成功时为空；服务端不回显手机号"),
                                fieldWithPath("traceId").description("链路追踪 ID"))));

        verify(accountDeletionService).sendDeletionCode(OPEN_ID);
    }

    /**
     * 验证注销接口生成 202 受理响应文档：记录验证码请求体、幂等键与
     * requestId/jobId/status 异步任务字段。
     */
    @Test
    @DisplayName("注销接口应记录验证码请求体、幂等键、202 受理响应和异步任务字段")
    void documentsDeleteCurrentUserEndpoint() {
        AccountDeletionRequest body = new AccountDeletionRequest();
        body.setCode("123456");
        when(requestHandlerUtil.handleRequestBody(any(), eq(AccountDeletionRequest.class)))
                .thenReturn(Mono.just(body));
        when(requestHandlerUtil.extractClientIp(any())).thenReturn(CLIENT_IP);
        when(accountDeletionService.deleteCurrentUser(
                        eq(OPEN_ID), eq("123456"), eq("request-01"), eq(CLIENT_IP), any()))
                .thenReturn(Mono.just(new AccountDeletionAcceptedResponse("request-01", "9001", "ACCEPTED")));

        client.method(HttpMethod.DELETE)
                .uri("/api/v1/user/me")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "request-01")
                .bodyValue("{\"code\":\"123456\"}")
                .exchange()
                .expectStatus()
                .isAccepted()
                .expectBody()
                .consumeWith(document(
                        "account-deletion",
                        preprocessRequest(),
                        preprocessResponse(),
                        requestFields(fieldWithPath("code").description("当前账号绑定手机号收到的 6 位注销验证码")),
                        responseFields(
                                fieldWithPath("code").description("业务状态码，成功为 0"),
                                fieldWithPath("msg").description("结果说明"),
                                fieldWithPath("data.requestId").description("注销请求标识"),
                                fieldWithPath("data.jobId").description("异步清理任务 ID"),
                                fieldWithPath("data.status").description("受理状态，成功为 ACCEPTED"),
                                fieldWithPath("traceId").description("链路追踪 ID"))));

        verify(accountDeletionService)
                .deleteCurrentUser(eq(OPEN_ID), eq("123456"), eq("request-01"), eq(CLIENT_IP), any());
    }
}
