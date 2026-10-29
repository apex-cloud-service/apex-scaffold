# SAML 签名算法配置指南

## 概述

本文档说明如何为SAML SSO集成配置灵活的签名算法，以适配不同IdP的安全要求和兼容性需求。

## 支持的签名算法

| 算法 | 安全级别 | 推荐场景 | 说明 |
|------|---------|---------|------|
| **SHA256withRSA** | ⭐⭐⭐⭐ 推荐 | 生产环境默认 | 平衡安全性和性能，业界标准 |
| **SHA384withRSA** | ⭐⭐⭐⭐⭐ 高安全 | 金融、医疗等高安全场景 | 更强的安全性，略微影响性能 |
| **SHA512withRSA** | ⭐⭐⭐⭐⭐ 最高安全 | 政府、军工等极高安全场景 | 最强安全性，性能开销较大 |
| **SHA1withRSA** | ⚠️ 不推荐 | 仅用于兼容旧系统 | 已知安全漏洞，仅用于兼容 |

## 配置方法

### 1. 数据库配置（推荐）

在 `sso_provider` 表的 `config` JSON字段中添加 `signatureAlgorithm` 配置项：

```json
{
  "entityId": "https://your-sp.example.com",
  "authEndpoint": "https://idp.example.com/sso/saml",
  "acsUrl": "https://your-sp.example.com/api/sso/callback",
  "certificate": "MIIDXTCCAkWgAwIBAgI...",
  "signatureAlgorithm": "SHA256withRSA"
}
```

### 2. 不同IdP的推荐配置

#### 2.1 Okta

```json
{
  "signatureAlgorithm": "SHA256withRSA"
}
```

Okta默认使用SHA256withRSA，兼容性最佳。

#### 2.2 Azure AD (Entra ID)

```json
{
  "signatureAlgorithm": "SHA256withRSA"
}
```

Azure AD支持SHA256/SHA384/SHA512，推荐使用SHA256。

#### 2.3 Google Workspace

```json
{
  "signatureAlgorithm": "SHA256withRSA"
}
```

Google仅支持SHA256withRSA。

#### 2.4 Keycloak

```json
{
  "signatureAlgorithm": "SHA512withRSA"
}
```

Keycloak默认使用SHA512，可配置降级到SHA256。

#### 2.5 旧版ADFS (Windows Server 2012 R2及以下)

```json
{
  "signatureAlgorithm": "SHA1withRSA"
}
```

⚠️ **安全警告**: SHA1已被证明存在碰撞攻击漏洞，仅用于兼容无法升级的旧系统。
**强烈建议升级ADFS到Windows Server 2016+并使用SHA256。**

### 3. 默认行为

如果未配置 `signatureAlgorithm`，系统将自动使用 **SHA256withRSA** 作为默认算法。

```json
{
  "entityId": "https://your-sp.example.com",
  "authEndpoint": "https://idp.example.com/sso/saml",
  "acsUrl": "https://your-sp.example.com/api/sso/callback",
  "certificate": "MIIDXTCCAkWgAwIBAgI..."
  // signatureAlgorithm 未配置，自动使用 SHA256withRSA
}
```

## 安全最佳实践

### ✅ 推荐做法

1. **生产环境使用SHA256或更高**
   ```json
   {
     "signatureAlgorithm": "SHA256withRSA"  // 或 SHA384withRSA、SHA512withRSA
   }
   ```

2. **定期审查算法配置**
   - 每年检查一次IdP的签名算法配置
   - 关注NIST等安全机构的算法推荐更新
   - 测试升级到更强算法的兼容性

3. **监控SHA1使用**
   - 系统会自动记录SHA1使用的WARNING日志
   - 设置告警规则监控SHA1使用情况
   - 制定SHA1淘汰计划

### ❌ 禁止做法

1. **不要在生产环境新部署时使用SHA1**
   ```json
   {
     "signatureAlgorithm": "SHA1withRSA"  // ❌ 禁止新系统使用
   }
   ```

