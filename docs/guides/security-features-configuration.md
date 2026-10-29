# 安全功能配置指南

本文档说明如何配置和使用项目中新增的安全功能。

## 目录

1. [Token加密存储](#1-token加密存储)
2. [Refresh Token轮换](#2-refresh-token轮换)
3. [JWT密钥轮换](#3-jwt密钥轮换)
4. [统一审计日志](#4-统一审计日志)
5. [SSO配置加密](#5-sso配置加密)
6. [OAuth2客户端密钥轮换](#6-oauth2客户端密钥轮换)
7. [SAML重放攻击防护](#7-saml重放攻击防护)
8. [OIDC State验证](#8-oidc-state验证)

---

<a id="1-token加密存储"></a>

## 1. Token加密存储

### 功能说明
对存储在数据库中的Access Token和Refresh Token进行AES-256-GCM加密，防止数据库泄露导致的Token泄露。

### 配置

#### application.yml
```yaml
apex:
  security:
    token:
      encryption:
        # 加密主密钥（生产环境必须配置！）
        # 建议从AWS KMS、HashiCorp Vault等密钥管理服务获取
        master-key: ${TOKEN_ENCRYPTION_KEY:}  # 256位Base64编码的密钥
        
        # 是否启用Token加密
        enabled: true
```

#### 环境变量（推荐）
```bash
# 生成256位密钥
export TOKEN_ENCRYPTION_KEY=$(openssl rand -base64 32)
```

#### 数据库迁移
```sql
-- 已自动创建迁移文件
-- /apex-interfaces/src/main/resources/db/migration/mysql/V26__add_token_encryption.sql
-- 执行 mvn flyway:migrate 即可应用
```

### 使用说明

Token加密对应用层透明，无需修改业务代码。系统会自动：
- 在存储Token时加密
- 在读取Token时解密
- 在验证Token时自动处理

### 安全建议

1. **生产环境必须配置外部密钥**：不要依赖自动生成的密钥
2. **使用密钥管理服务**：AWS KMS、Azure Key Vault、HashiCorp Vault
3. **定期备份主密钥**：丢失主密钥将导致所有Token无法解密
4. **密钥轮换**：每年至少轮换一次主密钥

---

<a id="2-refresh-token轮换"></a>

## 2. Refresh Token轮换

### 功能说明
每次使用Refresh Token刷新Access Token时，颁发新的Refresh Token并撤销旧Token，防止Refresh Token被盗用后长期滥用。

### 配置

#### application.yml
```yaml
apex:
  security:
    token:
      refresh:
        # Refresh Token轮换配置
        rotation:
          enabled: true
          
          # 异常检测配置
          anomaly-detection:
            enabled: true
            max-requests-per-hour: 10  # 每小时最多刷新次数
            suspicious-ip-change: true  # 检测IP异常变化
```

### 工作原理

1. 客户端使用Refresh Token请求新Access Token
2. 系统验证Refresh Token有效性
3. 系统检测异常行为（频率、IP变化、设备指纹等）
4. 颁发新的Access Token和Refresh Token
5. 撤销旧的Refresh Token
6. 记录审计日志

### 异常检测规则

| 异常类型 | 检测条件 | 处理方式 |
|---------|---------|---------|
| 频繁刷新 | 1小时内超过10次 | 拒绝请求并记录安全事件 |
| IP异常变化 | IP地址与上次不同 | 允许但记录警告 |
| 设备指纹变化 | User-Agent变化 | 允许但记录警告 |
| 重复使用旧Token | 使用已撤销的Token | 撤销整个Token家族并告警 |

### 安全建议

1. **客户端必须存储新Token**：每次刷新后更新本地存储
2. **处理Token撤销**：客户端应处理401错误并重新登录
3. **监控异常刷新**：关注审计日志中的异常事件

---

<a id="3-jwt密钥轮换"></a>

## 3. JWT密钥轮换

### 功能说明
定期轮换JWT签名密钥（RSA-2048），降低密钥长期暴露的风险。

### 配置

#### application.yml
```yaml
apex:
  security:
    jwt:
      key-rotation:
        enabled: true
        rotation-period-days: 30  # 轮换周期：30天
        grace-period-days: 7      # 宽限期：7天
        
      # 定时任务配置
      scheduled-rotation:
        enabled: true
        cron: "0 0 2 * * ?"  # 每天凌晨2点检查
```

### 手动轮换

```bash
# 使用管理API手动触发轮换
curl -X POST http://localhost:8080/admin/jwt/rotate-keys \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

### 紧急轮换（密钥泄露响应）

```java
@Autowired
private JwtKeyRotationService keyRotationService;

// 强制立即轮换并删除所有旧密钥
keyRotationService.forceRotateKeys("密钥疑似泄露，紧急轮换")
    .subscribe();
```

### 工作原理

1. 每30天自动生成新的RSA密钥对
2. 新密钥立即用于签发JWT
3. 旧密钥在7天宽限期内仍可验证JWT
4. 7天后删除旧密钥

### 密钥存储

```
Redis结构：
jwt:keys:current           -> 当前活跃密钥ID
jwt:keys:all               -> 所有可用密钥ID集合
jwt:key:{keyId}:private    -> 私钥（Base64）
jwt:key:{keyId}:public     -> 公钥（Base64）
jwt:key:{keyId}:created    -> 创建时间戳
```

### 安全建议

1. **监控轮换日志**：确保定时任务正常执行
2. **备份密钥**：轮换前备份旧密钥（仅用于灾难恢复）
3. **快速响应**：密钥泄露时立即执行强制轮换

---

<a id="4-统一审计日志"></a>

## 4. 统一审计日志

### 功能说明
统一记录所有安全相关事件，满足合规要求和安全追溯需求。

### 配置

#### application.yml
```yaml
apex:
  security:
    audit:
      enabled: true
      
      # Redis Stream配置
      stream:
        name: "audit:stream"
        security-alert-stream: "audit:security:alert"
        
      # 日志级别
      log-level:
        authentication: INFO
        authorization: INFO
        oauth2: INFO
        sso: INFO
        security: WARN
        system: INFO
```

### 使用方法

#### 记录认证事件
```java
@Autowired
private UnifiedAuditService auditService;

// 登录成功
auditService.logAuthenticationEvent(
    "LOGIN",
    userId,
    tenantId,
    clientIp,
    true,  // 成功
    null,
    Map.of("method", "password")
).subscribe();

// 登录失败
auditService.logAuthenticationEvent(
    "LOGIN",
    userId,
    tenantId,
    clientIp,
    false,  // 失败
    "密码错误",
    Map.of("attemptCount", 3)
).subscribe();
```

#### 记录安全事件
```java
// 暴力破解检测
auditService.logSecurityEvent(
    "BRUTE_FORCE_DETECTED",
    userId,
    tenantId,
    clientIp,
    "HIGH",  // 严重程度
    "检测到暴力破解攻击，已锁定账户",
    Map.of("attemptCount", 5, "lockDuration", "30min")
).subscribe();
```

### 事件类型

| 事件类型 | 操作示例 |
|---------|---------|
| AUTHENTICATION | LOGIN, LOGOUT, PASSWORD_CHANGE, MFA_ENABLE |
| AUTHORIZATION | PERMISSION_CHECK, ROLE_ASSIGN, RESOURCE_ACCESS |
| OAUTH2 | AUTHORIZE, TOKEN_ISSUE, TOKEN_REFRESH, TOKEN_REVOKE |
| SSO | SAML_AUTH, OIDC_AUTH, SSO_LOGOUT |
| SECURITY | LOGIN_FAILED, BRUTE_FORCE, SUSPICIOUS_ACTIVITY, KEY_LEAKED |
| SYSTEM | KEY_ROTATION, CONFIG_CHANGE, SERVICE_START, SERVICE_STOP |

### 日志查询

```bash
# 使用Redis CLI查询审计日志
redis-cli XREAD COUNT 100 STREAMS audit:stream 0

# 查询安全告警
redis-cli XREAD COUNT 100 STREAMS audit:security:alert 0
```

### 集成ELK Stack（推荐）

```yaml
# Logstash配置示例
input {
  redis {
    host => "localhost"
    port => 6379
    data_type => "stream"
    key => "audit:stream"
    codec => json
  }
}

filter {
  json {
    source => "data"
  }
}

output {
  elasticsearch {
    hosts => ["localhost:9200"]
    index => "audit-logs-%{+YYYY.MM.dd}"
  }
}
```

---

<a id="5-sso配置加密"></a>

## 5. SSO配置加密

### 功能说明
对SSO敏感配置（client_secret、私钥等）进行AES-256-GCM加密存储。

### 配置

#### application.yml
```yaml
apex:
  security:
    sso:
      encryption:
        enabled: true
        # 主密钥（生产环境必须配置！）
        master-key: ${SSO_ENCRYPTION_KEY:}  # 256位Base64编码
```

#### 环境变量
```bash
export SSO_ENCRYPTION_KEY=$(openssl rand -base64 32)
```

### 使用方法

#### 加密敏感字段
```java
@Autowired
private SsoConfigEncryptionService encryptionService;

// 加密client_secret
String encryptedSecret = encryptionService.encrypt(clientSecret)
    .block();

// 存储到数据库
ssoConfig.setClientSecret(encryptedSecret);
ssoConfigRepository.save(ssoConfig);
```

#### 解密敏感字段
```java
// 从数据库读取
SsoConfig config = ssoConfigRepository.findById(id).block();

// 解密client_secret
String plainSecret = encryptionService.decrypt(config.getClientSecret())
    .block();

// 使用解密后的密钥
oauth2Client.setClientSecret(plainSecret);
```

### 敏感字段识别

系统自动识别以下字段为敏感字段：
- `clientSecret`
- `privateKey`
- `certificate`
- `bindPassword`
- `apiKey`
- `token`
- 任何包含 `secret`、`password`、`key` 的字段

### 主密钥轮换

```java
// 轮换主密钥（需要维护窗口）
encryptionService.rotateMasterKey()
    .subscribe(newKeyId -> {
        // 重新加密所有SSO配置
        // ...
    });
```

---

<a id="6-oauth2客户端密钥轮换"></a>

## 6. OAuth2客户端密钥轮换

### 功能说明
定期轮换OAuth2客户端密钥，支持宽限期和提前通知。

### 配置

#### application.yml
```yaml
apex:
  security:
    oauth2:
      client-secret-rotation:
        enabled: true
        rotation-period-days: 90   # 轮换周期：90天
        grace-period-days: 7        # 宽限期：7天
        notification-advance-days: 7  # 提前通知：7天
        
      # 定时任务
      scheduled-rotation:
        enabled: true
        cron: "0 0 3 * * ?"  # 每天凌晨3点检查
```

### 手动轮换

```java
@Autowired
private ClientSecretRotationService rotationService;

// 轮换密钥并通知客户端
String newSecret = rotationService.rotateClientSecret(clientId, true)
    .block();

// 将新密钥提供给客户端（仅返回一次）
System.out.println("新的client_secret: " + newSecret);
```

### 强制轮换（紧急响应）

```java
// 密钥泄露时立即轮换
String newSecret = rotationService.forceRotateClientSecret(
    clientId,
    "密钥疑似泄露，紧急轮换"
).block();
```

### 客户端通知

系统会在轮换前7天通过以下方式通知客户端：
1. **邮件通知**：发送到注册邮箱
2. **Webhook回调**：POST到配置的回调URL
3. **管理后台提醒**：在OAuth2客户端管理页面显示

### 客户端集成指南

#### 密钥更新流程
1. 收到轮换通知（提前7天）
2. 在管理后台获取新密钥
3. 更新应用配置
4. 验证新密钥可用
5. 等待宽限期结束

#### 宽限期内的行为
- 新旧密钥均可使用
- 建议尽快切换到新密钥
- 7天后旧密钥失效

---

<a id="7-saml重放攻击防护"></a>

## 7. SAML重放攻击防护

### 功能说明
防止SAML Assertion被重放使用，通过Redis记录已使用的Assertion ID。

### 配置

#### application.yml
```yaml
apex:
  security:
    sso:
      saml:
        replay-protection:
          enabled: true
          assertion-ttl: 300  # Assertion有效期：5分钟
```

### 工作原理

1. 客户端发送SAML Assertion
2. 系统提取Assertion ID
3. 检查Redis中是否已存在该ID
4. 如果是首次使用，存储ID并继续认证
5. 如果是重复使用，拒绝并记录安全事件

### Redis存储结构

```
saml:assertion:{assertionId} -> "used"  (TTL: 5分钟)
```

### 监控

```bash
# 查看被阻止的重放攻击
redis-cli XREAD COUNT 100 STREAMS audit:security:alert 0 | grep SAML_REPLAY
```

---

<a id="8-oidc-state验证"></a>

## 8. OIDC State验证

### 功能说明
验证OIDC State参数，防止CSRF攻击。

### 配置

#### application.yml
```yaml
apex:
  security:
    sso:
      oidc:
        state-validation:
          enabled: true
          state-ttl: 600  # State有效期：10分钟
```

### 工作原理

1. 发起OIDC认证时生成随机State
2. 将State存储到Redis（TTL: 10分钟）
3. 回调时验证State参数
4. 验证通过后删除State（防止重放）
5. 验证失败则拒绝并记录安全事件

### Redis存储结构

```
oidc:state:{state} -> callbackUrl  (TTL: 10分钟)
```

### 客户端集成

OIDC State验证对客户端透明，无需额外配置。系统会自动：
- 生成State参数
- 在回调时验证State
- 记录验证失败事件

---

## 生产环境部署检查清单

### 1. 密钥配置 ✓
- [ ] 配置Token加密主密钥（`TOKEN_ENCRYPTION_KEY`）
- [ ] 配置SSO加密主密钥（`SSO_ENCRYPTION_KEY`）
- [ ] 使用外部密钥管理服务（AWS KMS / Vault）
- [ ] 确保密钥不在代码库或日志中

### 2. 数据库迁移 ✓
- [ ] 执行 `V26__add_token_encryption.sql`
- [ ] 执行 `V27__add_token_hash.sql`
- [ ] 备份现有数据库
- [ ] 验证迁移成功

### 3. Redis配置 ✓
- [ ] 配置Redis连接信息
- [ ] 启用Redis持久化（AOF + RDB）
- [ ] 配置Redis密码认证
- [ ] 配置Redis主从复制（高可用）

### 4. 审计日志 ✓
- [ ] 配置日志收集器（Logstash / Fluentd）
- [ ] 集成Elasticsearch存储
- [ ] 配置Kibana可视化
- [ ] 设置安全告警规则

### 5. 定时任务 ✓
- [ ] 验证JWT密钥轮换定时任务运行
- [ ] 验证客户端密钥轮换定时任务运行
- [ ] 验证Token清理定时任务运行
- [ ] 配置任务失败告警

### 6. 监控告警 ✓
- [ ] 配置暴力破解告警
- [ ] 配置密钥轮换失败告警
- [ ] 配置异常Token刷新告警
- [ ] 配置SAML/OIDC重放攻击告警

### 7. 安全加固 ✓
- [ ] 启用HTTPS（TLS 1.2+）
- [ ] 配置防火墙规则
- [ ] 限制管理API访问
- [ ] 定期安全扫描

---

## 故障排查

### Token解密失败

**症状**：用户无法验证Token，日志显示"Token解密失败"

**原因**：
1. 主密钥配置错误或丢失
2. Token在加密前生成，无法用新密钥解密

**解决方案**：
```bash
# 1. 检查主密钥配置
echo $TOKEN_ENCRYPTION_KEY

# 2. 验证Redis中的主密钥
redis-cli GET token:encryption:master_key

# 3. 如果密钥丢失，需要重新生成并让所有用户重新登录
```

### JWT验证失败

**症状**：JWT验证失败，错误"Invalid signature"

**原因**：
1. JWT密钥轮换后，旧Token使用了已删除的密钥
2. 宽限期过短

**解决方案**：
```yaml
# 延长宽限期
apex:
  security:
    jwt:
      key-rotation:
        grace-period-days: 14  # 从7天延长到14天
```

### 审计日志丢失

**症状**：审计日志未记录或不完整

**原因**：
1. Redis Stream写入失败
2. 日志收集器未运行

**解决方案**：
```bash
# 检查Redis Stream
redis-cli XINFO STREAM audit:stream

# 检查Logstash运行状态
systemctl status logstash

# 查看错误日志
tail -f /var/log/logstash/logstash-plain.log
```

---

## 性能优化建议

### 1. Redis性能优化
```yaml
# 使用连接池
spring:
  redis:
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
```

### 2. 加密性能优化
- 使用硬件加速（AES-NI）
- 批量加密/解密
- 缓存解密结果（短时间）

### 3. 审计日志优化
- 使用异步写入（已实现）
- 批量发送到Elasticsearch
- 定期归档历史日志

---

## 相关文档

- [OAuth2集成指南](../01-guides/oauth2-integration-guide.md)
- [SSO完整配置](../01-guides/sso-oauth2-keycloak-complete.md)
- [安全评审报告](../reviews/auth-security-review-report.md)

---

**文档版本**：1.0.0  
**最后更新**：2026-09-25  
**维护团队**：Apex Security Team
