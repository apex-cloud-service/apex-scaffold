# 认证授权安全修复实施计划

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

> **项目**: Apex Scaffold  
> **创建日期**: 2026-09-25  
> **负责人**: Apex Security Team  
> **基于**: [认证授权安全评审报告](./auth-security-review-report.md)

---

## 📋 执行摘要

本文档是《认证授权安全评审报告》的配套实施计划，详细规划了20个已识别安全问题的修复顺序、技术方案和验证方法。

### 修复优先级分布

| 优先级 | 问题数量 | 必须上线前完成 | 工期 |
|-------|---------|--------------|------|
| 🔴 P0 (严重) | 5个 | ✅ 是 | 10天 |
| 🟠 P1 (高危) | 4个 | ✅ 是 | 8天 |
| 🟡 P2 (中危) | 5个 | ⚠️ 建议 | 7天 |
| 🟢 P3 (低危) | 6个 | ❌ 否 | 4天 |
| **总计** | **20个** | - | **29天** |

### 上线阻断问题（必须修复）

共9个P0-P1问题，预计工期18天：

1. **问题1**: Token明文存储 (P0, 2天)
2. **问题3**: Refresh Token轮换机制 (P0, 3天)
3. **问题6**: JWT密钥管理无轮换 (P0, 2天)
4. **问题12**: 审计日志不完整 (P0, 2天)
5. **问题14**: 敏感配置硬编码 (P0, 1天)
6. **问题17**: SAML重放攻击防护 (P0, 2天)
7. **问题18**: OIDC State验证 (P0, 1天)
8. **问题15**: SSO配置明文存储 (P1, 2天)
9. **问题4**: 客户端密钥轮换 (P1, 3天)

---

## 🎯 第一阶段：P0严重问题修复 (预计10天)

### 修复任务1: Token加密存储

**优先级**: 🔴 P0  
**工期**: 2天  
**负责模块**: apex-auth-center, apex-infrastructure  
**相关文件**:
- `TokenServiceImpl.java`
- `V25__oauth2_authorization_server_schema.sql`
- 新增: `TokenEncryptionService.java`

#### 实施步骤

**Day 1: 创建加密服务和数据库迁移**

```java
// 文件: apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/encryption/TokenEncryptionService.java
package com.apex.scaffold.infrastructure.encryption;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Token加密服务
 * 使用AES-256-GCM加密Token
 */
@Service
@RequiredArgsConstructor
public class TokenEncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    
    private final SecretKey encryptionKey;
    
    /**
     * 计算Token的SHA-256哈希
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Token哈希计算失败", e);
        }
    }
    
    /**
     * 加密Token
     * @return EncryptedToken对象，包含加密数据和IV
     */
    public EncryptedToken encrypt(String token) {
        try {
            byte[] iv = generateIV();
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, gcmSpec);
            
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            
            return new EncryptedToken(
                Base64.getEncoder().encodeToString(encrypted),
                Base64.getEncoder().encodeToString(iv)
            );
        } catch (Exception e) {
            throw new RuntimeException("Token加密失败", e);
        }
    }
    
    /**
     * 解密Token
     */
    public String decrypt(String encryptedData, String ivBase64) {
        try {
            byte[] encrypted = Base64.getDecoder().decode(encryptedData);
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, gcmSpec);
            
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Token解密失败", e);
        }
    }
    
    private byte[] generateIV() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
    
    public record EncryptedToken(String encryptedData, String iv) {}
}
```

**数据库迁移脚本**:

