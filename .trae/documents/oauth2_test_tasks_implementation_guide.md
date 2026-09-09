# OAuth2单元测试短期和中期任务实施指南

> **创建时间**: 2026-09-08  
> **状态**: 待实施  
> **限制**: 坚决不允许使用H2数据库  
> **说明**: 所有测试均使用Mockito进行Mock，无需真实数据库

---

## 📋 任务清单

### 短期任务（1-2周）✅ 优先级高

1. ✅ **运行OAuth2单元测试验证** - Mock测试，无需H2
2. 📝 **补充Token刷新测试** - 新增测试方法
3. 📝 **补充Token撤销测试** - 新增测试方法  
4. 📝 **补充PKCE完整流程测试** - 新增测试方法
5. 📝 **创建测试数据Builder工具类** - 提高可维护性

### 中期任务（1个月）⚡ 优先级中

6. 📝 **添加并发场景测试** - 使用CountDownLatch
7. 📝 **性能测试** - Token签发/授权码生成/Redis缓存
8. 📝 **完善测试文档** - 测试用例文档

---

## 🚫 重要说明：不使用H2数据库

### 为什么不用H2？

❌ **H2是内存数据库**，项目要求：
- 真实生产环境使用MySQL/PostgreSQL
- 测试应该Mock而不是使用内存数据库
- 避免H2与MySQL/PostgreSQL行为差异

### ✅ 正确的测试策略

**单元测试**: 使用**Mockito Mock所有依赖**  
**集成测试**: 使用**Testcontainers**启动真实MySQL/PostgreSQL容器  
**E2E测试**: 在CI/CD中使用真实数据库环境

---

## 📝 短期任务1：补充Token刷新测试

### 测试代码示例

```java
@Test
@DisplayName("Token刷新 - 成功")
void refreshToken_Success() {
    String clientId = "test-client";
    String clientSecret = "test-secret";
    String refreshToken = "valid-refresh-token";
    
    OAuth2AccessTokenEntity oldToken = new OAuth2AccessTokenEntity();
    oldToken.setClientId(clientId);
    oldToken.setUserId("user-123");
    oldToken.setScope("read write");
    oldToken.setRefreshToken(refreshToken);
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    when(passwordEncoder.matches(clientSecret, testClient.getClientSecret()))
            .thenReturn(true);
    when(mapCache.get(refreshToken))
            .thenReturn(oldToken);
    
    Mono<OAuth2AccessTokenEntity> result = authorizationService.refreshTokenGrant(
            clientId, clientSecret, refreshToken);
    
    StepVerifier.create(result)
            .assertNext(token -> {
                assertThat(token.getTokenValue()).isNotNull();
                assertThat(token.getTokenType()).isEqualTo("Bearer");
                assertThat(token.getClientId()).isEqualTo(clientId);
                assertThat(token.getUserId()).isEqualTo("user-123");
                assertThat(token.getScope()).isEqualTo("read write");
                assertThat(token.getRefreshToken()).isNotEqualTo(refreshToken); // 应该轮换刷新令牌
            })
            .verifyComplete();
    
    verify(mapCache).remove(refreshToken); // 验证旧的刷新令牌被移除
}

@Test
@DisplayName("Token刷新 - 刷新令牌无效")
void refreshToken_InvalidToken() {
    String clientId = "test-client";
    String clientSecret = "test-secret";
    String refreshToken = "invalid-refresh-token";
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    when(passwordEncoder.matches(clientSecret, testClient.getClientSecret()))
            .thenReturn(true);
    when(mapCache.get(refreshToken))
            .thenReturn(null);
    
    Mono<OAuth2AccessTokenEntity> result = authorizationService.refreshTokenGrant(
            clientId, clientSecret, refreshToken);
    
    StepVerifier.create(result)
            .expectError(BusinessException.class)
            .verify();
}

@Test
@DisplayName("Token刷新 - 刷新令牌已过期")
void refreshToken_Expired() {
    String clientId = "test-client";
    String clientSecret = "test-secret";
    String refreshToken = "expired-refresh-token";
    
    OAuth2AccessTokenEntity expiredToken = new OAuth2AccessTokenEntity();
    expiredToken.setClientId(clientId);
    expiredToken.setExpiresAt(Instant.now().minusSeconds(3600)); // 已过期
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    when(passwordEncoder.matches(clientSecret, testClient.getClientSecret()))
            .thenReturn(true);
    when(mapCache.get(refreshToken))
            .thenReturn(expiredToken);
    
    Mono<OAuth2AccessTokenEntity> result = authorizationService.refreshTokenGrant(
            clientId, clientSecret, refreshToken);
    
    StepVerifier.create(result)
            .expectError(BusinessException.class)
            .verify();
}
```

