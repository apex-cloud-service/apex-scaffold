package com.apex.scaffold.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.SystemMessageEntity;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.SystemMessageRepository;
import com.apex.scaffold.interfaces.util.MessageUtils;
import java.net.URI;
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
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link SystemMessageHandler} 的单元测试类。
 *
 * <p>覆盖站内信读写闭环：分页查询按当前登录用户过滤、未读数统计、
 * 单条标记已读（幂等）与全部标记已读，并验证未认证请求被拒绝（禁止越权）。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@DisplayName("SystemMessageHandler 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SystemMessageHandlerTest {

    @Mock
    private SystemMessageRepository systemMessageRepository;

    @Mock
    private UserService userService;

    @Mock
    private MessageUtils messageUtils;

    private static final String TEST_OPEN_ID = "test-open-id-123";
    private static final Long TEST_USER_ID = 1L;

    private SystemMessageHandler handler;

    @BeforeEach
    void setUp() {
        when(messageUtils.get(anyString())).thenReturn(Mono.just("错误消息"));
        when(messageUtils.get(anyString(), any())).thenReturn(Mono.just("错误消息"));
        handler = new SystemMessageHandler(systemMessageRepository, userService, messageUtils);

        UserEntity user = new UserEntity();
        user.setId(TEST_USER_ID);
        user.setOpenId(TEST_OPEN_ID);
        when(userService.getByOpenId(TEST_OPEN_ID)).thenReturn(Mono.just(user));
    }

    private ServerRequest authenticatedRequest(HttpMethod method, String path) {
        return MockServerRequest.builder()
                .method(method)
                .uri(URI.create(path))
                .principal((Principal) () -> TEST_OPEN_ID)
                .build();
    }

    @Nested
    @DisplayName("inbox 方法")
    class InboxTests {

        @Test
        @DisplayName("应使用默认分页并按当前登录用户过滤")
        void shouldUseDefaultPagingAndFilterByCurrentUser() {
            SystemMessageEntity msg = new SystemMessageEntity();
            msg.setUserId(TEST_USER_ID);
            PageResponse<SystemMessageEntity> page = PageResponse.of(List.of(msg), 1L, 1, 10);
            when(systemMessageRepository.pageByUser(TEST_USER_ID, 1, 10, false)).thenReturn(Mono.just(page));

            ServerRequest request = authenticatedRequest(HttpMethod.GET, "/api/v1/messages/inbox");

            StepVerifier.create(handler.inbox(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(200))
                    .verifyComplete();

            verify(userService).getByOpenId(TEST_OPEN_ID);
            verify(systemMessageRepository).pageByUser(TEST_USER_ID, 1, 10, false);
        }

        @Test
        @DisplayName("应透传分页参数与未读过滤条件")
        void shouldForwardPagingAndUnreadFilter() {
            PageResponse<SystemMessageEntity> page = PageResponse.of(List.of(), 0L, 2, 5);
            when(systemMessageRepository.pageByUser(TEST_USER_ID, 2, 5, true)).thenReturn(Mono.just(page));

            ServerRequest request = MockServerRequest.builder()
                    .method(HttpMethod.GET)
                    .uri(URI.create("/api/v1/messages/inbox"))
                    .queryParam("page", "2")
                    .queryParam("size", "5")
                    .queryParam("unreadOnly", "true")
                    .principal((Principal) () -> TEST_OPEN_ID)
                    .build();

            StepVerifier.create(handler.inbox(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(200))
                    .verifyComplete();

            verify(systemMessageRepository).pageByUser(TEST_USER_ID, 2, 5, true);
        }
    }

    @Nested
    @DisplayName("unreadCount 方法")
    class UnreadCountTests {

        @Test
        @DisplayName("应返回当前用户未读消息数")
        void shouldReturnUnreadCountForCurrentUser() {
            when(systemMessageRepository.countUnread(TEST_USER_ID)).thenReturn(Mono.just(3L));

            ServerRequest request = authenticatedRequest(HttpMethod.GET, "/api/v1/messages/unread-count");

            StepVerifier.create(handler.unreadCount(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(200))
                    .verifyComplete();

            verify(systemMessageRepository).countUnread(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("markRead 方法")
    class MarkReadTests {

        @Test
        @DisplayName("应按当前用户与路径ID标记已读")
        void shouldMarkMessageReadScopedToCurrentUser() {
            when(systemMessageRepository.markRead(TEST_USER_ID, 99L)).thenReturn(Mono.just(true));

            ServerRequest request = MockServerRequest.builder()
                    .method(HttpMethod.POST)
                    .uri(URI.create("/api/v1/messages/99/read"))
                    .pathVariable("id", "99")
                    .principal((Principal) () -> TEST_OPEN_ID)
                    .build();

            StepVerifier.create(handler.markRead(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(200))
                    .verifyComplete();

            verify(systemMessageRepository).markRead(TEST_USER_ID, 99L);
        }

        @Test
        @DisplayName("重复标记已读应幂等返回成功")
        void shouldBeIdempotentWhenAlreadyRead() {
            when(systemMessageRepository.markRead(TEST_USER_ID, 99L)).thenReturn(Mono.just(false));

            ServerRequest request = MockServerRequest.builder()
                    .method(HttpMethod.POST)
                    .uri(URI.create("/api/v1/messages/99/read"))
                    .pathVariable("id", "99")
                    .principal((Principal) () -> TEST_OPEN_ID)
                    .build();

            StepVerifier.create(handler.markRead(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("非法ID应返回参数错误且不访问仓储")
        void shouldRejectInvalidId() {
            ServerRequest request = MockServerRequest.builder()
                    .method(HttpMethod.POST)
                    .uri(URI.create("/api/v1/messages/0/read"))
                    .pathVariable("id", "0")
                    .principal((Principal) () -> TEST_OPEN_ID)
                    .build();

            StepVerifier.create(handler.markRead(request))
                    .expectError(BusinessException.class)
                    .verify();

            verify(systemMessageRepository, never()).markRead(any(), any());
        }
    }

    @Nested
    @DisplayName("markAllRead 方法")
    class MarkAllReadTests {

        @Test
        @DisplayName("应标记当前用户全部消息已读")
        void shouldMarkAllReadForCurrentUser() {
            when(systemMessageRepository.markAllRead(TEST_USER_ID)).thenReturn(Mono.just(5L));

            ServerRequest request = authenticatedRequest(HttpMethod.POST, "/api/v1/messages/read-all");

            StepVerifier.create(handler.markAllRead(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(200))
                    .verifyComplete();

            verify(systemMessageRepository).markAllRead(TEST_USER_ID);
        }
    }

    @Test
    @DisplayName("未认证请求应抛出未授权异常")
    void shouldRejectUnauthenticatedRequest() {
        ServerRequest request = MockServerRequest.builder()
                .method(HttpMethod.GET)
                .uri(URI.create("/api/v1/messages/unread-count"))
                .build();

        StepVerifier.create(handler.unreadCount(request))
                .expectError(BusinessException.class)
                .verify();
    }
}
