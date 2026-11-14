# 🚨 认证授权安全评审 - 快速参考卡

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

> **适用人群**: 开发人员、安全工程师、项目经理  
> **更新日期**: 2026-09-25

---

## 🎯 一句话总结

**项目认证授权系统整体良好(4.0/5.0)，但存在9个必须在上线前修复的P0-P1问题，预计需要18个工作日完成。**

---

## ⚠️ 上线阻断问题 (必须修复)

### 🔴 P0 - 严重 (5个, 10天)

| # | 问题 | 影响 | 工期 | 负责模块 |
|---|-----|------|------|---------|
| 1 | Token明文存储 | 💥 数据泄露 | 2天 | apex-auth-center |
| 3 | Refresh Token无轮换 | 💥 Token泄露检测失败 | 3天 | apex-auth-center |
| 6 | JWT密钥无轮换 | 💥 密钥泄露无法应对 | 2天 | apex-auth-center |
| 17 | SAML重放攻击 | 💥 认证绕过 | 2天 | apex-application |
| 18 | OIDC State未验证 | 💥 CSRF攻击 | 1天 | apex-application |

### 🟠 P1 - 高危 (4个, 8天)

| # | 问题 | 影响 | 工期 | 负责模块 |
|---|-----|------|------|---------|
| 4 | 客户端密钥无轮换 | 🔥 密钥泄露永久有效 | 3天 | apex-application |
| 12 | 审计日志不完整 | 🔥 安全事件无法追溯 | 2天 | 全局 |
| 14 | 敏感配置硬编码 | 🔥 配置泄露 | 1天 | 全局 |
| 15 | SSO配置明文 | 🔥 IdP凭证泄露 | 2天 | apex-application |

**总计**: 9个问题，18天工期

---

## 📋 修复优先级

```
第1周 (Day 1-5):  问题1, 问题3  ✅ 完成Token安全
第2周 (Day 6-10): 问题17, 问题18, 问题6  ✅ 完成SSO和JWT安全
第3周 (Day 11-15): 问题14, 问题12, 问题15  ✅ 完成配置和审计
第4周 (Day 16-18): 问题4  ✅ 完成OAuth2安全
```

---

## 🔧 关键修复点速查

### 1️⃣ Token加密存储

**文件**: `TokenServiceImpl.java`, `TokenEncryptionService.java`(新增)

```java
// 使用AES-256-GCM加密
public EncryptedToken encrypt(String token) {
    // 实现加密逻辑...
}

// 只存储哈希值用于查询
String tokenHash = SHA256(token);
```

**数据库变更**:
```sql
ALTER TABLE oauth2_authorization 
ADD COLUMN access_token_hash VARCHAR(64),
ADD COLUMN access_token_encrypted TEXT;
```

---

### 2️⃣ Refresh Token轮换

**文件**: `RefreshTokenRotationService.java`(新增)

```java
// Token Family机制
public Mono<TokenPair> rotateRefreshToken(String oldToken) {
    // 1. 检查Family是否被撤销（重放检测）
    // 2. 标记旧Token为已使用（SETNX原子操作）
    // 3. 生成新Token继承Family ID
    // 4. 撤销旧Token
}
```

**Redis Key设计**:
```
token:family:{familyId}:{tokenHash} = "used"  (TTL: 10分钟)
token:revoked:family:{familyId} = "revoked"  (TTL: 7天)
```

---

### 3️⃣ SAML重放防护

**文件**: `SamlProtocolAdapter.java`

```java
// InResponseTo验证
@Override
public Mono<Map<String, Object>> authenticate(...) {
    // 1. 验证InResponseTo对应的Request存在
    // 2. 使用SETNX防止Response ID重放
    // 3. 删除已使用的Request ID
}
```

**Redis Key设计**:
```
saml:request:{requestId} = {...}  (TTL: 5分钟)
saml:response:{responseId} = "processed"  (TTL: 10分钟)
```

---

### 4️⃣ OIDC State验证

**文件**: `OidcProtocolAdapter.java`

```java
// State参数验证
@Override
public Mono<Map<String, Object>> authenticate(...) {
    // 1. 验证state参数存在
    // 2. 从Redis获取state数据
    // 3. 删除已使用的state（一次性）
    // 4. 验证nonce
}
```

**Redis Key设计**:
```
oidc:state:{state} = {"nonce": "...", "callbackUrl": "..."}  (TTL: 5分钟)
```

---

## 🧪 快速测试命令

### Token加密验证
```bash
# 1. 登录获取Token
curl -X POST http://localhost:8080/api/auth/login \
  -d '{"username":"admin","password":"Admin@123"}'

# 2. 检查数据库Token已加密
mysql -e "SELECT access_token_hash, access_token_encrypted FROM oauth2_authorization LIMIT 1;"
```

### Refresh Token轮换测试
```bash
# 1. 刷新Token（成功）
REFRESH_TOKEN="<token>"
curl -X POST http://localhost:8080/api/auth/refresh -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"

# 2. 重放相同Token（应失败）
curl -X POST http://localhost:8080/api/auth/refresh -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
# 预期: {"code":401,"message":"检测到Token重用"}
```

