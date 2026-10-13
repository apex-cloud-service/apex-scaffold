package com.apex.scaffold.infrastructure.cache.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway.Authorization;
import com.apex.scaffold.domain.gateway.UnifiedAuthorizationGateway.Role;
import com.apex.scaffold.infrastructure.cache.config.CacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import reactor.test.StepVerifier;
import redis.embedded.RedisServer;

class RealRedisCacheIntegrationTest {
    private RedisServer server;
    private RedissonClient writer;
    private RedissonClient reader;
    private MultiLevelCacheService cache;
    private MultiLevelCacheService otherCache;
    private final String key = "integration:tenant:" + UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        server = RedisServer.newRedisServer()
                .bind("127.0.0.1")
                .port(port)
                .setting("maxmemory 32mb")
                .build();
        server.start();
        writer = client(port);
        reader = client(port);
        cache = cache(writer);
        otherCache = cache(reader);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (reader != null) {
            reader.shutdown();
        }
        if (writer != null) {
            writer.shutdown();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void authorizationRoundTripsAcrossIndependentClientsAndInvalidatesLocalCache() {
        Authorization value = new Authorization("tenant", 7L, List.of("user:read"), List.of(new Role("USER", "SELF")));
        AuthorizationCacheAdapter adapter = new AuthorizationCacheAdapter(cache);
        StepVerifier.create(adapter.set(key, value, Duration.ofSeconds(30)))
                .expectNext(true)
                .verifyComplete();
        assertThat(reader.getBucket(key, StringCodec.INSTANCE).remainTimeToLive())
                .isBetween(1L, 30000L);
        StepVerifier.create(new AuthorizationCacheAdapter(otherCache).get(key, Authorization.class))
                .expectNext(value)
                .verifyComplete();
        StepVerifier.create(cache.get(key, Authorization.class))
                .expectNext(value)
                .verifyComplete();
        StepVerifier.create(cache.delete(key)).expectNext(1L).verifyComplete();
        assertThat(reader.getBucket(key, StringCodec.INSTANCE).isExists()).isFalse();
        StepVerifier.create(adapter.get(key, Authorization.class)).verifyComplete();
    }

    @Test
    void redisExpiresValueAndColdReaderDoesNotReturnIt() {
        StepVerifier.create(cache.set(key, "short-lived", Duration.ofMillis(200)))
                .expectNext(true)
                .verifyComplete();
        StepVerifier.create(reactor.core.publisher.Mono.delay(Duration.ofMillis(350)))
                .expectNext(0L)
                .verifyComplete();
        assertThat(reader.getBucket(key, StringCodec.INSTANCE).isExists()).isFalse();
        StepVerifier.create(otherCache.get(key, String.class)).verifyComplete();
    }

    private RedissonClient client(int port) {
        Config config = new Config();
        config.setThreads(2).setNettyThreads(2);
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:" + port)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(2);
        return Redisson.create(config);
    }

    private MultiLevelCacheService cache(RedissonClient client) {
        ObjectMapper mapper = new ObjectMapper();
        return new MultiLevelCacheService(
                new LocalCacheService(new CaffeineCacheManager(), mapper),
                client,
                mapper,
                new CacheProperties(),
                new SimpleMeterRegistry());
    }
}
