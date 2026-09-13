# Keycloak 认证方式集成方案

## 一、方案概述

### 1.1 集成目标

在现有 JWT 体系基础上，增加 Keycloak 支持的多种认证方式，实现以下能力：

- **保留现有能力**：维持现有的自研 JWT 认证体系和 OAuth2 授权服务器，确保现有业务不受影响
- **新增 OIDC 支持**：集成 OpenID Connect 协议，支持标准化的身份认证流程
- **社交登录集成**：通过 Keycloak Identity Brokering 支持 GitHub、Google、微信等社交账号登录
- **多因素认证（MFA）**：支持 TOTP（Google Authenticator）、WebAuthn（指纹/人脸识别）等多因素认证
- **SAML 2.0 支持**：对接企业级 SAML IdP（如 LDAP、Active Directory）
- **用户联邦**：支持从 LDAP/AD 等外部用户源同步用户信息

### 1.2 架构设计原则

- **渐进式集成**：新旧体系并存，通过配置开关控制
- **最小侵入**：复用现有 SecurityConfig，通过新增过滤器和配置实现扩展
- **向后兼容**：现有 JWT Token 继续有效，不影响现有客户端
- **标准化优先**：优先使用 Spring Security OAuth2 标准实现，避免引入 Keycloak 专有适配器

## 二、当前架构分析

### 2.1 现有认证授权体系

项目当前实现了双轨认证架构：

**JWT 认证体系（主业务流）**
- 过滤器：`JwtAuthenticationFilter`（SecurityWebFiltersOrder.AUTHENTICATION）
- Token 服务：`TokenServiceImpl` 基于 Redis 存储
- 特性：
  - Token 滑动刷新（剩余 30 分钟自动续期）
  - 分布式黑名单（主动失效、主体撤销）
  - 角色权限缓存（Caffeine 60秒 TTL）
  - 设备指纹绑定

**OAuth2 授权服务器（对外 API）**
- 过滤器：`OAuth2ResourceServerFilter`（仅处理 `/oauth2/api/**`）
- 数据库表：`oauth2_registered_client`、`oauth2_authorization`、`oauth2_authorization_consent`
- 协议支持：OAuth2.0 授权码、客户端凭证、密码模式、刷新令牌

**授权规则**
- 公开路径：`/api/v1/auth/**`、`/actuator/health/**`、`/swagger-ui/**` 等
- RBAC 开关：`apex.features.rbac`（basic 版本关闭，standard/enterprise 启用）
- 管理端路径：`/admin-api/v1/**`、`/internal/v1/interests/**` 需要特定角色

### 2.2 技术栈

- **Spring Boot**: 3.5.15
- **Spring Security**: 6.5.0
- **WebFlux**: 响应式编程
- **JWT 库**: io.jsonwebtoken:jjwt 0.12.6
- **数据库**: MySQL 8 / PostgreSQL
- **缓存**: Redis（Redisson 客户端）+ Caffeine 本地缓存

## 三、Keycloak 集成方案

### 3.1 部署架构

```
┌─────────────────────────────────────────────────────────────┐
│                    客户端（Web/App/小程序）                      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  Spring Cloud Gateway / Nginx                │
└─────────────────────────────────────────────────────────────┘
                              │
                ┌─────────────┴─────────────┐
                ▼                           ▼
┌────────────────────────┐    ┌────────────────────────┐
│   Keycloak Server      │    │  Apex Scaffold         │
│   (Identity Provider)  │    │  (Resource Server)     │
│  ┌──────────────────┐  │    │  ┌──────────────────┐  │
│  │ OIDC/OAuth2      │  │    │  │ JWT Auth Filter  │  │
│  │ Social Login     │  │◀──┐│  │ OIDC Auth Filter │  │
│  │ SAML Federation  │  │   ││  │ OAuth2 Filter    │  │
│  │ MFA (TOTP/WebAuth)│ │   ││  └──────────────────┘  │
│  │ LDAP/AD Sync     │  │   │└────────────────────────┘
│  └──────────────────┘  │   │
└────────────────────────┘   │
           │                 │
           │  Token 验证     │
           └─────────────────┘
```

### 3.2 认证流程设计

#### 流程 1：现有 JWT 认证（保持不变）

```
客户端请求 → JwtAuthenticationFilter
    ↓
提取 Bearer Token → 黑名单检查 → JWT 验签 → 主体撤销检查
    ↓
查询用户 + 加载角色 → Token 滑动刷新 → SecurityContext
    ↓
业务 Handler 处理
```

#### 流程 2：OIDC 认证（新增）

```
客户端请求 → 无 Token / Token 无效
    ↓
OidcAuthenticationFilter → 重定向到 Keycloak 登录页
    ↓
用户在 Keycloak 完成认证（可选 MFA）
    ↓
Keycloak 回调到应用 /login/oauth2/code/keycloak
    ↓
应用通过授权码换取 ID Token + Access Token
    ↓
验证 ID Token → 提取用户信息 → 创建本地用户（如不存在）
    ↓
生成本地 JWT Token（兼容现有体系）→ SecurityContext
    ↓
业务 Handler 处理
```

#### 流程 3：社交登录（新增）

```
客户端请求 → 选择社交登录（GitHub/Google/微信）
    ↓
重定向到 Keycloak → Keycloak 重定向到社交平台
    ↓
用户授权 → 社交平台回调到 Keycloak
    ↓
Keycloak 验证并创建/关联用户 → 回调到应用
    ↓
应用生成本地 JWT Token → SecurityContext
    ↓
业务 Handler 处理
```

