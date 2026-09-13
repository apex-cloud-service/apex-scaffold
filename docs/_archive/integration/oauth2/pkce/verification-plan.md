# PKCE 功能验证计划

## 一、实际实现分析

### 1.1 核心实现确认

经过代码检查，确认以下 PKCE 实现已完成：

#### OAuth2AuthorizationCodeEntity
- ✅ `codeChallenge` 字段
- ✅ `codeChallengeMethod` 字段
- ✅ 完整的过期检查逻辑

#### generateAuthorizationCode 方法
```java
public Mono<OAuth2AuthorizationCodeEntity> generateAuthorizationCode(
        String clientId,
        String userId,
        String redirectUri,
        String scope,
        String state,
        String codeChallenge,          // ✅ 支持
        String codeChallengeMethod)    // ✅ 支持
```
- ✅ 接收 `codeChallenge` 和 `codeChallengeMethod` 参数
- ✅ 保存到 `OAuth2AuthorizationCodeEntity` 实体
- ✅ 存储到 Redis 缓存（5分钟有效期）

#### exchangeCodeForToken 方法
```java
public Mono<OAuth2AccessTokenEntity> exchangeCodeForToken(
        String code,
        String clientId,
        String clientSecret,
        String redirectUri,
        String codeVerifier)           // ✅ 支持
```
- ✅ 接收 `codeVerifier` 参数
- ✅ 检查授权码是否包含 `codeChallenge`
- ✅ 如果存在 challenge，则调用 `verifyPKCE` 验证

#### verifyPKCE 方法
```java
private boolean verifyPKCE(String codeVerifier, String codeChallenge, String method) {
    if (codeVerifier == null || codeChallenge == null) {
        return false;  // ✅ 参数校验
    }
    
    try {
        String computed;
        if ("S256".equals(method)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } else {
            computed = codeVerifier;  // ✅ plain 模式
        }
        
        return computed.equals(codeChallenge);
    } catch (Exception e) {
        log.error("PKCE验证失败", e);
        return false;
    }
}
```
- ✅ 支持 `S256` 模式（SHA-256 + Base64Url）
- ✅ 支持 `plain` 模式（直接比较）
- ✅ 参数为空返回 `false`
- ✅ 异常情况返回 `false`

### 1.2 PKCE 验证逻辑

根据 `exchangeCodeForToken` 第 137-141 行：
```java
if (authCode.getCodeChallenge() != null) {
    if (!verifyPKCE(codeVerifier, authCode.getCodeChallenge(), authCode.getCodeChallengeMethod())) {
        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "PKCE验证失败"));
    }
}
```

**关键行为：**
- ✅ 只有当授权码包含 `codeChallenge` 时才验证
- ✅ 如果授权码不包含 `codeChallenge`，则跳过 PKCE 验证
- ✅ PKCE 是可选的，不是强制的

## 二、测试矩阵

### 2.1 S256 模式测试场景

| 场景 | 测试方法 | 预期结果 | 优先级 |
|------|---------|---------|--------|
| S256 正常流程 | `pkceFlow_S256_Success` | 成功换取 Token | 高 |
| verifier 错误 | `pkceFlow_VerifierMismatch` | 抛出 "PKCE验证失败" | 高 |
| verifier 为空 | `pkceFlow_VerifierNull` | 抛出 "PKCE验证失败" | 中 |

### 2.2 plain 模式测试场景

| 场景 | 测试方法 | 预期结果 | 优先级 |
|------|---------|---------|--------|
| plain 正常流程 | `pkceFlow_Plain_Success` | 成功换取 Token | 高 |
| plain verifier 错误 | `pkceFlow_Plain_Mismatch` | 抛出 "PKCE验证失败" | 中 |

### 2.3 无 PKCE 场景

| 场景 | 测试方法 | 预期结果 | 优先级 |
|------|---------|---------|--------|
| 无 challenge 无 verifier | 现有测试已覆盖 | 成功换取 Token | - |

## 三、现有测试代码状态

### 3.1 已实现但被禁用的测试

