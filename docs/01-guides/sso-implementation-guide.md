# SSO 单点登录实施方案

> 版本: v1.0 | 最后更新: 2026-09-11 | 适用版本: apex-scaffold v1.2.0+
>
> 配套图表: [34_sso_architecture.svg](../diagrams/34_sso_architecture.svg)、[35_sso_login_sequence.svg](../diagrams/35_sso_login_sequence.svg)

---

## 1. SSO 原理与架构

### 1.1 什么是 SSO

SSO(Single Sign-On,单点登录)是一种身份认证方案,允许用户**只需登录一次**,即可访问多个相互独立的应用系统,无需在每个应用中重复输入用户名和密码。

### 1.2 SSO 的优势

| 优势 | 说明 |
|------|------|
| 提升用户体验 | 用户只需记忆一套凭证,减少登录操作 |
| 降低安全风险 | 减少密码泄露点,集中管理认证 |
| 统一权限管理 | 所有应用权限在统一认证中心配置 |
| 降低运维成本 | 集中管理用户,减少重复账户管理 |
| 审计集中 | 所有登录行为可统一审计追踪 |

### 1.3 Apex Scaffold 的 SSO 架构设计

Apex Scaffold 采用**基于 JWT Token + Redis Session 共享**的 SSO 架构:

**核心组件**:
- **统一认证中心(UAC)**: 负责用户认证、Token 颁发、Session 管理
- **Redis Session Store**: 跨应用共享 Session 状态
- **Cookie Domain 共享**: 一级域名下所有子应用共享 Cookie
- **Token 黑名单**: 支持单点登出(SLO)

**架构图参考**: [34_sso_architecture.svg](../diagrams/34_sso_architecture.svg)

### 1.4 SSO 实现方案对比

| 方案 | 原理 | 优点 | 缺点 | 适用场景 |
|------|------|------|------|---------|
| **基于 JWT** (推荐) | 统一颁发 JWT,Cookie 共享 | 无状态、可扩展、性能好 | 登出依赖黑名单 | 微服务、多应用 |
| 基于 Cookie | 同域名共享 Cookie | 实现简单 | 跨域困难、状态管理复杂 | 同一一级域名下多应用 |
| 基于 Keycloak | OIDC 协议统一认证 | 功能完整、企业级 | 依赖外部 IdP | 已有 Keycloak/AD |
| 基于 CAS | CAS 协议 | 成熟稳定 | 协议老旧 | 遗留系统 |

**Apex Scaffold 推荐**: 基于 JWT 的 SSO(原生支持)+ Keycloak OIDC(企业级扩展)

---

## 2. Session 共享方案

### 2.1 Redis Session Store

Spring Session + Redis 实现跨应用 Session 共享。

**Maven 依赖**:

```xml
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-data-redis</artifactId>
</dependency>
```

**配置**:

```yaml
spring:
  session:
    store-type: redis
    redis:
      namespace: spring:session:apex
      flush-mode: immediate
    timeout: 30m
```

**Java 配置** (WebFlux 响应式):

```java
@Configuration
@EnableRedisWebSession(maxInactiveIntervalInSeconds = 1800)
public class SessionConfig {

    @Bean
    public ReactiveSessionRepository<MapSession> sessionRepository(
            ReactiveRedisConnectionFactory connectionFactory) {
        ReactiveRedisSessionRepository repository = new ReactiveRedisSessionRepository(connectionFactory);
        repository.setDefaultMaxInactiveInterval(Duration.ofMinutes(30));
        return repository;
    }
}
```

### 2.2 Session 同步机制

**滑动窗口续期**:

- 用户每次请求 → 自动更新 Session 最后访问时间
- Session 有效期 = 30 分钟(从最后访问时间起算)
- 超过 30 分钟无操作 → Session 自动失效

**Session 数据结构** (Redis):

```
Key: spring:session:apex:sessions:{sessionId}
Value: {
  userId: 123,
  username: "zhangsan",
  roles: ["ADMIN", "USER"],
  permissions: ["system:user:list", "system:role:create"],
  lastAccessTime: 1694321234567,
  maxInactiveInterval: 1800
}
TTL: 30分钟
```

### 2.3 Session 超时策略

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `spring.session.timeout` | `30m` | 会话超时(滑动窗口) |
| Access Token 有效期 | `2h` | JWT Token 有效期 |
| Refresh Token 有效期 | `7d` | 刷新令牌有效期 |
| Cookie 有效期 | `7d` | 浏览器 Cookie |

**用户体验**:
- 30 分钟内有操作 → Session 自动续期,无需重新登录
- 超过 30 分钟无操作 → 需重新登录(或用 Refresh Token 自动续期)

