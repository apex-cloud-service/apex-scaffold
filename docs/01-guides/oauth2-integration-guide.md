# OAuth2 集成开发指南

> 版本: v1.0 | 最后更新: 2026-09-11 | 适用版本: apex-scaffold v1.2.0+
>
> 配套图表: [30_oauth2_authorization_server_architecture.svg](../diagrams/30_oauth2_authorization_server_architecture.svg)、[31_oauth2_grant_types_comparison.svg](../diagrams/31_oauth2_grant_types_comparison.svg)、[32_oauth2_authorization_code_sequence_part1.svg](../diagrams/32_oauth2_authorization_code_sequence_part1.svg)、[33_oauth2_token_exchange_sequence.svg](../diagrams/33_oauth2_token_exchange_sequence.svg)

---

## 1. OAuth2 概述

### 1.1 什么是 OAuth 2.0

OAuth 2.0 是一个**授权框架**(Authorization Framework),允许第三方应用在用户授权的情况下,访问该用户在资源服务器上的受保护资源,而无需暴露用户的凭证(用户名/密码)。

**核心角色**:

| 角色 | 英文 | 说明 | Apex Scaffold 对应实现 |
|------|------|------|----------------------|
| 资源所有者 | Resource Owner | 最终用户 | 登录用户 |
| 客户端 | Client | 第三方应用 | 注册的 OAuth2 Client |
| 授权服务器 | Authorization Server | 颁发 Token 的服务器 | `OAuth2AuthorizationHandler` |
| 资源服务器 | Resource Server | 托管受保护资源的服务器 | `OAuth2ResourceServerFilter` + `/oauth2/api/**` |

### 1.2 Apex Scaffold 的 OAuth2 架构

Apex Scaffold 内置了完整的 OAuth2 授权服务器,基于 Spring Security 实现,采用 WebFlux 响应式架构,支持以下特性:

- ✅ **四种授权模式**: 授权码(+PKCE)、隐式(已弃用)、密码(遗留)、客户端凭证
- ✅ **PKCE 安全增强**: 强制公共客户端使用 PKCE(S256)
- ✅ **Token 管理**: JWT Access Token + UUID Refresh Token(轮换机制)
- ✅ **Scope 权限体系**: read / write / admin / user:profile / order:manage
- ✅ **客户端管理**: 后台 API 注册、更新、删除客户端
- ✅ **用户授权同意**: 用户可查看、撤销已授权的客户端
- ✅ **令牌黑名单**: Redis 实现 Token 撤销(30 分钟 TTL)

### 1.3 适用场景

| 场景 | 推荐授权模式 | 说明 |
|------|------------|------|
| Web 单页应用(SPA) | 授权码 + PKCE | 无后端 secret,PKCE 防劫持 |
| 移动应用(iOS/Android) | 授权码 + PKCE | 同上,需注意回调 scheme |
| 微信小程序 | 授权码 + PKCE | 通过后端中转 |
| 服务间调用(M2M) | 客户端凭证 | 无用户上下文 |
| 后台定时任务 | 客户端凭证 | 使用 Service Account |
| 遗留系统迁移 | 密码模式 | 仅过渡用,不推荐 |

---

## 2. 四种授权模式选择

### 2.1 授权码模式 + PKCE(推荐)

**安全性**: ⭐⭐⭐⭐⭐
**实现复杂度**: 中等
**适用场景**: 所有浏览器/移动端应用

**流程概述**:
1. 客户端生成 `code_verifier`(随机字符串)和 `code_challenge`(SHA256 哈希)
2. 客户端将用户重定向到授权端点,携带 `code_challenge`
3. 用户登录并授权
4. 授权服务器返回 `authorization_code`
5. 客户端用 `code` + `code_verifier` 换取 Token
6. 授权服务器验证 `code_verifier` 与 `code_challenge` 匹配后颁发 Token

**为什么需要 PKCE**: 防止授权码在传输过程中被劫持。即使攻击者截获了 `code`,没有 `code_verifier` 也无法换取 Token。

### 2.2 客户端凭证模式

**安全性**: ⭐⭐⭐⭐
**实现复杂度**: 简单
**适用场景**: 服务间调用、后台任务、CLI 工具

**流程概述**:
1. 客户端用 `client_id` + `client_secret`(或 JWT assertion)请求 Token
2. 授权服务器验证后直接颁发 Access Token
3. Token 中不包含用户信息(sub 为 client_id)

### 2.3 密码模式(不推荐)

**安全性**: ⭐⭐
**适用场景**: 仅遗留系统、高度信任的第一方应用

客户端直接收集用户用户名密码换取 Token。违反 OAuth2 的代理授权原则,无法实现 SSO。

### 2.4 隐式模式(已弃用)

**安全性**: ⭐
**状态**: OAuth 2.1 已移除