### 3.3 新增组件清单

#### 3.3.1 依赖变更

**pom.xml 新增依赖**（父 POM 版本管理）
```xml
<properties>
    <keycloak.version>26.7.3</keycloak.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Keycloak Admin Client（用于用户管理、Realm 配置） -->
        <dependency>
            <groupId>org.keycloak</groupId>
            <artifactId>keycloak-admin-client</artifactId>
            <version>${keycloak.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**apex-interfaces/pom.xml**（已有 OAuth2 依赖，无需新增）
```xml
<!-- 已存在，支持 OIDC/OAuth2 Client -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>

<!-- 已存在，支持 OAuth2 Resource Server -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

#### 3.3.2 配置文件新增

**application.yml 新增 Keycloak 配置**
```yaml
# Keycloak 集成配置
keycloak:
  enabled: ${KEYCLOAK_ENABLED:false}  # 总开关，默认关闭
  server-url: ${KEYCLOAK_SERVER_URL:http://localhost:8180}
  realm: ${KEYCLOAK_REALM:apex-realm}
  # Admin Client 配置（用于用户同步、管理）
  admin:
    client-id: ${KEYCLOAK_ADMIN_CLIENT_ID:apex-admin}
    client-secret: ${KEYCLOAK_ADMIN_CLIENT_SECRET:}
    username: ${KEYCLOAK_ADMIN_USERNAME:}
    password: ${KEYCLOAK_ADMIN_PASSWORD:}

spring:
  security:
    oauth2:
      client:
        provider:
          keycloak:
            issuer-uri: ${keycloak.server-url}/realms/${keycloak.realm}
            user-name-attribute: preferred_username
        registration:
          keycloak:
            client-id: ${KEYCLOAK_CLIENT_ID:apex-scaffold}
            client-secret: ${KEYCLOAK_CLIENT_SECRET:}
            scope: openid,profile,email
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/keycloak"
      resourceserver:
        jwt:
          issuer-uri: ${keycloak.server-url}/realms/${keycloak.realm}
```

**application-dev.yml 开发环境配置**
```yaml
keycloak:
  enabled: true
  server-url: http://localhost:8180
  realm: apex-realm
  admin:
    client-id: apex-admin
    client-secret: dev-admin-secret
    username: admin
    password: admin
```

#### 3.3.3 新增 Java 类

**1. Keycloak 配置类**

文件路径：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/KeycloakConfig.java`

```java
@Configuration
@ConditionalOnProperty(prefix = "keycloak", name = "enabled", havingValue = "true")
@ConfigurationProperties(prefix = "keycloak")
@Data
public class KeycloakConfig {
    
    private boolean enabled;
    private String serverUrl;
    private String realm;
    private Admin admin;
    
    @Data
    public static class Admin {
        private String clientId;
        private String clientSecret;
        private String username;
        private String password;
    }
    
    /**
     * Keycloak Admin Client Bean
     * 用于管理 Realm、用户、角色等
     */
    @Bean
    @ConditionalOnProperty(prefix = "keycloak.admin", name = "username")
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master")  // Admin API 使用 master realm
                .username(admin.getUsername())
                .password(admin.getPassword())
                .clientId("admin-cli")
                .build();
    }
}
```

**2. OIDC 认证成功处理器（WebFlux 响应式）**

文件路径：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/security/OidcAuthenticationSuccessHandler.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "keycloak", name = "enabled", havingValue = "true")
public class OidcAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {
    
    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    
    /**
     * OIDC 认证成功后的处理：同步用户信息，生成本地 JWT
     */
    @Override
    public Mono<Void> onAuthenticationSuccess(
            WebFilterExchange webFilterExchange, 
            Authentication authentication) {
        
        if (!(authentication instanceof OAuth2AuthenticationToken)) {
            return Mono.empty();
        }
        
        OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = oauth2Token.getPrincipal();
        
        return processOidcUser(oauth2User)
                .flatMap(user -> generateLocalTokens(webFilterExchange, user))
                .then(redirectToHomePage(webFilterExchange));
    }
    
    /**
     * 处理 OIDC 用户：查找或创建本地用户（纯响应式）
     */
    private Mono<UserEntity> processOidcUser(OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        String keycloakSub = oauth2User.getAttribute("sub");
        String openId = "keycloak:" + keycloakSub;
        
        return userRepository.findByOpenId(openId)
                .switchIfEmpty(userRepository.findByEmail(email))
                .switchIfEmpty(createUserFromOidc(oauth2User))
                .flatMap(user -> updateUserFromOidc(user, oauth2User));
    }
    
    /**
     * 从 OIDC 用户信息创建本地用户（纯响应式）
     */
    private Mono<UserEntity> createUserFromOidc(OAuth2User oauth2User) {
        UserEntity user = new UserEntity();
        user.setUsername(oauth2User.getAttribute("preferred_username"));
        user.setEmail(oauth2User.getAttribute("email"));
        user.setNickname(oauth2User.getAttribute("name"));
        user.setOpenId("keycloak:" + oauth2User.getAttribute("sub"));
        user.setEnabled(true);
        user.setSource("keycloak");
        
        return userRepository.save(user)
                .flatMap(savedUser -> assignDefaultRole(savedUser)
                        .thenReturn(savedUser));
    }
    
    /**
     * 更新用户信息（从 OIDC 同步最新数据）
     */
    private Mono<UserEntity> updateUserFromOidc(UserEntity user, OAuth2User oauth2User) {
        user.setEmail(oauth2User.getAttribute("email"));
        user.setNickname(oauth2User.getAttribute("name"));
        return userRepository.save(user);
    }
    
    /**
     * 分配默认角色（纯响应式）
     */
    private Mono<Void> assignDefaultRole(UserEntity user) {
        return roleRepository.findByCode("USER")
                .flatMap(role -> {
                    // 假设有 UserRoleRepository.assignRole 方法
                    return Mono.empty(); // TODO: 实现角色分配
                });
    }
    
    /**
     * 生成本地 JWT Token 并写入响应头（纯响应式）
     */
    private Mono<Void> generateLocalTokens(WebFilterExchange webFilterExchange, UserEntity user) {
        String accessToken = tokenService.generateAccessToken(user.getOpenId(), null);
        String refreshToken = tokenService.generateRefreshToken(user.getOpenId(), null);
        
        return tokenService.storeToken(user.getOpenId(), accessToken, refreshToken)
                .doOnSuccess(v -> {
                    // 将 Token 写入响应头
                    ServerHttpResponse response = webFilterExchange.getExchange().getResponse();
                    response.getHeaders().set("X-Access-Token", accessToken);
                    response.getHeaders().set("X-Refresh-Token", refreshToken);
                    
                    // 也可以通过 Cookie 下发
                    ResponseCookie accessCookie = ResponseCookie.from("access_token", accessToken)
                            .httpOnly(true)
                            .secure(false) // 生产环境设为 true
                            .path("/")
                            .maxAge(Duration.ofHours(2))
                            .build();
                    response.addCookie(accessCookie);
                    
                    log.info("OIDC 用户登录成功，已生成本地 JWT: {}", user.getUsername());
                });
    }
    
    /**
     * 重定向到首页（纯响应式）
     */
    private Mono<Void> redirectToHomePage(WebFilterExchange webFilterExchange) {
        ServerHttpResponse response = webFilterExchange.getExchange().getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create("/"));
        return response.setComplete();
    }
}
```

