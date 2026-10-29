# Keycloak 集成配置指南

> 版本: v1.0 | 最后更新: 2026-09-11 | 适用版本: apex-scaffold v1.2.0+
>
> 配套图表: [36_keycloak_integration_architecture.svg](../diagrams/36_keycloak_integration_architecture.svg)、[37_keycloak_oidc_sequence.svg](../diagrams/37_keycloak_oidc_sequence.svg)

---

## 1. Keycloak 简介

### 1.1 什么是 Keycloak

Keycloak 是 Red Hat 开源的**身份和访问管理**(IAM)解决方案,提供单点登录(SSO)、身份代理、用户联邦等功能,支持 OIDC(OpenID Connect)、SAML 2.0 等标准协议。

### 1.2 核心功能

| 功能 | 说明 |
|------|------|
| 单点登录(SSO) | 用户只需登录一次即可访问多个应用 |
| 身份代理(Identity Brokering) | 集成 GitHub、Google、微信等第三方登录 |
| 用户联邦(User Federation) | 对接 LDAP、Active Directory |
| 多因素认证(MFA) | OTP、SMS、WebAuthn |
| 细粒度授权 | 基于策略的访问控制 |
| 会话管理 | 集中管理用户会话,支持单点登出 |
| 管理控制台 | Web 管理界面,可视化配置 |

### 1.3 与 Apex Scaffold 的集成架构

Apex Scaffold 将 Keycloak 作为**身份提供商(IdP)**,通过 OIDC 协议集成。核心设计:

1. 用户通过 Keycloak 完成认证
2. Apex 后端接收 OIDC 回调,解析 ID Token
3. 根据 `keycloak_sub` 自动同步本地用户
4. 生成本地 Apex JWT Token 用于业务 API 调用
5. 业务 API 仍使用本地 JWT,不直接使用 Keycloak Token

**架构图参考**: [36_keycloak_integration_architecture.svg](../diagrams/36_keycloak_integration_architecture.svg)

---

## 2. Podman 部署配置

### 2.1 开发环境部署 (Podman Compose)

在 `podman-compose.yml` 中添加 Keycloak 服务:

```yaml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:23.0
    container_name: apex-keycloak
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin123
      KC_DB: mysql
      KC_DB_URL: jdbc:mysql://mysql:3306/keycloak?useSSL=false&serverTimezone=Asia/Shanghai
      KC_DB_USERNAME: root
      KC_DB_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      KC_HOSTNAME: localhost
      KC_HTTP_ENABLED: "true"
      KC_HTTPS_KEY_STORE_FILE: /opt/keycloak/conf/server.keystore
    ports:
      - "8080:8080"
    volumes:
      - keycloak_data:/opt/keycloak/data
      - ./keycloak/import:/opt/keycloak/data/import
    depends_on:
      mysql:
        condition: service_healthy
    command: start-dev
    networks:
      - apex-network

  mysql:
    image: mysql:9.0
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: keycloak
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  keycloak_data:
```

### 2.2 生产环境部署建议

**高可用架构**:

```yaml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:23.0
    environment:
      KC_PROXY: edge                    # 反向代理模式
      KC_HOSTNAME: keycloak.apex-scaffold.com
      KC_HTTP_ENABLED: "true"
      KC_DB: mysql
      KC_DB_URL: jdbc:mysql://mysql-cluster:3306/keycloak
      # 集群配置
      KC_CACHE: ispn
      KC_CACHE_STACK: kubernetes
      JGROUPS_DISCOVERY_PROTOCOL: dns.DNS_PING
      JGROUPS_DISCOVERY_PROPERTIES: dns_query=keycloak-discovery
    deploy:
      replicas: 3
    networks:
      - apex-network
```

**关键生产配置**:

| 配置项 | 说明 | 推荐值 |
|--------|------|--------|
| `KC_PROXY` | 反向代理模式 | `edge`(TLS 在代理终结) |
| `KC_HOSTNAME` | 对外访问域名 | `keycloak.apex-scaffold.com` |
| `KC_DB` | 数据库类型 | `mysql` |
| `KC_CACHE` | 集群缓存 | `ispn`(Infinispan) |
| `KC_HTTP_ENABLED` | 启用 HTTP | `true`(配合反向代理) |

### 2.3 HTTPS 证书配置

```yaml
keycloak:
  environment:
    KC_HTTPS_CERTIFICATE_FILE: /etc/keycloak/tls.crt
    KC_HTTPS_CERTIFICATE_KEY_FILE: /etc/keycloak/tls.key
  volumes:
    - /etc/letsencrypt/live/keycloak.apex-scaffold.com/fullchain.pem:/etc/keycloak/tls.crt
    - /etc/letsencrypt/live/keycloak.apex-scaffold.com/privkey.pem:/etc/keycloak/tls.key
```