Token 直接在 URL Fragment 返回,易被 XSS 窃取,无 Refresh Token。应迁移到授权码 + PKCE。

---

## 3. 客户端注册流程

### 3.1 通过管理后台注册

在管理后台 → 系统管理 → OAuth2 客户端管理页面注册新客户端:

**必填字段**:

| 字段 | 说明 | 示例 |
|------|------|------|
| 名称 | 客户端名称 | 第三方数据分析平台 |
| Client ID | 自动生成或自定义 | third-party-analytics |
| Client Secret | 自动生成(只显示一次) | ******** |
| Redirect URI | 回调地址(需完全匹配) | https://analytics.example.com/callback |
| 授权模式 | grant_types | authorization_code, refresh_token |
| Scope | 授权范围 | read, user:profile |
| PKCE 要求 | 是否强制 PKCE | true(公共客户端必选) |

### 3.2 API 注册示例

```http
POST /admin-api/v1/oauth2/clients
Authorization: Bearer <admin_access_token>
Content-Type: application/json

{
  "name": "第三方数据分析平台",
  "clientId": "third-party-analytics",
  "redirectUri": "https://analytics.example.com/callback",
  "scopes": ["read", "user:profile"],
  "grantTypes": ["authorization_code", "refresh_token"],
  "accessTokenValiditySeconds": 7200,
  "refreshTokenValiditySeconds": 604800,
  "pkceRequired": true
}
```

**响应**:

```json
{
  "code": 0,
  "data": {
    "clientId": "third-party-analytics",
    "clientSecret": "生成的密钥(请妥善保存)",
    "redirectUri": "https://analytics.example.com/callback",
    "scopes": ["read", "user:profile"]
  }
}
```

### 3.3 注册客户端的核心实体

`OAuth2RegisteredClientEntity` 关键字段:

```java
// 唯一标识
private String clientId;
private String clientSecret; // BCrypt 加密存储

// 认证方式
private Set<String> grantTypes;       // authorization_code, client_credentials, refresh_token
private Set<String> scopes;           // read, write, admin, ...
private Set<String> redirectUris;     // 回调地址白名单

// 有效期配置
private Long accessTokenValiditySeconds;   // 默认 7200(2小时)
private Long refreshTokenValiditySeconds;  // 默认 604800(7天)

// 安全配置
private Boolean pkceRequired;         // 是否强制 PKCE
private Boolean enabled;              // 是否启用
```

---

## 4. PKCE 实现指南

### 4.1 PKCE 原理

PKCE(Proof Key for Code Exchange)通过在授权请求中携带一个挑战码(`code_challenge`),在换取 Token 时携带原始验证器(`code_verifier`),授权服务器验证两者匹配后才颁发 Token。

### 4.2 前端实现(TypeScript)

```typescript
/**
 * 生成 PKCE 验证器和挑战码
 */
export function generatePKCE(): { codeVerifier: string; codeChallenge: string } {
  // 1. 生成随机 code_verifier (43-128 字符)
  const codeVerifier = base64urlEncode(randomBytes(32));

  // 2. 计算 code_challenge = base64url(SHA256(code_verifier))
  const codeChallenge = base64urlEncode(sha256(codeVerifier));

  return { codeVerifier, codeChallenge };
}

/**
 * 发起 OAuth2 授权请求
 */
export function redirectToAuthorize(): void {
  const { codeVerifier, codeChallenge } = generatePKCE();

  // 保存 code_verifier 到 sessionStorage,换取 Token 时使用
  sessionStorage.setItem('code_verifier', codeVerifier);

  // 生成 state (防 CSRF)
  const state = base64urlEncode(randomBytes(16));
  sessionStorage.setItem('oauth_state', state);

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: 'third-party-analytics',
    redirect_uri: 'https://analytics.example.com/callback',
    scope: 'read user:profile',
    state,
    code_challenge: codeChallenge,
    code_challenge_method: 'S256'
  });

  window.location.href = `https://api.apex-scaffold.com/oauth2/authorize?${params}`;
}

/**
 * 用授权码换取 Token
 */
export async function exchangeCodeForToken(code: string): Promise<TokenPair> {
  const codeVerifier = sessionStorage.getItem('code_verifier');
  const state = sessionStorage.getItem('oauth_state');

  const response = await fetch('https://api.apex-scaffold.com/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      client_id: 'third-party-analytics',
      redirect_uri: 'https://analytics.example.com/callback',
      code_verifier: codeVerifier,
      state
    })
  });

  if (!response.ok) throw new Error('Token exchange failed');
  return response.json();
}
```

### 4.3 工具函数实现

```typescript
/** 生成随机字节数组 */
function randomBytes(length: number): Uint8Array {
  const array = new Uint8Array(length);
  crypto.getRandomValues(array);
  return array;
}