```sql
-- 文件: V42__add_token_encryption.sql

-- 1. 为oauth2_authorization表添加加密字段
ALTER TABLE oauth2_authorization 
ADD COLUMN access_token_hash VARCHAR(64) COMMENT 'Token SHA256哈希值',
ADD COLUMN access_token_encrypted TEXT COMMENT 'AES-256-GCM加密的Token',
ADD COLUMN access_token_iv VARCHAR(32) COMMENT '加密向量',
ADD COLUMN refresh_token_hash VARCHAR(64) COMMENT 'Refresh Token哈希',
ADD COLUMN refresh_token_encrypted TEXT COMMENT '加密的Refresh Token',
ADD COLUMN refresh_token_iv VARCHAR(32) COMMENT 'Refresh Token IV',
ADD COLUMN revoked_at DATETIME NULL COMMENT 'Token撤销时间',
ADD INDEX idx_access_token_hash (access_token_hash),
ADD INDEX idx_refresh_token_hash (refresh_token_hash);

-- 2. 备份现有数据（如果有）
CREATE TABLE IF NOT EXISTS oauth2_authorization_backup AS 
SELECT * FROM oauth2_authorization WHERE id IS NOT NULL;

-- 3. 迁移现有明文Token（如果存在）
-- 注意：这一步需要在应用层执行，因为加密需要密钥
-- UPDATE oauth2_authorization SET 
--   access_token_hash = SHA2(access_token_value, 256),
--   access_token_encrypted = ..., -- 需要应用层加密
--   refresh_token_hash = SHA2(refresh_token_value, 256);

-- 4. 逐步废弃明文字段（先标记为deprecated，一段时间后删除）
-- ALTER TABLE oauth2_authorization 
-- DROP COLUMN access_token_value,
-- DROP COLUMN refresh_token_value;
```

**Day 2: 修改TokenService实现**

```java
// 修改: TokenServiceImpl.java
@Override
public Mono<TokenPair> issueToken(Long userId, String tenantId, String deviceId, String clientIp) {
    String jti = UUID.randomUUID().toString();
    Instant now = Instant.now();
    Instant accessExpiry = now.plus(accessTokenTtl);
    Instant refreshExpiry = now.plus(refreshTokenTtl);
    
    return userRepository.findById(userId)
        .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "用户不存在")))
        .flatMap(user -> {
            // 生成JWT Token
            String accessToken = jwtService.generateAccessToken(user, jti, accessExpiry);
            String refreshToken = jwtService.generateRefreshToken(user, UUID.randomUUID().toString(), refreshExpiry);
            
            // 加密Token
            TokenEncryptionService.EncryptedToken encryptedAccess = tokenEncryptionService.encrypt(accessToken);
            TokenEncryptionService.EncryptedToken encryptedRefresh = tokenEncryptionService.encrypt(refreshToken);
            
            // 计算哈希
            String accessTokenHash = tokenEncryptionService.hashToken(accessToken);
            String refreshTokenHash = tokenEncryptionService.hashToken(refreshToken);
            
            // 保存到数据库（只存储哈希和加密数据）
            TokenEntity tokenEntity = TokenEntity.builder()
                .jti(jti)
                .userId(userId)
                .tenantId(tenantId)
                .deviceId(deviceId)
                .clientIp(clientIp)
                .accessTokenHash(accessTokenHash)
                .accessTokenEncrypted(encryptedAccess.encryptedData())
                .accessTokenIv(encryptedAccess.iv())
                .refreshTokenHash(refreshTokenHash)
                .refreshTokenEncrypted(encryptedRefresh.encryptedData())
                .refreshTokenIv(encryptedRefresh.iv())
                .issuedAt(now)
                .accessExpiresAt(accessExpiry)
                .refreshExpiresAt(refreshExpiry)
                .status(TokenStatus.ACTIVE)
                .build();
            
            return tokenRepository.save(tokenEntity)
                .thenReturn(new TokenPair(accessToken, refreshToken, accessTokenTtl.getSeconds()));
        });
}

@Override
public Mono<TokenEntity> validateToken(String token) {
    String tokenHash = tokenEncryptionService.hashToken(token);
    
    return tokenRepository.findByAccessTokenHash(tokenHash)
        .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token无效")))
        .flatMap(entity -> {
            // 验证Token状态
            if (entity.getStatus() != TokenStatus.ACTIVE) {
                return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token已失效"));
            }
            
            // 验证过期时间
            if (entity.getAccessExpiresAt().isBefore(Instant.now())) {
                return revokeToken(entity.getJti())
                    .then(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token已过期")));
            }
            
            // 验证JWT签名（从加密数据解密后验证）
            String decryptedToken = tokenEncryptionService.decrypt(
                entity.getAccessTokenEncrypted(), 
                entity.getAccessTokenIv()
            );
            
            return jwtService.verifyToken(decryptedToken)
                .thenReturn(entity);
        });
}
```