---

## 3. 跨域认证处理

### 3.1 Cookie Domain 配置

**核心**: 所有应用在同一一级域名下,共享 Cookie。

```java
@Configuration
public class WebSessionConfig {

    @Bean
    public WebSessionIdResolver sessionIdResolver() {
        CookieWebSessionIdResolver resolver = new CookieWebSessionIdResolver();
        resolver.setCookieName("APEX_SESSION_ID");
        resolver.addCookieInitializer(builder -> {
            builder.domain(".apex-scaffold.com");   // 一级域名共享
            builder.path("/");
            builder.httpOnly(true);                  // 防 XSS
            builder.secure(true);                    // 仅 HTTPS
            builder.sameSite("Lax");                 // 防 CSRF
            builder.maxAge(Duration.ofDays(7));      // 7 天
        });
        return resolver;
    }
}
```

**域名规划示例**:

| 应用 | 域名 | Cookie 可见性 |
|------|------|--------------|
| 管理后台 | admin.apex-scaffold.com | ✅ 共享 |
| C 端应用 | user.apex-scaffold.com | ✅ 共享 |
| 移动应用后端 | api.apex-scaffold.com | ✅ 共享 |
| 第三方 | third-party.com | ❌ 不可见(需用 OAuth2) |

### 3.2 CORS 配置

```yaml
apex:
  cors:
    allowed-origins:
      - https://admin.apex-scaffold.com
      - https://user.apex-scaffold.com
      - https://api.apex-scaffold.com
    allowed-methods: GET,POST,PUT,DELETE,OPTIONS
    allowed-headers: Authorization,Content-Type,X-Requested-With
    allow-credentials: true
    max-age: 3600
```

### 3.3 SameSite 策略

| SameSite 值 | 说明 | 适用场景 |
|------------|------|---------|
| `Strict` | 仅同站请求携带 Cookie | 安全性最高,但影响跳转体验 |
| `Lax`(推荐) | 同站 + 顶级导航 GET | 平衡安全和体验 |
| `None` | 所有请求携带(需 Secure) | 跨站 POST 场景 |

**Apex Scaffold 默认使用 `Lax`**,在保证安全的同时不影响 SSO 跳转体验。

---

## 4. 多应用集成步骤

### 4.1 统一认证中心搭建

确保 `apex-interfaces` 模块已配置好:

1. JWT 认证过滤器(`JwtAuthenticationFilter`)
2. Redis Session 存储
3. Cookie Domain 共享配置
4. Token 黑名单机制

### 4.2 应用 A 接入 (apex-admin-web)

**后端配置**(已在 apex-interfaces 中,无需额外配置):
- `/api/v1/**` → JwtAuthenticationFilter
- `/admin-api/v1/**` → 需要 ADMIN 角色

**前端配置** (Vue 3):

```typescript
// api/index.ts
const instance = axios.create({
  baseURL: 'https://api.apex-scaffold.com/api/v1',
  withCredentials: true  // 携带 Cookie
})

// Token 拦截器
instance.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 401 自动刷新
instance.interceptors.response.use(
  res => res,
  async error => {
    if (error.response?.status === 401) {
      const refreshed = await refreshToken()
      if (refreshed) return instance(error.config)
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)
```

**登录流程**:

```typescript
// login.ts
export async function login(username: string, password: string) {
  const res = await axios.post('/api/v1/auth/login', { username, password })
  const { accessToken, refreshToken } = res.data.data
  localStorage.setItem('accessToken', accessToken)
  localStorage.setItem('refreshToken', refreshToken)
  // Cookie 由后端自动设置(APEX_SESSION_ID + APEX_TOKEN)
}
```

### 4.3 应用 B 接入 (apex-user-web)

**免登录核心逻辑**:

```typescript
// auth.ts
export async function autoLogin(): Promise<boolean> {
  const token = localStorage.getItem('accessToken')

  // 1. 有本地 Token,验证有效性
  if (token) {
    const valid = await validateToken(token)
    if (valid) return true
  }

  // 2. 无本地 Token,检查共享 Cookie(跨应用 SSO)
  const sessionCookie = getCookie('APEX_SESSION_ID')
  if (sessionCookie) {
    // 调用统一认证中心验证 Session
    const res = await axios.get('/api/v1/auth/sso/validate', { withCredentials: true })
    if (res.data.data.authenticated) {
      localStorage.setItem('accessToken', res.data.data.accessToken)
      localStorage.setItem('refreshToken', res.data.data.refreshToken)
      return true
    }
  }

  return false
}

function getCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'))
  return match ? match[2] : null
}
```