**3. SecurityConfig 修改（纯 WebFlux 响应式）**

文件路径：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/SecurityConfig.java`

在现有 `securityWebFilterChain` 方法中新增 OIDC 配置：

```java
@Bean
public SecurityWebFilterChain securityWebFilterChain(
        ServerHttpSecurity http,
        @Autowired(required = false) OidcAuthenticationSuccessHandler oidcSuccessHandler) {
    
    http.csrf(csrf -> csrf.disable())
        .authorizeExchange(exchanges -> {
            exchanges
                .pathMatchers(SystemConstants.PUBLIC_PATHS.toArray(new String[0]))
                .permitAll()
                // OIDC 回调路径放行
                .pathMatchers("/login/oauth2/code/**", "/oauth2/authorization/**")
                .permitAll()
                .pathMatchers(SystemConstants.AUTHENTICATED_USER_PATHS.toArray(new String[0]))
                .authenticated();
            
            // 现有 RBAC 配置保持不变
            if (rbacEnabled) {
                exchanges
                    .pathMatchers("/internal/v1/interests/**")
                    .hasAnyRole("ADMIN", "INTEREST_GRAPH_EDITOR", "INTEREST_PUBLISHER")
                    .pathMatchers("/admin-api/v1/**")
                    .hasAnyRole(securityProperties.getAdminRoles())
                    .pathMatchers(securityProperties.getAdminPaths())
                    .hasAnyRole(securityProperties.getAdminRole(), "SUPER_ADMIN");
            } else {
                exchanges
                    .pathMatchers("/internal/v1/interests/**", "/admin-api/v1/**")
                    .authenticated()
                    .pathMatchers(securityProperties.getAdminPaths())
                    .authenticated();
            }
            exchanges.anyExchange().authenticated();
        })
        .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterAfter(oauth2ResourceServerFilter, SecurityWebFiltersOrder.AUTHENTICATION);
    
    // 如果启用了 Keycloak，添加 OIDC 登录支持（纯 WebFlux）
    if (oidcSuccessHandler != null) {
        http.oauth2Login(oauth2 -> oauth2
                .authenticationSuccessHandler(oidcSuccessHandler)
                // 认证失败处理器（响应式）
                .authenticationFailureHandler((webFilterExchange, exception) -> {
                    log.error("OIDC 认证失败", exception);
                    ServerHttpResponse response = webFilterExchange.getExchange().getResponse();
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return response.setComplete();
                })
        )
        // OAuth2 Client 支持（用于 Token 管理）
        .oauth2Client();
    }
    
    // 现有异常处理配置保持不变
    http.exceptionHandling(exceptionHandling -> exceptionHandling
            .authenticationEntryPoint((exchange, ex) -> Mono.deferContextual(ctx -> {
                Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
                String msg = messageUtils.get(locale, "auth.login_required");
                return ResponseUtil.unauthorized(exchange, msg);
            }))
            .accessDeniedHandler((exchange, denied) -> Mono.deferContextual(ctx -> {
                Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
                String msg = messageUtils.get(locale, "auth.access_denied");
                return ResponseUtil.forbidden(exchange, msg);
            })));
    
    return http.build();
}
```

**4. Keycloak 用户同步服务（纯 WebFlux 响应式）**

文件路径：`apex-application/src/main/java/com/apex/scaffold/application/service/KeycloakUserSyncService.java`

```java
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnBean(Keycloak.class)
public class KeycloakUserSyncService {
    
