# 安全修复验证指南

本文档说明如何验证已实施的安全修复是否正常工作。

## 前置条件

### 环境要求
- Java 17+
- Maven 3.8+ 或使用项目自带的 Maven Wrapper
- Redis 6.0+
- MySQL 8.0+

### 配置环境变量
```bash
# Token加密主密钥
export TOKEN_ENCRYPTION_KEY=$(openssl rand -base64 32)

# SSO配置加密主密钥
export SSO_ENCRYPTION_KEY=$(openssl rand -base64 32)

# Redis连接
export SPRING_REDIS_HOST=localhost
export SPRING_REDIS_PORT=6379

# MySQL连接
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/apex_scaffold
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=your_password
```

---

## 验证步骤

### 1. 编译项目

```bash
# 清理并编译
mvn clean compile -DskipTests

# 预期结果：编译成功，无错误
```

### 2. 执行数据库迁移

```bash
# 运行Flyway迁移
mvn flyway:migrate

# 预期结果：成功应用以下迁移
# - V26__add_token_encryption.sql
# - V27__add_token_hash.sql
```

### 3. 运行单元测试

```bash
# 运行所有单元测试
mvn test

# 仅运行认证相关测试
mvn test -Dtest="*Auth*Test"

# 仅运行Token相关测试
mvn test -Dtest="*Token*Test"
```

---

## 功能验证清单

### ✅ 1. Token加密存储验证

#### 测试步骤
```bash
# 1. 启动应用
mvn spring-boot:run

# 2. 登录获取Token
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test@example.com",
    "password": "Password123!"
  }'

# 3. 检查数据库中的Token是否加密
mysql -u root -p apex_scaffold -e "SELECT token FROM oauth2_token LIMIT 1;"

# 预期结果：token字段应该是加密的（不可读的密文）
```

#### 验证点
- [ ] Token在数据库中已加密存储
- [ ] Token验证仍然正常工作
- [ ] 解密失败时返回适当错误
- [ ] 审计日志记录Token操作

---

### ✅ 2. Refresh Token轮换验证

#### 测试步骤
```bash
# 1. 使用Refresh Token刷新Access Token
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN"
  }'

# 2. 尝试再次使用旧的Refresh Token（应该失败）
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "OLD_REFRESH_TOKEN"
  }'

# 预期结果：第二次请求应返回401 Unauthorized
```

#### 验证点
- [ ] 刷新Token后返回新的Access Token和Refresh Token
- [ ] 旧的Refresh Token被撤销
- [ ] 重复使用旧Token被拒绝
- [ ] 异常刷新被检测并记录（频繁刷新、IP变化）
- [ ] 审计日志记录所有Token刷新操作

---

### ✅ 3. JWT密钥轮换验证

#### 测试步骤
```bash
# 1. 检查当前JWT密钥
redis-cli GET jwt:keys:current

# 2. 手动触发密钥轮换
curl -X POST http://localhost:8080/admin/jwt/rotate-keys \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"

# 3. 验证新密钥已生成
redis-cli GET jwt:keys:current

# 4. 使用旧密钥签发的JWT仍能验证（宽限期内）
curl -X GET http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer ${OLD_JWT}"

# 预期结果：在宽限期内旧JWT仍然有效
```

#### 验证点
- [ ] 新密钥成功生成（RSA-2048）
- [ ] 当前密钥ID已更新
- [ ] 旧密钥在宽限期内仍可验证JWT
- [ ] 宽限期后旧密钥被清理
- [ ] 定时任务每天检查并执行轮换
- [ ] 审计日志记录密钥轮换事件

---

### ✅ 4. 统一审计日志验证

#### 测试步骤
```bash
# 1. 执行各种操作触发审计日志
# - 登录
# - 权限检查
# - Token刷新
# - SSO认证

# 2. 查看Redis Stream中的审计日志
redis-cli XREAD COUNT 10 STREAMS audit:stream 0

# 3. 查看安全告警
redis-cli XREAD COUNT 10 STREAMS audit:security:alert 0

# 4. 检查应用日志
tail -f logs/apex-scaffold.log | grep AUDIT
```