```java
@Test
@Disabled("PKCE功能待实现")
@DisplayName("PKCE流程 - S256方法成功")
void pkceFlow_S256_Success()

@Test
@Disabled("PKCE功能待实现")
@DisplayName("PKCE流程 - code_verifier验证失败")
void pkceFlow_VerifierMismatch()

@Test
@Disabled("PKCE功能待实现")
@DisplayName("PKCE流程 - plain方法")
void pkceFlow_Plain_Success()
```

### 3.2 测试代码问题分析

当前测试代码存在以下问题需要修正：

1. **Mock 缺失**：测试中没有 Mock `generateAuthorizationCode` 方法生成的授权码
2. **授权码实体构造**：需要手动构造包含 `codeChallenge` 和 `codeChallengeMethod` 的授权码实体
3. **Redis Mock**：需要 Mock `mapCache.get(code)` 返回构造的授权码实体

## 四、测试修正方案

### 4.1 S256 成功测试修正

```java
@Test
@DisplayName("PKCE流程 - S256方法成功")
void pkceFlow_S256_Success() {
    // 生成 code_verifier 和 code_challenge
    String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    String codeChallenge = computeS256Challenge(codeVerifier);
    String code = "test-auth-code-with-pkce";
    String clientId = "test-client";
    
    // 构造包含 PKCE 的授权码实体
    OAuth2AuthorizationCodeEntity authCode = new OAuth2AuthorizationCodeEntity();
    authCode.setCode(code);
    authCode.setClientId(clientId);
    authCode.setUserId("user-123");
    authCode.setRedirectUri("http://localhost:3000/callback");
    authCode.setScope("read write");
    authCode.setCodeChallenge(codeChallenge);
    authCode.setCodeChallengeMethod("S256");
    authCode.setIssuedAt(java.time.Instant.now());
    authCode.setExpiresAt(java.time.Instant.now().plusSeconds(300));
    
    // Mock
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    when(passwordEncoder.matches(anyString(), anyString()))
            .thenReturn(true);
    when(mapCache.get(code))
            .thenReturn(authCode);
    
    // 调用实际方法，传入正确的 codeVerifier
    Mono<OAuth2AccessTokenEntity> result =
            authorizationService.exchangeCodeForToken(
                    code,
                    clientId,
                    "test-secret",
                    "http://localhost:3000/callback",
                    codeVerifier);  // ✅ 正确的 verifier
    
    // 验证
    StepVerifier.create(result)
            .assertNext(token -> {
                assertThat(token.getTokenValue()).isNotNull();
                assertThat(token.getTokenType()).isEqualTo("Bearer");
                assertThat(token.getClientId()).isEqualTo(clientId);
            })
            .verifyComplete();
    
    // 验证授权码已被删除
    verify(mapCache).remove(code);
}
```

### 4.2 S256 验证失败测试修正

```java
@Test
@DisplayName("PKCE流程 - code_verifier验证失败")
void pkceFlow_VerifierMismatch() {
    String codeVerifier = "correct-verifier";
    String codeChallenge = computeS256Challenge(codeVerifier);
    String wrongVerifier = "wrong-verifier";
    String code = "test-auth-code-pkce-fail";
    String clientId = "test-client";
    
    OAuth2AuthorizationCodeEntity authCode = new OAuth2AuthorizationCodeEntity();
    authCode.setCode(code);
    authCode.setClientId(clientId);
    authCode.setUserId("user-123");
    authCode.setRedirectUri("http://localhost:3000/callback");
    authCode.setScope("read write");
    authCode.setCodeChallenge(codeChallenge);
    authCode.setCodeChallengeMethod("S256");
    authCode.setIssuedAt(java.time.Instant.now());
    authCode.setExpiresAt(java.time.Instant.now().plusSeconds(300));
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    when(passwordEncoder.matches(anyString(), anyString()))
            .thenReturn(true);
    when(mapCache.get(code))
            .thenReturn(authCode);
    
    // 传入错误的 verifier
    Mono<OAuth2AccessTokenEntity> result =
            authorizationService.exchangeCodeForToken(
                    code,
                    clientId,
                    "test-secret",
                    "http://localhost:3000/callback",
                    wrongVerifier);  // ❌ 错误的 verifier
    
    // 验证抛出异常
    StepVerifier.create(result)
            .expectErrorMatches(ex ->
                    ex instanceof BusinessException &&
                    ex.getMessage().contains("PKCE验证失败"))
            .verify();
}
```