    private final Keycloak keycloakAdmin;
    private final KeycloakConfig keycloakConfig;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    
    /**
     * 从 Keycloak 同步用户到本地数据库（纯响应式）
     * 
     * 注意：Keycloak Admin Client 是阻塞式 API，需要在专用调度器上执行
     */
    public Mono<SyncResult> syncUsersFromKeycloak() {
        return Mono.fromCallable(() -> {
            RealmResource realm = keycloakAdmin.realm(keycloakConfig.getRealm());
            List<UserRepresentation> keycloakUsers = realm.users().list();
            log.info("从 Keycloak 获取到 {} 个用户", keycloakUsers.size());
            return keycloakUsers;
        })
        .subscribeOn(BlockingIoSchedulers.jdbc())  // 在 JDBC 调度器上执行阻塞调用
        .flatMapMany(Flux::fromIterable)
        .flatMap(this::syncUser)
        .reduce(new SyncResult(), (result, success) -> {
            if (success) {
                result.incrementSuccess();
            } else {
                result.incrementFailed();
            }
            return result;
        })
        .doOnSuccess(result -> log.info("用户同步完成: 成功 {}, 失败 {}", 
                result.getSuccessCount(), result.getFailedCount()));
    }
    
    /**
     * 同步单个用户（纯响应式）
     */
    private Mono<Boolean> syncUser(UserRepresentation kcUser) {
        String keycloakId = kcUser.getId();
        String email = kcUser.getEmail();
        String openId = "keycloak:" + keycloakId;
        
        if (email == null || email.isBlank()) {
            log.warn("Keycloak 用户 {} 没有邮箱，跳过同步", kcUser.getUsername());
            return Mono.just(false);
        }
        
        return userRepository.findByOpenId(openId)
                .switchIfEmpty(userRepository.findByEmail(email))
                .flatMap(existingUser -> updateUserFromKeycloak(existingUser, kcUser))
                .switchIfEmpty(Mono.defer(() -> createUserFromKeycloak(kcUser)))
                .flatMap(user -> syncUserRoles(user, kcUser))
                .thenReturn(true)
                .onErrorResume(e -> {
                    log.error("同步 Keycloak 用户失败: {}", email, e);
                    return Mono.just(false);
                });
    }
    
    /**
     * 从 Keycloak 创建本地用户（纯响应式）
     */
    private Mono<UserEntity> createUserFromKeycloak(UserRepresentation kcUser) {
        UserEntity user = new UserEntity();
        user.setUsername(kcUser.getUsername());
        user.setEmail(kcUser.getEmail());
        user.setNickname(buildNickname(kcUser));
        user.setOpenId("keycloak:" + kcUser.getId());
        user.setEnabled(kcUser.isEnabled());
        user.setSource("keycloak");
        user.setExternalId(kcUser.getId());
        user.setLastSyncAt(new Date());
        
        return userRepository.save(user)
                .doOnSuccess(u -> log.info("创建 Keycloak 用户: {}", u.getUsername()));
    }
    
    /**
     * 更新本地用户信息（纯响应式）
     */
    private Mono<UserEntity> updateUserFromKeycloak(UserEntity user, UserRepresentation kcUser) {
        user.setEmail(kcUser.getEmail());
        user.setNickname(buildNickname(kcUser));
        user.setEnabled(kcUser.isEnabled());
        user.setExternalId(kcUser.getId());
        user.setLastSyncAt(new Date());
        
        return userRepository.save(user)
                .doOnSuccess(u -> log.debug("更新 Keycloak 用户: {}", u.getUsername()));
    }
    
    /**
     * 同步用户角色（纯响应式）
     * 
     * 注意：Keycloak Admin Client 是阻塞式 API，需要在专用调度器上执行
     */
    private Mono<UserEntity> syncUserRoles(UserEntity user, UserRepresentation kcUser) {
        return Mono.fromCallable(() -> {
            RealmResource realm = keycloakAdmin.realm(keycloakConfig.getRealm());
            UserResource userResource = realm.users().get(kcUser.getId());
            List<RoleRepresentation> kcRoles = userResource.roles().realmLevel().listEffective();
            return kcRoles.stream()
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toList());
        })
        .subscribeOn(BlockingIoSchedulers.jdbc())
        .flatMapMany(Flux::fromIterable)
        .flatMap(roleName -> roleRepository.findByCode(roleName.toUpperCase()))
        .collectList()
        .flatMap(roles -> {
            // TODO: 实现角色关联更新（假设有 UserRoleRepository）
            // userRoleRepository.updateUserRoles(user.getId(), roles)
            return Mono.just(user);
        })
        .doOnSuccess(u -> log.debug("同步用户角色完成: {}", u.getUsername()));
    }
    
    /**
     * 构建用户昵称
     */
    private String buildNickname(UserRepresentation kcUser) {
        if (kcUser.getFirstName() != null && kcUser.getLastName() != null) {
            return kcUser.getFirstName() + " " + kcUser.getLastName();
        }
        if (kcUser.getFirstName() != null) {
            return kcUser.getFirstName();
        }
        return kcUser.getUsername();
    }
    
    /**
     * 同步结果统计
     */
    @Data
    public static class SyncResult {
        private int successCount = 0;
        private int failedCount = 0;
        
        public void incrementSuccess() {
            successCount++;
        }
        
        public void incrementFailed() {
            failedCount++;
        }
    }
}
```

**5. 社交登录配置 Handler（纯 WebFlux 响应式）**

文件路径：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/SocialLoginHandler.java`