#### 验证点
- [ ] 认证事件被正确记录（LOGIN/LOGOUT）
- [ ] 授权事件被正确记录（PERMISSION_CHECK）
- [ ] OAuth2事件被正确记录（TOKEN_ISSUE/TOKEN_REFRESH）
- [ ] SSO事件被正确记录（SAML_AUTH/OIDC_AUTH）
- [ ] 安全事件被正确记录并告警（LOGIN_FAILED/BRUTE_FORCE）
- [ ] 系统事件被正确记录（KEY_ROTATION）
- [ ] 审计日志包含必要字段（eventId/userId/clientIp/timestamp等）
- [ ] 高危事件发送到安全告警队列

---

### ✅ 5. SSO配置加密验证

#### 测试步骤
```bash
# 1. 创建SSO配置并加密敏感字段
curl -X POST http://localhost:8080/admin/sso/config \
  -H "Content-Type: application/json" \
  -d '{
    "protocol": "OIDC",
    "clientId": "test-client",
    "clientSecret": "super-secret-key",
    "issuerUri": "https://idp.example.com"
  }'

# 2. 检查数据库中的clientSecret是否加密
mysql -u root -p apex_scaffold -e \
  "SELECT client_secret FROM sso_config WHERE client_id='test-client';"

# 3. 读取配置验证自动解密
curl -X GET http://localhost:8080/admin/sso/config/test-client \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"

# 预期结果：clientSecret在数据库中加密，读取时自动解密
```

#### 验证点
- [ ] 敏感字段在存储前被加密
- [ ] 加密格式为 Base64(IV):Base64(Ciphertext)
- [ ] 读取时自动解密
- [ ] 加密/解密失败有适当的错误处理
- [ ] 主密钥从环境变量正确加载

---

### ✅ 6. OAuth2客户端密钥轮换验证

#### 测试步骤
```bash
# 1. 为客户端轮换密钥
curl -X POST http://localhost:8080/admin/oauth2/clients/test-client/rotate-secret \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"

# 响应示例：
# {
#   "newSecret": "new-random-secret-xxxxx",
#   "expiresAt": "2026-10-25T00:00:00Z",
#   "gracePeriodDays": 7
# }

# 2. 使用旧密钥验证（宽限期内应成功）
curl -X POST http://localhost:8080/oauth2/token \
  -u "test-client:old-secret" \
  -d "grant_type=client_credentials"

# 3. 使用新密钥验证（应成功）
curl -X POST http://localhost:8080/oauth2/token \
  -u "test-client:new-secret" \
  -d "grant_type=client_credentials"

# 4. 等待宽限期结束后，旧密钥应失效
```

#### 验证点
- [ ] 新密钥成功生成（256位随机密钥）
- [ ] 旧密钥在宽限期内仍可使用
- [ ] 密钥哈希后存储（不存储明文）
- [ ] 宽限期后旧密钥被清理
- [ ] 轮换前通知客户端（邮件/Webhook）
- [ ] 审计日志记录密钥轮换事件

---

### ✅ 7. SAML重放攻击防护验证

#### 测试步骤
```bash
# 1. 发起SAML认证（获取Assertion）
# （需要配置SAML IdP）

# 2. 使用相同的Assertion再次认证
curl -X POST http://localhost:8080/auth/saml/callback \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "SAMLResponse=${SAME_ASSERTION}"

# 预期结果：第二次请求应被拒绝，错误信息"SAML Assertion重放攻击检测"
```

#### 验证点
- [ ] 首次使用Assertion认证成功
- [ ] 重复使用相同Assertion被拒绝
- [ ] Assertion ID存储在Redis（TTL: 5分钟）
- [ ] 重放攻击被记录为安全事件
- [ ] 审计日志包含Assertion ID和错误详情

---

### ✅ 8. OIDC State验证验证

