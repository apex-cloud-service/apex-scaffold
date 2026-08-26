package com.apex.scaffold.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.assembler.UserAssembler;
import com.apex.scaffold.application.dto.PageRequest;
import com.apex.scaffold.application.dto.UserDTO;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.RequestHandlerUtil;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link UserHandler} 的单元测试类
 *
 * <p>测试用户Handler的核心逻辑：参数解析、鉴权、业务调用、响应构建。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("UserHandler 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private UserAssembler userAssembler;

    @Mock
    private RequestHandlerUtil requestHandlerUtil;

    @Mock
    private MessageUtils messageUtils;

    private static final String TEST_OPEN_ID = "test-open-id-123";

    private UserHandler handler;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        handler = new UserHandler(userService, userAssembler, requestHandlerUtil, messageUtils);
    }

    /**
     * 创建{@code requestWithOpenId}。
     * @param path 参数 {@code path}
     * @return 操作结果
     */
    private ServerRequest createRequestWithOpenId(String path) {
        MockServerHttpRequest httpRequest = MockServerHttpRequest.get(path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
        var exchangeWithPrincipal = exchange.mutate()
                .principal(Mono.just((Principal) () -> TEST_OPEN_ID))
                .build();
        return ServerRequest.create(
                exchangeWithPrincipal, HandlerStrategies.withDefaults().messageReaders());
    }

    /**
     * 创建{@code postRequestWithOpenId}。
     * @param path 参数 {@code path}
     * @param body 参数 {@code body}
     * @return 操作结果
     */
    private ServerRequest createPostRequestWithOpenId(String path, String body) {
        MockServerHttpRequest httpRequest = MockServerHttpRequest.post(path).body(body);
        MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
        var exchangeWithPrincipal = exchange.mutate()
                .principal(Mono.just((Principal) () -> TEST_OPEN_ID))
                .build();
        return ServerRequest.create(
                exchangeWithPrincipal, HandlerStrategies.withDefaults().messageReaders());
    }

    @Nested
    @DisplayName("getCurrentUserInfo 方法")
    class GetCurrentUserInfoTests {

        /**
         * 执行 {@code shouldReturnUserInfoWhenFound} 操作。
         */
        @Test
        @DisplayName("用户存在时应返回200和UserDTO")
        void shouldReturnUserInfoWhenFound() {
            UserEntity entity = new UserEntity();
            entity.setId(1L);
            entity.setOpenId(TEST_OPEN_ID);
            entity.setNickName("测试用户");

            UserDTO dto = new UserDTO();
            dto.setId(1L);
            dto.setOpenId(TEST_OPEN_ID);
            dto.setNickName("测试用户");

            when(userService.getByOpenId(TEST_OPEN_ID)).thenReturn(Mono.just(entity));
            when(userAssembler.toDTO(entity)).thenReturn(dto);

            ServerRequest request = createRequestWithOpenId("/users/me");

            StepVerifier.create(handler.getCurrentUserInfo(request))
                    .assertNext(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(200);
                    })
                    .verifyComplete();

            verify(userService).getByOpenId(TEST_OPEN_ID);
            verify(userAssembler).toDTO(entity);
        }

        /**
         * 执行 {@code shouldReturnNotFoundWhenUserNotFound} 操作。
         */
        @Test
        @DisplayName("用户不存在时应返回404错误")
        void shouldReturnNotFoundWhenUserNotFound() {
            when(userService.getByOpenId(TEST_OPEN_ID)).thenReturn(Mono.empty());

            ServerRequest request = createRequestWithOpenId("/users/me");

            StepVerifier.create(handler.getCurrentUserInfo(request))
                    .assertNext(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(404);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getUserById 方法")
    class GetUserByIdTests {

        /**
         * 执行 {@code shouldReturnUserWhenValidId} 操作。
         */
        @Test
        @DisplayName("有效ID且用户存在时应返回200和UserDTO")
        void shouldReturnUserWhenValidId() {
            UserEntity entity = new UserEntity();
            entity.setId(1L);
            entity.setNickName("目标用户");

            UserDTO dto = new UserDTO();
            dto.setId(1L);
            dto.setNickName("目标用户");

            when(userService.getById(1L)).thenReturn(Mono.just(entity));
            when(userAssembler.toDTO(entity)).thenReturn(dto);

            MockServerRequest request = MockServerRequest.builder()
                    .method(HttpMethod.GET)
                    .pathVariable("userId", "1")
                    .build();

            StepVerifier.create(handler.getUserById(request))
                    .assertNext(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(200);
                    })
                    .verifyComplete();
        }

        /**
         * 执行 {@code shouldReturnBadRequestWhenInvalidId} 操作。
         */
        @Test
        @DisplayName("无效的userId应返回400错误")
        void shouldReturnBadRequestWhenInvalidId() {
            MockServerRequest request = MockServerRequest.builder()
                    .method(HttpMethod.GET)
                    .pathVariable("userId", "abc")
                    .build();

            StepVerifier.create(handler.getUserById(request))
                    .assertNext(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(400);
                    })
                    .verifyComplete();
        }

        /**
         * 执行 {@code shouldReturnNotFoundWhenUserNotFound} 操作。
         */
        @Test
        @DisplayName("用户不存在时应返回404错误")
        void shouldReturnNotFoundWhenUserNotFound() {
            when(userService.getById(999L)).thenReturn(Mono.empty());

            MockServerRequest request = MockServerRequest.builder()
                    .method(HttpMethod.GET)
                    .pathVariable("userId", "999")
                    .build();

            StepVerifier.create(handler.getUserById(request))
                    .assertNext(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(404);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("listUsers 方法")
    class ListUsersTests {

        /**
         * 执行 {@code shouldListUsersSuccessfully} 操作。
         */
        @Test
        @DisplayName("有效分页请求时应返回200和分页数据")
        void shouldListUsersSuccessfully() {
            PageRequest pageRequest = new PageRequest();
            pageRequest.setPageNum(1);
            pageRequest.setPageSize(10);

            UserEntity entity1 = new UserEntity();
            entity1.setId(1L);
            entity1.setNickName("用户1");

            UserEntity entity2 = new UserEntity();
            entity2.setId(2L);
            entity2.setNickName("用户2");

            PageResponse<UserEntity> pageResponse = new PageResponse<>();
            pageResponse.setList(List.of(entity1, entity2));
            pageResponse.setTotal(2L);

            ServerRequest request = createPostRequestWithOpenId("/users/list", "{}");

            when(requestHandlerUtil.handleRequestBody(any(ServerRequest.class), any()))
                    .thenReturn(Mono.just(pageRequest));
            when(userService.findPage(anyInt(), anyInt())).thenReturn(Mono.just(pageResponse));

            StepVerifier.create(handler.listUsers(request))
                    .assertNext(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(200);
                    })
                    .verifyComplete();

            verify(userService).findPage(1, 10);
        }
    }
}