**添加位置**: `OAuth2AuthorizationServiceImplTest.java`

---

## 📝 短期任务2：补充Token撤销测试

### 测试代码示例

```java
@Test
@DisplayName("撤销Token - 成功")
void revokeToken_Success() {
    String clientId = "test-client";
    String clientSecret = "test-secret";
    String token = "access-token-to-revoke";
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    when(passwordEncoder.matches(clientSecret, testClient.getClientSecret()))
            .thenReturn(true);
    when(mapCache.remove(token))
            .thenReturn(Mono.empty().then());
    
    Mono<Void> result = authorizationService.revokeToken(clientId, clientSecret, token);
    
    StepVerifier.create(result)
            .verifyComplete();
    
    verify(mapCache).remove(token);
}

@Test
@DisplayName("撤销Token - 客户端认证失败")
void revokeToken_InvalidClient() {
    String clientId = "test-client";
    String wrongSecret = "wrong-secret";
    String token = "access-token";
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    when(passwordEncoder.matches(wrongSecret, testClient.getClientSecret()))
            .thenReturn(false);
    
    Mono<Void> result = authorizationService.revokeToken(clientId, wrongSecret, token);
    
    StepVerifier.create(result)
            .expectError(BusinessException.class)
            .verify();
    
    verify(mapCache, never()).remove(anyString());
}

@Test
@DisplayName("撤销Token - Token不存在（应该成功，符合RFC规范）")
void revokeToken_TokenNotExists() {
    String clientId = "test-client";
    String clientSecret = "test-secret";
    String token = "non-existent-token";
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    when(passwordEncoder.matches(clientSecret, testClient.getClientSecret()))
            .thenReturn(true);
    when(mapCache.remove(token))
            .thenReturn(Mono.empty().then());
    
    // RFC 7009规定：即使token不存在，也应该返回成功
    Mono<Void> result = authorizationService.revokeToken(clientId, clientSecret, token);
    
    StepVerifier.create(result)
            .verifyComplete();
}
```

**添加位置**: `OAuth2AuthorizationServiceImplTest.java`

---

## 📝 短期任务3：补充PKCE完整流程测试

### 测试代码示例