---

## 3. Realm 和 Client 配置

### 3.1 创建 Realm

1. 访问 `http://localhost:8080/admin`,用 admin 账户登录
2. 左上角下拉菜单 → "Create Realm"
3. 填写:
   - **Realm name**: `apex-scaffold`
   - **Enabled**: 开启
4. 点击 "Create"

### 3.2 配置 Client

在 `apex-scaffold` Realm 下创建 Client:

| 配置项 | 值 | 说明 |
|--------|-----|------|
| Client type | `OpenID Connect` | 使用 OIDC 协议 |
| Client ID | `apex-web-client` | 客户端唯一标识 |
| Name | `Apex Web Client` | 显示名称 |
| Always display in UI | `ON` | 在账户页面显示 |

**Capability config**:

| 配置项 | 值 |
|--------|-----|
| Client authentication | `ON` (confidential 类型) |
| Authorization | `OFF` |
| Authentication flow | Standard flow (授权码) |
| Direct access grants | `OFF` (禁用密码模式) |
| Implicit flow | `OFF` |
| Service accounts roles | `OFF` |
| OAuth 2.0 Device Authorization Grant | `OFF` |

**Login settings**:

| 配置项 | 值 |
|--------|-----|
| Valid redirect URIs | `https://api.apex-scaffold.com/login/oauth2/code/keycloak` |
| Valid post logout redirect URIs | `https://admin.apex-scaffold.com/login` |
| Web origins | `https://admin.apex-scaffold.com` |
| Backchannel logout URL | `https://api.apex-scaffold.com/api/v1/auth/backchannel-logout` |

**Advanced settings**:

| 配置项 | 值 | 说明 |
|--------|-----|------|
| PKCE code challenge method | `S256` | 强制 PKCE |
| Access Token Lifespan | `300`(5分钟) | IDP Token 短暂有效 |
| Client Session Idle | `1800`(30分钟) |
| Client Session Max | `28800`(8小时) |

### 3.3 配置 Client Scopes

在 Client Scopes 配置页,确保以下 Scope 存在:

- `openid`(必须,默认存在)
- `profile`(用户基本信息: name, preferred_username)
- `email`(邮箱)
- `roles`(角色信息)

### 3.4 获取 Client Secret

在 Clients → apex-web-client → Credentials 标签页:
- 记录 `Client secret` 值(后续配置需要)
- 如需要可重新生成

---

## 4. OIDC 集成步骤

### 4.1 Apex 后端配置

**application.yml** (or application-enterprise.yml):

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: apex-web-client
            client-secret: ${KEYCLOAK_CLIENT_SECRET}
            scope: openid,profile,email
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            client-name: Keycloak SSO
        provider:
          keycloak:
            issuer-uri: ${KEYCLOAK_ISSUER_URI}
            user-name-attribute: preferred_username

keycloak:
  enabled: true
  server-url: ${KEYCLOAK_SERVER_URL}
  realm: apex-scaffold
  client-id: apex-web-client
  client-secret: ${KEYCLOAK_CLIENT_SECRET}
  user-sync-enabled: true
  frontend-callback-url: ${KEYCLOAK_FRONTEND_CALLBACK_URL}
```

**环境变量**:

```bash
KEYCLOAK_ISSUER_URI=https://keycloak.apex-scaffold.com/realms/apex-scaffold
KEYCLOAK_SERVER_URL=https://keycloak.apex-scaffold.com/auth
KEYCLOAK_CLIENT_SECRET=你的客户端密钥
KEYCLOAK_FRONTEND_CALLBACK_URL=https://admin.apex-scaffold.com/callback
```

### 4.2 核心组件说明

#### OidcAuthenticationSuccessHandler

处理 Keycloak OIDC 认证成功后的回调:

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final KeycloakConfig keycloakConfig;

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange exchange, Authentication authentication) {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        String sub = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String username = oidcUser.getPreferredUsername();

        return userRepository.findByKeycloakSub(sub)
            .switchIfEmpty(createNewUser(sub, email, username))
            .flatMap(user -> {
                // 更新最后登录时间
                user.setLastLoginTime(LocalDateTime.now());
                return userRepository.save(user);
            })
            .flatMap(user -> tokenService.generateTokenPair(user.getId()))
            .flatMap(tokenPair -> {
                // 构建前端回调 URL
                String callbackUrl = UriComponentsBuilder
                    .fromUriString(keycloakConfig.getFrontendCallbackUrl())
                    .queryParam("accessToken", tokenPair.getAccessToken())
                    .queryParam("refreshToken", tokenPair.getRefreshToken())
                    .queryParam("userId", user.getId())
                    .build().toUriString();

                ServerHttpResponse response = exchange.getExchange().getResponse();
                response.setStatusCode(HttpStatus.FOUND);
                response.getHeaders().setLocation(URI.create(callbackUrl));
                return response.setComplete();
            });
    }

    private Mono<UserEntity> createNewUser(String sub, String email, String username) {
        if (!keycloakConfig.isUserSyncEnabled()) {
            return Mono.error(new BusinessException(ResultCode.FORBIDDEN));
        }
        UserEntity newUser = UserEntity.builder()
            .keycloakSub(sub)
            .username(username)
            .email(email)
            .status(UserStatus.ACTIVE)
            .build();
        return userRepository.save(newUser);
    }
}
```