#### 验证方法

```bash
# 1. 运行单元测试
./mvnw test -Dtest=TokenEncryptionServiceTest
./mvnw test -Dtest=TokenServiceImplTest

# 2. 集成测试
./mvnw verify -Pit

# 3. 手动验证
# 3.1 登录获取Token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}'

# 3.2 验证数据库中Token已加密
mysql -e "SELECT id, access_token_hash, LENGTH(access_token_encrypted), access_token_iv FROM oauth2_authorization LIMIT 1;"

# 3.3 使用Token访问受保护资源
curl -X GET http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer <access_token>"
```

---

### 修复任务2: Refresh Token轮换机制

**优先级**: 🔴 P0  
**工期**: 3天  
**负责模块**: apex-auth-center  
**相关文件**: `TokenServiceImpl.java`, `RefreshTokenRotationService.java` (新增)

#### 实施步骤

**Day 1-2: 实现Token轮换和异常检测**

```java
// 新增文件: RefreshTokenRotationService.java
package com.apex.scaffold.authcenter.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Refresh Token轮换服务
 * 实现Token Family机制，防止Refresh Token泄露
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenRotationService {
    
    private final TokenRepository tokenRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final SecurityAlertService alertService;
    
    private static final String TOKEN_FAMILY_KEY = "token:family:";
    private static final String REVOKED_FAMILY_KEY = "token:revoked:family:";
    
    /**
     * 刷新Token并实现轮换
     * 
     * @param oldRefreshToken 旧的Refresh Token
     * @return 新的Token对
     */
    public Mono<TokenPair> rotateRefreshToken(String oldRefreshToken) {
        String oldTokenHash = tokenEncryptionService.hashToken(oldRefreshToken);
        
        return tokenRepository.findByRefreshTokenHash(oldTokenHash)
            .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "无效的Refresh Token")))
            .flatMap(oldTokenEntity -> {
                // 1. 检查Token Family是否被撤销（检测到重放攻击）
                String familyId = oldTokenEntity.getTokenFamily();
                if (familyId == null) {
                    familyId = UUID.randomUUID().toString();
                }
                
                return checkFamilyRevoked(familyId)
                    .flatMap(isRevoked -> {
                        if (isRevoked) {
                            log.warn("检测到Refresh Token重用攻击: familyId={}, userId={}", 
                                familyId, oldTokenEntity.getUserId());
                            
                            // 撤销整个Token Family
                            return revokeTokenFamily(familyId)
                                .then(alertService.sendSecurityAlert(
                                    "Refresh Token重用攻击",
                                    String.format("用户%d的Token Family %s被检测到重放攻击", 
                                        oldTokenEntity.getUserId(), familyId),
                                    AlertLevel.CRITICAL
                                ))
                                .then(Mono.error(new SecurityException(
                                    "检测到Token重用，所有会话已撤销，请重新登录"
                                )));
                        }
                        
                        // 2. 验证Refresh Token是否过期
                        if (oldTokenEntity.getRefreshExpiresAt().isBefore(Instant.now())) {
                            return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Refresh Token已过期"));
                        }
                        
                        // 3. 立即标记旧Token为已使用（原子操作）
                        String finalFamilyId = familyId;
                        return markTokenAsUsed(oldRefreshToken, finalFamilyId)
                            .flatMap(marked -> {
                                if (!marked) {
                                    // 并发使用同一个Refresh Token - 可能是攻击
                                    log.warn("检测到Refresh Token并发使用: familyId={}", finalFamilyId);
                                    return revokeTokenFamily(finalFamilyId)
                                        .then(Mono.error(new SecurityException(
                                            "检测到Token并发使用，所有会话已撤销"
                                        )));
                                }
                                
                                // 4. 生成新的Token（继承Token Family）
                                return issueRotatedToken(oldTokenEntity, finalFamilyId)
                                    .doOnSuccess(newTokenPair -> {
                                        log.info("Refresh Token轮换成功: userId={}, oldJti={}, newJti={}", 
                                            oldTokenEntity.getUserId(), 
                                            oldTokenEntity.getJti(), 
                                            newTokenPair.jti());
                                        
                                        // 异步记录审计日志
                                        auditLogService.logTokenRotation(
                                            oldTokenEntity.getUserId(),
                                            oldTokenEntity.getJti(),
                                            newTokenPair.jti(),
                                            oldTokenEntity.getClientIp()
                                        ).subscribe();
                                    });
                            });
                    });
            });
    }
    
    /**
     * 检查Token Family是否被撤销
     */
    private Mono<Boolean> checkFamilyRevoked(String familyId) {
        return redisTemplate.hasKey(REVOKED_FAMILY_KEY + familyId);
    }
    
    /**
     * 标记Token为已使用（使用Redis SETNX确保原子性）
     * @return true表示成功标记，false表示Token已被使用
     */
    private Mono<Boolean> markTokenAsUsed(String refreshToken, String familyId) {
        String tokenHash = tokenEncryptionService.hashToken(refreshToken);
        String redisKey = TOKEN_FAMILY_KEY + familyId + ":" + tokenHash;
        
        // SETNX + EXPIRE 确保原子性
        return redisTemplate.opsForValue()
            .setIfAbsent(redisKey, "used", Duration.ofMinutes(10));
    }
    
    /**
     * 撤销整个Token Family
     */
    private Mono<Void> revokeTokenFamily(String familyId) {
        log.warn("撤销Token Family: familyId={}", familyId);
        
        // 1. 在Redis中标记Family为已撤销（持续7天）
        return redisTemplate.opsForValue()
            .set(REVOKED_FAMILY_KEY + familyId, "revoked", Duration.ofDays(7))
            .then(
                // 2. 撤销数据库中该Family的所有Token
                tokenRepository.findByTokenFamily(familyId)
                    .flatMap(token -> {
                        token.setStatus(TokenStatus.REVOKED);
                        token.setRevokedAt(Instant.now());
                        return tokenRepository.save(token);
                    })
                    .then()
            );
    }
    
    /**
     * 签发轮换后的新Token
     */
    private Mono<TokenPair> issueRotatedToken(TokenEntity oldToken, String familyId) {
        String newJti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant accessExpiry = now.plus(accessTokenTtl);
        Instant refreshExpiry = now.plus(refreshTokenTtl);
        
        return userRepository.findById(oldToken.getUserId())
            .flatMap(user -> {
                // 生成新Token
                String accessToken = jwtService.generateAccessToken(user, newJti, accessExpiry);
                String refreshToken = jwtService.generateRefreshToken(user, UUID.randomUUID().toString(), refreshExpiry);
                
                // 加密
                TokenEncryptionService.EncryptedToken encryptedAccess = tokenEncryptionService.encrypt(accessToken);
                TokenEncryptionService.EncryptedToken encryptedRefresh = tokenEncryptionService.encrypt(refreshToken);
                
                // 创建新Token实体（继承Token Family）
                TokenEntity newTokenEntity = TokenEntity.builder()
                    .jti(newJti)
                    .userId(oldToken.getUserId())
                    .tenantId(oldToken.getTenantId())
                    .deviceId(oldToken.getDeviceId())
                    .clientIp(oldToken.getClientIp())
                    .tokenFamily(familyId) // 继承Family ID
                    .accessTokenHash(tokenEncryptionService.hashToken(accessToken))
                    .accessTokenEncrypted(encryptedAccess.encryptedData())
                    .accessTokenIv(encryptedAccess.iv())
                    .refreshTokenHash(tokenEncryptionService.hashToken(refreshToken))
                    .refreshTokenEncrypted(encryptedRefresh.encryptedData())
                    .refreshTokenIv(encryptedRefresh.iv())
                    .issuedAt(now)
                    .accessExpiresAt(accessExpiry)
                    .refreshExpiresAt(refreshExpiry)
                    .status(TokenStatus.ACTIVE)
                    .previousJti(oldToken.getJti()) // 记录前一个Token
                    .build();
                
                // 保存新Token并撤销旧Token
                return tokenRepository.save(newTokenEntity)
                    .then(revokeOldToken(oldToken))
                    .thenReturn(new TokenPair(accessToken, refreshToken, accessTokenTtl.getSeconds(), newJti));
            });
    }
    
    private Mono<Void> revokeOldToken(TokenEntity oldToken) {
        oldToken.setStatus(TokenStatus.ROTATED);
        oldToken.setRevokedAt(Instant.now());
        return tokenRepository.save(oldToken).then();
    }
}
```