2. **不要使用不受支持的算法**
   ```json
   {
     "signatureAlgorithm": "MD5withRSA"  // ❌ 系统会拒绝并回退到SHA256
   }
   ```

## 故障排查

### 问题1: 签名验证失败

**错误信息**:
```
SecurityException: SAML 签名验证失败: 签名算法=SHA256withRSA
```

**可能原因**:
1. IdP使用的签名算法与SP配置不匹配
2. IdP证书不正确或已过期
3. SAML Response在传输过程中被修改

**解决方案**:
1. 检查IdP管理控制台的签名算法配置
2. 导出IdP最新证书并更新到SP配置
3. 尝试切换到IdP推荐的签名算法

### 问题2: 不支持的算法

**错误信息**:
```
IllegalArgumentException: 不支持的签名算法: SHA1withDSA, 支持的算法: SHA256withRSA, SHA384withRSA, SHA512withRSA, SHA1withRSA
```

**解决方案**:
系统仅支持RSA算法族，不支持DSA/ECDSA。请在IdP中配置RSA证书和签名算法。

### 问题3: SHA1安全警告

**日志信息**:
```
WARN: 使用不安全的签名算法SHA1withRSA，建议升级到SHA256withRSA或更高版本
```

**解决方案**:
1. 联系IdP管理员升级到SHA256或更高
2. 如果是ADFS，升级到Windows Server 2016或更高版本
3. 制定SHA1淘汰时间表，限期迁移

## 测试验证

### 1. 验证签名算法配置

使用SSO测试连接接口验证配置：

```bash
POST /api/sso/test-connection
Content-Type: application/json

{
  "providerId": 1
}
```

响应示例：
```json
{
  "success": true,
  "message": "SAML 连接测试成功",
  "details": {
    "entityId": "正常",
    "authEndpoint": "正常",
    "certificate": "证书格式正确",
    "certificateValidity": "有效期至: 2027-12-31T23:59:59Z",
    "signatureAlgorithm": "SHA256withRSA"
  }
}
```

### 2. 端到端认证测试

1. 发起SAML认证请求
2. 在IdP完成登录
3. 检查应用日志，确认签名验证成功：
   ```
   DEBUG: SAML 签名验证成功: 算法=SHA256withRSA
   ```

### 3. 监控指标

在Grafana中监控以下指标：

- `saml_signature_verification_total{algorithm="SHA256withRSA"}` - 签名验证总数
- `saml_signature_verification_failures{algorithm="SHA256withRSA"}` - 验证失败数
- `saml_sha1_usage_count` - SHA1使用次数（应为0）

## 配置变更流程

### 生产环境算法升级步骤

1. **测试环境验证**
   - 在测试环境配置新算法
   - 完成完整的登录/登出测试
   - 验证审计日志正确记录

2. **IdP配置更新**
   - 在IdP中启用新签名算法
   - 导出新证书（如果算法变更）
   - 保持旧算法兼容（灰度期间）

3. **SP配置更新**
   - 更新数据库配置的 `signatureAlgorithm`
   - 更新证书（如果证书变更）
   - 无需重启服务，配置动态生效

4. **监控验证**
   - 监控签名验证成功率
   - 检查错误日志
   - 观察7天无异常

5. **IdP清理**
   - 禁用旧算法
   - 更新IdP元数据

## 合规性参考

- **NIST SP 800-131A**: 禁止SHA1用于数字签名（2013年后）
- **PCI DSS 4.0**: 要求SHA256或更高
- **等保2.0**: 推荐使用SHA256及以上算法
- **GDPR**: 要求使用"最先进"的加密技术

## 参考资料

- [SAML 2.0 Technical Overview](https://docs.oasis-open.org/security/saml/Post2.0/sstc-saml-tech-overview-2.0.html)
- [NIST SP 800-57 Part 1: Key Management](https://csrc.nist.gov/publications/detail/sp/800-57-part-1/rev-5/final)
- [RFC 8017: PKCS #1: RSA Cryptography Specifications](https://tools.ietf.org/html/rfc8017)

---

**文档版本**: 1.0  
**最后更新**: 2026-09-25  
**维护者**: Security Team