```java
@Test
@DisplayName("PKCE流程 - S256方法成功")
void pkceFlow_S256_Success() {
    String clientId = "test-client";
    String userId = "user-123";
    String redirectUri = "http://localhost:3000/callback";
    String scope = "read write";
    String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"; // 43-128字符
    
    // 1. 客户端计算code_challenge
    String codeChallenge = computeS256Challenge(codeVerifier);
    String codeChallengeMethod = "S256";
    
    // 2. 生成授权码（带PKCE）
    OAuth2RegisteredClientEntity pkceClient = createTestClient();
    pkceClient.setClientSettings("{\"requireProofKey\":true}");
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(pkceClient));
    
    StepVerifier.create(
        authorizationService.generateAuthorizationCode(
            clientId, userId, redirectUri, scope, "xyz", codeChallenge, codeChallengeMethod))
            .assertNext(authCode -> {
                assertThat(authCode.getCodeChallenge()).isEqualTo(codeChallenge);
                assertThat(authCode.getCodeChallengeMethod()).isEqualTo(codeChallengeMethod);
                
                // 3. 使用授权码换取Token（需要提供code_verifier）
                when(mapCache.get(authCode.getCode())).thenReturn(authCode);
                
                StepVerifier.create(
                    authorizationService.exchangeAuthorizationCode(
                        clientId, "", authCode.getCode(), redirectUri, codeVerifier))
                        .assertNext(token -> {
                            assertThat(token.getTokenValue()).isNotNull();
                            assertThat(token.getUserId()).isEqualTo(userId);
                        })
                        .verifyComplete();
            })
            .verifyComplete();
}

@Test
@DisplayName("PKCE流程 - code_verifier验证失败")
void pkceFlow_VerifierMismatch() {
    String clientId = "test-client";
    String code = "auth-code-123";
    String wrongVerifier = "wrong-verifier-value";
    
    OAuth2AuthorizationCodeEntity authCode = new OAuth2AuthorizationCodeEntity();
    authCode.setCode(code);
    authCode.setClientId(clientId);
    authCode.setCodeChallenge(computeS256Challenge("correct-verifier"));
    authCode.setCodeChallengeMethod("S256");
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    when(mapCache.get(code)).thenReturn(authCode);
    
    Mono<OAuth2AccessTokenEntity> result = authorizationService.exchangeAuthorizationCode(
            clientId, "", code, "http://localhost/callback", wrongVerifier);
    
    StepVerifier.create(result)
            .expectError(BusinessException.class)
            .verify();
}

@Test
@DisplayName("PKCE流程 - plain方法")
void pkceFlow_Plain_Success() {
    String clientId = "test-client";
    String codeVerifier = "plain-code-verifier-value";
    String codeChallenge = codeVerifier; // plain方法：challenge = verifier
    
    OAuth2AuthorizationCodeEntity authCode = new OAuth2AuthorizationCodeEntity();
    authCode.setCode("code-123");
    authCode.setClientId(clientId);
    authCode.setUserId("user-123");
    authCode.setRedirectUri("http://localhost/callback");
    authCode.setCodeChallenge(codeChallenge);
    authCode.setCodeChallengeMethod("plain");
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    when(mapCache.get("code-123")).thenReturn(authCode);
    
    Mono<OAuth2AccessTokenEntity> result = authorizationService.exchangeAuthorizationCode(
            clientId, "", "code-123", "http://localhost/callback", codeVerifier);
    
    StepVerifier.create(result)
            .assertNext(token -> {
                assertThat(token.getTokenValue()).isNotNull();
                assertThat(token.getUserId()).isEqualTo("user-123");
            })
            .verifyComplete();
}

// 辅助方法
private String computeS256Challenge(String verifier) {
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
    }
}
```

**添加位置**: `OAuth2AuthorizationServiceImplTest.java`

**需要导入**:
```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
```

---

## 📝 短期任务4：创建测试数据Builder工具类

### OAuth2TestDataBuilder.java