```java
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "keycloak", name = "enabled", havingValue = "true")
public class SocialLoginHandler {
    
    private final MessageUtils messageUtils;
    
    /**
     * 获取可用的社交登录提供商列表（纯响应式）
     * GET /api/v1/auth/social/providers
     */
    public Mono<ServerResponse> listProviders(ServerRequest request) {
        List<SocialProvider> providers = Arrays.asList(
                new SocialProvider("github", "GitHub", "通过 GitHub 登录"),
                new SocialProvider("google", "Google", "通过 Google 登录"),
                new SocialProvider("wechat", "WeChat", "通过微信登录")
        );
        
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.success(providers));
    }
    
    /**
     * 初始化社交登录流程（纯响应式）
     * GET /api/v1/auth/social/login/{provider}
     * 
     * 重定向到 Keycloak，并通过 kc_idp_hint 参数指定社交登录提供商
     */
    public Mono<ServerResponse> initiateSocialLogin(ServerRequest request) {
        String provider = request.pathVariable("provider");
        
        // 验证 provider 是否合法
        List<String> validProviders = Arrays.asList("github", "google", "wechat");
        if (!validProviders.contains(provider)) {
            return Mono.deferContextual(ctx -> {
                Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
                String msg = messageUtils.get(locale, "auth.invalid_provider");
                return ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.fail(400, msg));
            });
        }
        
        // 构建重定向 URL（kc_idp_hint 告诉 Keycloak 使用哪个 IdP）
        String redirectUrl = String.format(
                "/oauth2/authorization/keycloak?kc_idp_hint=%s", 
                provider
        );
        
        return ServerResponse.temporaryRedirect(URI.create(redirectUrl)).build();
    }
    
    /**
     * 社交账号绑定（纯响应式）
     * POST /api/v1/auth/social/bind/{provider}
     * 
     * 将社交账号绑定到当前登录用户
     */
    public Mono<ServerResponse> bindSocialAccount(ServerRequest request) {
        String provider = request.pathVariable("provider");
        
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .cast(UsernamePasswordAuthenticationToken.class)
                .map(auth -> (AuthenticatedUser) auth.getPrincipal())
                .flatMap(user -> {
                    // TODO: 实现社交账号绑定逻辑
                    // 1. 重定向到 Keycloak 进行 OAuth 授权
                    // 2. 回调后保存 provider_user_id 到 t_user_social_binding 表
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.success("社交账号绑定成功"));
                })
                .switchIfEmpty(Mono.defer(() -> 
                    ServerResponse.status(HttpStatus.UNAUTHORIZED)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.fail(401, "未登录"))
                ));
    }
    
    /**
     * 解绑社交账号（纯响应式）
     * DELETE /api/v1/auth/social/unbind/{provider}
     */
    public Mono<ServerResponse> unbindSocialAccount(ServerRequest request) {
        String provider = request.pathVariable("provider");
        
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .cast(UsernamePasswordAuthenticationToken.class)
                .map(auth -> (AuthenticatedUser) auth.getPrincipal())
                .flatMap(user -> {
                    // TODO: 实现解绑逻辑
                    // 从 t_user_social_binding 表删除对应记录
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.success("社交账号解绑成功"));
                })
                .switchIfEmpty(Mono.defer(() -> 
                    ServerResponse.status(HttpStatus.UNAUTHORIZED)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.fail(401, "未登录"))
                ));
    }
    
    /**
     * 社交登录提供商 DTO
     */
    @Data
    @AllArgsConstructor
    public static class SocialProvider {
        private String id;
        private String name;
        private String description;
    }
}
```

**6. 路由配置新增（纯 WebFlux 响应式）**

文件路径：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/RouterConfig.java`

```java
// 在现有 RouterConfig 中新增路由（使用 WebFlux 函数式路由）
@Bean
@ConditionalOnProperty(prefix = "keycloak", name = "enabled", havingValue = "true")
public RouterFunction<ServerResponse> socialLoginRoutes(SocialLoginHandler handler) {
    return RouterFunctions.route()
            .GET("/api/v1/auth/social/providers", handler::listProviders)
            .GET("/api/v1/auth/social/login/{provider}", handler::initiateSocialLogin)
            .POST("/api/v1/auth/social/bind/{provider}", handler::bindSocialAccount)
            .DELETE("/api/v1/auth/social/unbind/{provider}", handler::unbindSocialAccount)
            .build();
}

/**
 * Keycloak 用户同步路由（仅管理员可访问）
 */
@Bean
@ConditionalOnProperty(prefix = "keycloak", name = "enabled", havingValue = "true")
public RouterFunction<ServerResponse> keycloakAdminRoutes(KeycloakAdminHandler handler) {
    return RouterFunctions.route()
            .POST("/admin-api/v1/keycloak/sync-users", handler::syncUsers)
            .GET("/admin-api/v1/keycloak/sync-status", handler::getSyncStatus)
            .build();
}
```

**7. Keycloak 管理 Handler（纯 WebFlux 响应式）**

文件路径：`apex-interfaces/src/main/java/com/apex/scaffold/interfaces/handler/KeycloakAdminHandler.java`

```java
@Component
@RequiredArgsConstructor
@ConditionalOnBean(KeycloakUserSyncService.class)
public class KeycloakAdminHandler {
    
