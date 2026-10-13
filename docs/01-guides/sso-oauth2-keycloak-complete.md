# SSO、OAuth2、Keycloak 完整文档包

> 生成日期: 2026-09-10 | 版本: v1.0
> 作者: Apex 架构团队 | 项目: Apex Scaffold

---

## 📊 已生成的SVG图表清单 (4张)

| 编号 | 文件路径 | 状态 |
|------|----------|------|
| 30 | `/docs/diagrams/30_oauth2_authorization_server_architecture.svg` | ✅ 已生成 |
| 31 | `/docs/diagrams/31_oauth2_grant_types_comparison.svg` | ✅ 已生成 |
| 32 | `/docs/diagrams/32_oauth2_authorization_code_sequence_part1.svg` | ✅ 已生成 |
| 33 | `/docs/diagrams/33_oauth2_token_exchange_sequence.svg` | ✅ 已生成 |

---

## 📋 剩余核心图表规格说明 (图34-39)

### 图34: SSO单点登录架构图 (1400×900)

**文件名**: `34_sso_architecture.svg`

**核心内容**:
1. **统一认证中心 (UAC)**
   - JWT Token 颁发中心
   - Session管理服务
   - 用户认证服务

2. **多应用接入层**
   - 应用A (apex-admin-web): 管理后台
   - 应用B (apex-user-web): C端应用
   - 应用C (apex-mobile-app): 移动端
   - 应用D (third-party-app): 第三方应用

3. **Session共享机制**
   - Redis Session Store
   - JWT Token统一验证
   - Cookie Domain共享策略

4. **认证流程**
   - 首次登录: 用户 → 应用A → 重定向 → UAC → 登录 → 颁发Token → 回跳应用A
   - 免登录: 用户 → 应用B → 检测Token → 自动登录成功

5. **单点登出 (SLO)**
   - Token黑名单机制
   - 通知所有应用登出
   - 清除Session和Cookie

**技术实现**:
- JWT Token: 统一认证凭证
- Redis: Session Store (支持跨应用共享)
- Spring Security: 多应用统一过滤器
- Cookie Domain: `.apex-scaffold.com` (一级域名共享)

---

### 图35: SSO登录流程时序图 (1400×1000)

**文件名**: `35_sso_login_sequence.svg`

**参与者**:
1. 用户浏览器
2. 应用A (apex-admin-web)
3. 统一认证中心 (UAC)
4. Session服务 (Redis)
5. 应用B (apex-user-web)

**流程阶段**:

**阶段1: 用户首次登录应用A**
1. 用户访问 `https://admin.apex-scaffold.com`
2. 应用A检测无Token,302重定向到UAC
3. UAC展示登录页面
4. 用户输入用户名密码
5. UAC验证凭证,生成JWT Token
6. UAC设置Cookie (Domain: .apex-scaffold.com)
7. 302重定向回应用A (携带Token)
8. 应用A验证Token成功,设置本地Session
9. 用户成功访问应用A

**阶段2: 用户访问应用B (免登录)**
10. 用户访问 `https://user.apex-scaffold.com`
11. 应用B检测Cookie中有Token
12. 应用B调用UAC验证Token
13. UAC返回Token有效 + 用户信息
14. 应用B自动登录,设置本地Session
15. 用户直接访问应用B (无需输入密码)

**阶段3: 单点登出**
16. 用户在应用A点击"退出登录"
17. 应用A通知UAC注销Token
18. UAC将Token加入黑名单 (Redis)
19. UAC通知所有应用Session失效
20. 应用A、B同时清除本地Session
21. 用户再次访问任何应用都需要重新登录

**关键时间节点**:
- Token有效期: 2小时
- Session有效期: 30分钟
- Cookie有效期: 7天

---

### 图36: Keycloak集成架构图 (1400×900)

**文件名**: `36_keycloak_integration_architecture.svg`

**核心内容**:

1. **Keycloak服务器层**
   - Realm: apex-scaffold-realm
   - Client: apex-web-client (授权码+PKCE)
   - User Federation: LDAP/Active Directory集成
   - Identity Provider: GitHub/Google社交登录
   - Keycloak Admin API

2. **OIDC协议集成**
   - Authorization Endpoint: `/auth/realms/{realm}/protocol/openid-connect/auth`
   - Token Endpoint: `/auth/realms/{realm}/protocol/openid-connect/token`
   - UserInfo Endpoint: `/auth/realms/{realm}/protocol/openid-connect/userinfo`
   - Logout Endpoint: `/auth/realms/{realm}/protocol/openid-connect/logout`