### 4.3 plain 模式测试修正

```java
@Test
@DisplayName("PKCE流程 - plain方法")
void pkceFlow_Plain_Success() {
    String codeVerifier = "plain-code-verifier-value";
    String codeChallenge = codeVerifier;  // plain 模式下 challenge = verifier
    String code = "test-auth-code-plain";
    String clientId = "test-client";
    
    OAuth2AuthorizationCodeEntity authCode = new OAuth2AuthorizationCodeEntity();
    authCode.setCode(code);
    authCode.setClientId(clientId);
    authCode.setUserId("user-123");
    authCode.setRedirectUri("http://localhost:3000/callback");
    authCode.setScope("read write");
    authCode.setCodeChallenge(codeChallenge);
    authCode.setCodeChallengeMethod("plain");  // ✅ plain 模式
    authCode.setIssuedAt(java.time.Instant.now());
    authCode.setExpiresAt(java.time.Instant.now().plusSeconds(300));
    
    when(clientRepository.findByClientId(clientId))
            .thenReturn(Mono.just(testClient));
    when(passwordEncoder.matches(anyString(), anyString()))
            .thenReturn(true);
    when(mapCache.get(code))
            .thenReturn(authCode);
    
    Mono<OAuth2AccessTokenEntity> result =
            authorizationService.exchangeCodeForToken(
                    code,
                    clientId,
                    "test-secret",
                    "http://localhost:3000/callback",
                    codeVerifier);
    
    StepVerifier.create(result)
            .assertNext(token -> {
                assertThat(token.getTokenValue()).isNotNull();
                assertThat(token.getTokenType()).isEqualTo("Bearer");
            })
            .verifyComplete();
    
    verify(mapCache).remove(code);
}
```

## 五、执行计划

### 5.1 修正步骤

1. ✅ 检查实际 PKCE 实现代码
2. ⏳ 修正现有三个 PKCE 测试方法
3. ⏳ 移除 `@Disabled` 注解
4. ⏳ 使用 `mvnd test-compile` 编译验证
5. ⏳ 使用 `mvnd test -Dtest="OAuth2*"` 运行测试
6. ⏳ 确认所有测试通过（预期 24 通过，0 跳过）

### 5.2 技术约束

- ✅ 必须使用 `mvnd`，不能使用 `mvn`
- ✅ 不允许使用 H2，全部使用 Mockito Mock
- ✅ 只能使用 WebFlux，不能引入 Spring MVC
- ✅ 使用 Reactor Test `StepVerifier`
- ✅ 测试中设置 `jwt.secret` 系统属性

### 5.3 验收标准

1. 所有 PKCE 测试不再被 `@Disabled`
2. `mvnd test-compile` 成功
3. `mvnd test -Dtest="OAuth2AuthorizationServiceImplTest"` 结果：
   - Tests run: 24
   - Failures: 0
   - Errors: 0
   - Skipped: 0
4. 测试覆盖 PKCE 的主要场景：
   - S256 成功
   - S256 验证失败
   - plain 成功

## 六、风险与注意事项

### 6.1 已识别风险

1. **授权码删除验证**：需要确认 `mapCache.remove(code)` 被调用
2. **异常消息匹配**：`BusinessException` 消息需要精确匹配 "PKCE验证失败"
3. **时间敏感**：授权码 `expiresAt` 需要设置为未来时间

### 6.2 后续优化建议

1. 补充测试场景：
   - `codeVerifier` 为 `null` 时的行为
   - 不支持的 `codeChallengeMethod`（如 `MD5`）
   - 授权码过期后的 PKCE 验证
2. 集成测试：
   - 完整流程测试（生成授权码 → 换取 Token）
3. 文档补充：
   - API 文档中说明 PKCE 参数使用方法
   - 客户端集成示例

## 七、参考资料

- RFC 7636: Proof Key for Code Exchange by OAuth Public Clients
- 实现文件：`OAuth2AuthorizationServiceImpl.java`
- 实体文件：`OAuth2AuthorizationCodeEntity.java`
- 测试文件：`OAuth2AuthorizationServiceImplTest.java`