    private final KeycloakUserSyncService userSyncService;
    
    /**
     * 手动触发用户同步（纯响应式）
     * POST /admin-api/v1/keycloak/sync-users
     */
    public Mono<ServerResponse> syncUsers(ServerRequest request) {
        return userSyncService.syncUsersFromKeycloak()
                .flatMap(result -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(result)))
                .onErrorResume(e -> {
                    log.error("用户同步失败", e);
                    return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.fail(500, "用户同步失败: " + e.getMessage()));
                });
    }
    
    /**
     * 获取同步状态（纯响应式）
     * GET /admin-api/v1/keycloak/sync-status
     */
    public Mono<ServerResponse> getSyncStatus(ServerRequest request) {
        // TODO: 实现同步状态查询（从 Redis 或数据库读取最后同步时间和结果）
        Map<String, Object> status = new HashMap<>();
        status.put("lastSyncAt", new Date());
        status.put("status", "success");
        
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.success(status));
    }
}
```

#### 3.3.4 数据库变更

**Flyway 迁移脚本**：`V30__add_keycloak_fields.sql`

```sql
-- 用户表新增字段，记录用户来源和外部 IdP 关联
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS source VARCHAR(50) DEFAULT 'local' COMMENT '用户来源：local/keycloak/wechat/github';
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS external_id VARCHAR(255) COMMENT '外部 IdP 用户ID';
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS last_sync_at TIMESTAMP COMMENT '最后同步时间（从 Keycloak）';

-- 创建索引
CREATE INDEX idx_user_source ON t_user(source);
CREATE INDEX idx_user_external_id ON t_user(external_id);

