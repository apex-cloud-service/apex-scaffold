package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway.Authorization;
import com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway.Role;
import com.apex.scaffold.infrastructure.cache.config.CacheProperties;
import com.apex.scaffold.infrastructure.cache.service.AuthorizationCacheAdapter;
import com.apex.scaffold.infrastructure.cache.service.LocalCacheService;
import com.apex.scaffold.infrastructure.cache.service.MultiLevelCacheService;
import com.apex.scaffold.infrastructure.remote.AuthCenterPermissionClient;
import com.apex.scaffold.infrastructure.remote.config.AuthCenterPermissionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;
import redis.embedded.RedisServer;

class UnifiedAuthorizationServiceIntegrationTest {
    private static final String REDIS_KEY = "auth-center:authorization:v1:bWFpbg:7";
    private static final String PASSWORD = "integration-only";

    @TempDir
    Path temporary;

    private DisposableServer authorizationServer;
    private RedisServer redisServer;
    private AtomicInteger authorizationRequests;
    private RedissonClient redisson;
    private MultiLevelCacheService cache;

    @BeforeEach
    void setUp() throws Exception {
        authorizationServer = startHttpsAuthorizationServer();
        int redisPort = freePort();
        redisServer =
                RedisServer.newRedisServer().bind("127.0.0.1").port(redisPort).build();
        redisServer.start();
        Config config = new Config();
        config.setThreads(2).setNettyThreads(2);
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:" + redisPort)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(2);
        redisson = Redisson.create(config);
        ObjectMapper mapper = new ObjectMapper();
        cache = new MultiLevelCacheService(
                new LocalCacheService(new CaffeineCacheManager(), mapper),
                redisson,
                mapper,
                new CacheProperties(),
                new SimpleMeterRegistry());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (cache != null) {
            cache.clearLocal().block(Duration.ofSeconds(5));
        }
        if (redisson != null) {
            redisson.getKeys().delete(REDIS_KEY);
            redisson.shutdown();
        }
        if (redisServer != null) {
            redisServer.stop();
        }
        if (authorizationServer != null) {
            authorizationServer.disposeNow();
        }
    }

    @Test
    void productionClientUsesHttpsAndAuthorizationServiceUsesRealRedisCache() throws Exception {
        AuthCenterPermissionClient client = client("test-access-token");
        UnifiedAuthorizationService service = service(client, 30);

        StepVerifier.create(service.permissions(7L).contextWrite(TenantContext.withTenant("main")))
                .assertNext(permissions -> assertThat(permissions)
                        .extracting(permission -> permission.getCode())
                        .containsExactly("user:read"))
                .verifyComplete();
        assertThat(redisson.getBucket(REDIS_KEY, StringCodec.INSTANCE).remainTimeToLive())
                .isBetween(1L, 30000L);
        assertThat(authorizationRequests).hasValue(1);

        cache.clearLocal().block(Duration.ofSeconds(5));
        authorizationServer.disposeNow();
        authorizationServer = null;
        StepVerifier.create(service.roles(7L).contextWrite(TenantContext.withTenant("main")))
                .assertNext(roles ->
                        assertThat(roles).extracting(role -> role.getCode()).containsExactly("ADMIN"))
                .verifyComplete();
    }

    @Test
    void productionClientRejectsNonHttpsEndpointBeforeNetworkAccess() {
        AuthCenterPermissionProperties properties = properties("http://127.0.0.1:1");
        AuthCenterPermissionClient client = new AuthCenterPermissionClient(
                properties,
                org.springframework.web.reactive.function.client.WebClient.builder()
                        .build(),
                null);

        StepVerifier.create(client.query("main", 7L))
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && error.getMessage().contains("Secure authorization endpoint unavailable"))
                .verify();
    }

    @Test
    void productionClientRejectsInvalidBearerAndDoesNotPopulateRedis() {
        UnifiedAuthorizationService service = service(client("wrong-token"), 30);

        StepVerifier.create(service.permissions(7L).contextWrite(TenantContext.withTenant("main")))
                .expectErrorMatches(error -> error instanceof WebClientResponseException.Unauthorized)
                .verify();
        assertThat(redisson.getBucket(REDIS_KEY, StringCodec.INSTANCE).isExists())
                .isFalse();
    }

    private UnifiedAuthorizationService service(AuthCenterPermissionClient client, long cacheSeconds) {
        return new UnifiedAuthorizationService(
                client,
                new MockEnvironment()
                        .withProperty("apex.features.auth-center-permission", "true")
                        .withProperty("apex.auth-center-permission.cache-seconds", String.valueOf(cacheSeconds)),
                new AuthorizationCacheAdapter(cache));
    }

    private AuthCenterPermissionClient client(String token) {
        return new AuthCenterPermissionClient(
                properties(endpoint(), token),
                org.springframework.web.reactive.function.client.WebClient.builder()
                        .clientConnector(new ReactorClientHttpConnector(clientHttpClient()))
                        .build(),
                null);
    }

    private AuthCenterPermissionProperties properties(String baseUrl) {
        return properties(baseUrl, "test-access-token");
    }

    private AuthCenterPermissionProperties properties(String baseUrl, String token) {
        AuthCenterPermissionProperties properties = new AuthCenterPermissionProperties();
        properties.setBaseUrl(baseUrl);
        properties.setAccessToken(token);
        properties.setMappings(List.of(new AuthCenterPermissionProperties.Mapping("main", 7L, "center", 99L)));
        return properties;
    }

    private HttpClient clientHttpClient() {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(temporary.resolve("test.p12"))) {
                keyStore.load(input, PASSWORD.toCharArray());
            }
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate("localhost");
            SslContext sslContext =
                    SslContextBuilder.forClient().trustManager(certificate).build();
            return HttpClient.create().secure(ssl -> ssl.sslContext(sslContext));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to create test HTTPS client", error);
        }
    }

    private DisposableServer startHttpsAuthorizationServer() throws Exception {
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
                        PASSWORD,
                        "-keypass",
                        PASSWORD,
                        "-dname",
                        "CN=localhost",
                        "-ext",
                        "SAN=dns:localhost,ip:127.0.0.1",
                        "-validity",
                        "1",
                        "-noprompt")
                .redirectErrorStream(true)
                .start();
        assertThat(certificate.waitFor()).isZero();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(store)) {
            keyStore.load(input, PASSWORD.toCharArray());
        }
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keyStore, PASSWORD.toCharArray());
        SslContext sslContext = SslContextBuilder.forServer(managers).build();
        authorizationRequests = new AtomicInteger();
        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(Result.success(
                new Authorization("center", 99L, List.of("user:read"), List.of(new Role("ADMIN", "SELF")))));
        return HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .secure(ssl -> ssl.sslContext(sslContext))
                .handle((request, response) -> {
                    if (!"/auth/v1/authorization/users/99".equals(request.uri())) {
                        return response.status(404).send();
                    }
                    if (!"Bearer test-access-token"
                            .equals(request.requestHeaders().get("Authorization"))) {
                        return response.status(401).send();
                    }
                    authorizationRequests.incrementAndGet();
                    return response.header("Content-Type", "application/json").sendString(Mono.just(body));
                })
                .bindNow(Duration.ofSeconds(10));
    }

    private String endpoint() {
        return "https://localhost:" + authorizationServer.port();
    }

    private int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
