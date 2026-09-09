# OAuth2接入指南

> **项目**: Apex Scaffold - 纯WebFlux OAuth2实现  
> **版本**: 2.0.0  
> **更新时间**: 2026-09-08

---

## 📋 实现概述

基于纯响应式WebFlux架构，完全自主实现OAuth2标准协议，支持4种授权模式。

### ✅ 已实现功能

- ✅ **授权码模式** (Authorization Code + PKCE)
- ✅ **客户端凭证模式** (Client Credentials)
- ✅ **密码模式** (Resource Owner Password)
- ✅ **刷新令牌** (Refresh Token)
- ✅ **JWT Token签发与验证**
- ✅ **OAuth2 Scope权限控制**
- ✅ **客户端管理CRUD接口**
- ✅ **Token撤销与自省**

---

## 🚀 快速开始

### 1. 数据库初始化

项目已内置Flyway迁移脚本（V25），启动时自动执行：
- `oauth2_registered_client` - OAuth2客户端注册表
- `oauth2_authorization` - 授权记录表
- `oauth2_authorization_consent` - 用户授权同意表
- `oauth2_scope_permission` - Scope权限映射表

### 2. 内置测试客户端

#### 前端管理后台 (apex-admin-web)
```
Client ID: apex-admin-web
Client Secret: apex-admin-web-secret-2026
授权类型: authorization_code, refresh_token
重定向URI: http://localhost:5173/oauth2/callback
Scopes: read, write, user:profile, admin:users
```

#### M2M服务客户端 (apex-m2m-service)
```
Client ID: apex-m2m-service
Client Secret: apex-m2m-secret-2026
授权类型: client_credentials
Scopes: api:read, api:write
```

#### 移动应用 (apex-mobile-app)
```
Client ID: apex-mobile-app
Client Secret: (无,公共客户端)
授权类型: authorization_code, refresh_token (强制PKCE)
重定向URI: com.apex.app://oauth2/callback
Scopes: read, write, user:profile
```

---

## 📘 OAuth2端点

### 授权端点
```http
GET/POST /oauth2/authorize
```

**请求参数**:
| 参数 | 必填 | 说明 |
|------|------|------|
| response_type | 是 | 固定为"code" |
| client_id | 是 | 客户端ID |
| redirect_uri | 是 | 重定向URI |
| scope | 否 | 权限作用域（空格分隔） |
| state | 推荐 | 状态参数（防CSRF） |
| code_challenge | PKCE | PKCE challenge |
| code_challenge_method | PKCE | S256或plain |

**响应**: 重定向到 `redirect_uri?code=xxx&state=xxx`

### Token端点
```http
POST /oauth2/token
Content-Type: application/x-www-form-urlencoded
```

**请求参数** (根据grant_type不同):

#### 授权码模式
```
grant_type=authorization_code
code=授权码
redirect_uri=重定向URI
client_id=客户端ID
client_secret=客户端密钥
code_verifier=PKCE验证器(PKCE)
```

#### 客户端凭证模式
```
grant_type=client_credentials
client_id=客户端ID
client_secret=客户端密钥
scope=作用域
```

#### 密码模式
```
grant_type=password
username=用户名
password=密码
client_id=客户端ID
client_secret=客户端密钥
scope=作用域
```

#### 刷新令牌
```
grant_type=refresh_token
refresh_token=刷新令牌
client_id=客户端ID
client_secret=客户端密钥
```

**响应示例**:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 7200,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "scope": "read write user:profile"
}
```

### Token撤销端点
```http
POST /oauth2/revoke
Content-Type: application/x-www-form-urlencoded

token=要撤销的Token
client_id=客户端ID
```

### Token自省端点
```http
POST /oauth2/introspect
Content-Type: application/x-www-form-urlencoded

token=要检查的Token
```

**响应示例**:
```json
{
  "active": true,
  "client_id": "apex-admin-web",
  "user_id": "user-openid-123",
  "scope": "read write",
  "exp": 1725766800
}
```

---

## 🔐 客户端管理接口

### 创建客户端
```http
POST /admin-api/v1/oauth2/clients
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