**路由守卫**:

```typescript
// router/index.ts
router.beforeEach(async (to, from, next) => {
  if (to.meta.requiresAuth) {
    const loggedIn = await autoLogin()
    if (!loggedIn) {
      next('/login')
    } else {
      next()
    }
  } else {
    next()
  }
})
```

### 4.4 后端 SSO 验证接口

```java
@Configuration
public class SsoRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> ssoRoutes(AuthService authService) {
        return RouterFunctions.route()
            .GET("/api/v1/auth/sso/validate", req -> {
                // 从 Cookie 读取 Session ID,查询 Redis Session
                String sessionId = req.cookies().getFirst("APEX_SESSION_ID");
                if (sessionId == null) {
                    return ResponseUtil.ok(Map.of("authenticated", false));
                }
                return authService.validateSession(sessionId)
                    .flatMap(user -> {
                        // Session 有效,生成新的 JWT Token
                        return authService.generateTokenPair(user.getId())
                            .map(tokenPair -> ResponseUtil.ok(Map.of(
                                "authenticated", true,
                                "accessToken", tokenPair.getAccessToken(),
                                "refreshToken", tokenPair.getRefreshToken()
                            )));
                    })
                    .switchIfEmpty(Mono.just(ResponseUtil.ok(Map.of("authenticated", false))));
            })
            .build();
    }
}
```

---

## 5. 单点登出 (SLO) 实现

### 5.1 Token 黑名单机制

```java
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    /** 将 Token 加入黑名单 */
    public Mono<Void> blacklist(String token, long ttlMinutes) {
        String key = BLACKLIST_PREFIX + token.hashCode();
        return redisTemplate.opsForValue()
            .set(key, "1", Duration.ofMinutes(ttlMinutes))
            .then();
    }

    /** 检查 Token 是否在黑名单中 */
    public Mono<Boolean> isBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token.hashCode();
        return redisTemplate.hasKey(key);
    }
}
```

### 5.2 登出流程

**后端登出接口**:

```java
@Bean
public RouterFunction<ServerResponse> logoutRoute(TokenBlacklistService blacklistService) {
    return RouterFunctions.route()
        .POST("/api/v1/auth/logout", req -> {
            String token = extractToken(req);
            String refreshToken = extractRefreshToken(req);

            // 将 Token 加入黑名单(30 分钟)
            return Mono.zip(
                blacklistService.blacklist(token, 30),
                blacklistService.blacklist(refreshToken, 30)
            ).then(
                // 清除 Session
                req.exchange().getSession().flatMap(session -> {
                    session.invalidate();
                    // 清除 Cookie
                    req.exchange().getResponse().addCookie(
                        ResponseCookie.from("APEX_SESSION_ID", "")
                            .domain(".apex-scaffold.com")
                            .path("/")
                            .maxAge(0)
                            .build()
                    );
                    return Mono.empty();
                })
            ).thenReturn(ResponseUtil.ok("logout success"));
        })
        .build();
}
```

### 5.3 通知所有应用登出 (SSE 推送)

**方案**: 通过 SSE(Server-Sent Events)实时推送登出事件。

```java
@Configuration
public class SsoLogoutRouter {

    // 保存所有活动连接
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Bean
    public RouterFunction<ServerResponse> sseRoutes() {
        return RouterFunctions.route()
            // 客户端建立 SSE 连接
            .GET("/api/v1/auth/sso/events", req -> {
                String sessionId = req.cookies().getFirst("APEX_SESSION_ID");
                SseEmitter emitter = new SseEmitter(0L);
                emitters.put(sessionId, emitter);
                emitter.onCompletion(() -> emitters.remove(sessionId));
                return ServerResponse.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(BodyInserters.fromServerSentEvents(emitter));
            })
            .build();
    }

    /** 广播登出事件 */
    public void broadcastLogout(String sessionId) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                    .name("logout")
                    .data(Map.of("message", "logged out")));
                emitter.complete();
            } catch (IOException e) {
                emitters.remove(sessionId);
            }
        }
    }
}
```

**前端监听**:

```typescript
const eventSource = new EventSource('/api/v1/auth/sso/events', { withCredentials: true })
eventSource.addEventListener('logout', () => {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  window.location.href = '/login'
})
```

---

## 6. 安全考虑

### 6.1 Token 加密传输

- 所有认证通信强制 HTTPS
- Access Token 不存入 Cookie(防 CSRF),存 LocalStorage 或内存
- Refresh Token 可存 HttpOnly Cookie

### 6.2 CSRF 防护

