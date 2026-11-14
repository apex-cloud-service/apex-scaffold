package com.apex.scaffold.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.service.UnifiedAuthorizationService;
import com.apex.scaffold.auth.application.service.RoleService;
import com.apex.scaffold.auth.application.service.UnifiedAuthorizationQueryService;
import com.apex.scaffold.auth.domain.model.entity.PermissionEntity;
import com.apex.scaffold.auth.domain.model.entity.RoleEntity;
import com.apex.scaffold.auth.domain.model.entity.TokenEntity;
import com.apex.scaffold.auth.domain.model.entity.UserEntity;
import com.apex.scaffold.auth.domain.repository.PermissionRepository;
import com.apex.scaffold.auth.domain.repository.RoleRepository;
import com.apex.scaffold.auth.domain.repository.UserRepository;
import com.apex.scaffold.auth.domain.service.TokenService;
import com.apex.scaffold.authservice.interfaces.handler.UnifiedAuthorizationHandler;
import com.apex.scaffold.authservice.interfaces.router.UnifiedAuthorizationRouter;
import com.apex.scaffold.authservice.interfaces.util.RequestHandlerUtil;
import com.apex.scaffold.common.config.JwtProperties;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway.Authorization;
import com.apex.scaffold.infrastructure.cache.config.CacheProperties;
import com.apex.scaffold.infrastructure.cache.service.AuthorizationCacheAdapter;
import com.apex.scaffold.infrastructure.cache.service.LocalCacheService;
import com.apex.scaffold.infrastructure.cache.service.MultiLevelCacheService;
import com.apex.scaffold.infrastructure.remote.AuthCenterPermissionClient;
import com.apex.scaffold.infrastructure.remote.config.AuthCenterPermissionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.handler.ssl.SslContextBuilder;
import jakarta.validation.Validation;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;
import redis.embedded.RedisServer;

class DualServiceAuthorizationIntegrationTest {
    @TempDir
    Path temporary;

    private AnnotationConfigApplicationContext context;
    private DisposableServer center;
    private WebClient transport;
    private UserRepository users;