{
  "clientId": "my-app",
  "clientSecret": "my-secret",
  "clientName": "我的应用",
  "clientAuthenticationMethods": ["client_secret_basic"],
  "authorizationGrantTypes": ["authorization_code", "refresh_token"],
  "redirectUris": ["https://myapp.com/callback"],
  "scopes": ["read", "write"],
  "requireProofKey": true,
  "requireAuthorizationConsent": false,
  "accessTokenTimeToLive": 7200,
  "refreshTokenTimeToLive": 604800
}
```

### 更新客户端
```http
PUT /admin-api/v1/oauth2/clients/{clientId}
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json
```

### 删除客户端
```http
DELETE /admin-api/v1/oauth2/clients/{clientId}
Authorization: Bearer {JWT_TOKEN}
```

### 查询客户端
```http
GET /admin-api/v1/oauth2/clients/{clientId}
Authorization: Bearer {JWT_TOKEN}
```

### 客户端列表
```http
GET /admin-api/v1/oauth2/clients?page=1&size=10
Authorization: Bearer {JWT_TOKEN}
```

### 重置客户端密钥
```http
POST /admin-api/v1/oauth2/clients/{clientId}/reset-secret
Authorization: Bearer {JWT_TOKEN}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "clientId": "my-app",
    "clientSecret": "new-generated-secret-xyz"
  }
}
```

---

## 🛡️ 资源保护

### OAuth2资源端点
OAuth2 Token保护的资源端点统一使用`/oauth2/api/`前缀：

```java
// 需要read权限
@PreAuthorize("hasAuthority('SCOPE_READ')")
GET /oauth2/api/v1/users

// 需要user:profile权限
@PreAuthorize("hasAuthority('SCOPE_USER_PROFILE')")
GET /oauth2/api/v1/profile

// 需要admin:users权限
@PreAuthorize("hasAuthority('SCOPE_ADMIN_USERS')")
POST /oauth2/api/v1/users
```

### 访问OAuth2保护的资源
```http
GET /oauth2/api/v1/profile
Authorization: Bearer {OAUTH2_ACCESS_TOKEN}
```

---

## 🎯 完整授权流程示例

### 1. 授权码模式（推荐）

#### Step 1: 引导用户授权
```
GET https://api.apex.com/oauth2/authorize?
  response_type=code&
  client_id=apex-admin-web&
  redirect_uri=https://myapp.com/callback&
  scope=read%20write&
  state=random_state_123&
  code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&
  code_challenge_method=S256
```

#### Step 2: 用户登录并授权
用户在授权页面登录并同意授权后，重定向到：
```
https://myapp.com/callback?code=SplxlOBeZQQYbYS6WxSbIA&state=random_state_123
```

#### Step 3: 授权码换取Token
```bash
curl -X POST https://api.apex.com/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=SplxlOBeZQQYbYS6WxSbIA" \
  -d "redirect_uri=https://myapp.com/callback" \
  -d "client_id=apex-admin-web" \
  -d "client_secret=apex-admin-web-secret-2026" \
  -d "code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
```

#### Step 4: 使用AccessToken访问资源
```bash
curl -X GET https://api.apex.com/oauth2/api/v1/profile \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### 2. 客户端凭证模式（M2M）

```bash
curl -X POST https://api.apex.com/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=apex-m2m-service" \
  -d "client_secret=apex-m2m-secret-2026" \
  -d "scope=api:read api:write"
```

---

## ⚙️ PKCE实现

### 生成Code Verifier和Challenge

```javascript
// 1. 生成Code Verifier (43-128位随机字符串)
const codeVerifier = base64URLEncode(crypto.randomBytes(32));

// 2. 生成Code Challenge
const codeChallenge = base64URLEncode(
  crypto.createHash('sha256')
    .update(codeVerifier)
    .digest()
);

// 3. 授权请求时携带
const authorizeUrl = `https://api.apex.com/oauth2/authorize?` +
  `response_type=code&` +
  `client_id=apex-mobile-app&` +
  `redirect_uri=com.apex.app://oauth2/callback&` +
  `code_challenge=${codeChallenge}&` +
  `code_challenge_method=S256`;