```java
package com.apex.scaffold.application.service.test;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.model.entity.OAuth2AuthorizationConsentEntity;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * OAuth2测试数据构建器
 *
 * <p>提供便捷的测试数据创建方法，支持链式调用和默认值。
 *
 * @author Apex Team
 * @version 1.0
 */
public class OAuth2TestDataBuilder {
    
    /**
     * 创建测试客户端Builder
     */
    public static ClientBuilder client() {
        return new ClientBuilder();
    }
    
    /**
     * 创建测试用户Builder
     */
    public static UserBuilder user() {
        return new UserBuilder();
    }
    
    /**
     * 创建授权同意Builder
     */
    public static ConsentBuilder consent() {
        return new ConsentBuilder();
    }
    
    /**
     * 客户端Builder
     */
    public static class ClientBuilder {
        private String id = UUID.randomUUID().toString();
        private String clientId = "test-client";
        private String clientSecret = "$2a$10$encrypted-secret";
        private String clientName = "Test Client";
        private Set<String> authMethods = Set.of("client_secret_basic");
        private Set<String> grantTypes = Set.of("authorization_code", "refresh_token");
        private Set<String> redirectUris = Set.of("http://localhost:3000/callback");
        private Set<String> scopes = Set.of("read", "write");
        private boolean requireProofKey = false;
        private boolean requireConsent = true;
        
        public ClientBuilder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }
        
        public ClientBuilder withPKCE() {
            this.requireProofKey = true;
            return this;
        }
        
        public ClientBuilder withoutConsent() {
            this.requireConsent = false;
            return this;
        }
        
        public ClientBuilder grantTypes(String... types) {
            this.grantTypes = Set.of(types);
            return this;
        }
        
        public ClientBuilder scopes(String... scopes) {
            this.scopes = Set.of(scopes);
            return this;
        }
        
        public OAuth2RegisteredClientEntity build() {
            OAuth2RegisteredClientEntity client = new OAuth2RegisteredClientEntity();
            client.setId(id);
            client.setClientId(clientId);
            client.setClientSecret(clientSecret);
            client.setClientName(clientName);
            client.setClientAuthenticationMethods(authMethods);
            client.setAuthorizationGrantTypes(grantTypes);
            client.setRedirectUris(redirectUris);
            client.setScopes(scopes);
            
            String clientSettings = String.format(
                "{\"requireProofKey\":%s,\"requireAuthorizationConsent\":%s}",
                requireProofKey, requireConsent);
            client.setClientSettings(clientSettings);
            
            client.setTokenSettings("{\"accessTokenTimeToLive\":7200,\"refreshTokenTimeToLive\":604800}");
            
            return client;
        }
    }
    
    /**
     * 用户Builder
     */
    public static class UserBuilder {
        private Long id = 1L;
        private String openId = "user-openid-" + UUID.randomUUID().toString().substring(0, 8);
        private String username = "testuser";
        private String password = "$2a$10$encrypted-password";
        private Integer status = 1;
        
        public UserBuilder id(Long id) {
            this.id = id;
            return this;
        }
        
        public UserBuilder openId(String openId) {
            this.openId = openId;
            return this;
        }
        
        public UserBuilder username(String username) {
            this.username = username;
            return this;
        }
        
        public UserBuilder disabled() {
            this.status = 0;
            return this;
        }
        
        public UserEntity build() {
            UserEntity user = new UserEntity();
            try {
                setField(BaseEntity.class, user, "id", id);
                setField(UserEntity.class, user, "openId", openId);
                setField(UserEntity.class, user, "username", username);
                setField(UserEntity.class, user, "password", password);
                setField(UserEntity.class, user, "status", status);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create test user", e);
            }
            return user;
        }
    }
    
    /**
     * 授权同意Builder
     */
    public static class ConsentBuilder {
        private String clientId = "test-client";
        private String userId = "user-123";
        private Set<String> scopes = new HashSet<>(Set.of("read", "write"));
        
        public ConsentBuilder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }
        
        public ConsentBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }
        
        public ConsentBuilder scopes(String... scopes) {
            this.scopes = new HashSet<>(Set.of(scopes));
            return this;
        }
        
        public ConsentBuilder addScope(String scope) {
            this.scopes.add(scope);
            return this;
        }
        
        public OAuth2AuthorizationConsentEntity build() {
            OAuth2AuthorizationConsentEntity consent = new OAuth2AuthorizationConsentEntity();
            consent.setClientId(clientId);
            consent.setUserId(userId);
            consent.setAuthorizedScopes(scopes);
            consent.setGrantedAt(Instant.now());
            consent.setLastModifiedAt(Instant.now());
            return consent;
        }
    }
    
    // 辅助方法：使用反射设置私有字段
    private static void setField(Class<?> clazz, Object obj, String fieldName, Object value) 
            throws NoSuchFieldException, IllegalAccessException {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
```

**使用示例**:
```java
// 在测试中使用Builder
OAuth2RegisteredClientEntity client = OAuth2TestDataBuilder.client()
        .clientId("my-app")
        .withPKCE()
        .grantTypes("authorization_code", "refresh_token")
        .build();

UserEntity user = OAuth2TestDataBuilder.user()
        .username("john")
        .openId("john-openid")
        .build();

OAuth2AuthorizationConsentEntity consent = OAuth2TestDataBuilder.consent()
        .clientId("my-app")
        .userId("john-openid")
        .scopes("read", "write", "admin")
        .build();
```

**添加位置**: `apex-application/src/test/java/com/apex/scaffold/application/service/test/OAuth2TestDataBuilder.java`

---

## 📝 中期任务1：添加并发场景测试

### 并发测试示例