### SAML重放测试
```bash
# 1. 捕获SAMLResponse
SAML_RESP="<captured_response>"

# 2. 第一次提交（成功）
curl -X POST http://localhost:8080/api/sso/saml/callback -d "SAMLResponse=$SAML_RESP"

# 3. 第二次提交（应失败）
curl -X POST http://localhost:8080/api/sso/saml/callback -d "SAMLResponse=$SAML_RESP"
# 预期: {"code":403,"message":"检测到重放攻击"}
```

---

## 📊 监控指标

### 关键安全指标

```prometheus
# Token轮换
security_token_rotation_total
security_token_reuse_detected_total

# SAML安全
saml_security_replay_attempt_total
saml_security_invalid_in_response_to_total

# OIDC安全
oidc_security_invalid_state_total
oidc_security_missing_state_total

# 登录安全
auth_login_failed_total
auth_brute_force_detected_total
```

### 告警规则

```yaml
- alert: TokenReuseDetected
  expr: rate(security_token_reuse_detected_total[5m]) > 0
  severity: critical
  
- alert: SamlReplayAttack
  expr: rate(saml_security_replay_attempt_total[5m]) > 0
  severity: critical
  
- alert: BruteForceAttack
  expr: rate(auth_login_failed_total[1m]) > 10
  severity: high
```

---

## 📁 核心文件清单

### 需要修改的文件

```
apex-services-bom/apex-auth-service/
├── application/service/
│   ├── TokenServiceImpl.java           ← 修改：加密存储、轮换
│   └── RefreshTokenRotationService.java ← 新增：Token轮换

apex-infrastructure/
└── encryption/
    └── TokenEncryptionService.java      ← 新增：Token加密

apex-components-parent/apex-system/
└── support/sso/
    ├── SamlProtocolAdapter.java         ← 修改：重放防护
    └── OidcProtocolAdapter.java         ← 修改：State验证

apex-components-parent/apex-system/
└── resources/db/migration/mysql/
    └── V42__add_token_encryption.sql    ← 新增：数据库迁移
```

### 配置文件

```
src/main/resources/
├── application.yml                      ← 修改：外部化配置
├── application-prod.yml                 ← 新增：生产配置
└── bootstrap.yml                        ← 新增：配置中心
```

---

## 🚀 快速启动修复

### Step 1: 准备环境
```bash
# 1. 创建feature分支
git checkout -b feature/security-fixes

# 2. 确保Redis运行
podman-compose -f podman-compose.infra.yml up -d redis

# 3. 运行现有测试
./mvnw clean test
```

### Step 2: 实施P0修复
```bash
# 按顺序执行
# Day 1-2: Token加密
# Day 3-5: Refresh轮换
# Day 6-7: SAML重放
# Day 8: OIDC State
# Day 9-10: JWT密钥
```

### Step 3: 验证测试
```bash
# 运行安全测试套件
./mvnw test -Dtest=*SecurityTest

# 运行集成测试
./mvnw verify -Pit

# 运行渗透测试
./scripts/security-pentest.sh
```

### Step 4: 部署上线
```bash
# 1. 合并到develop
git checkout develop
git merge feature/security-fixes

# 2. 部署到测试环境
./scripts/deploy-test.sh

# 3. 生产部署
./scripts/deploy-prod.sh
```

---

## 🔗 相关链接

- 📖 [完整评审报告](./auth-security-review-report.md)
- 🛠️ [修复实施计划](./auth-fix-implementation-plan.md)
- 📋 [评审总结](./README.md)

---

## 📞 紧急联系

| 角色 | 联系方式 |
|-----|---------|
| 安全负责人 | security-lead@apex.com |
| 技术总监 | cto@apex.com |
| 24x7值班 | oncall@apex.com / +86-xxx-xxxx-xxxx |

---

## ✅ 检查清单

### 开发人员自检

- [ ] 我理解了9个必须修复的问题
- [ ] 我知道如何测试Token加密
- [ ] 我知道如何测试Token轮换
- [ ] 我知道如何测试SAML重放防护
- [ ] 我知道Redis缓存的Key设计
- [ ] 我配置了监控告警

### Code Review检查

- [ ] Token确实已加密存储
- [ ] Refresh Token轮换逻辑正确
- [ ] SAML InResponseTo验证完整
- [ ] OIDC State验证通过
- [ ] 没有硬编码敏感配置
- [ ] 审计日志记录完整
- [ ] 单元测试覆盖率 > 80%

### 上线前检查

- [ ] 所有P0问题已修复
- [ ] 所有P1问题已修复
- [ ] 安全测试全部通过
- [ ] 性能测试满足要求
- [ ] 监控告警配置完成
- [ ] 回滚方案已准备

---

**最后更新**: 2026-09-25 18:45 CST  
**版本**: v1.0  
**状态**: ✅ 评审完成，待修复实施