// 4. Token请求时验证
fetch('https://api.apex.com/oauth2/token', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({
    grant_type: 'authorization_code',
    code: authorizationCode,
    redirect_uri: 'com.apex.app://oauth2/callback',
    client_id: 'apex-mobile-app',
    code_verifier: codeVerifier
  })
});
```

---

## 🔄 Token刷新

当AccessToken即将过期时，使用RefreshToken获取新Token：

```bash
curl -X POST https://api.apex.com/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token" \
  -d "refresh_token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -d "client_id=apex-admin-web" \
  -d "client_secret=apex-admin-web-secret-2026"
```

---

## 📊 OAuth2 Scope权限列表

| Scope | 权限标识 | 说明 |
|-------|---------|------|
| read | SCOPE_READ | 读取基本资源 |
| write | SCOPE_WRITE | 写入基本资源 |
| user:profile | SCOPE_USER_PROFILE | 读取用户个人信息 |
| user:update | SCOPE_USER_UPDATE | 更新用户个人信息 |
| user:avatar | SCOPE_USER_AVATAR | 上传和修改用户头像 |
| admin:users | SCOPE_ADMIN_USERS | 管理用户 |
| admin:roles | SCOPE_ADMIN_ROLES | 管理角色权限 |
| admin:system | SCOPE_ADMIN_SYSTEM | 系统管理 |
| api:read | SCOPE_API_READ | API只读访问 |
| api:write | SCOPE_API_WRITE | API读写访问 |
| file:upload | SCOPE_FILE_UPLOAD | 文件上传 |
| file:download | SCOPE_FILE_DOWNLOAD | 文件下载 |

---

## ⚠️ 注意事项

### 安全最佳实践

1. **HTTPS必需**: 生产环境必须使用HTTPS
2. **密钥管理**: 客户端密钥使用BCrypt加密存储
3. **State参数**: 授权码模式必须使用state参数防CSRF
4. **PKCE强制**: 公共客户端（移动应用）强制启用PKCE
5. **Token有效期**: 
   - 授权码: 5分钟
   - AccessToken: 2小时
   - RefreshToken: 7天
6. **Token撤销**: 注销时应主动调用撤销端点

### 与现有JWT认证共存

- **现有JWT登录**: `/api/v1/auth/login` 保持不变
- **OAuth2授权**: `/oauth2/**` 新增端点
- **资源保护**:
  - `/api/v1/**` - JWT Token保护（前端应用）
  - `/oauth2/api/**` - OAuth2 Token保护（第三方应用）

---

## 🐛 故障排查

### 常见错误码

| 错误码 | 说明 | 解决方法 |
|--------|------|---------|
| invalid_client | 客户端ID或密钥错误 | 检查client_id和client_secret |
| invalid_grant | 授权码无效或已过期 | 重新获取授权码 |
| unsupported_grant_type | 不支持的授权类型 | 检查grant_type参数 |
| invalid_scope | 无效的作用域 | 检查客户端注册的scopes |
| invalid_request | 缺少必需参数 | 检查请求参数完整性 |

### 调试建议

1. 检查数据库中的客户端配置
2. 查看应用日志中的OAuth2相关日志
3. 使用Token自省端点验证Token状态
4. 确认Redis缓存正常运行

---

## 📚 扩展阅读

- [RFC 6749 - OAuth 2.0](https://tools.ietf.org/html/rfc6749)
- [RFC 7636 - PKCE](https://tools.ietf.org/html/rfc7636)
- [RFC 7519 - JWT](https://tools.ietf.org/html/rfc7519)

---

**文档版本**: v1.0  
**最后更新**: 2026-09-08  
**技术支持**: Apex Team