/** Base64URL 编码 */
function base64urlEncode(array: Uint8Array | string): string {
  const str = typeof array === 'string' ? array : String.fromCharCode(...array);
  return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** SHA-256 哈希 */
async function sha256(message: string): Promise<Uint8Array> {
  const encoder = new TextEncoder();
  const data = encoder.encode(message);
  const hash = await crypto.subtle.digest('SHA-256', data);
  return new Uint8Array(hash);
}
```

---

## 5. Token 管理最佳实践

### 5.1 Token 存储

| 存储位置 | Access Token | Refresh Token | 安全性 |
|---------|-------------|---------------|--------|
| LocalStorage | ✅ | ❌ | 易受 XSS 攻击 |
| SessionStorage | ✅ | ❌ | 关闭窗口即失效 |
| HttpOnly Cookie | ✅ | ✅ | 最安全(防 XSS) |
| 内存变量 | ✅ | ❌ | 刷新丢失 |

**推荐方案**: Access Token 存内存 + HttpOnly Cookie 存 Refresh Token

### 5.2 Token 续期策略

**后端滑动窗口续期**(`TokenService.refreshToken`):

```java
public Mono<TokenPair> refreshToken(String refreshToken) {
    return tokenRepository.findByRefreshToken(refreshToken)
        .filter(token -> !token.isExpired())
        .flatMap(token -> {
            // 生成新的 Access Token
            String newAccessToken = jwtUtil.generate(token.getUserId(), 7200);
            Instant now = Instant.now();
            Duration remaining = Duration.between(now, token.getRefreshTokenExpiresAt());

            String newRefreshToken = null;
            // 剩余时间少于 30 分钟,触发续期
            if (remaining.toMinutes() < 30) {
                newRefreshToken = UUID.randomUUID().toString();
                token.setRefreshToken(newRefreshToken);
                token.setRefreshTokenExpiresAt(now.plusSeconds(604800)); // 7天
                // 旧 Token 加入黑名单
                tokenBlacklistService.blacklist(refreshToken, 30);
            }
            token.setAccessToken(newAccessToken);
            return tokenRepository.save(token)
                .thenReturn(TokenPair.of(newAccessToken, newRefreshToken));
        });
}
```

### 5.3 Refresh Token 轮换(Token Rotation)

**目的**: 防止 Refresh Token 泄露后被长期滥用。

**策略**:
1. 每次刷新后,旧 Refresh Token 立即失效(加入黑名单)
2. 服务端检测异常刷新模式:同一 Token 短时间内多次使用 → 触发安全告警
3. 可选: 绑定设备指纹,异常设备刷新需要重新登录

### 5.4 Token 撤销

```http
POST /oauth2/revoke
Content-Type: application/x-www-form-urlencoded

token=<refresh_token>
token_type_hint=refresh_token
client_id=third-party-analytics
client_secret=<client_secret>
```

后端将 Token 加入 Redis 黑名单,30 分钟后自动过期。

---

## 6. 安全防护措施

### 6.1 HTTPS 传输

- 所有 OAuth2 端点必须通过 HTTPS 访问
- 生产环境强制 HSTS

### 6.2 State 参数防 CSRF

客户端在授权请求中携带随机 `state`,回调时验证 `state` 一致,防止 CSRF 攻击。

### 6.3 Client Secret 保护

- 公共客户端(SPA、移动应用)不得存储 client_secret
- 机密客户端的 secret 应通过环境变量注入,不得硬编码

### 6.4 Scope 最小权限原则

注册客户端时只授予必要的 Scope,避免过度授权。

---

## 7. API 对接示例

### 7.1 前端完整示例

```typescript
// oauth2-client.ts
import { generatePKCE } from './pkce';

export class OAuth2Client {
  private clientId: string;
  private redirectUri: string;

  constructor(clientId: string, redirectUri: string) {
    this.clientId = clientId;
    this.redirectUri = redirectUri;
  }

  /** 发起授权 */
  authorize(scopes: string[]): void {
    const { codeVerifier, codeChallenge } = generatePKCE();
    sessionStorage.setItem('pkce_verifier', codeVerifier);

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: this.clientId,
      redirect_uri: this.redirectUri,
      scope: scopes.join(' '),
      code_challenge: codeChallenge,
      code_challenge_method: 'S256'
    });
    window.location.href = `https://api.apex-scaffold.com/oauth2/authorize?${params}`;
  }

  /** 处理回调 */
  async handleCallback(code: string): Promise<void> {
    const codeVerifier = sessionStorage.getItem('pkce_verifier');
    const token = await this.exchangeCode(code, codeVerifier);
    this.saveToken(token);
  }

  private async exchangeCode(code: string, verifier: string) {
    const res = await fetch('https://api.apex-scaffold.com/oauth2/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'authorization_code',
        code,
        client_id: this.clientId,
        redirect_uri: this.redirectUri,
        code_verifier: verifier
      })
    });
    return res.json();
  }

  private saveToken(token: any): void {
    localStorage.setItem('access_token', token.access_token);
    localStorage.setItem('refresh_token', token.refresh_token);
  }

  /** 调用受保护 API */
  async fetchProtectedResource(url: string): Promise<any> {
    const accessToken = localStorage.getItem('access_token');
    const res = await fetch(url, {
      headers: { Authorization: `Bearer ${accessToken}` }
    });
    if (res.status === 401) {
      await this.refreshToken();
      return this.fetchProtectedResource(url);
    }
    return res.json();
  }

  /** 刷新 Token */
  private async refreshToken(): Promise<void> {
    const refreshToken = localStorage.getItem('refresh_token');
    const res = await fetch('https://api.apex-scaffold.com/oauth2/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'refresh_token',
        refresh_token: refreshToken,
        client_id: this.clientId
      })
    });
    const token = await res.json();
    this.saveToken(token);
  }
}
```

### 7.2 后端验证 Token 示例(资源服务器)

```java
// OAuth2ResourceServerFilter.java
@Override
public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String token = extractToken(exchange.getRequest());
    if (token == null) {
        return handleError(exchange, ResultCode.UNAUTHORIZED);
    }

    return authorizationService.validateToken(token)
        .flatMap(tokenInfo -> {
            if (!tokenInfo.isActive()) {
                return handleError(exchange, ResultCode.UNAUTHORIZED);
            }
            // 将 scope 转换为 SCOPE_* 权限
            List<SimpleGrantedAuthority> authorities = tokenInfo.getScopes().stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope.toUpperCase().replace(":", "_")))
                .collect(Collectors.toList());

            Authentication auth = new UsernamePasswordAuthenticationToken(
                tokenInfo.getClientId(), null, authorities);
            SecurityContext context = new SecurityContextImpl(auth);

            return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
        });
}
```

### 7.3 控制器层使用 Scope 权限

```java
@Configuration
public class OAuth2ApiRouter {
    @Bean
    public RouterFunction<ServerResponse> userProfileRoutes(OAuth2ApiHandler handler) {
        return RouterFunctions.route()
            .path("/oauth2/api/v1", builder -> builder
                .GET("/user/profile", accept(MediaType.APPLICATION_JSON),
                    req -> PreAuthorize.hasAuthority("SCOPE_USER_PROFILE")
                        .then(handler.getProfile(req)))
                .GET("/orders", accept(MediaType.APPLICATION_JSON),
                    req -> PreAuthorize.hasAuthority("SCOPE_ORDER_MANAGE")
                        .then(handler.getOrders(req)))
            ).build();
    }
}
```

---

## 8. 常见问题排查

### 8.1 授权码已使用或过期

**错误**: `invalid_grant: Authorization code has expired or been used`

**原因**: 授权码有效期仅 10 分钟,且一次性使用。

**解决**: 重新发起授权请求获取新的授权码。

### 8.2 PKCE 验证失败

**错误**: `invalid_grant: PKCE code_verifier does not match`

**排查**:
1. 确认 `code_challenge_method` 为 `S256`
2. 确认 `code_verifier` 未被篡改(检查 SessionStorage)
3. 确认 SHA256 计算正确(注意 base64url 编码)

### 8.3 Redirect URI 不匹配

**错误**: `invalid_request: Redirect URI mismatch`

**原因**: 请求中的 `redirect_uri` 必须与注册时完全一致(包括协议、域名、端口、路径)。

### 8.4 Scope 权限不足

**错误**: `insufficient_scope`

**解决**: 在客户端注册时添加所需 Scope,或重新授权。

### 8.5 Token 验证失败

**排查清单**:
1. Token 是否过期(检查 `exp` 声明)
2. Token 是否在黑名单中(检查 Redis)
3. Token 签名是否正确(RS256 公钥验证)
4. Token 类型是否匹配(access_token 用于 API,refresh_token 用于刷新)

---

## 9. 参考图表

- [OAuth2 授权服务器架构图](../diagrams/30_oauth2_authorization_server_architecture.svg)
- [OAuth2 四种授权模式对比图](../diagrams/31_oauth2_grant_types_comparison.svg)
- [OAuth2 授权码流程时序图](../diagrams/32_oauth2_authorization_code_sequence_part1.svg)
- [OAuth2 Token 交换时序图](../diagrams/33_oauth2_token_exchange_sequence.svg)
- [统一权限认证整体架构图](../diagrams/38_unified_auth_architecture.svg)
- [Token 刷新与续期机制图](../diagrams/39_token_refresh_mechanism.svg)

---

© 2026 Apex Team · OAuth2 集成开发指南
