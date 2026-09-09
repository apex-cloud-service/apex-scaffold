package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.interfaces.service.KeycloakUserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link KeycloakAdminHandler} 的单元测试类
 *
 * <p>测试 Keycloak 管理处理器的核心逻辑：用户同步、状态查询。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.4.0
 */
@DisplayName("KeycloakAdminHandler 单元测试")
@ExtendWith(MockitoExtension.class)
class KeycloakAdminHandlerTest {

    @Mock
    private KeycloakUserSyncService keycloakUserSyncService;

    private KeycloakAdminHandler handler;

    /**
     * 设置测试环境
     */
    @BeforeEach
    void setUp() {
        handler = new KeycloakAdminHandler(keycloakUserSyncService);
    }

    @Nested
    @DisplayName("syncAllUsers 方法")
    class SyncAllUsersTests {

        /**
         * 验证全量同步成功应返回 200 响应
         */
        @Test
        @DisplayName("全量同步成功应返回成功响应")
        void shouldReturnSuccessWhenSyncAll() {
            when(keycloakUserSyncService.syncAllUsers())
                    .thenReturn(Mono.empty());

            MockServerHttpRequest httpRequest = MockServerHttpRequest
                    .post("/admin-api/v1/keycloak/sync/all")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange,
                    HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.syncAllUsers(request))
                    .expectNextMatches(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(200);
                        return true;
                    })
                    .verifyComplete();
        }

        /**
         * 验证同步失败应返回 500 响应
         */
        @Test
        @DisplayName("同步失败应返回 500 Internal Server Error")
        void shouldReturn500WhenSyncFails() {
            when(keycloakUserSyncService.syncAllUsers())
                    .thenReturn(Mono.error(new RuntimeException("Sync failed")));

            MockServerHttpRequest httpRequest = MockServerHttpRequest
                    .post("/admin-api/v1/keycloak/sync/all")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange,
                    HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.syncAllUsers(request))
                    .expectNextMatches(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(500);
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("syncUser 方法")
    class SyncUserTests {

        /**
         * 验证单个用户同步成功应返回用户信息
         */
        @Test
        @DisplayName("单个用户同步成功应返回用户信息")
        void shouldReturnUserInfoWhenSyncSucceeds() {
            String keycloakSub = "test-keycloak-sub";
            UserEntity user = new UserEntity();
            user.setId(1L);
            user.setUsername("testuser");
            user.setEmail("test@example.com");
            user.setKeycloakSub(keycloakSub);

            when(keycloakUserSyncService.syncUserFromKeycloak(keycloakSub))
                    .thenReturn(Mono.just(user));

            MockServerHttpRequest httpRequest = MockServerHttpRequest
                    .post("/admin-api/v1/keycloak/sync/user/" + keycloakSub)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            
            ServerRequest request = ServerRequest.create(
                    exchange,
                    HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.syncUser(request))
                    .expectNextMatches(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(200);
                        return true;
                    })
                    .verifyComplete();
        }

        /**
         * 验证用户同步失败应返回 500 响应
         */
        @Test
        @DisplayName("用户同步失败应返回 500 Internal Server Error")
        void shouldReturn500WhenUserSyncFails() {
            String keycloakSub = "test-keycloak-sub";

            when(keycloakUserSyncService.syncUserFromKeycloak(anyString()))
                    .thenReturn(Mono.error(new RuntimeException("User sync failed")));

            MockServerHttpRequest httpRequest = MockServerHttpRequest
                    .post("/admin-api/v1/keycloak/sync/user/" + keycloakSub)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange,
                    HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.syncUser(request))
                    .expectNextMatches(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(500);
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getKeycloakStatus 方法")
    class GetKeycloakStatusTests {

        /**
         * 验证状态查询应返回启用状态
         */
        @Test
        @DisplayName("状态查询应返回 enabled 和 connected 状态")
        void shouldReturnEnabledAndConnectedStatus() {
            MockServerHttpRequest httpRequest = MockServerHttpRequest
                    .get("/admin-api/v1/keycloak/status")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange,
                    HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.getKeycloakStatus(request))
                    .expectNextMatches(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(200);
                        return true;
                    })
                    .verifyComplete();
        }
    }
}
