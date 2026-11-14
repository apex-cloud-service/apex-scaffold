package com.apex.scaffold.interfaces.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.service.TenantGuard;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.interfaces.config.KeycloakConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link KeycloakUserSyncService} 的单元测试类
 *
 * <p>测试 Keycloak 用户同步服务的核心逻辑：单个用户同步、批量用户同步。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.4.0
 */
@DisplayName("KeycloakUserSyncService 单元测试")
@ExtendWith(MockitoExtension.class)
class KeycloakUserSyncServiceTest {

    @Mock
    private Keycloak keycloakAdminClient;

    @Mock
    private KeycloakConfig keycloakConfig;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantGuard tenantGuard;

    @Mock
    private RealmResource realmResource;

    @Mock
    private UsersResource usersResource;

    @Mock
    private UserResource userResource;

    private KeycloakUserSyncService service;

    /**
     * 设置测试环境
     */
    @BeforeEach
    void setUp() {
        service = new KeycloakUserSyncService(keycloakAdminClient, keycloakConfig, userRepository, tenantGuard);

        when(keycloakConfig.getRealm()).thenReturn("test-realm");
        when(keycloakAdminClient.realm(anyString())).thenReturn(realmResource);
        lenient().when(realmResource.users()).thenReturn(usersResource);
        lenient().when(tenantGuard.assertUserQuota(anyLong())).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("syncUserFromKeycloak 方法")
    class SyncUserFromKeycloakTests {

        /**
         * 验证从 Keycloak 同步新用户应创建本地用户
         */
        @Test
        @DisplayName("从 Keycloak 同步新用户应创建本地用户")
        void shouldCreateLocalUserWhenSyncingFromKeycloak() {
            String keycloakSub = "test-keycloak-sub";
            String username = "testuser";
            String email = "test@example.com";
            String firstName = "Test";

            UserRepresentation kcUser = new UserRepresentation();
            kcUser.setId(keycloakSub);
            kcUser.setUsername(username);
            kcUser.setEmail(email);
            kcUser.setFirstName(firstName);

            when(usersResource.get(keycloakSub)).thenReturn(userResource);
            when(userResource.toRepresentation()).thenReturn(kcUser);
            when(userRepository.findByKeycloakSub(keycloakSub)).thenReturn(Mono.empty());

            UserEntity savedUser = new UserEntity();
            savedUser.setId(1L);
            savedUser.setUsername(username);
            savedUser.setEmail(email);
            savedUser.setKeycloakSub(keycloakSub);

            when(userRepository.save(any(UserEntity.class))).thenReturn(Mono.just(savedUser));

            StepVerifier.create(service.syncUserFromKeycloak(keycloakSub))
                    .expectNextMatches(user -> {
                        assertThat(user).isNotNull();
                        assertThat(user.getUsername()).isEqualTo(username);
                        assertThat(user.getEmail()).isEqualTo(email);
                        assertThat(user.getKeycloakSub()).isEqualTo(keycloakSub);
                        return true;
                    })
                    .verifyComplete();
        }

        /**
         * 验证 Keycloak API 调用失败应传播错误
         */
        @Test
        @DisplayName("Keycloak API 调用失败应传播错误")
        void shouldPropagateErrorWhenKeycloakApiFails() {
            String keycloakSub = "test-keycloak-sub";

            when(userRepository.findByKeycloakSub(keycloakSub)).thenReturn(Mono.empty());
            when(usersResource.get(keycloakSub)).thenReturn(userResource);
            when(userResource.toRepresentation()).thenThrow(new RuntimeException("Keycloak API error"));

            StepVerifier.create(service.syncUserFromKeycloak(keycloakSub))
                    .expectError(RuntimeException.class)
                    .verify();
        }

        /**
         * 验证用户名为空时应使用 email
         */
        @Test
        @DisplayName("用户名为空时应使用 email 作为 username")
        void shouldUseEmailAsUsernameWhenUsernameIsNull() {
            String keycloakSub = "test-keycloak-sub";
            String email = "test@example.com";

            UserRepresentation kcUser = new UserRepresentation();
            kcUser.setId(keycloakSub);
            kcUser.setUsername(null);
            kcUser.setEmail(email);
            kcUser.setFirstName(null);

            when(usersResource.get(keycloakSub)).thenReturn(userResource);
            when(userResource.toRepresentation()).thenReturn(kcUser);
            when(userRepository.findByKeycloakSub(keycloakSub)).thenReturn(Mono.empty());

            UserEntity savedUser = new UserEntity();
            savedUser.setId(1L);
            savedUser.setUsername(email);
            savedUser.setEmail(email);
            savedUser.setKeycloakSub(keycloakSub);

            when(userRepository.save(any(UserEntity.class))).thenReturn(Mono.just(savedUser));

            StepVerifier.create(service.syncUserFromKeycloak(keycloakSub))
                    .expectNextMatches(user -> {
                        assertThat(user).isNotNull();
                        assertThat(user.getUsername()).isEqualTo(email);
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("syncAllUsers 方法")
    class SyncAllUsersTests {

        /**
         * 验证批量同步空用户列表应正常完成
         */
        @Test
        @DisplayName("批量同步空用户列表应正常完成")
        void shouldCompleteSuccessfullyWhenNoUsers() {
            when(usersResource.list()).thenReturn(java.util.Collections.emptyList());

            StepVerifier.create(service.syncAllUsers())
                    .assertNext(result -> {
                        assertThat(result.requested()).isZero();
                        assertThat(result.synced()).isZero();
                        assertThat(result.failed()).isZero();
                    })
                    .verifyComplete();
        }

        /**
         * 验证批量同步应处理所有用户
         */
        @Test
        @DisplayName("批量同步应处理所有用户")
        void shouldProcessAllUsersWhenSyncingAll() {
            UserRepresentation kcUser1 = new UserRepresentation();
            kcUser1.setId("user-1");
            kcUser1.setUsername("user1");
            kcUser1.setEmail("user1@example.com");

            UserRepresentation kcUser2 = new UserRepresentation();
            kcUser2.setId("user-2");
            kcUser2.setUsername("user2");
            kcUser2.setEmail("user2@example.com");

            when(usersResource.list()).thenReturn(java.util.Arrays.asList(kcUser1, kcUser2));

            UserEntity savedUser1 = new UserEntity();
            savedUser1.setId(1L);
            savedUser1.setKeycloakSub("user-1");

            UserEntity savedUser2 = new UserEntity();
            savedUser2.setId(2L);
            savedUser2.setKeycloakSub("user-2");

            when(userRepository.findByKeycloakSub(anyString())).thenReturn(Mono.empty());
            when(usersResource.get("user-1")).thenReturn(userResource);
            when(usersResource.get("user-2")).thenReturn(userResource);
            when(userResource.toRepresentation()).thenReturn(kcUser1, kcUser2);

            when(userRepository.save(any(UserEntity.class))).thenReturn(Mono.just(savedUser1), Mono.just(savedUser2));

            StepVerifier.create(service.syncAllUsers())
                    .assertNext(result -> {
                        assertThat(result.requested()).isEqualTo(2);
                        assertThat(result.synced()).isEqualTo(2);
                        assertThat(result.skipped()).isZero();
                        assertThat(result.failed()).isZero();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("status 方法")
    class StatusTests {

        /**
         * 验证连通时返回 CONNECTED 与延迟数据
         */
        @Test
        @DisplayName("连通时返回 CONNECTED")
        void shouldReturnConnectedWhenRealmReachable() {
            when(realmResource.toRepresentation())
                    .thenReturn(new org.keycloak.representations.idm.RealmRepresentation());

            StepVerifier.create(service.status())
                    .assertNext(result -> {
                        assertThat(result.enabled()).isTrue();
                        assertThat(result.status()).isEqualTo("CONNECTED");
                        assertThat(result.error()).isNull();
                    })
                    .verifyComplete();
        }

        /**
         * 验证远端失败时返回 ERROR 且不抛出
         */
        @Test
        @DisplayName("远端失败时返回 ERROR")
        void shouldReturnErrorWhenRealmUnreachable() {
            when(realmResource.toRepresentation()).thenThrow(new RuntimeException("unreachable"));

            StepVerifier.create(service.status())
                    .assertNext(result -> {
                        assertThat(result.status()).isEqualTo("ERROR");
                        assertThat(result.error()).contains("unreachable");
                    })
                    .verifyComplete();
        }
    }
}