```java
@Test
@DisplayName("并发Token签发 - 多线程同时请求")
void concurrentTokenIssuance() throws InterruptedException {
    int threadCount = 10;
    CountDownLatch latch = new CountDownLatch(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);
    
    String clientId = "test-client";
    String clientSecret = "test-secret";
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    when(passwordEncoder.matches(clientSecret, testClient.getClientSecret()))
            .thenReturn(true);
    
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    
    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                startLatch.await(); // 等待统一启动信号
                
                authorizationService.clientCredentialsGrant(clientId, clientSecret, "read write")
                        .doOnSuccess(token -> successCount.incrementAndGet())
                        .doOnError(e -> failureCount.incrementAndGet())
                        .block();
                        
            } catch (Exception e) {
                failureCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
    }
    
    startLatch.countDown(); // 发送启动信号
    boolean finished = latch.await(10, TimeUnit.SECONDS);
    executor.shutdown();
    
    assertThat(finished).isTrue();
    assertThat(successCount.get()).isEqualTo(threadCount);
    assertThat(failureCount.get()).isZero();
}

@Test
@DisplayName("并发授权码生成 - 同一用户多次授权")
void concurrentAuthorizationCodeGeneration() throws InterruptedException {
    int requestCount = 5;
    CountDownLatch latch = new CountDownLatch(requestCount);
    Set<String> generatedCodes = ConcurrentHashMap.newKeySet();
    
    String clientId = "test-client";
    String userId = "user-123";
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    
    ExecutorService executor = Executors.newFixedThreadPool(requestCount);
    
    for (int i = 0; i < requestCount; i++) {
        executor.submit(() -> {
            try {
                authorizationService.generateAuthorizationCode(
                    clientId, userId, "http://localhost/callback", "read", "state", null, null)
                        .doOnSuccess(authCode -> generatedCodes.add(authCode.getCode()))
                        .block();
            } finally {
                latch.countDown();
            }
        });
    }
    
    latch.await(10, TimeUnit.SECONDS);
    executor.shutdown();
    
    // 验证生成的授权码都是唯一的
    assertThat(generatedCodes).hasSize(requestCount);
}
```

**需要导入**:
```java
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
```

**添加位置**: `OAuth2AuthorizationServiceImplTest.java`

---

## 📝 中期任务2：性能测试

### 性能测试示例

```java
@Test
@DisplayName("性能测试 - Token签发吞吐量")
void performanceTest_TokenIssuance() {
    int iterations = 1000;
    String clientId = "test-client";
    String clientSecret = "test-secret";
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    when(passwordEncoder.matches(clientSecret, testClient.getClientSecret()))
            .thenReturn(true);
    
    long startTime = System.currentTimeMillis();
    
    for (int i = 0; i < iterations; i++) {
        authorizationService.clientCredentialsGrant(clientId, clientSecret, "read")
                .block();
    }
    
    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;
    double throughput = (iterations * 1000.0) / duration;
    
    System.out.printf("Token签发性能: %d次/%dms = %.2f次/秒%n", 
            iterations, duration, throughput);
    
    // 性能断言：应该能达到 > 100 次/秒
    assertThat(throughput).isGreaterThan(100.0);
}

@Test
@DisplayName("性能测试 - 授权码生成响应时间")
void performanceTest_AuthorizationCodeLatency() {
    String clientId = "test-client";
    String userId = "user-123";
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    
    List<Long> latencies = new ArrayList<>();
    
    for (int i = 0; i < 100; i++) {
        long start = System.nanoTime();
        
        authorizationService.generateAuthorizationCode(
            clientId, userId, "http://localhost/callback", "read", "state", null, null)
                .block();
                
        long end = System.nanoTime();
        latencies.add((end - start) / 1_000_000); // 转换为毫秒
    }
    
    double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
    long p95Latency = latencies.stream().sorted().skip(95).findFirst().orElse(0L);
    long p99Latency = latencies.stream().sorted().skip(99).findFirst().orElse(0L);
    
    System.out.printf("授权码生成延迟: 平均=%.2fms, P95=%dms, P99=%dms%n",
            avgLatency, p95Latency, p99Latency);
    
    // 性能断言
    assertThat(avgLatency).isLessThan(50.0); // 平均延迟 < 50ms
    assertThat(p95Latency).isLessThan(100L); // P95延迟 < 100ms
}
```

**添加位置**: 新建测试类 `OAuth2PerformanceTest.java`

---

## 📝 短期任务5：完善测试文档

### 创建测试用例文档

**文件**: `.trae/documents/oauth2_test_cases.md`