#### KeycloakConfig

```java
@Data
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakConfig {
    private boolean enabled;
    private String serverUrl;
    private String realm;
    private String clientId;
    private String clientSecret;
    private boolean userSyncEnabled;
    private String frontendCallbackUrl;
}
```

### 4.3 数据库迁移

新增 `keycloak_sub` 字段到 `sys_user` 表:

```sql
-- V23__add_keycloak_sub_to_user.sql
ALTER TABLE sys_user ADD COLUMN keycloak_sub VARCHAR(255) DEFAULT NULL COMMENT 'Keycloak用户唯一标识(sub)';
ALTER TABLE sys_user ADD UNIQUE KEY uk_keycloak_sub (keycloak_sub);
```

### 4.4 前端集成

**路由配置** (Vue Router):

```typescript
// router/index.ts
const routes = [
  {
    path: '/login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/callback',
    component: () => import('@/views/KeycloakCallback.vue')
  },
  // ...
]
```

**Keycloak 登录按钮**:

```vue
<!-- Login.vue -->
<template>
  <button @click="loginWithKeycloak">使用企业账号登录</button>
</template>

<script setup lang="ts">
function loginWithKeycloak() {
  window.location.href = '/oauth2/authorization/keycloak';
}
</script>
```

**回调处理**:

```vue
<!-- KeycloakCallback.vue -->
<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onMounted(() => {
  const params = new URLSearchParams(window.location.search)
  const accessToken = params.get('accessToken')
  const refreshToken = params.get('refreshToken')

  if (accessToken) {
    localStorage.setItem('accessToken', accessToken)
    localStorage.setItem('refreshToken', refreshToken || '')
    router.push('/dashboard')
  } else {
    router.push('/login?error=keycloak_auth_failed')
  }
})
</script>
```

---

## 5. 用户同步策略

### 5.1 自动同步 (user-sync-enabled=true)

**同步时机**: 每次 Keycloak 登录回调时执行。

**同步流程**:

```
1. 从 OidcUser 提取 sub, email, preferred_username
2. 调用 userRepository.findByKeycloakSub(sub)
3. 如果用户不存在:
   a. 创建新用户,keycloak_sub = sub
   b. 默认状态: ACTIVE
   c. 默认角色: ROLE_USER
4. 如果用户已存在:
   a. 更新 email, username(可选)
   b. 更新最后登录时间
5. 生成本地 JWT Token
```

**同步字段映射**:

| Keycloak Claim | Apex UserEntity 字段 | 同步时机 |
|---------------|---------------------|---------|
| `sub` | `keycloakSub` | 创建时 |
| `email` | `email` | 创建+每次登录(可选) |
| `preferred_username` | `username` | 创建时 |
| `name` | `nickName` | 创建+每次登录(可选) |

### 5.2 禁用同步 (user-sync-enabled=false)

**适用场景**: 严格权限控制,不允许外部用户自动注册。

**行为**:
1. 用户通过 Keycloak 认证成功
2. 后端查询 `keycloak_sub`,用户不存在 → 返回 403
3. 需管理员预先在 Apex 后台创建用户并绑定 `keycloak_sub`

**管理员绑定用户**:

```http
POST /admin-api/v1/users/{userId}/bind-keycloak
Authorization: Bearer <admin_token>

{
  "keycloakSub": "f:apex-scaffold:user-uuid-123"
}
```

### 5.3 角色映射

在 Keycloak 的 Client Mappers 中配置角色映射,或在 Apex 侧处理:

```java
// 在 OidcAuthenticationSuccessHandler 中提取角色
List<String> keycloakRoles = oidcUser.getClaimAsStringList("realm_access_roles");

// 映射到本地角色
Map<String, String> roleMapping = Map.of(
    "admin", "ADMIN",
    "user", "USER",
    "editor", "EDITOR"
);

List<RoleEntity> roles = keycloakRoles.stream()
    .map(roleMapping::get)
    .filter(Objects::nonNull)
    .map(roleCode -> roleRepository.findByCode(roleCode))
    .flatMap(Optional::stream)
    .collect(Collectors.toList());

user.setRoles(roles);
```