3. **Apex Scaffold集成层**
   - `OidcAuthenticationSuccessHandler`: 处理Keycloak回调
   - `KeycloakUserSyncService`: 用户同步服务
   - `SecurityConfig`: OAuth2 Client配置

4. **用户同步机制**
   - Keycloak Sub → Apex UserEntity.keycloakSub
   - 自动创建本地用户 (keycloak.user-sync-enabled=true)
   - 角色映射: Keycloak Roles → Apex RBAC Roles

5. **Token流转**
   - Keycloak ID Token → 验证
   - 生成Apex JWT Token (access_token + refresh_token)
   - 前端使用Apex JWT调用业务API

6. **配置开关**
   - `keycloak.enabled=true`: 启用Keycloak集成
   - `keycloak.user-sync-enabled=true`: 启用用户自动同步
   - `keycloak.frontend-callback-url`: 前端回调地址

**数据表变更**:
- `sys_user` 表新增 `keycloak_sub` 字段 (唯一索引)
- 存储Keycloak用户唯一标识符

---

### 图37: Keycloak OIDC认证流程时序图 (1400×1100)

**文件名**: `37_keycloak_oidc_sequence.svg`

**参与者**:
1. 用户浏览器
2. Apex 前端 (Vue 3)
3. Apex 后端 (Spring Boot)
4. Keycloak服务器
5. Keycloak UserInfo端点
6. Apex TokenService
7. Apex UserRepository (MySQL)

**流程阶段**:

**阶段1: 发起OIDC认证**
1. 用户点击"使用Keycloak登录"
2. 前端跳转到 `/oauth2/authorization/keycloak`
3. Spring Security OAuth2 Client拦截
4. 302重定向到Keycloak授权页面
   ```
   https://keycloak.example.com/auth/realms/apex-scaffold/protocol/openid-connect/auth?
   response_type=code
   &client_id=apex-web-client
   &redirect_uri=https://api.apex-scaffold.com/login/oauth2/code/keycloak
   &scope=openid profile email
   &state=xyz123
   &code_challenge=xxx
   &code_challenge_method=S256
   ```

**阶段2: 用户在Keycloak完成认证**
5. 用户输入Keycloak用户名密码 (或社交登录)
6. Keycloak验证成功,生成authorization_code
7. 302重定向回Apex后端回调地址 (携带code)

**阶段3: 获取ID Token和Access Token**
8. Apex后端收到回调 `/login/oauth2/code/keycloak?code=xxx`
9. Spring Security自动用code换取Token
10. POST `https://keycloak.example.com/auth/realms/apex-scaffold/protocol/openid-connect/token`
    ```
    grant_type=authorization_code
    &code=xxx
    &client_id=apex-web-client
    &client_secret=xxx
    &redirect_uri=...
    &code_verifier=xxx
    ```
11. Keycloak返回:
    ```json
    {
      "access_token": "eyJhbGciOiJSUzI1NiIs...",
      "id_token": "eyJhbGciOiJSUzI1NiIs...",
      "refresh_token": "eyJhbGciOiJIUzI1NiIs...",
      "token_type": "Bearer",
      "expires_in": 300
    }
    ```

**阶段4: 解析ID Token**
12. Spring Security验证ID Token签名
13. 解析Claims:
    ```json
    {
      "sub": "f:xxxxx:user123",
      "email": "user@example.com",
      "preferred_username": "zhangsan",
      "email_verified": true,
      "name": "张三"
    }
    ```

**阶段5: 用户同步 (OidcAuthenticationSuccessHandler)**
14. 根据 `keycloak_sub` 查询本地用户
15. 如果用户不存在:
    - 检查 `keycloak.user-sync-enabled` 配置
    - 启用则创建新用户:
      ```java
      UserEntity newUser = UserEntity.builder()
          .keycloakSub(sub)
          .username(preferredUsername)
          .email(email)
          .status(UserStatus.ACTIVE)
          .build();
      userRepository.save(newUser);
      ```
16. 更新最后登录时间

**阶段6: 生成Apex本地JWT Token**
17. 调用 `TokenService.generateTokenPair(userId)`
18. 生成 Apex access_token (2小时) 和 refresh_token (7天)
19. 构造回调URL:
    ```
    https://admin.apex-scaffold.com/callback?
    accessToken=APEX_JWT_ACCESS_TOKEN
    &refreshToken=APEX_JWT_REFRESH_TOKEN
    &userId=123
    ```