**Day 3: 添加异常检测和监控**

```java
// 新增: TokenAnomalyDetectionService.java
@Service
@RequiredArgsConstructor
public class TokenAnomalyDetectionService {
    
    private final TokenRepository tokenRepository;
    private final SecurityAlertService alertService;
    
    /**
     * 检测异常Token使用模式
     * 定时任务，每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000)
    public void detectAnomalies() {
        detectMultipleIPUsage()
            .then(detectRapidRefreshPattern())
            .then(detectGeographicallyImpossibleUsage())
            .subscribe();
    }
    
    /**
     * 检测同一Token在多个IP使用
     */
    private Mono<Void> detectMultipleIPUsage() {
        Instant since = Instant.now().minus(Duration.ofMinutes(15));
        
        return tokenRepository.findRecentlyUsedTokens(since)
            .filter(token -> token.getClientIpHistory().size() > 3)
            .flatMap(token -> {
                log.warn("检测到Token在多个IP使用: jti={}, ips={}", 
                    token.getJti(), token.getClientIpHistory());
                
                return alertService.sendSecurityAlert(
                    "Token异常使用",
                    String.format("Token %s 在15分钟内被%d个不同IP使用",  token.getJti(), token.getClientIpHistory().size()),
                    AlertLevel.HIGH
                ).then(revokeTokenIfNecessary(token));
            })
            .then();
    }
    
    /**
     * 检测快速连续刷新模式（可能是自动化攻击）
     */
    private Mono<Void> detectRapidRefreshPattern() {
        Instant since = Instant.now().minus(Duration.ofMinutes(5));
        
        return tokenRepository.findTokenRotationHistory(since)
            .groupBy(TokenEntity::getUserId)
            .flatMap(group -> group.count()
                .filter(count -> count > 10) // 5分钟内刷新超过10次
                .flatMap(count -> {
                    log.warn("检测到快速Token刷新: userId={}, count=", group.key(), count);
                    
                    return alertService.sendSecurityAlert(
                        "异常Token刷新频率",
                        String.format("用户%d在5分钟内刷新Token %d次", group.key(), count),
                        AlertLevel.MEDIUM
                    );
                })
            )
            .then();
    }
}
```