-- 社交账号绑定表（支持同一用户绑定多个社交账号）
CREATE TABLE IF NOT EXISTS t_user_social_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    provider VARCHAR(50) NOT NULL COMMENT '社交平台：github/google/wechat',
    provider_user_id VARCHAR(255) NOT NULL COMMENT '社交平台用户ID',
    provider_username VARCHAR(255) COMMENT '社交平台用户名',
    provider_email VARCHAR(255) COMMENT '社交平台邮箱',
    access_token TEXT COMMENT '社交平台 Access Token（加密存储）',
    refresh_token TEXT COMMENT '社交平台 Refresh Token（加密存储）',
    expires_at TIMESTAMP COMMENT 'Token 过期时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_provider_user(provider, provider_user_id),
    KEY idx_user_id(user_id),
    FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE
) COMMENT='用户社交账号绑定表';
```

## 四、实施步骤

### 阶段 1：Keycloak 服务器部署与配置（2 天）

**任务 1.1**：Docker Compose 部署 Keycloak（1 天）
- [ ] 修改 `docker-compose.yml`，新增 Keycloak 服务
- [ ] 配置持久化存储（使用 PostgreSQL）
- [ ] 配置 Realm 导入/导出机制
- [ ] 编写 Keycloak 初始化脚本

**任务 1.2**：Keycloak Realm 配置（1 天）
- [ ] 创建 `apex-realm` Realm
- [ ] 配置 OIDC Client（apex-scaffold）
- [ ] 配置 Admin Client（apex-admin）用于用户管理
- [ ] 配置社交登录 IdP（GitHub、Google）
- [ ] 配置 LDAP/AD 用户联邦（可选）
- [ ] 配置 MFA 策略（TOTP、WebAuthn）
- [ ] 导出 Realm 配置为 JSON（用于自动化部署）

### 阶段 2：Spring Boot 集成开发（5 天）

**任务 2.1**：依赖和配置文件（0.5 天）
- [ ] 修改 `pom.xml`，新增 Keycloak Admin Client 依赖
- [ ] 修改 `application.yml`，新增 Keycloak 配置项
- [ ] 修改 `application-dev.yml`，配置开发环境 Keycloak

**任务 2.2**：核心组件开发（纯 WebFlux 响应式）（3 天）
- [ ] 实现 `KeycloakConfig` 配置类
- [ ] 实现 `OidcAuthenticationSuccessHandler` OIDC 认证成功处理器（WebFlux ServerAuthenticationSuccessHandler）
- [ ] 修改 `SecurityConfig`，集成 OAuth2 Login（使用 WebFlux oauth2Login()）
- [ ] 实现 `KeycloakUserSyncService` 用户同步服务（Keycloak Admin Client 阻塞调用使用 BlockingIoSchedulers.jdbc()）
- [ ] 实现 `SocialLoginHandler` 社交登录 Handler（WebFlux ServerRequest/ServerResponse）
- [ ] 实现 `KeycloakAdminHandler` 管理 Handler（WebFlux ServerRequest/ServerResponse）
- [ ] 修改 `RouterConfig`，新增社交登录和管理路由（WebFlux RouterFunction）

**任务 2.3**：数据库迁移（0.5 天）
- [ ] 编写 Flyway 迁移脚本 `V30__add_keycloak_fields.sql`
- [ ] 测试数据库迁移（MySQL + PostgreSQL）

**任务 2.4**：集成测试（1 天）
- [ ] 编写 Keycloak Testcontainers 测试
- [ ] 测试 OIDC 登录流程（WebFlux WebTestClient）
- [ ] 测试用户同步功能
- [ ] 测试社交登录重定向
- [ ] 验证所有实现均为响应式（无阻塞调用）

### 阶段 3：功能扩展与优化（3 天）

**任务 3.1**：多因素认证（MFA）（1 天）
- [ ] 配置 Keycloak TOTP 认证流程
- [ ] 前端集成 QR Code 扫描（Google Authenticator）
- [ ] 配置 WebAuthn（可选）

**任务 3.2**：用户联邦集成（1 天）
- [ ] 配置 LDAP User Federation（如有需求）
- [ ] 编写用户同步定时任务（WebFlux Scheduler）
- [ ] 测试 LDAP 用户登录

**任务 3.3**：Token 互通与转换（1 天）
- [ ] 实现 Keycloak JWT → 本地 JWT 转换（响应式）
- [ ] 实现本地 JWT 黑名单与 Keycloak Token 撤销联动
- [ ] 优化 Token 滑动刷新策略

### 阶段 4：测试与文档（2 天）

**任务 4.1**：端到端测试（1 天）
- [ ] 测试现有 JWT 认证流程（确保不受影响）
- [ ] 测试 OIDC 登录流程
- [ ] 测试社交登录（GitHub、Google）
- [ ] 测试 MFA 登录
- [ ] 测试用户同步
- [ ] 性能测试（JWT vs OIDC）
- [ ] 验证 WebFlux 响应式特性（使用 Reactor 调试工具）

**任务 4.2**：文档编写（1 天）
- [ ] 编写 Keycloak 部署指南
- [ ] 编写 OIDC 集成文档
- [ ] 编写社交登录配置指南
- [ ] 编写 MFA 配置指南
- [ ] 更新 API 文档（新增社交登录端点）
- [ ] 更新前端集成指南
- [ ] 编写 WebFlux 响应式最佳实践文档

## 五、WebFlux 响应式编程关键点

### 5.1 核心原则

**✅ 必须遵守的原则**

1. **所有 I/O 操作必须是响应式的**
   - 数据库操作：使用 R2DBC 或 Mono.fromCallable() + subscribeOn()
   - HTTP 调用：使用 WebClient（不使用 RestTemplate）
   - Redis 操作：使用 ReactiveRedisTemplate（不使用 RedisTemplate）
   - 文件操作：使用 DataBufferUtils（不使用 InputStream/OutputStream）

2. **阻塞调用必须隔离到专用调度器**
   - Keycloak Admin Client：`subscribeOn(BlockingIoSchedulers.jdbc())`
   - MyBatis-Plus 查询：`Mono.fromCallable(...).subscribeOn(BlockingIoSchedulers.jdbc())`
   - LDAP 查询：`subscribeOn(BlockingIoSchedulers.jdbc())`

3. **避免 .block() 调用**
   - ❌ 错误：`userRepository.findById(1L).block()`
   - ✅ 正确：`userRepository.findById(1L).flatMap(...)`

4. **使用响应式安全上下文**
   - ❌ 错误：`SecurityContextHolder.getContext()`
   - ✅ 正确：`ReactiveSecurityContextHolder.getContext()`

5. **使用 WebFlux 路由和处理器**
   - ❌ 错误：`@RestController` + `@RequestMapping`
   - ✅ 正确：`RouterFunction<ServerResponse>` + Handler

### 5.2 常见陷阱与解决方案

| 陷阱 | 问题 | 解决方案 |
|------|------|---------|
| 同步数据库查询 | MyBatis-Plus 查询是阻塞的 | `Mono.fromCallable(() -> mapper.selectById(id)).subscribeOn(BlockingIoSchedulers.jdbc())` |
| Keycloak Admin Client | 所有 API 都是阻塞的 | `Mono.fromCallable(() -> keycloak.realm("x").users().list()).subscribeOn(BlockingIoSchedulers.jdbc())` |
| 循环中的响应式调用 | `for` 循环无法处理 Mono/Flux | 使用 `Flux.fromIterable(...).flatMap(...)` |
| 多个独立操作 | 顺序执行浪费时间 | 使用 `Mono.zip()` 或 `Flux.merge()` 并行执行 |
| 异常处理 | try-catch 无法捕获响应式异常 | 使用 `onErrorResume()` 或 `onErrorReturn()` |

### 5.3 代码示例对比

**❌ 错误示例（使用 Spring MVC 阻塞式）**

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserMapper userMapper;
    
    @GetMapping("/{id}")
    public Result<User> getUser(@PathVariable Long id) {
        User user = userMapper.selectById(id);  // 阻塞调用
        return Result.success(user);
    }
}
```

**✅ 正确示例（使用 WebFlux 响应式）**

```java
@Component
@RequiredArgsConstructor
public class UserHandler {
    
    private final UserRepository userRepository;  // R2DBC 响应式仓储
    
    public Mono<ServerResponse> getUser(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        
        return userRepository.findById(id)  // 纯响应式查询
                .flatMap(user -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(user)))
                .switchIfEmpty(ServerResponse.notFound().build());
    }
}
```

### 5.4 Keycloak Admin Client 响应式包装

由于 Keycloak Admin Client 是完全阻塞式的，必须严格隔离：