- Cookie 设置 `SameSite=Lax`
- 敏感操作使用 `SameSite=Strict`
- CSRF Token 校验(Stateful 端点)

### 6.3 XSS 防护

- HttpOnly Cookie 防止 JavaScript 读取
- 前端输入转义,防止恶意脚本注入
- CSP(Content Security Policy)响应头

### 6.4 Session 劫持防护

- 绑定 IP / User-Agent(可选,可能影响体验)
- 检测异常 Session 行为(异地登录告警)
- 支持强制登出(管理员可吊销指定 Session)

### 6.5 安全响应头

```yaml
server:
  headers:
    content-security-policy: "default-src 'self'"
    x-frame-options: DENY
    x-content-type-options: nosniff
    strict-transport-security: max-age=31536000; includeSubDomains
```

---

## 7. 测试验证

### 7.1 功能测试用例

| 用例 | 步骤 | 预期结果 |
|------|------|---------|
| 首次登录应用 A | 访问应用 A → 输入凭据登录 | 登录成功,跳转到首页 |
| 免登录访问应用 B | 在应用 A 已登录状态下访问应用 B | 自动登录,无需输入凭据 |
| Token 过期自动续期 | Access Token 过期后发起请求 | 自动刷新 Token,请求成功 |
| 单点登出 | 在应用 A 点击登出 | 应用 A、B 同时登出 |
| 过期 Session 处理 | 30 分钟无操作后访问 | 跳转到登录页 |
| Refresh Token 过期 | 7 天后访问 | 跳转到登录页 |
| 多设备登录 | 同一账号在两个设备登录 | 两个设备均可正常使用 |
| 强制登出 | 管理员吊销某用户 Session | 该用户所有设备登出 |

### 7.2 性能测试

| 指标 | 目标 | 测试工具 |
|------|------|---------|
| 登录响应时间 | < 200ms (P95) | JMeter / wrk |
| Token 验证时间 | < 50ms (P99) | JMeter |
| SSO 免登录成功率 | > 99.5% | 自动化脚本 |
| 并发登录 QPS | > 10,000 | JMeter |
| Session 读取延迟 | < 10ms | Redis Benchmark |

### 7.3 安全测试

- **渗透测试**: OWASP ZAP 扫描
- **Token 安全**: JWT 签名验证、过期处理
- **CSRF**: 跨站请求伪造测试
- **XSS**: 跨站脚本测试
- **Cookie 安全**: HttpOnly、Secure、SameSite 验证

---

## 8. 多应用部署拓扑

```
                    ┌─────────────────┐
                    │   Nginx (SSL)   │
                    └────────┬────────┘
                             │
            ┌────────────────┼────────────────┐
            │                │                │
    ┌───────▼──────┐ ┌──────▼───────┐ ┌──────▼───────┐
    │ admin-web    │ │  user-web    │ │  api-gateway │
    │ .apex...com  │ │  .apex...com │ │  .apex...com │
    └───────┬──────┘ └──────┬───────┘ └──────┬───────┘
            │                │                │
            └────────────────┼────────────────┘
                             │
                    ┌────────▼────────┐
                    │ Apex Backend    │
                    │ (JWT + Session) │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │ Redis Cluster   │
                    │ (Session Store) │
                    └─────────────────┘
```

**域名规划**:

| 应用 | 域名 | 说明 |
|------|------|------|
| 管理后台 | admin.apex-scaffold.com | Vue 3 SPA |
| C 端应用 | user.apex-scaffold.com | Vue 3 SPA |
| API 网关 | api.apex-scaffold.com | Spring Boot |
| 认证中心 | auth.apex-scaffold.com | 可与 API 网关合并 |
| Keycloak | keycloak.apex-scaffold.com | 企业 SSO(可选) |

---

## 9. 与 Keycloak SSO 集成

如需企业级 SSO(对接 AD/LDAP),可同时启用 Keycloak:

1. 用户选择"企业账号登录"
2. 跳转到 Keycloak 认证
3. Keycloak 回调 Apex,同步用户
4. 生成本地 JWT,后续走 Apex SSO

详见 [Keycloak 集成配置指南](./keycloak-integration-guide.md)

---

## 10. 参考图表

- [SSO 单点登录架构图](../diagrams/34_sso_architecture.svg)
- [SSO 登录流程时序图](../diagrams/35_sso_login_sequence.svg)
- [统一权限认证整体架构图](../diagrams/38_unified_auth_architecture.svg)
- [Token 刷新与续期机制图](../diagrams/39_token_refresh_mechanism.svg)

---

© 2026 Apex Team · SSO 单点登录实施方案
