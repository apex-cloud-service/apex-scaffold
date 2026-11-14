package com.apex.scaffold.auth.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWT密钥轮换服务测试")
class JwtKeyRotationServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private ReactiveSetOperations<String, String> setOperations;

    private JwtKeyRotationService service;

    private static final String TEST_KEY_ID = "key-test-123";
    private static final String REDIS_KEY_CURRENT = "jwt:keys:current";
    private static final String REDIS_KEY_ALL = "jwt:keys:all";

    private KeyPair testKeyPair;
    private String privateKeyEncoded;
    private String publicKeyEncoded;

    @BeforeEach
    void setUp() throws Exception {
        service = new JwtKeyRotationService(redisTemplate);

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        testKeyPair = keyGen.generateKeyPair();

        privateKeyEncoded =
                Base64.getEncoder().encodeToString(testKeyPair.getPrivate().getEncoded());
        publicKeyEncoded =
                Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());
    }

    @Test
    @DisplayName("初始化密钥 - 系统中已有密钥")
    void testInitializeKeys_ExistingKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY_CURRENT)).thenReturn(Mono.just(TEST_KEY_ID));

        StepVerifier.create(service.initializeKeys())
                .expectNext("JWT密钥已就绪: keyId=" + TEST_KEY_ID)
                .verifyComplete();

        verify(valueOperations).get(REDIS_KEY_CURRENT);
    }

    @Test
    @DisplayName("初始化密钥 - 系统中没有密钥，生成新密钥")
    void testInitializeKeys_NoExistingKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.get(REDIS_KEY_CURRENT)).thenReturn(Mono.empty());
        when(valueOperations.set(anyString(), anyString())).thenReturn(Mono.just(true));
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(setOperations.members(REDIS_KEY_ALL)).thenReturn(Flux.empty());

        StepVerifier.create(service.initializeKeys())
                .expectNextMatches(result -> result.contains("JWT密钥已就绪"))
                .verifyComplete();

        verify(valueOperations, atLeastOnce()).set(eq(REDIS_KEY_CURRENT), anyString());
    }

    @Test
    @DisplayName("获取当前密钥ID - 成功")
    void testGetCurrentKeyId_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY_CURRENT)).thenReturn(Mono.just(TEST_KEY_ID));

        StepVerifier.create(service.getCurrentKeyId()).expectNext(TEST_KEY_ID).verifyComplete();
    }

    @Test
    @DisplayName("获取当前密钥ID - 不存在")
    void testGetCurrentKeyId_NotExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY_CURRENT)).thenReturn(Mono.empty());

        StepVerifier.create(service.getCurrentKeyId()).verifyComplete();
    }

    @Test
    @DisplayName("获取当前私钥 - 成功")
    void testGetCurrentPrivateKey_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY_CURRENT)).thenReturn(Mono.just(TEST_KEY_ID));
        when(valueOperations.get("jwt:key:" + TEST_KEY_ID + ":private")).thenReturn(Mono.just(privateKeyEncoded));

        StepVerifier.create(service.getCurrentPrivateKey())
                .assertNext(privateKey -> {
                    assertNotNull(privateKey);
                    assertEquals("RSA", privateKey.getAlgorithm());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("获取当前私钥 - 密钥未初始化")
    void testGetCurrentPrivateKey_NotInitialized() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY_CURRENT)).thenReturn(Mono.empty());

        StepVerifier.create(service.getCurrentPrivateKey())
                .expectErrorMatches(e ->
                        e instanceof IllegalStateException && e.getMessage().contains("JWT密钥未初始化"))
                .verify();
    }

    @Test
    @DisplayName("获取当前公钥 - 成功")
    void testGetCurrentPublicKey_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY_CURRENT)).thenReturn(Mono.just(TEST_KEY_ID));
        when(valueOperations.get("jwt:key:" + TEST_KEY_ID + ":public")).thenReturn(Mono.just(publicKeyEncoded));

        StepVerifier.create(service.getCurrentPublicKey())
                .assertNext(publicKey -> {
                    assertNotNull(publicKey);
                    assertEquals("RSA", publicKey.getAlgorithm());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("获取当前公钥 - 密钥未初始化")
    void testGetCurrentPublicKey_NotInitialized() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY_CURRENT)).thenReturn(Mono.empty());

        StepVerifier.create(service.getCurrentPublicKey())
                .expectErrorMatches(e ->
                        e instanceof IllegalStateException && e.getMessage().contains("JWT密钥未初始化"))
                .verify();
    }

    @Test
    @DisplayName("根据密钥ID获取公钥 - 成功")
    void testGetPublicKey_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("jwt:key:" + TEST_KEY_ID + ":public")).thenReturn(Mono.just(publicKeyEncoded));

        StepVerifier.create(service.getPublicKey(TEST_KEY_ID))
                .assertNext(publicKey -> {
                    assertNotNull(publicKey);
                    assertEquals("RSA", publicKey.getAlgorithm());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("根据密钥ID获取公钥 - 密钥不存在")
    void testGetPublicKey_NotExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("jwt:key:" + TEST_KEY_ID + ":public")).thenReturn(Mono.empty());

        StepVerifier.create(service.getPublicKey(TEST_KEY_ID)).verifyComplete();
    }

    @Test
    @DisplayName("根据密钥ID获取公钥 - 解析失败")
    void testGetPublicKey_ParseError() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("jwt:key:" + TEST_KEY_ID + ":public")).thenReturn(Mono.just("invalid-base64"));

        StepVerifier.create(service.getPublicKey(TEST_KEY_ID))
                .expectErrorMatches(
                        e -> e instanceof RuntimeException && e.getMessage().contains("解析公钥失败"))
                .verify();
    }

    @Test
    @DisplayName("执行密钥轮换 - 成功")
    void testRotateKeys_Success() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.set(anyString(), anyString())).thenReturn(Mono.just(true));
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(setOperations.members(REDIS_KEY_ALL)).thenReturn(Flux.empty());

        StepVerifier.create(service.rotateKeys())
                .assertNext(newKeyId -> {
                    assertNotNull(newKeyId);
                    assertTrue(newKeyId.startsWith("key-"));
                })
                .verifyComplete();

        verify(valueOperations, atLeastOnce()).set(eq(REDIS_KEY_CURRENT), anyString());
        verify(setOperations, atLeastOnce()).add(eq(REDIS_KEY_ALL), anyString());
    }

    @Test
    @DisplayName("执行密钥轮换 - 清理过期密钥")
    void testRotateKeys_WithCleanup() {
        String oldKeyId = "key-old-456";
        Instant oldKeyCreated = Instant.now().minus(40, ChronoUnit.DAYS);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.set(anyString(), anyString())).thenReturn(Mono.just(true));
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(setOperations.members(REDIS_KEY_ALL)).thenReturn(Flux.just(oldKeyId));
        when(valueOperations.get("jwt:key:" + oldKeyId + ":created"))
                .thenReturn(Mono.just(String.valueOf(oldKeyCreated.toEpochMilli())));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
        when(setOperations.remove(anyString(), any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.rotateKeys())
                .assertNext(newKeyId -> assertTrue(newKeyId.startsWith("key-")))
                .verifyComplete();

        verify(redisTemplate, atLeastOnce()).delete(contains(oldKeyId));
    }

    @Test
    @DisplayName("获取所有可用密钥ID")
    void testGetAllAvailableKeyIds() {
        String keyId1 = "key-1";
        String keyId2 = "key-2";

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(REDIS_KEY_ALL)).thenReturn(Flux.just(keyId1, keyId2));

        StepVerifier.create(service.getAllAvailableKeyIds())
                .assertNext(keyIds -> {
                    assertEquals(2, keyIds.size());
                    assertTrue(keyIds.contains(keyId1));
                    assertTrue(keyIds.contains(keyId2));
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("强制轮换密钥 - 成功")
    void testForceRotateKeys_Success() {
        String oldKeyId1 = "key-old-1";
        String oldKeyId2 = "key-old-2";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.set(anyString(), anyString())).thenReturn(Mono.just(true));
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(setOperations.members(REDIS_KEY_ALL)).thenReturn(Flux.empty()).thenReturn(Flux.just(oldKeyId1, oldKeyId2));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
        when(setOperations.remove(anyString(), any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.forceRotateKeys("密钥泄露"))
                .assertNext(newKeyId -> {
                    assertNotNull(newKeyId);
                    assertTrue(newKeyId.startsWith("key-"));
                })
                .verifyComplete();

        verify(redisTemplate, atLeastOnce()).delete(anyString());
        verify(setOperations, atLeastOnce()).remove(eq(REDIS_KEY_ALL), anyString());
    }

    @Test
    @DisplayName("强制轮换密钥 - 删除所有旧密钥")
    void testForceRotateKeys_RemoveAllOldKeys() {
        String oldKeyId = "key-old-789";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(valueOperations.set(anyString(), anyString())).thenReturn(Mono.just(true));
        when(setOperations.add(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(setOperations.members(REDIS_KEY_ALL)).thenReturn(Flux.empty()).thenReturn(Flux.just(oldKeyId));
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
        when(setOperations.remove(anyString(), any())).thenReturn(Mono.just(1L));

        StepVerifier.create(service.forceRotateKeys("安全审计要求"))
                .assertNext(newKeyId -> assertNotEquals(oldKeyId, newKeyId))
                .verifyComplete();

        verify(redisTemplate, times(3)).delete(contains(oldKeyId));
    }
}