#### 验证方法

```bash
# 1. 测试正常Token刷新
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refresh_token>"}'

# 2. 测试Token重用检测
# 保存refresh token
REFRESH_TOKEN="<refresh_token>"

# 第一次刷新（成功）
curl -X POST http://localhost:8080/api/auth/refresh \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"

# 第二次使用相同的token（应该失败并撤销Family）
curl -X POST http://localhost:8080/api/auth/refresh \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
# 预期: {"code":401,"message":"检测到Token重用，所有会话已撤销"}

# 3. 验证数据库中Token Family
mysql -e "SELECT jti, token_family, status, previous_jti FROM token WHERE user_id=1 ORDER BY issued_at DESC LIMIT 5;"

# 4. 检查监控告警
curl http://localhost:8080/actuator/metrics/security.token.rotation.count
curl http://localhost:8080/actuator/metrics/security.token.reuse.detected
```

---

### 修复任务3: SAML重放攻击防护

**优先级**: 🔴 P0  
**工期**: 2天  
**负责模块**: apex-application  
**相关文件**: `SamlProtocolAdapter.java`

#### 实施步骤

**Day 1: 实现Request/Response ID缓存**

```java
// 修改: SamlProtocolAdapter.java
@Component
public class SamlProtocolAdapter implements SsoProtocolAdapter {
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private static final String SAML_REQUEST_KEY = "saml:request:";
    private static final String SAML_RESPONSE_KEY = "saml:response:";
    
    @Override
    public Mono<Map<String, Object>> initiate(Map<String, Object> config, String callbackUrl) {
        String requestId = "_" + UUID.randomUUID().toString();
        String issueInstant = SAML_DATE_FORMAT.format(Instant.now());
        
        return Mono.fromCallable(() -> {
            String authnRequestXml = buildAuthnRequest(requestId, issueInstant, entityId, acsUrl);
            String encodedRequest = Base64.getEncoder().encodeToString(
                authnRequestXml.getBytes(StandardCharsets.UTF_8)
            );
            
            // 缓存请求ID，5分钟有效期
            Map<String, String> requestData = Map.of(
                "issueInstant", issueInstant,
                "callbackUrl", callbackUrl,
                "entityId", entityId
            );
            
            return redisTemplate.opsForValue()
                .set(SAML_REQUEST_KEY + requestId, 
                     objectMapper.writeValueAsString(requestData), 
                     Duration.ofMinutes(5))
                .thenReturn(Map.of(
                    "redirectUrl", authEndpoint + "?SAMLRequest=" + 
                        UriUtils.encode(encodedRequest, StandardCharsets.UTF_8) + 
                        "&RelayState=" + UriUtils.encode(callbackUrl, StandardCharsets.UTF_8),
                    "requestId", requestId,
                    "samlRequest", encodedRequest
                ));
        }).flatMap(mono -> mono);
    }
    
    @Override
    public Mono<Map<String, Object>> authenticate(
            Map<String, Object> config, Map<String, String> callbackParams) {
        
        return Mono.fromCallable(() -> {
            String samlResponse = callbackParams.get("SAMLResponse");
            byte[] decodedResponse = Base64.getDecoder().decode(samlResponse);
            Document doc = parseXml(decodedResponse);
            
            Element responseElement = doc.getDocumentElement();
            String responseId = responseElement.getAttribute("ID");
            String inResponseTo = responseElement.getAttribute("InResponseTo");
            
            return Map.of(
                "responseId", responseId,
                "inResponseTo", inResponseTo,
                "document", doc,
                "responseElement", responseElement
            );
        })
        .flatMap(data -> {
            String responseId = (String) data.get("responseId");
            String inResponseTo = (String) data.get("inResponseTo");
            
            // 1. 验证InResponseTo
            return validateInResponseTo(inResponseTo)
                // 2. 防止Response重放
                .then(preventResponseReplay(responseId))
                // 3. 删除已使用的Request ID
                .then(redisTemplate.delete(SAML_REQUEST_KEY + inResponseTo))
                // 4. 继续验证和提取用户信息
                .thenReturn(data);
        })
        .flatMap(data -> {
            Document doc = (Document) data.get("document");
            Element responseElement = (Element) data.get("responseElement");
            
            Element assertion = (Element) responseElement
                .getElementsByTagNameNS(SAML_ASSERTION_NS, "Assertion")
                .item(0);
            
            String certificate = (String) config.get("certificate");
            String entityId = (String) config.get("entityId");
            
            PublicKey publicKey = parsePublicKey(certificate);
            verifySignature(assertion, publicKey);
            validateAssertion(assertion, entityId);
            
            return Mono.just(extractUserInfo(assertion));
        });
    }
    
    private Mono<Void> validateInResponseTo(String inResponseTo) {
        if (inResponseTo == null || inResponseTo.isEmpty()) {
            return Mono.error(new SecurityException("SAMLResponse缺少InResponseTo属性"));
        }
        
        return redisTemplate.hasKey(SAML_REQUEST_KEY + inResponseTo)
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.error(new SecurityException(
                        "无效的InResponseTo: " + inResponseTo + " (可能已过期或不存在)"
                    ));
                }
                return Mono.empty();
            });
    }
    
    private Mono<Void> preventResponseReplay(String responseId) {
        String redisKey = SAML_RESPONSE_KEY + responseId;
        
        // 使用SETNX确保Response ID只能被使用一次
        return redisTemplate.opsForValue()
            .setIfAbsent(redisKey, "processed", Duration.ofMinutes(10))
            .flatMap(isNew -> {
                if (!isNew) {
                    log.error("检测到SAML Response重放攻击: responseId={}", responseId);
                    return Mono.error(new SecurityException(
                        "检测到SAML Response重放攻击，Response ID: " + responseId
                    ));
                }
                return Mono.empty();
            });
    }
}
```