#### 测试步骤
```bash
# 1. 发起OIDC认证（生成State）
curl http://localhost:8080/auth/oidc/initiate?provider=google

# 响应示例：
# {
#   "authorizationUrl": "https://accounts.google.com/o/oauth2/v2/auth?state=xxx&...",
#   "state": "random-state-value"
# }

# 2. 模拟回调，使用无效的State
curl http://localhost:8080/auth/oidc/callback?code=xxx&state=invalid-state

# 预期结果：请求被拒绝，错误信息"OIDC State验证失败，可能的CSRF攻击"

# 3. 使用正确的State
curl http://localhost:8080/auth/oidc/callback?code=xxx&state=valid-state

# 预期结果：认证成功
```

#### 验证点
- [ ] 发起认证时生成随机State
- [ ] State存储在Redis（TTL: 10分钟）
- [ ] 回调时验证State参数
- [ ] State验证失败被拒绝
- [ ] 验证成功后State被删除（防止重放）
- [ ] State验证失败记录为安全事件

---

## 集成测试

### 完整认证流程测试

```bash
#!/bin/bash

echo "=== 完整认证流程测试 ==="

# 1. 用户注册
echo "1. 注册用户..."
REGISTER_RESPONSE=$(curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Password123!",
    "name": "Test User"
  }')
echo "注册响应: $REGISTER_RESPONSE"

# 2. 用户登录
echo "2. 用户登录..."
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Password123!"
  }')
echo "登录响应: $LOGIN_RESPONSE"

ACCESS_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.accessToken')
REFRESH_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.refreshToken')

# 3. 访问受保护资源
echo "3. 访问受保护资源..."
PROFILE_RESPONSE=$(curl -s -X GET http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer $ACCESS_TOKEN")
echo "用户资料: $PROFILE_RESPONSE"

# 4. 刷新Token
echo "4. 刷新Token..."
REFRESH_RESPONSE=$(curl -s -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}")
echo "刷新响应: $REFRESH_RESPONSE"

NEW_ACCESS_TOKEN=$(echo $REFRESH_RESPONSE | jq -r '.accessToken')
NEW_REFRESH_TOKEN=$(echo $REFRESH_RESPONSE | jq -r '.refreshToken')

# 5. 使用新Token访问资源
echo "5. 使用新Token访问资源..."
PROFILE_RESPONSE2=$(curl -s -X GET http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer $NEW_ACCESS_TOKEN")
echo "用户资料: $PROFILE_RESPONSE2"

# 6. 尝试使用旧Refresh Token（应失败）
echo "6. 尝试使用旧Refresh Token..."
OLD_REFRESH_RESPONSE=$(curl -s -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}")
echo "旧Token刷新响应（应失败）: $OLD_REFRESH_RESPONSE"

# 7. 登出
echo "7. 用户登出..."
LOGOUT_RESPONSE=$(curl -s -X POST http://localhost:8080/auth/logout \
  -H "Authorization: Bearer $NEW_ACCESS_TOKEN")
echo "登出响应: $LOGOUT_RESPONSE"

# 8. 验证Token已失效
echo "8. 验证Token已失效..."
INVALID_RESPONSE=$(curl -s -X GET http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer $NEW_ACCESS_TOKEN")
echo "失效Token响应（应401）: $INVALID_RESPONSE"

echo "=== 测试完成 ==="
```

### 预期结果
- 所有步骤成功执行
- Token加密、轮换、验证正常
- 审计日志完整记录所有操作
- 安全策略正确生效

---

## 性能测试

### Token加密性能测试

```bash
# 使用Apache Bench测试Token验证性能
ab -n 1000 -c 10 \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  http://localhost:8080/api/user/profile

# 预期结果：
# - QPS > 500
# - 平均响应时间 < 20ms
# - 错误率 = 0%
```

### 并发刷新Token测试