```java
// ❌ 错误：直接在 WebFlux 线程调用
public Mono<List<UserRepresentation>> getKeycloakUsers() {
    RealmResource realm = keycloak.realm("apex-realm");
    List<UserRepresentation> users = realm.users().list();  // 阻塞！会阻塞 Netty 事件循环
    return Mono.just(users);
}

// ✅ 正确：使用 Mono.fromCallable + subscribeOn 隔离
public Mono<List<UserRepresentation>> getKeycloakUsers() {
    return Mono.fromCallable(() -> {
        RealmResource realm = keycloak.realm("apex-realm");
        return realm.users().list();  // 在专用线程池执行
    }).subscribeOn(BlockingIoSchedulers.jdbc());  // 隔离到 JDBC 调度器
}

// ✅ 正确：批量处理时使用 flatMapMany
public Flux<UserEntity> syncAllUsers() {
    return Mono.fromCallable(() -> {
        RealmResource realm = keycloak.realm("apex-realm");
        return realm.users().list();
    })
    .subscribeOn(BlockingIoSchedulers.jdbc())
    .flatMapMany(Flux::fromIterable)  // 转为 Flux
    .flatMap(kcUser -> syncUser(kcUser));  // 并行处理每个用户
}
```

### 5.5 验证响应式实现

**使用 Reactor 调试工具**

```java
// 在测试或开发环境启用 Reactor 调试
static {
    Hooks.onOperatorDebug();  // 启用调试（性能影响，仅开发环境）
}

// 检测阻塞调用
BlockHound.install();  // 集成测试中使用，检测到阻塞调用会抛异常
```

**性能指标验证**

- Netty 线程池：`eventLoopThreads` 应保持低占用（< 50%）
- 吞吐量：响应式实现应比阻塞式提升 3-5 倍
- 延迟：P99 延迟应降低 50% 以上

## 六、风险评估与缓解

### 6.1 技术风险

| 风险项 | 风险等级 | 缓解措施 |
|--------|---------|---------|
| Keycloak 与现有 JWT 体系冲突 | 🟡 中 | 通过 ConditionalOnProperty 实现完全隔离，Keycloak 关闭时不影响现有流程 |
| Token 互通性问题 | 🟡 中 | OIDC 登录后生成本地 JWT，保持 Token 格式一致 |
| 性能下降（额外网络调用） | 🟢 低 | 使用 Spring Security OAuth2 Client 内置缓存，本地验证 JWT 签名 |
| Keycloak 单点故障 | 🟡 中 | 部署 Keycloak 集群，配置健康检查，Keycloak 故障时降级到现有 JWT 认证 |

### 6.2 业务风险

| 风险项 | 风险等级 | 缓解措施 |
|--------|---------|---------|
| 用户账号重复（邮箱冲突） | 🟡 中 | 实现账号绑定机制，同一邮箱多平台登录关联到同一用户 |
| 现有用户无法使用 OIDC 登录 | 🟢 低 | 保留现有登录方式，OIDC 作为可选登录方式 |
| 社交平台 API 变更 | 🟢 低 | Keycloak 封装社交登录细节，平台 API 变更时仅需更新 Keycloak 配置 |

## 七、配置开关设计

### 7.1 Feature Toggle

```yaml
# 渐进式启用 Keycloak 功能
keycloak:
  enabled: false  # 总开关
  features:
    oidc-login: true        # OIDC 标准登录
    social-login: true      # 社交登录
    mfa: false              # 多因素认证
    user-federation: false  # LDAP/AD 用户联邦
    user-sync: false        # 自动用户同步
```

### 7.2 降级策略

```yaml
# Keycloak 不可用时的降级配置
resilience:
  keycloak:
    circuit-breaker:
      failure-rate-threshold: 50  # 失败率阈值
      wait-duration-in-open-state: 60s  # 熔断器打开时间
    fallback:
      use-local-jwt: true  # 降级到现有 JWT 认证
```

## 八、验收标准

### 8.1 功能验收

- [ ] 现有 JWT 认证流程不受影响，现有客户端无需修改
- [ ] OIDC 登录流程完整可用，支持授权码流程
- [ ] 社交登录（GitHub、Google）可用，用户信息正确同步
- [ ] MFA 登录流程可用（TOTP）
- [ ] 用户同步功能可用，Keycloak 用户同步到本地数据库
- [ ] Token 互通，OIDC Token 和本地 JWT 都能访问受保护资源
- [ ] Keycloak 故障时，系统降级到现有 JWT 认证

### 8.2 性能验收

- [ ] OIDC 登录响应时间 < 2s（含 Keycloak 重定向）
- [ ] JWT 验证性能不下降（< 10ms）
- [ ] 用户同步批量处理 1000 用户 < 5s

### 8.3 安全验收

- [ ] OIDC Token 验证正确，伪造 Token 被拒绝
- [ ] 社交登录账号绑定安全，防止账号劫持
- [ ] MFA 强制开启后，无法绕过
- [ ] 敏感配置（Client Secret）加密存储

## 八、总结

本方案采用渐进式集成策略，在不影响现有 JWT 认证体系的前提下，通过 Spring Security OAuth2 标准实现，集成 Keycloak 的多种认证方式：

- **OIDC 标准协议**：支持企业级单点登录（SSO）
- **社交登录**：快速接入 GitHub、Google、微信等平台
- **多因素认证**：提升账号安全性
- **用户联邦**：对接现有 LDAP/AD 用户目录

集成后，项目将具备企业级 IAM 能力，同时保持轻量化和高性能特性。