---

## 6. 前后端集成示例

### 6.1 完整登录流程

```
用户点击"企业登录"
    ↓
前端: window.location.href = '/oauth2/authorization/keycloak'
    ↓
后端: 302 重定向到 Keycloak 授权页
    ↓
用户: 输入 Keycloak 凭据登录
    ↓
Keycloak: 302 重定向回 /login/oauth2/code/keycloak?code=xxx
    ↓
后端: Spring Security 自动用 code 换 ID Token + Access Token
    ↓
后端: OidcAuthenticationSuccessHandler 处理
    ├── 提取 sub, email, username
    ├── findByKeycloakSub(sub)
    ├── 不存在则创建(或 403)
    ├── 更新登录时间
    └── generateTokenPair(userId) → Apex JWT
    ↓
后端: 302 重定向到前端回调 ?accessToken=xxx&refreshToken=xxx
    ↓
前端: 存储 Token,跳转到首页 ✅
```

### 6.2 Token 使用

**重要**: 业务 API 使用 **Apex JWT Token**,不使用 Keycloak Token。

```typescript
// axios 配置
const api = axios.create({
  baseURL: 'https://api.apex-scaffold.com/api/v1'
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
```

### 6.3 登出处理

```typescript
async function logout() {
  const refreshToken = localStorage.getItem('refreshToken')
  try {
    await axios.post('/api/v1/auth/logout', null, {
      headers: { Authorization: `Bearer ${refreshToken}` }
    })
  } finally {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    // 跳转到 Keycloak 登出(可选)
    window.location.href = `https://keycloak.apex-scaffold.com/realms/apex-scaffold/protocol/openid-connect/logout?post_logout_redirect_uri=${encodeURIComponent('https://admin.apex-scaffold.com/login')}`
  }
}
```

---

## 7. 生产环境部署

### 7.1 高可用 Keycloak 集群

- **节点数**: 至少 3 个(Infinispan 集群奇数)
- **负载均衡**: Nginx / HAProxy 前置
- **数据库**: MySQL 主从 + 读写分离
- **缓存**: Infinispan 分布式缓存(会话、Token)

### 7.2 性能优化

| 优化项 | 建议值 |
|--------|--------|
| JVM 堆内存 | 2GB+ |
| 数据库连接池 | 100 |
| HTTP 线程池 | 200 |
| Token 签名密钥 | RS256 (2048位) |
| Access Token 有效期 | 5 分钟 |

### 7.3 监控告警

通过 Prometheus + Grafana 监控:

- `keycloak_active_sessions`: 活跃会话数
- `keycloak_logins_total`: 登录次数
- `keycloak_token_issuances_total`: Token 颁发数
- `keycloak_user_federation_query_duration`: 用户联邦查询耗时

---

## 8. 故障排查

### 8.1 OIDC 回调失败

**症状**: 登录后重定向到错误页面

**排查**:
1. 检查 `redirect-uri` 是否与 Keycloak 配置的 Valid redirect URIs 完全匹配
2. 检查 `issuer-uri` 是否可达(`curl https://keycloak.../.well-known/openid-configuration`)
3. 检查 `client-secret` 是否正确

### 8.2 用户同步失败

**症状**: Keycloak 登录成功但 Apex 返回 403

**原因**: `user-sync-enabled=false` 且用户未绑定 `keycloak_sub`

**解决**: 管理员在后台绑定用户的 `keycloak_sub`,或开启自动同步。

### 8.3 Token 验证失败

**症状**: 前端获取到 Apex Token 后调用 API 仍 401

**排查**:
1. 确认使用的是 Apex JWT 而非 Keycloak ID Token
2. 检查 JWT 密钥是否一致(JWT_SECRET 环境变量)
3. 检查 Token 是否过期

### 8.4 PKCE 验证失败

**症状**: Keycloak 返回 `PKCE code_verifier does not match`

**解决**: 确保 Apex 后端和 Keycloak 都配置 `code_challenge_method=S256`。

---

## 9. 参考图表

- [Keycloak 集成架构图](../diagrams/36_keycloak_integration_architecture.svg)
- [Keycloak OIDC 认证流程时序图](../diagrams/37_keycloak_oidc_sequence.svg)
- [统一权限认证整体架构图](../diagrams/38_unified_auth_architecture.svg)

---

© 2026 Apex Team · Keycloak 集成配置指南
