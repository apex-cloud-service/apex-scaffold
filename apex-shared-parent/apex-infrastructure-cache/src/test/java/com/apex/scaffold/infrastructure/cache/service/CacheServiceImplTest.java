package com.apex.scaffold.infrastructure.cache.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import reactor.test.StepVerifier;

/**
 * 缓存服务实现类单元测试
 *
 * <p>测试 CacheServiceImpl 的 get、set、delete、getBatch、setBatch 方法，
 * 验证 Redisson 缓存操作的正确性，包括空值处理、JSON序列化、批量操作等场景。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class CacheServiceImplTest {

    @Mock
    private RedissonClient redissonClient;

    private ObjectMapper objectMapper;

    private CacheServiceImpl cacheService;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        cacheService = new CacheServiceImpl(redissonClient, objectMapper);
    }

    @Test
    @DisplayName("get - key为null应返回Mono.empty")
    void get_NullKey_ReturnsEmpty() {
        cacheService.get(null, String.class).as(StepVerifier::create).verifyComplete();
    }

    @Test
    @DisplayName("get - 缓存中存在String值应返回该值")
    void get_StringValueExists_ReturnsValue() {
        RBucket<Object> bucket = mock(RBucket.class);
        when(redissonClient.getBucket(eq("test-key"), any())).thenReturn(bucket);
        when(bucket.get()).thenReturn("test-value");

        cacheService
                .get("test-key", String.class)
                .as(StepVerifier::create)
                .expectNext("test-value")
                .verifyComplete();
    }

    @Test
    @DisplayName("get - 缓存中值为null应返回Mono.empty")
    void get_NullValue_ReturnsEmpty() {
        RBucket<Object> bucket = mock(RBucket.class);
        when(redissonClient.getBucket(eq("test-key"), any())).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);

        cacheService.get("test-key", String.class).as(StepVerifier::create).verifyComplete();
    }

    @Test
    @DisplayName("get - JSON反序列化失败应返回Mono.error")
    void get_TypeMismatchAndNotString_ReturnsEmpty() {
        RBucket<Object> bucket = mock(RBucket.class);
        when(redissonClient.getBucket(eq("test-key"), any())).thenReturn(bucket);
        when(bucket.get()).thenReturn("not-valid-json");

        cacheService.get("test-key", Integer.class).as(StepVerifier::create).verifyError();
    }

    @Test
    @DisplayName("get - 缓存值为JSON字符串应反序列化为目标类型")
    void get_JsonString_DeserializesToTargetType() throws Exception {
        RBucket<Object> bucket = mock(RBucket.class);
        when(redissonClient.getBucket(eq("user-key"), any())).thenReturn(bucket);
        when(bucket.get()).thenReturn("{\"name\":\"Alice\"}");

        cacheService
                .get("user-key", UserInfo.class)
                .as(StepVerifier::create)
                .assertNext(user -> assertThat(user.getName()).isEqualTo("Alice"))
                .verifyComplete();
    }

    /**
     * 设置{@code _NullKey_ReturnsFalse}。
     */
    @Test
    @DisplayName("set - key为null应返回false")
    void set_NullKey_ReturnsFalse() {
        cacheService
                .set(null, "value", Duration.ofMinutes(1))
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 设置{@code _StringValue_SetsDirectly}。
     */
    @Test
    @DisplayName("set - 存储String值应直接存入Redis")
    void set_StringValue_SetsDirectly() {
        RBucket<Object> bucket = mock(RBucket.class);
        when(redissonClient.getBucket(eq("str-key"), any())).thenReturn(bucket);
        doNothing().when(bucket).set("hello", Duration.ofMinutes(5));

        cacheService
                .set("str-key", "hello", Duration.ofMinutes(5))
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();

        verify(bucket).set("hello", Duration.ofMinutes(5));
    }

    /**
     * 设置{@code _NumberValue_SetsDirectly}。
     */
    @Test
    @DisplayName("set - 存储Number值应直接存入Redis")
    void set_NumberValue_SetsDirectly() {
        RBucket<Object> bucket = mock(RBucket.class);
        when(redissonClient.getBucket(eq("num-key"), any())).thenReturn(bucket);
        doNothing().when(bucket).set("42", Duration.ofMinutes(1));

        cacheService
                .set("num-key", 42, Duration.ofMinutes(1))
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();

        verify(bucket).set("42", Duration.ofMinutes(1));
    }

    /**
     * 设置{@code _BooleanValue_SetsDirectly}。
     */
    @Test
    @DisplayName("set - 存储Boolean值应直接存入Redis")
    void set_BooleanValue_SetsDirectly() {
        RBucket<Object> bucket = mock(RBucket.class);
        when(redissonClient.getBucket(eq("bool-key"), any())).thenReturn(bucket);
        doNothing().when(bucket).set("true", Duration.ofMinutes(1));

        cacheService
                .set("bool-key", true, Duration.ofMinutes(1))
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();

        verify(bucket).set("true", Duration.ofMinutes(1));
    }

    /**
     * 设置{@code _ComplexObject_SerializesToJson}。
     */
    @Test
    @DisplayName("set - 存储复杂对象应序列化为JSON再存入Redis")
    void set_ComplexObject_SerializesToJson() throws Exception {
        RBucket<Object> bucket = mock(RBucket.class);
        when(redissonClient.getBucket(eq("obj-key"), any())).thenReturn(bucket);
        doNothing().when(bucket).set(anyString(), eq(Duration.ofMinutes(1)));

        UserInfo user = new UserInfo("Alice");
        cacheService
                .set("obj-key", user, Duration.ofMinutes(1))
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();

        verify(bucket).set(objectMapper.writeValueAsString(user), Duration.ofMinutes(1));
    }

    /**
     * 删除{@code _NullKeys_ReturnsZero}。
     */
    @Test
    @DisplayName("delete - 传入null应返回0L")
    void delete_NullKeys_ReturnsZero() {
        cacheService
                .delete((String[]) null)
                .as(StepVerifier::create)
                .expectNext(0L)
                .verifyComplete();
    }

    /**
     * 删除{@code _EmptyArray_ReturnsZero}。
     */
    @Test
    @DisplayName("delete - 传入空数组应返回0L")
    void delete_EmptyArray_ReturnsZero() {
        cacheService.delete().as(StepVerifier::create).expectNext(0L).verifyComplete();
    }

    /**
     * 删除{@code _MultipleKeys_ReturnsDeletedCount}。
     */
    @Test
    @DisplayName("delete - 删除多个key应返回删除数量")
    void delete_MultipleKeys_ReturnsDeletedCount() {
        RKeys rKeys = mock(RKeys.class);
        when(redissonClient.getKeys()).thenReturn(rKeys);
        when(rKeys.delete("key1", "key2", "key3")).thenReturn(2L);

        cacheService
                .delete("key1", "key2", "key3")
                .as(StepVerifier::create)
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    @DisplayName("getBatch - 传入null应返回空列表")
    void getBatch_NullKeys_ReturnsEmptyList() {
        cacheService
                .getBatch(null, String.class)
                .as(StepVerifier::create)
                .expectNext(List.of())
                .verifyComplete();
    }

    @Test
    @DisplayName("getBatch - 传入空列表应返回空列表")
    void getBatch_EmptyKeys_ReturnsEmptyList() {
        cacheService
                .getBatch(List.of(), String.class)
                .as(StepVerifier::create)
                .expectNext(List.of())
                .verifyComplete();
    }

    @Test
    @DisplayName("getBatch - 批量获取缓存值")
    void getBatch_MultipleKeys_ReturnsValues() {
        RBucket<Object> bucket1 = mock(RBucket.class);
        RBucket<Object> bucket2 = mock(RBucket.class);
        when(redissonClient.getBucket(eq("k1"), any())).thenReturn(bucket1);
        when(redissonClient.getBucket(eq("k2"), any())).thenReturn(bucket2);
        when(bucket1.get()).thenReturn("v1");
        when(bucket2.get()).thenReturn("v2");

        cacheService
                .getBatch(List.of("k1", "k2"), String.class)
                .as(StepVerifier::create)
                .assertNext(results -> {
                    assertThat(results).hasSize(2);
                    assertThat(results).containsExactly("v1", "v2");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getBatch - 包含null key应被过滤")
    void getBatch_WithNullKey_FiltersOutNull() {
        RBucket<Object> bucket = mock(RBucket.class);
        when(redissonClient.getBucket(eq("k1"), any())).thenReturn(bucket);
        when(bucket.get()).thenReturn("v1");

        cacheService
                .getBatch(Arrays.asList("k1", null), String.class)
                .as(StepVerifier::create)
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    assertThat(results).containsExactly("v1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getBatch - 缓存中null值应被过滤")
    void getBatch_WithNullValue_FiltersOutNull() {
        RBucket<Object> bucket1 = mock(RBucket.class);
        RBucket<Object> bucket2 = mock(RBucket.class);
        when(redissonClient.getBucket(eq("k1"), any())).thenReturn(bucket1);
        when(redissonClient.getBucket(eq("k2"), any())).thenReturn(bucket2);
        when(bucket1.get()).thenReturn("v1");
        when(bucket2.get()).thenReturn(null);

        cacheService
                .getBatch(List.of("k1", "k2"), String.class)
                .as(StepVerifier::create)
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    assertThat(results).containsExactly("v1");
                })
                .verifyComplete();
    }

    /**
     * 设置{@code batch_NullMap_ReturnsFalse}。
     */
    @Test
    @DisplayName("setBatch - 传入null应返回false")
    void setBatch_NullMap_ReturnsFalse() {
        cacheService
                .setBatch(null, Duration.ofMinutes(1))
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 设置{@code batch_EmptyMap_ReturnsFalse}。
     */
    @Test
    @DisplayName("setBatch - 传入空Map应返回false")
    void setBatch_EmptyMap_ReturnsFalse() {
        cacheService
                .setBatch(Map.of(), Duration.ofMinutes(1))
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 设置{@code batch_StringValues_SetsAll}。
     */
    @Test
    @DisplayName("setBatch - 批量设置String值")
    void setBatch_StringValues_SetsAll() {
        RBucket<Object> bucket1 = mock(RBucket.class);
        RBucket<Object> bucket2 = mock(RBucket.class);
        when(redissonClient.getBucket(eq("k1"), any())).thenReturn(bucket1);
        when(redissonClient.getBucket(eq("k2"), any())).thenReturn(bucket2);
        doNothing().when(bucket1).set("v1", Duration.ofMinutes(1));
        doNothing().when(bucket2).set("v2", Duration.ofMinutes(1));

        cacheService
                .setBatch(Map.of("k1", "v1", "k2", "v2"), Duration.ofMinutes(1))
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();

        verify(bucket1).set("v1", Duration.ofMinutes(1));
        verify(bucket2).set("v2", Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("setBatch - 单项写入异常应返回false")
    void setBatch_WhenOneWriteFails_ReturnsFalse() {
        RBucket<Object> bucket1 = mock(RBucket.class);
        RBucket<Object> bucket2 = mock(RBucket.class);
        when(redissonClient.getBucket(eq("k1"), any())).thenReturn(bucket1);
        when(redissonClient.getBucket(eq("k2"), any())).thenReturn(bucket2);
        doNothing().when(bucket1).set("v1", Duration.ofMinutes(1));
        doThrow(new IllegalStateException("write failed")).when(bucket2).set("v2", Duration.ofMinutes(1));
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("k1", "v1");
        entries.put("k2", "v2");

        cacheService
                .setBatch(entries, Duration.ofMinutes(1))
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 设置{@code batch_ComplexObject_SerializesToJson}。
     */
    @Test
    @DisplayName("setBatch - 批量设置复杂对象应序列化为JSON")
    void setBatch_ComplexObject_SerializesToJson() throws Exception {
        RBucket<Object> bucket = mock(RBucket.class);
        when(redissonClient.getBucket(eq("uk"), any())).thenReturn(bucket);
        doNothing().when(bucket).set(anyString(), eq(Duration.ofMinutes(2)));

        UserInfo user = new UserInfo("Bob");
        cacheService
                .setBatch(Map.of("uk", user), Duration.ofMinutes(2))
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();

        verify(bucket).set(objectMapper.writeValueAsString(user), Duration.ofMinutes(2));
    }

    @Getter
    @Setter
    static class UserInfo {
        private String name;

        public UserInfo() {}

        public UserInfo(String name) {
            this.name = name;
        }
    }
}