    @BeforeEach
    void setUp() throws Exception {
        Path store = temporary.resolve("test.p12");
        Process certificate = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "keytool")
                                .toString(),
                        "-genkeypair",
                        "-alias",
                        "localhost",
                        "-keyalg",
                        "RSA",
                        "-keystore",
                        store.toString(),
                        "-storetype",
                        "PKCS12",
                        "-storepass",
                        "integration-only",
                        "-keypass",
                        "integration-only",
                        "-dname",
                        "CN=localhost",
                        "-ext",
                        "SAN=dns:localhost,ip:127.0.0.1",
                        "-validity",
                        "1",
                        "-noprompt")
                .redirectErrorStream(true)
                .redirectOutput(temporary.resolve("keytool.log").toFile())
                .start();
        assertThat(certificate.waitFor()).isZero();
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(store)) {
            keys.load(input, "integration-only".toCharArray());
        }
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, "integration-only".toCharArray());
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        users = context.getBean(UserRepository.class);
        when(users.findById(1L)).thenReturn(Mono.just(user(1L, "center")));
        when(users.findById(99L)).thenReturn(Mono.just(user(99L, "center")));
        when(context.getBean(RoleRepository.class).findByUserId(99L, "center"))
                .thenReturn(Flux.just(RoleEntity.builder()
                        .id(10L)
                        .tenantId("center")
                        .code("USER")
                        .dataScope("SELF")
                        .enabled(true)
                        .build()));
        when(context.getBean(PermissionRepository.class).findByRoleId(10L))
                .thenReturn(Flux.just(PermissionEntity.builder()
                        .tenantId("center")
                        .code("user:read")
                        .enabled(true)
                        .build()));
        TokenService tokens = context.getBean(TokenService.class);
        when(tokens.validateToken(anyString())).thenReturn(Mono.error(new IllegalStateException("Invalid")));
        when(tokens.validateToken("reader")).thenReturn(Mono.just(token(1L)));
        when(tokens.validateToken("ordinary")).thenReturn(Mono.just(token(2L)));
        when(context.getBean(RoleService.class).getUserRoles(anyLong(), eq("center")))
                .thenReturn(Flux.empty());
        var tls = SslContextBuilder.forServer(managers).build();
        center = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .secure(ssl -> ssl.sslContext(tls))
                .handle(new ReactorHttpHandlerAdapter(
                        WebHttpHandlerBuilder.applicationContext(context).build()))
                .bindNow(Duration.ofSeconds(10));
        var trust = SslContextBuilder.forClient()
                .trustManager((java.security.cert.X509Certificate) keys.getCertificate("localhost"))
                .build();
        transport = WebClient.builder()
                .clientConnector(
                        new ReactorClientHttpConnector(HttpClient.create().secure(ssl -> ssl.sslContext(trust))))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (center != null) {
            center.disposeNow();
        }
        if (context != null) {
            context.close();
        }
    }

    @Test
    void mainClientQueriesRealCenterRouterSecurityAndQueryServiceOverHttps() {
        StepVerifier.create(client("reader", "center").query("main", 7L))
                .assertNext(value -> {
                    assertThat(value.tenantId()).isEqualTo("main");
                    assertThat(value.userId()).isEqualTo(7L);
                    assertThat(value.permissions()).containsExactly("user:read");
                    assertThat(value.roles())
                            .containsExactly(new com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway.Role(
                                    "USER", "SELF"));
                })
                .verifyComplete();
        verify(users).findById(1L);
        verify(users).findById(99L);
    }

    @Test
    void unifiedServiceCachesHttpsCenterAuthorizationInRealRedis() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var redis = RedisServer.newRedisServer()
                .bind("127.0.0.1")
                .port(port)
                .setting("maxmemory 32mb")
                .build();
        RedissonClient redisson = null;
        redis.start();
        try {
            var config = new Config();
            config.setThreads(2).setNettyThreads(2);
            config.useSingleServer()
                    .setAddress("redis://127.0.0.1:" + port)
                    .setConnectionMinimumIdleSize(1)
                    .setConnectionPoolSize(2);
            redisson = Redisson.create(config);
            var mapper = new ObjectMapper();
            var cache = new MultiLevelCacheService(
                    new LocalCacheService(new CaffeineCacheManager(), mapper),
                    redisson,
                    mapper,
                    new CacheProperties(),
                    new SimpleMeterRegistry());
            var service = new UnifiedAuthorizationService(
                    client("reader", "center"),
                    new MockEnvironment()
                            .withProperty("apex.features.auth-center-permission", "true")
                            .withProperty(
                                    "apex.auth-center-permission.cache-seconds",
                                    System.getProperty("apex.test.cache-seconds", "30")),
                    new AuthorizationCacheAdapter(cache));
            var trustedTenant = TenantContext.withTenant("main");
            StepVerifier.create(service.permissions(7L).contextWrite(trustedTenant))
                    .assertNext(value -> assertThat(value)
                            .extracting(com.apex.scaffold.domain.model.entity.PermissionEntity::getCode)
                            .containsExactly("user:read"))
                    .verifyComplete();
            String key = "auth-center:authorization:v1:bWFpbg:7";
            assertThat(redisson.getBucket(key, StringCodec.INSTANCE).remainTimeToLive())
                    .isBetween(1L, 30000L);
            StepVerifier.create(cache.clearLocal()).verifyComplete();
            StepVerifier.create(service.permissions(7L).contextWrite(trustedTenant))
                    .assertNext(value -> assertThat(value)
                            .extracting(com.apex.scaffold.domain.model.entity.PermissionEntity::getCode)
                            .containsExactly("user:read"))
                    .verifyComplete();
            verify(users, times(1)).findById(99L);
            verify(context.getBean(RoleRepository.class), times(1)).findByUserId(99L, "center");
            verify(context.getBean(PermissionRepository.class), times(1)).findByRoleId(10L);
        } finally {
            if (redisson != null) {
                redisson.shutdown();
            }
            redis.stop();
        }
    }

    @Test
    void missingAndInvalidBearerAreUnauthorized() {
        StepVerifier.create(transport
                        .get()
                        .uri(endpoint() + "/auth/v1/authorization/users/99")
                        .retrieve()
                        .bodyToMono(Authorization.class))
                .expectErrorMatches(error -> error instanceof WebClientResponseException response
                        && response.getStatusCode().value() == 401)
                .verify();
        StepVerifier.create(client("invalid", "center").query("main", 7L))
                .expectErrorMatches(error -> error instanceof WebClientResponseException response
                        && response.getStatusCode().value() == 401)
                .verify();
        verifyNoInteractions(users);
    }

    @Test
    void ordinaryAccountAndForeignTargetAreForbidden() {
        StepVerifier.create(client("ordinary", "center").query("main", 7L))
                .expectErrorMatches(error -> error instanceof WebClientResponseException response
                        && response.getStatusCode().value() == 403)
                .verify();
        verifyNoInteractions(users);
        when(users.findById(99L)).thenReturn(Mono.just(user(99L, "foreign")));
        StepVerifier.create(client("reader", "center").query("main", 7L))
                .expectErrorMatches(error -> error instanceof WebClientResponseException response
                        && response.getStatusCode().value() == 403)
                .verify();
    }

    @Test
    void incorrectTrustedMappingRejectsCenterResponse() {
        StepVerifier.create(client("reader", "foreign").query("main", 7L))
                .expectError(IllegalStateException.class)
                .verify();
    }

    private AuthCenterPermissionClient client(String token, String mappedTenant) {
        AuthCenterPermissionProperties properties = new AuthCenterPermissionProperties();
        properties.setBaseUrl(endpoint());
        properties.setAccessToken(token);
        properties.setMappings(List.of(new AuthCenterPermissionProperties.Mapping("main", 7L, mappedTenant, 99L)));
        return new AuthCenterPermissionClient(properties, transport, null);
    }

    private String endpoint() {
        return "https://localhost:" + center.port();
    }

    private UserEntity user(Long id, String tenant) {
        return UserEntity.builder().id(id).tenantId(tenant).status(1).deleted(0).build();
    }

    private TokenEntity token(Long id) {
        return TokenEntity.builder()
                .userId(id)
                .tenantId("center")
                .tokenType("ACCESS")
                .build();
    }

    @Configuration
    @EnableWebFlux
    @EnableWebFluxSecurity
    static class TestConfiguration {
        @Bean
        TokenService tokens() {
            return mock(TokenService.class);
        }

        @Bean
        RoleService roles() {
            return mock(RoleService.class);
        }

        @Bean
        UserRepository users() {
            return mock(UserRepository.class);
        }

        @Bean
        RoleRepository roleRepository() {
            return mock(RoleRepository.class);
        }

        @Bean
        PermissionRepository permissions() {
            return mock(PermissionRepository.class);
        }

        @Bean
        UnifiedAuthorizationQueryService service(
                UserRepository users, RoleRepository roles, PermissionRepository permissions) {
            return new UnifiedAuthorizationQueryService(users, roles, permissions);
        }

        @Bean
        SecurityWebFilterChain security(ServerHttpSecurity http, TokenService tokens, RoleService roles) {
            return new AuthCenterConfig()
                    .securityWebFilterChain(
                            http,
                            tokens,
                            roles,
                            new JwtProperties(),
                            new MockEnvironment().withProperty("auth-center.authorization-reader-user-id", "1"));
        }

        @Bean
        RouterFunction<ServerResponse> routes(UnifiedAuthorizationQueryService service) {
            UnifiedAuthorizationHandler handler = new UnifiedAuthorizationHandler(
                    service,
                    new MockEnvironment().withProperty("auth-center.authorization-reader-user-id", "1"),
                    new RequestHandlerUtil(
                            Validation.buildDefaultValidatorFactory().getValidator()));
            return new UnifiedAuthorizationRouter().unifiedAuthorizationRoutes(handler);
        }
    }
}