**Day 2: 添加监控和测试**

```java
// 新增: SamlSecurityMetrics.java
@Component
public class SamlSecurityMetrics {
    
    private final MeterRegistry meterRegistry;
    
    public void recordReplayAttempt(String responseId) {
        meterRegistry.counter("saml.security.replay_attempt", 
            "response_id", responseId).increment();
    }
    
    public void recordInvalidInResponseTo(String inResponseTo) {
        meterRegistry.counter("saml.security.invalid_in_response_to",
            "in_response_to", inResponseTo).increment();
    }
}
```

#### 验证方法

```bash
# 1. 正常SAML认证流程测试
# 1.1 发起认证
curl -X GET "http://localhost:8080/api/sso/saml/login?providerId=1"

# 1.2 模拟IdP回调（正常场景）
curl -X POST "http://localhost:8080/api/sso/saml/callback" \
  -d "SAMLResponse=<valid_saml_response>"

# 2. 重放攻击测试
# 2.1 捕获有效的SAMLResponse
SAML_RESPONSE="<captured_saml_response>"

# 2.2 第一次提交（应该成功）
curl -X POST "http://localhost:8080/api/sso/saml/callback" \
  -d "SAMLResponse=$SAML_RESPONSE"

# 2.3 第二次提交相同的Response（应该失败）
curl -X POST "http://localhost:8080/api/sso/saml/callback" \
  -d "SAMLResponse=$SAML_RESPONSE"
# 预期: {"code":403,"message":"检测到SAML Response重放攻击"}

# 3. 验证Redis缓存
redis-cli KEYS "saml:*"
redis-cli GET "saml:request:<request_id>"
redis-cli GET "saml:response:<response_id>"

# 4. 检查监控指标
curl http://localhost:8080/actuator/metrics/saml.security.replay_attempt
curl http://localhost:8080/actuator/metrics/saml.security.invalid_in_response_to
```