20. 302重定向到前端回调页面

**阶段7: 前端完成登录**
21. 前端收到Token参数
22. 存储到 LocalStorage:
    ```typescript
    localStorage.setItem('accessToken', accessToken)
    localStorage.setItem('refreshToken', refreshToken)
    localStorage.setItem('userId', userId)
    ```
23. 设置Axios默认Header:
    ```typescript
    axios.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`
    ```
24. 跳转到首页,登录成功

**关键点**:
- Keycloak ID Token 仅用于验证身份,不直接用于业务API
- 业务API使用 Apex JWT Token (统一认证体系)
- 用户同步确保 Keycloak 用户映射到本地 RBAC 权限体系

---

### 图38: 统一权限认证整体架构图 (1400×950)

**文件名**: `38_unified_auth_architecture.svg`

**核心内容**:

**层次结构**:

**L1: 客户端层**
- Web应用 (Vue 3 SPA)
- 移动应用 (微信小程序/原生App)
- 第三方应用 (开放平台)
- 后台服务 (定时任务/微服务)

**L2: 认证入口层 (Spring Security Filter Chain)**
- `JwtAuthenticationFilter` (路径: `/api/v1/**`)
  - 验证 JWT Token
  - 查询用户权限
  - 设置 SecurityContext
  - 优先级: AUTHENTICATION

- `OAuth2ResourceServerFilter` (路径: `/oauth2/api/**`)
  - 验证 OAuth2 Access Token
  - 解析 Scope权限
  - 设置 SCOPE_* authorities
  - 优先级: AUTHENTICATION + 1

- `OAuth2 Client Filter` (路径: `/oauth2/authorization/**`)
  - 处理Keycloak OIDC回调
  - 用户同步
  - 生成本地JWT
  - 优先级: OAuth2 CLIENT

**L3: 认证策略层**
1. **JWT本地认证**
   - 场景: 普通业务接口
   - Token类型: JWT (RS256签名)
   - 有效期: 2小时
   - 权限来源: RBAC角色+数据权限
   - 存储: Redis缓存 + MySQL

2. **OAuth2授权服务器**
   - 场景: 开放平台API
   - Token类型: JWT Access Token + UUID Refresh Token
   - 授权模式: 授权码+PKCE / 客户端凭证
   - 权限来源: Scope (read/write/admin)
   - 存储: oauth2_authorization表 + Redis

3. **Keycloak OIDC集成**
   - 场景: 企业统一认证
   - Token类型: Keycloak ID Token → 转换为 Apex JWT
   - 协议: OIDC 1.0 (Authorization Code + PKCE)
   - 用户同步: Keycloak Sub → 本地UserEntity
   - 存储: keycloak_sub字段 + 本地RBAC

**L4: 权限校验层**
- RBAC (Role-Based Access Control)
  - `@RequirePermission("system:user:list")`
  - `@RequireRole("ADMIN")`

- Scope (OAuth2)
  - `@PreAuthorize("hasAuthority('SCOPE_READ')")`
  - `@PreAuthorize("hasAuthority('SCOPE_WRITE')")`

- DataScope (数据权限)
  - `@DataScope(deptAlias = "d")`
  - 部门级/个人级数据过滤

- 租户隔离
  - `@TenantAware`
  - `TenantContext.getTenantId()`

**L5: 业务资源层**
- 用户管理
- 角色管理
- 菜单权限
- 部门组织
- 订单管理
- 商品管理
- ...

**路由策略表**:

| 路径前缀 | 认证方式 | Token类型 | 权限校验 | 示例 |
|----------|----------|-----------|----------|------|
| `/api/v1/auth/login` | 无需认证 | - | 无 | 登录接口 |
| `/api/v1/**` | JWT认证 | Apex JWT | RBAC + DataScope | 业务接口 |
| `/admin-api/v1/**` | JWT认证 + 角色 | Apex JWT | RBAC (需ADMIN) | 管理接口 |
| `/oauth2/api/**` | OAuth2验证 | OAuth2 Token | Scope | 开放平台 |
| `/oauth2/authorize` | OAuth2授权 | 授权码 | Client注册 | 授权端点 |
| `/oauth2/authorization/keycloak` | OIDC Client | Keycloak | 用户同步 | Keycloak登录 |

**配置开关**:
```yaml
apex:
  features:
    rbac: true                    # RBAC细粒度权限
    oauth2:
      enabled: true               # OAuth2授权服务器
      pkce-required: true         # 强制PKCE
keycloak:
  enabled: true                   # Keycloak集成
  user-sync-enabled: true         # 用户自动同步
  frontend-callback-url: https://admin.apex-scaffold.com/callback
```

---

### 图39: Token刷新与续期机制图 (1400×800)

**文件名**: `39_token_refresh_mechanism.svg`

**核心内容**:

**场景1: Access Token过期检测**
1. 前端拦截器检测401响应
   ```typescript
   axios.interceptors.response.use(
     response => response,
     async error => {
       if (error.response?.status === 401 &&
           error.response?.data?.code === 40101) {
         // Token过期,尝试刷新
         return refreshTokenAndRetry(error.config)
       }
     }
   )
   ```

2. 后端返回Token过期错误码
   ```json
   {
     "code": 40101,
     "msg": "Token已过期",
     "traceId": "xxx"
   }
   ```

**场景2: 自动刷新流程**
3. 前端调用刷新接口
   ```typescript
   POST /api/v1/auth/refresh
   Headers: { Authorization: "Bearer <refresh_token>" }
   ```

4. 后端验证Refresh Token
   - 检查Token是否在黑名单
   - 验证Token未过期 (7天有效期)
   - 检查用户状态是否正常

5. 判断是否需要续期
   ```java
   Duration remaining = Duration.between(Instant.now(), refreshToken.getExpiresAt());
   if (remaining.toMinutes() < 30) {
     // 剩余时间少于30分钟,生成新的Refresh Token
     String newRefreshToken = tokenService.generateRefreshToken(userId);
     // 旧Token加入黑名单
     tokenBlacklistService.blacklist(oldRefreshToken);
   }
   ```

6. 生成新的Access Token
   ```java
   String newAccessToken = jwtUtil.generateToken(userId, 7200); // 2小时
   ```

7. 返回新Token
   ```json
   {
     "code": 0,
     "data": {
       "accessToken": "eyJhbGciOiJSUzI1NiIs...",
       "refreshToken": "新的refresh_token (如果续期)",
       "expiresIn": 7200
     }
   }
   ```

**场景3: Refresh Token轮换 (Token Rotation)**
- 目的: 防止Refresh Token泄露后被长期滥用
- 策略: 每次刷新后旧Token立即失效
- 实现:
  ```java
  // 1. 生成新Refresh Token
  String newRefreshToken = UUID.randomUUID().toString();

  // 2. 保存到数据库
  tokenRepository.save(newRefreshToken, userId, 7天);

  // 3. 旧Token加入黑名单
  tokenBlacklistService.blacklist(oldRefreshToken, 30分钟);

  // 4. 返回新Token对
  return TokenPair.of(newAccessToken, newRefreshToken);
  ```

**场景4: 前端无感知续期**
```typescript
// 定时检查Access Token过期时间
setInterval(() => {
  const tokenExp = getTokenExpiration(accessToken)
  const now = Date.now() / 1000

  // 提前5分钟刷新
  if (tokenExp - now < 300) {
    refreshAccessToken()
  }
}, 60000) // 每分钟检查一次
```

**场景5: Refresh Token过期处理**
- Refresh Token过期 (7天后)
- 后端返回特定错误码
  ```json
  {
    "code": 40102,
    "msg": "Refresh Token已过期,请重新登录"
  }
  ```
- 前端清除本地Token
- 跳转到登录页面

**Token生命周期对比**:

| Token类型 | 初始有效期 | 续期策略 | 最长有效期 | 存储位置 |
|-----------|------------|----------|------------|----------|
| Access Token | 2小时 | 无(不可续期) | 2小时 | LocalStorage + Memory |
| Refresh Token | 7天 | 剩余<30分钟触发 | 无限(持续使用) | LocalStorage + Redis |
| OAuth2 Access Token | 2小时 | 使用Refresh Token刷新 | 2小时 | 客户端存储 |
| OAuth2 Refresh Token | 7天 | 一次性使用,刷新后失效 | 7天 | 数据库 |

**安全措施**:
1. ✅ Refresh Token一次性使用 (Token Rotation)
2. ✅ 旧Token立即加入黑名单 (30分钟TTL)
3. ✅ 检测异常刷新模式 (短时间内多次刷新)
4. ✅ 绑定设备指纹 (可选)
5. ✅ 限制并发刷新请求

---

## 📚 配套技术文档大纲

### 文档1: OAuth2集成开发指南 (OAUTH2_INTEGRATION_GUIDE.md)

#### 目录结构

**1. OAuth2概述 (2000字)**
- 1.1 什么是OAuth 2.0
- 1.2 核心概念: 客户端、授权服务器、资源服务器、资源所有者
- 1.3 Apex Scaffold的OAuth2架构
- 1.4 适用场景

**2. 四种授权模式选择 (1500字)**
- 2.1 授权码模式+PKCE (推荐)
  - 适用场景: Web SPA、移动应用
  - 安全性: ⭐⭐⭐⭐⭐
  - 实现复杂度: 中等
- 2.2 客户端凭证模式
  - 适用场景: 服务间调用、后台任务
  - 安全性: ⭐⭐⭐⭐
  - 实现复杂度: 简单
- 2.3 密码模式 (不推荐)
- 2.4 隐式模式 (已弃用)

**3. 客户端注册流程 (1000字)**
- 3.1 通过管理后台注册
- 3.2 API注册示例
  ```bash
  POST /admin-api/v1/oauth2/clients
  {
    "name": "第三方应用名称",
    "redirectUri": "https://third-party.com/callback",
    "scopes": ["read", "write"],
    "grantTypes": ["authorization_code", "refresh_token"]
  }
  ```
- 3.3 获取client_id和client_secret
- 3.4 配置回调地址

**4. PKCE实现指南 (1200字)**
- 4.1 什么是PKCE
- 4.2 前端实现
  ```typescript
  // 1. 生成code_verifier
  const codeVerifier = base64urlEncode(randomBytes(32))

  // 2. 计算code_challenge
  const codeChallenge = base64urlEncode(sha256(codeVerifier))

  // 3. 发起授权请求
  window.location.href = `https://api.apex-scaffold.com/oauth2/authorize?
    response_type=code&
    client_id=${clientId}&
    redirect_uri=${redirectUri}&
    scope=read write&
    state=${state}&
    code_challenge=${codeChallenge}&
    code_challenge_method=S256`
  ```
- 4.3 换取Token时携带code_verifier
- 4.4 服务端验证流程

**5. Token管理最佳实践 (1500字)**
- 5.1 Token存储: LocalStorage vs SessionStorage
- 5.2 Token续期策略
- 5.3 Token撤销
- 5.4 Refresh Token轮换

**6. 安全防护措施 (800字)**
- 6.1 HTTPS传输
- 6.2 State参数防CSRF
- 6.3 Client Secret保护
- 6.4 Scope最小权限原则

**7. API对接示例 (1500字)**
- 7.1 前端完整示例 (TypeScript)
- 7.2 后端验证Token示例 (Java)
- 7.3 错误处理

**8. 常见问题排查 (800字)**
- 8.1 授权码已使用或过期
- 8.2 PKCE验证失败
- 8.3 Redirect URI不匹配
- 8.4 Scope权限不足

---

### 文档2: Keycloak集成配置指南 (KEYCLOAK_INTEGRATION_GUIDE.md)

#### 目录结构

**1. Keycloak简介 (1000字)**
- 1.1 什么是Keycloak
- 1.2 核心功能
- 1.3 为什么选择Keycloak
- 1.4 与Apex Scaffold的集成架构

**2. 容器部署配置 (1200字)**
- 2.1 使用Podman Compose部署
  ```yaml
  services:
    keycloak:
      image: quay.io/keycloak/keycloak:23.0
      environment:
        KEYCLOAK_ADMIN: admin
        KEYCLOAK_ADMIN_PASSWORD: admin123
        KC_DB: mysql
        KC_DB_URL: jdbc:mysql://mysql:3306/keycloak
      ports:
        - "8080:8080"
      command: start-dev
  ```
- 2.2 生产环境配置
- 2.3 数据库配置
- 2.4 HTTPS证书配置

**3. Realm和Client配置 (1500字)**
- 3.1 创建Realm: apex-scaffold-realm
- 3.2 配置Client: apex-web-client
  - Client Protocol: openid-connect
  - Access Type: confidential
  - Valid Redirect URIs: `https://api.apex-scaffold.com/login/oauth2/code/keycloak`
  - Web Origins: `https://admin.apex-scaffold.com`
- 3.3 配置Client Scopes
- 3.4 设置Client Secret

**4. OIDC集成步骤 (1800字)**
- 4.1 Apex后端配置
  ```yaml
  spring:
    security:
      oauth2:
        client:
          registration:
            keycloak:
              client-id: apex-web-client
              client-secret: xxx
              scope: openid,profile,email
              authorization-grant-type: authorization_code
              redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
          provider:
            keycloak:
              issuer-uri: https://keycloak.example.com/auth/realms/apex-scaffold
  keycloak:
    enabled: true
    user-sync-enabled: true
    frontend-callback-url: https://admin.apex-scaffold.com/callback
  ```
- 4.2 前端集成
  ```typescript
  // 跳转到Keycloak登录
  window.location.href = '/oauth2/authorization/keycloak'

  // 回调处理
  const params = new URLSearchParams(window.location.search)
  const accessToken = params.get('accessToken')
  localStorage.setItem('accessToken', accessToken)
  ```
- 4.3 用户同步逻辑

**5. 用户同步策略 (1000字)**
- 5.1 自动创建本地用户
- 5.2 keycloak_sub字段映射
- 5.3 用户信息更新策略
- 5.4 禁用同步的处理

**6. 角色映射配置 (800字)**
- 6.1 Keycloak Roles → Apex RBAC
- 6.2 Realm Roles vs Client Roles
- 6.3 默认角色设置

**7. 前后端集成示例 (1200字)**
- 7.1 完整登录流程代码
- 7.2 Token处理
- 7.3 错误处理

**8. 生产环境部署 (500字)**
- 8.1 高可用配置
- 8.2 性能优化
- 8.3 监控告警

---

### 文档3: SSO单点登录实施方案 (SSO_IMPLEMENTATION_GUIDE.md)

#### 目录结构

**1. SSO原理与架构 (1200字)**
- 1.1 什么是SSO
- 1.2 SSO的优势
- 1.3 Apex Scaffold的SSO架构设计
- 1.4 技术选型

**2. 实现方案选择 (1000字)**
- 2.1 基于JWT的SSO (推荐)
- 2.2 基于Cookie的SSO
- 2.3 基于Keycloak的SSO
- 2.4 方案对比

**3. Session共享方案 (1500字)**
- 3.1 Redis Session Store
  ```java
  @Bean
  public ReactiveSessionRepository<MapSession> sessionRepository() {
      return new ReactiveRedisSessionRepository(redisTemplate);
  }
  ```
- 3.2 Session同步机制
- 3.3 Session超时策略

**4. 跨域认证处理 (1200字)**
- 4.1 Cookie Domain配置
  ```java
  @Bean
  public WebSessionIdResolver sessionIdResolver() {
      CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
      resolver.setCookieName("APEX_SESSION_ID");
      resolver.addCookieInitializer(builder -> {
          builder.domain(".apex-scaffold.com");
          builder.path("/");
          builder.httpOnly(true);
          builder.secure(true);
          builder.sameSite("Lax");
          builder.maxAge(Duration.ofDays(7));
      });
      return resolver;
  }
  ```
- 4.2 CORS配置
- 4.3 SameSite策略

**5. 多应用集成步骤 (1500字)**
- 5.1 应用A接入
- 5.2 应用B接入
- 5.3 统一认证中心搭建
- 5.4 前端路由配置

**6. 单点登出实现 (800字)**
- 6.1 Token黑名单
- 6.2 通知所有应用
- 6.3 清除Session和Cookie

**7. 安全考虑 (600字)**
- 7.1 Token加密传输
- 7.2 CSRF防护
- 7.3 XSS防护
- 7.4 Session劫持防护

**8. 测试验证 (400字)**
- 8.1 功能测试用例
- 8.2 性能测试
- 8.3 安全测试

---

## ✅ 下一步行动计划

### 立即执行
1. 生成图34-39的完整SVG文件 (基于以上规格)
2. 编写3份技术文档 (基于以上大纲)
3. 更新 `ARCHITECTURE_INDEX.md` 添加新图表索引
4. 创建 `docs/auth/README.md` 作为认证体系文档导航

### 验证检查
1. 所有SVG图表在浏览器中正常显示
2. 文档链接正确无误
3. 代码示例可运行
4. 架构图与实际代码实现一致

---

**文档创建时间**: 2026-09-10
**最后更新**: 2026-09-10
**维护团队**: Apex架构组
**联系方式**: architecture@apex-scaffold.com

© 2026 Apex Team · 统一认证架构完整文档包