```markdown
# OAuth2单元测试用例文档

## 测试覆盖率

- **OAuth2AuthorizationServiceImpl**: 20个测试
- **OAuth2ClientManagementService**: 11个测试  
- **OAuth2AuthorizationConsentRepository**: 5个测试

**总计**: 36个测试方法

## 测试用例清单

### 1. 授权码模式测试
- TC001: 生成授权码 - 成功
- TC002: 生成授权码 - 客户端不存在
- TC003: 生成授权码 - 不支持授权码模式
- TC004: 生成授权码 - 无效的重定向URI

### 2. Token签发测试
- TC005: 客户端凭证模式 - 成功
- TC006: 客户端凭证模式 - 密钥错误
- TC007: 密码模式 - 成功
- TC008: 密码模式 - 凭证错误
- TC009: 密码模式 - 用户禁用

### 3. Token刷新测试
- TC010: Token刷新 - 成功
- TC011: Token刷新 - 刷新令牌无效
- TC012: Token刷新 - 刷新令牌过期

### 4. Token撤销测试
- TC013: 撤销Token - 成功
- TC014: 撤销Token - 客户端认证失败
- TC015: 撤销Token - Token不存在

### 5. PKCE测试
- TC016: PKCE流程 - S256方法成功
- TC017: PKCE流程 - code_verifier验证失败
- TC018: PKCE流程 - plain方法

### 6. 授权同意测试
- TC019: 检查授权同意 - 已授权
- TC020: 检查授权同意 - 未授权
- TC021: 检查授权同意 - 部分授权
- TC022: 保存授权同意 - 成功
- TC023: 撤销授权同意 - 成功

### 7. 并发测试
- TC024: 并发Token签发
- TC025: 并发授权码生成

### 8. 性能测试
- TC026: Token签发吞吐量
- TC027: 授权码生成响应时间
```

---

## ✅ 实施步骤

### Step 1: 补充测试方法（高优先级）

1. 打开 `OAuth2AuthorizationServiceImplTest.java`
2. 复制上述Token刷新/撤销/PKCE测试代码
3. 添加到现有测试类中
4. 添加必要的import语句
5. 运行测试验证

### Step 2: 创建测试Builder工具类

1. 创建 `OAuth2TestDataBuilder.java`
2. 复制上述Builder代码
3. 重构现有测试使用Builder

### Step 3: 添加并发和性能测试

1. 在现有测试类中添加并发测试
2. 创建新的性能测试类
3. 运行并验证性能指标

### Step 4: 完善文档

1. 创建测试用例文档
2. 更新README说明测试覆盖率
3. 添加测试运行指南

---

## 🚀 运行测试命令

### 运行OAuth2所有测试

```bash
cd /Users/work/apex-scaffold
mvnd test -Dtest="OAuth2*Test"
```

### 运行单个测试类

```bash
mvnd test -Dtest="OAuth2AuthorizationServiceImplTest"
```

### 运行特定测试方法

```bash
mvnd test -Dtest="OAuth2AuthorizationServiceImplTest#refreshToken_Success"
```

### 生成测试覆盖率报告

```bash
mvnd clean test jacoco:report
open apex-application/target/site/jacoco/index.html
```

---

## 📊 预期测试覆盖率

| 模块 | 目标覆盖率 | 当前覆盖率 |
|------|-----------|-----------|
| OAuth2AuthorizationService | 85% | 75% |
| OAuth2ClientManagementService | 90% | 90% |
| OAuth2Repositories | 85% | 85% |
| **总体目标** | **85%+** | **~80%** |

---

## ✨ 总结

### 已提供的内容

✅ **Token刷新测试** - 完整代码示例（3个测试）  
✅ **Token撤销测试** - 完整代码示例（3个测试）  
✅ **PKCE完整流程测试** - 完整代码示例（3个测试）  
✅ **测试数据Builder** - 完整工具类代码  
✅ **并发场景测试** - 完整代码示例（2个测试）  
✅ **性能测试** - 完整代码示例（2个测试）  
✅ **测试文档模板** - 测试用例文档示例  

### 实施时间估算

- **短期任务**: 2-3天（Token刷新/撤销/PKCE + Builder）
- **中期任务**: 3-5天（并发测试 + 性能测试 + 文档）
- **总计**: 约1周完成所有任务

### 关键原则

🚫 **不使用H2** - 所有测试使用Mockito Mock  
✅ **使用mvnd** - 构建速度提升3倍  
📊 **目标85%+** - 测试覆盖率目标  
🔒 **Mock所有依赖** - 真正的单元测试  

---

**文档版本**: v1.0  
**创建时间**: 2026-09-08  
**维护者**: Apex Team