---

## 📊 修复进度跟踪

| 任务ID | 问题描述 | 优先级 | 状态 | 负责人 | 预计完成 |
|-------|---------|-------|------|--------|---------|
| T1 | Token加密存储 | P0 | 🟡 待开始 | - | Day 2 |
| T2 | Refresh Token轮换 | P0 | 🟡 待开始 | - | Day 5 |
| T3 | SAML重放防护 | P0 | 🟡 待开始 | - | Day 7 |
| T4 | OIDC State验证 | P0 | 🟡 待开始 | - | Day 8 |
| T5 | JWT密钥轮换 | P0 | 🟡 待开始 | - | Day 10 |

---

## 🧪 集成测试清单

### P0问题验证清单

- [ ] Token加密存储
  - [ ] 新Token加密存储成功
  - [ ] Token解密和验证功能正常
  - [ ] 数据库中无明文Token
  - [ ] 性能测试（加解密开销<5ms）
  
- [ ] Refresh Token轮换
  - [ ] 正常刷新流程Token轮换成功
  - [ ] Token重用被检测并撤销Family
  - [ ] 异常检测告警触发
  - [ ] Token Family链路完整
  
- [ ] SAML重放防护
  - [ ] InResponseTo验证通过
  - [ ] Response重放被阻止
  - [ ] Redis缓存TTL正确
  - [ ] 监控指标正确记录

---

## 📝 后续行动

### 立即行动（本周）

1. ✅ 完成安全评审报告
2. 🔄 评审并批准修复方案
3. 🟡 开始P0问题修复实施

### 短期计划（2周内）

4. 完成所有P0问题修复
5. 执行全面回归测试
6. 部署到测试环境验证

### 中期计划（1个月内）

7. 完成P1问题修复
8. 优化P2问题
9. 准备生产上线

---

## 📞 联系方式

- **安全团队**: security@apex.com
- **技术负责人**: tech-lead@apex.com
- **紧急联系**: +86-xxx-xxxx-xxxx