```bash
# 测试并发Token刷新
ab -n 100 -c 10 \
  -p refresh_token.json \
  -T application/json \
  http://localhost:8080/auth/refresh

# 预期结果：
# - 异常检测正常工作
# - 无Token泄露
# - 审计日志完整
```

---

## 安全测试

### 1. Token泄露测试

模拟Token泄露场景，验证轮换机制是否有效：

```bash
# 1. 获取Token
# 2. 从不同IP使用该Token刷新
# 3. 系统应检测异常并记录安全事件
```

### 2. 重放攻击测试

```bash
# 1. 捕获SAML Assertion或OIDC回调
# 2. 重复发送相同请求
# 3. 系统应拒绝并记录安全事件
```

### 3. 暴力破解测试

```bash
# 1. 短时间内多次登录失败
# 2. 系统应触发账户锁定
# 3. 记录暴力破解安全事件
```

---

## 监控指标

### 关键指标

| 指标 | 阈值 | 监控方法 |
|------|------|---------|
| Token解密成功率 | > 99.9% | 审计日志统计 |
| JWT密钥轮换成功率 | 100% | 定时任务日志 |
| 审计日志写入成功率 | > 99.9% | Redis监控 |
| Token刷新异常检测率 | < 1% | 安全事件统计 |
| SAML重放攻击拦截数 | 监控 | 安全告警队列 |
| OIDC State验证失败数 | 监控 | 安全告警队列 |

### Prometheus指标示例

```yaml
# Token操作指标
apex_token_encryption_total{status="success|failure"}
apex_token_refresh_total{status="success|failure"}
apex_token_validation_total{status="success|failure"}

# 密钥轮换指标
apex_jwt_key_rotation_total{status="success|failure"}
apex_client_secret_rotation_total{status="success|failure"}

# 审计日志指标
apex_audit_log_total{event_type="auth|authz|oauth2|sso|security|system"}
apex_security_alert_total{severity="high|medium|low"}

# SSO安全指标
apex_saml_replay_blocked_total
apex_oidc_state_validation_failed_total
```

---

## 故障恢复测试

### 1. Redis故障恢复

```bash
# 1. 停止Redis
sudo systemctl stop redis

# 2. 观察应用行为（应降级处理）
# 3. 重启Redis
sudo systemctl start redis

# 4. 验证服务恢复正常
```

### 2. 密钥丢失恢复

```bash
# 1. 备份当前密钥
redis-cli GET token:encryption:master_key > backup_key.txt

# 2. 删除密钥模拟丢失
redis-cli DEL token:encryption:master_key

# 3. 恢复密钥
cat backup_key.txt | redis-cli -x SET token:encryption:master_key

# 4. 验证Token验证恢复正常
```

---

## 合规性检查

### GDPR合规
- [ ] 审计日志记录数据访问
- [ ] 支持用户数据删除
- [ ] Token撤销机制完整

### SOC 2合规
- [ ] 访问控制审计完整
- [ ] 密钥管理符合要求
- [ ] 安全事件实时告警

### ISO 27001合规
- [ ] 敏感数据加密存储
- [ ] 密钥定期轮换
- [ ] 安全事件记录完整

---

## 总结

完成以上所有验证后，确认：

✅ **P0问题（必须修复）**
- [x] Token加密存储
- [x] Refresh Token轮换
- [x] JWT密钥轮换
- [x] 统一审计日志
- [x] SAML重放攻击防护
- [x] OIDC State验证

✅ **P1问题（重要优化）**
- [x] SSO配置加密
- [x] OAuth2客户端密钥轮换

✅ **文档和配置**
- [x] 安全功能配置指南
- [x] 验证测试指南

### 下一步建议

1. **立即执行**：运行完整测试套件
2. **性能测试**：验证加密不影响性能
3. **安全扫描**：使用OWASP ZAP或Burp Suite扫描
4. **渗透测试**：聘请第三方进行渗透测试
5. **上线准备**：按照生产部署检查清单配置

---

**验证负责人**：____________________  
**验证日期**：____________________  
**审核人**：____________________  
**审核日期**：____________________
