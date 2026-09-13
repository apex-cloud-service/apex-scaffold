# SSO与OAuth2统一认证授权实现方案

> **项目**: Apex Scaffold v1.2.0  
> **生成时间**: 2026-09-08  
> **技术栈**: Spring Boot 3.5.15 + Spring Security 6.5.0 + Spring Authorization Server 1.4.0 + WebFlux  

---

## 一、方案概述

### 1.1 需求确认

根据需求调研和用户确认，实现以下功能：

1. **SSO单点登录**: 实现上述4项（基于Spring Authorization Server构建标准OAuth2授权服务器、集成到现有项目、保持JWT机制、对接第三方SSO）
2. **统一权限认证**: 在现有RBAC基础上增强OAuth2 Scope权限控制
3. **OAuth2授权模式**: 支持授权码模式、客户端模式、密码模式、刷新令牌模式
4. **前端集成**: 前端仍使用现有JWT登录，OAuth2仅用于第三方应用接入
5. **客户端管理**: 内置客户端用配置文件，第三方客户端用数据库动态管理

### 1.2 技术选型

| 组件 | 选型 | 版本 | 说明 |
|-----|------|------|------|
| 授权服务器 | Spring Authorization Server | 1.4.0 | Spring官方OAuth2授权服务器 |
| 认证框架 | Spring Security | 6.5.0 | 已集成，无需额外引入 |
| 响应式支持 | Spring WebFlux | - | 现有技术栈 |
| 数据持久化 | MyBatis-Plus + MySQL | 3.5.10.1 | 现有技术栈 |
| 缓存 | Redis + Redisson | 3.36.0 | 现有技术栈 |
| Token格式 | JWT | 0.12.6 | 保持现有JWT机制 |

### 1.3 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                        Apex Scaffold                             │
│                                                                   │
│  ┌───────────────────┐        ┌──────────────────────┐          │
│  │  前端应用          │        │  第三方应用           │          │
│  │ (apex-admin-web)  │        │ (外部系统)            │          │
│  └─────────┬─────────┘        └──────────┬───────────┘          │
│            │ JWT登录                     │ OAuth2授权            │
│            │                             │                       │
│  ┌─────────▼─────────────────────────────▼────────────────────┐ │
│  │           JWT认证过滤器 + OAuth2资源服务器                   │ │
│  │  ┌──────────────────┐  ┌────────────────────────────────┐  │ │
│  │  │ JWT Token验证    │  │ OAuth2 Token验证               │  │ │
│  │  │ (现有机制)        │  │ (Spring Authorization Server)  │  │ │
│  │  └──────────────────┘  └────────────────────────────────┘  │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │              OAuth2授权服务器 (Authorization Server)           │ │
│  │                                                               │ │
│  │  ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐   │ │
│  │  │ 授权端点     │  │ Token端点     │  │ 客户端管理        │   │ │
│  │  │ /authorize  │  │ /token       │  │ (/oauth2/clients) │   │ │
│  │  └─────────────┘  └──────────────┘  └──────────────────┘   │ │
│  │                                                               │ │
│  │  授权模式:                                                     │ │
│  │  ✓ 授权码模式 (authorization_code + PKCE)                     │ │
│  │  ✓ 客户端模式 (client_credentials)                            │ │
│  │  ✓ 密码模式 (password)                                        │ │
│  │  ✓ 刷新令牌 (refresh_token)                                   │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │                   业务资源服务器                               │ │
│  │  /api/v1/**  (JWT认证)                                        │ │
│  │  /oauth2/api/** (OAuth2认证，供第三方应用调用)                 │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │              统一权限控制 (RBAC + OAuth2 Scope)               │ │
│  │  - 用户角色权限 (ROLE_ADMIN, ROLE_USER)                       │ │
│  │  - OAuth2作用域 (read, write, user:profile, admin:users)     │ │
│  └──────────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────┘
```

---

## 二、当前状态分析

### 2.1 现有认证授权实现

#### 已实现功能 ✅

1. **JWT认证体系**
   - AccessToken + RefreshToken双Token机制
   - Token存储：Redis (Redisson)
   - Token黑名单：支持注销和主动失效
   - Token续期：自动滑动刷新 (剩余30分钟时自动续期)
   - 核心类：`TokenServiceImpl`、`JwtAuthenticationFilter`

2. **多种登录策略**
   - 微信授权码登录 (WX_CODE)
   - 微信一键登录 (WX_PHONE)
   - 短信验证码登录 (SMS)
   - 账号密码登录 (PASSWORD)
   - 创蓝一键登录 (CHUANGLAN_PHONE/SMS)
   - 核心类：`AuthService`、`LoginStrategyProvider`

3. **RBAC权限模型**
   - 用户-角色-权限三级模型
   - 数据表：sys_user, sys_role, sys_user_role, sys_permission
   - 数据权限：部门级、租户级隔离
   - 权限缓存：Caffeine本地缓存 (60秒TTL)
   - 核心类：`SecurityConfig`、`RoleRepository`

4. **安全防护机制**
   - IP限流：默认60秒120次
   - 登录保护：失败3次后需验证码
   - Token黑名单：注销后立即失效
   - 扫描流量拦截：UA检查

#### 待增强功能 🚧

1. **OAuth2标准协议支持**
   - ❌ 授权码模式 (authorization_code)
   - ❌ 客户端模式 (client_credentials)
   - ❌ 密码模式 (password)
   - ✅ 刷新令牌 (refresh_token) - 已有JWT刷新机制，需适配OAuth2标准

2. **客户端管理**
   - ❌ 客户端注册表 (oauth2_registered_client)
   - ❌ 客户端CRUD接口
   - ❌ 客户端密钥管理
   - ❌ 客户端权限作用域

3. **授权服务器端点**
   - ❌ 授权端点 /oauth2/authorize
   - ❌ Token端点 /oauth2/token
   - ❌ Token撤销端点 /oauth2/revoke
   - ❌ Token自省端点 /oauth2/introspect
   - ❌ JWKS端点 /oauth2/jwks

4. **OAuth2资源服务器**
   - ❌ OAuth2 Token验证
   - ❌ Scope权限控制
   - ❌ 资源保护端点

### 2.2 依赖现状

当前项目依赖：
```xml
<spring-boot.version>3.5.15</spring-boot.version>
<spring-security.version>6.5.0</spring-security.version>
<jwt.version>0.12.6</jwt.version>
```

需要新增依赖：
```xml
<spring-authorization-server.version>1.4.0</spring-authorization-server.version>
```

---

## 三、实现方案

### 3.1 Phase 1: 数据库设计与迁移脚本

#### 3.1.1 OAuth2客户端注册表

创建Flyway迁移脚本：`V15__oauth2_authorization_server_schema.sql`

```sql
-- OAuth2 客户端注册表 (符合Spring Authorization Server标准schema)
CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) NOT NULL COMMENT '客户端唯一标识',
    client_id VARCHAR(100) NOT NULL COMMENT '客户端ID',
    client_id_issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '客户端ID签发时间',
    client_secret VARCHAR(200) COMMENT '客户端密钥(BCrypt加密)',
    client_secret_expires_at TIMESTAMP COMMENT '客户端密钥过期时间',
    client_name VARCHAR(200) NOT NULL COMMENT '客户端名称',
    client_authentication_methods VARCHAR(1000) NOT NULL COMMENT '客户端认证方式(JSON数组)',
    authorization_grant_types VARCHAR(1000) NOT NULL COMMENT '授权类型(JSON数组)',
    redirect_uris TEXT COMMENT '重定向URI(JSON数组)',
    post_logout_redirect_uris TEXT COMMENT '登出后重定向URI(JSON数组)',
    scopes VARCHAR(1000) NOT NULL COMMENT '权限作用域(JSON数组)',
    client_settings TEXT NOT NULL COMMENT '客户端设置(JSON对象)',
    token_settings TEXT NOT NULL COMMENT 'Token设置(JSON对象)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_id (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2客户端注册表';

-- OAuth2 授权记录表
CREATE TABLE oauth2_authorization (
    id VARCHAR(100) NOT NULL COMMENT '授权唯一标识',
    registered_client_id VARCHAR(100) NOT NULL COMMENT '客户端ID',
    principal_name VARCHAR(200) NOT NULL COMMENT '用户标识(OpenID)',
    authorization_grant_type VARCHAR(100) NOT NULL COMMENT '授权类型',
    authorized_scopes VARCHAR(1000) COMMENT '已授权的作用域',
    attributes TEXT COMMENT '授权属性(JSON对象)',
    state VARCHAR(500) COMMENT 'OAuth2 state参数',
    authorization_code_value TEXT COMMENT '授权码值',
    authorization_code_issued_at TIMESTAMP COMMENT '授权码签发时间',
    authorization_code_expires_at TIMESTAMP COMMENT '授权码过期时间',
    authorization_code_metadata TEXT COMMENT '授权码元数据',
    access_token_value TEXT COMMENT '访问令牌值',
    access_token_issued_at TIMESTAMP COMMENT '访问令牌签发时间',
    access_token_expires_at TIMESTAMP COMMENT '访问令牌过期时间',
    access_token_metadata TEXT COMMENT '访问令牌元数据',
    access_token_type VARCHAR(100) COMMENT '访问令牌类型',
    access_token_scopes VARCHAR(1000) COMMENT '访问令牌作用域',
    oidc_id_token_value TEXT COMMENT 'OIDC ID令牌值',
    oidc_id_token_issued_at TIMESTAMP COMMENT 'OIDC ID令牌签发时间',
    oidc_id_token_expires_at TIMESTAMP COMMENT 'OIDC ID令牌过期时间',
    oidc_id_token_metadata TEXT COMMENT 'OIDC ID令牌元数据',
    refresh_token_value TEXT COMMENT '刷新令牌值',
    refresh_token_issued_at TIMESTAMP COMMENT '刷新令牌签发时间',
    refresh_token_expires_at TIMESTAMP COMMENT '刷新令牌过期时间',
    refresh_token_metadata TEXT COMMENT '刷新令牌元数据',
    user_code_value TEXT COMMENT '用户码值',
    user_code_issued_at TIMESTAMP COMMENT '用户码签发时间',
    user_code_expires_at TIMESTAMP COMMENT '用户码过期时间',
    user_code_metadata TEXT COMMENT '用户码元数据',
    device_code_value TEXT COMMENT '设备码值',
    device_code_issued_at TIMESTAMP COMMENT '设备码签发时间',
    device_code_expires_at TIMESTAMP COMMENT '设备码过期时间',
    device_code_metadata TEXT COMMENT '设备码元数据',
    PRIMARY KEY (id),
    INDEX idx_registered_client_id (registered_client_id),
    INDEX idx_principal_name (principal_name),
    INDEX idx_authorization_code_value (authorization_code_value(255)),
    INDEX idx_access_token_value (access_token_value(255)),
    INDEX idx_refresh_token_value (refresh_token_value(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2授权记录表';

-- OAuth2 授权确认表 (用户同意授权记录)
CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL COMMENT '客户端ID',
    principal_name VARCHAR(200) NOT NULL COMMENT '用户标识(OpenID)',
    authorities VARCHAR(1000) NOT NULL COMMENT '已授权的权限(JSON数组)',
    PRIMARY KEY (registered_client_id, principal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2授权确认表';

-- 初始化内置客户端 (apex-admin-web 前端应用)
INSERT INTO oauth2_registered_client (
    id, client_id, client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'apex-admin-web-client-001',
    'apex-admin-web',
    'Apex Admin Web后台管理系统',
    '["client_secret_basic"]',
    '["authorization_code","refresh_token"]',
    '["http://localhost:5173/oauth2/callback","https://admin.example.com/oauth2/callback"]',
    '["read","write","user:profile","admin:users"]',
    '{"@class":"java.util.HashMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.HashMap","settings.token.authorization-code-time-to-live":["java.time.Duration",300],"settings.token.access-token-time-to-live":["java.time.Duration",7200],"settings.token.refresh-token-time-to-live":["java.time.Duration",604800],"settings.token.reuse-refresh-tokens":false}'
);

-- 初始化机器对机器服务客户端 (用于服务间调用)
INSERT INTO oauth2_registered_client (
    id, client_id, client_secret, client_name,
    client_authentication_methods,
    authorization_grant_types,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'apex-m2m-service-001',
    'apex-m2m-service',
    '$2a$10$hashed_secret_placeholder', -- 实际使用时需用BCrypt加密
    'Apex M2M服务客户端',
    '["client_secret_basic","client_secret_post"]',
    '["client_credentials"]',
    '["api:read","api:write"]',
    '{"@class":"java.util.HashMap"}',
    '{"@class":"java.util.HashMap","settings.token.access-token-time-to-live":["java.time.Duration",3600]}'
);
```

#### 3.1.2 OAuth2 Scope权限映射表

```sql
-- OAuth2作用域权限映射表 (Scope -> 系统权限映射)
CREATE TABLE oauth2_scope_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    scope VARCHAR(100) NOT NULL COMMENT 'OAuth2作用域',
    permission_code VARCHAR(100) NOT NULL COMMENT '系统权限标识',
    description VARCHAR(500) COMMENT '描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_permission (scope, permission_code),
    INDEX idx_scope (scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2作用域权限映射表';

-- 初始化标准Scope映射
INSERT INTO oauth2_scope_permission (scope, permission_code, description) VALUES
('read', 'SCOPE_READ', '读取基本资源'),
('write', 'SCOPE_WRITE', '写入基本资源'),
('user:profile', 'SCOPE_USER_PROFILE', '读取用户个人信息'),
('user:update', 'SCOPE_USER_UPDATE', '更新用户个人信息'),
('admin:users', 'SCOPE_ADMIN_USERS', '管理用户'),
('admin:roles', 'SCOPE_ADMIN_ROLES', '管理角色权限'),
('api:read', 'SCOPE_API_READ', 'API只读访问'),
('api:write', 'SCOPE_API_WRITE', 'API读写访问');
```

### 3.2 Phase 2: Maven依赖配置

修改 `/apex-scaffold/pom.xml`，在 `<properties>` 中添加：

```xml
<spring-authorization-server.version>1.4.0</spring-authorization-server.version>
```

在 `<dependencyManagement>` 中添加：

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-authorization-server</artifactId>
    <version>${spring-authorization-server.version}</version>
</dependency>
```

修改 `/apex-interfaces/pom.xml`，添加依赖：

```xml
<!-- Spring Authorization Server -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-authorization-server</artifactId>
</dependency>

<!-- Spring Security OAuth2 Resource Server (已包含在starter-security中，无需额外引入) -->
```

### 3.3 Phase 3: 领域层实现

#### 3.3.1 OAuth2客户端领域实体

位置：`/apex-domain/src/main/java/com/apex/scaffold/domain/model/entity/OAuth2RegisteredClientEntity.java`

```java
package com.apex.scaffold.domain.model.entity;

import java.time.Instant;
import java.util.Set;
import lombok.Data;

/**
 * OAuth2注册客户端领域实体
 *
 * @author Apex Team
 * @since 2.0.0
 */
@Data
public class OAuth2RegisteredClientEntity {
    
    /** 客户端唯一标识 */
    private String id;
    
    /** 客户端ID */
    private String clientId;
    
    /** 客户端ID签发时间 */
    private Instant clientIdIssuedAt;
    
    /** 客户端密钥 (BCrypt加密) */
    private String clientSecret;
    
    /** 客户端密钥过期时间 */
    private Instant clientSecretExpiresAt;
    
    /** 客户端名称 */
    private String clientName;
    
    /** 客户端认证方式 */
    private Set<String> clientAuthenticationMethods;
    
    /** 授权类型 */
    private Set<String> authorizationGrantTypes;
    
    /** 重定向URI */
    private Set<String> redirectUris;
    
    /** 登出后重定向URI */
    private Set<String> postLogoutRedirectUris;
    
    /** 权限作用域 */
    private Set<String> scopes;
    
    /** 客户端设置 (JSON) */
    private String clientSettings;
    
    /** Token设置 (JSON) */
    private String tokenSettings;
}
```

#### 3.3.2 OAuth2客户端仓储接口

位置：`/apex-domain/src/main/java/com/apex/scaffold/domain/repository/OAuth2RegisteredClientRepository.java`

```java
package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OAuth2注册客户端仓储接口
 *
 * @author Apex Team
 * @since 2.0.0
 */
public interface OAuth2RegisteredClientRepository {
    
    /**
     * 根据客户端ID查找客户端
     *
     * @param clientId 客户端ID
     * @return 客户端实体
     */
    Mono<OAuth2RegisteredClientEntity> findByClientId(String clientId);
    
    /**
     * 根据主键ID查找客户端
     *
     * @param id 主键ID
     * @return 客户端实体
     */
    Mono<OAuth2RegisteredClientEntity> findById(String id);
    
    /**
     * 保存客户端
     *
     * @param client 客户端实体
     * @return 保存后的客户端实体
     */
    Mono<OAuth2RegisteredClientEntity> save(OAuth2RegisteredClientEntity client);
    
    /**
     * 删除客户端
     *
     * @param id 主键ID
     * @return 删除结果
     */
    Mono<Void> deleteById(String id);
    
    /**
     * 分页查询客户端列表
     *
     * @param page 页码
     * @param size 每页大小
     * @return 客户端列表
     */
    Flux<OAuth2RegisteredClientEntity> findAll(int page, int size);
    
    /**
     * 统计客户端总数
     *
     * @return 客户端总数
     */
    Mono<Long> count();
}
```

### 3.4 Phase 4: 基础设施层实现

#### 3.4.1 OAuth2客户端持久化对象

位置：`/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/po/OAuth2RegisteredClientPO.java`

```java
package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * OAuth2注册客户端持久化对象
 *
 * @author Apex Team
 * @since 2.0.0
 */
@Data
@TableName("oauth2_registered_client")
public class OAuth2RegisteredClientPO {
    
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    
    @TableField("client_id")
    private String clientId;
    
    @TableField("client_id_issued_at")
    private LocalDateTime clientIdIssuedAt;
    
    @TableField("client_secret")
    private String clientSecret;
    
    @TableField("client_secret_expires_at")
    private LocalDateTime clientSecretExpiresAt;
    
    @TableField("client_name")
    private String clientName;
    
    @TableField("client_authentication_methods")
    private String clientAuthenticationMethods;
    
    @TableField("authorization_grant_types")
    private String authorizationGrantTypes;
    
    @TableField("redirect_uris")
    private String redirectUris;
    
    @TableField("post_logout_redirect_uris")
    private String postLogoutRedirectUris;
    
    @TableField("scopes")
    private String scopes;
    
    @TableField("client_settings")
    private String clientSettings;
    
    @TableField("token_settings")
    private String tokenSettings;
}
```

#### 3.4.2 MyBatis-Plus Mapper

位置：`/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/persistence/mapper/OAuth2RegisteredClientMapper.java`

```java
package com.apex.scaffold.infrastructure.persistence.mapper;

import com.apex.scaffold.infrastructure.persistence.po.OAuth2RegisteredClientPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth2注册客户端Mapper
 *
 * @author Apex Team
 * @since 2.0.0
 */
@Mapper
public interface OAuth2RegisteredClientMapper extends BaseMapper<OAuth2RegisteredClientPO> {
}
```

#### 3.4.3 仓储实现

位置：`/apex-infrastructure/src/main/java/com/apex/scaffold/infrastructure/repository/OAuth2RegisteredClientRepositoryImpl.java`

```java
package com.apex.scaffold.infrastructure.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import com.apex.scaffold.domain.repository.OAuth2RegisteredClientRepository;
import com.apex.scaffold.infrastructure.persistence.mapper.OAuth2RegisteredClientMapper;
import com.apex.scaffold.infrastructure.persistence.po.OAuth2RegisteredClientPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OAuth2注册客户端仓储实现
 *
 * @author Apex Team
 * @since 2.0.0
 */
@Repository
@RequiredArgsConstructor
public class OAuth2RegisteredClientRepositoryImpl implements OAuth2RegisteredClientRepository {
    
    private final OAuth2RegisteredClientMapper mapper;
    
    @Override
    public Mono<OAuth2RegisteredClientEntity> findByClientId(String clientId) {
        return Mono.fromCallable(() -> {
            LambdaQueryWrapper<OAuth2RegisteredClientPO> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(OAuth2RegisteredClientPO::getClientId, clientId);
            OAuth2RegisteredClientPO po = mapper.selectOne(wrapper);
            return po != null ? toEntity(po) : null;
        }).subscribeOn(BlockingIoSchedulers.single());
    }
    
    @Override
    public Mono<OAuth2RegisteredClientEntity> findById(String id) {
        return Mono.fromCallable(() -> {
            OAuth2RegisteredClientPO po = mapper.selectById(id);
            return po != null ? toEntity(po) : null;
        }).subscribeOn(BlockingIoSchedulers.single());
    }
    
    @Override
    public Mono<OAuth2RegisteredClientEntity> save(OAuth2RegisteredClientEntity client) {
        return Mono.fromCallable(() -> {
            OAuth2RegisteredClientPO po = toPO(client);
            if (client.getId() != null && mapper.selectById(client.getId()) != null) {
                mapper.updateById(po);
            } else {
                mapper.insert(po);
            }
            return client;
        }).subscribeOn(BlockingIoSchedulers.single());
    }
    
    @Override
    public Mono<Void> deleteById(String id) {
        return Mono.fromRunnable(() -> mapper.deleteById(id))
                .subscribeOn(BlockingIoSchedulers.single())
                .then();
    }
    
    @Override
    public Flux<OAuth2RegisteredClientEntity> findAll(int page, int size) {
        return Mono.fromCallable(() -> {
            Page<OAuth2RegisteredClientPO> poPage = mapper.selectPage(
                    new Page<>(page, size), null);
            return poPage.getRecords().stream()
                    .map(this::toEntity)
                    .collect(Collectors.toList());
        }).subscribeOn(BlockingIoSchedulers.single())
                .flatMapMany(Flux::fromIterable);
    }
    
    @Override
    public Mono<Long> count() {
        return Mono.fromCallable(mapper::selectCount)
                .subscribeOn(BlockingIoSchedulers.single());
    }
    
    private OAuth2RegisteredClientEntity toEntity(OAuth2RegisteredClientPO po) {
        OAuth2RegisteredClientEntity entity = new OAuth2RegisteredClientEntity();
        entity.setId(po.getId());
        entity.setClientId(po.getClientId());
        entity.setClientIdIssuedAt(po.getClientIdIssuedAt() != null 
                ? po.getClientIdIssuedAt().atZone(ZoneId.systemDefault()).toInstant() 
                : null);
        entity.setClientSecret(po.getClientSecret());
        entity.setClientSecretExpiresAt(po.getClientSecretExpiresAt() != null 
                ? po.getClientSecretExpiresAt().atZone(ZoneId.systemDefault()).toInstant() 
                : null);
        entity.setClientName(po.getClientName());
        entity.setClientAuthenticationMethods(parseJsonArray(po.getClientAuthenticationMethods()));
        entity.setAuthorizationGrantTypes(parseJsonArray(po.getAuthorizationGrantTypes()));
        entity.setRedirectUris(parseJsonArray(po.getRedirectUris()));
        entity.setPostLogoutRedirectUris(parseJsonArray(po.getPostLogoutRedirectUris()));
        entity.setScopes(parseJsonArray(po.getScopes()));
        entity.setClientSettings(po.getClientSettings());
        entity.setTokenSettings(po.getTokenSettings());
        return entity;
    }
    
    private OAuth2RegisteredClientPO toPO(OAuth2RegisteredClientEntity entity) {
        OAuth2RegisteredClientPO po = new OAuth2RegisteredClientPO();
        po.setId(entity.getId());
        po.setClientId(entity.getClientId());
        po.setClientIdIssuedAt(entity.getClientIdIssuedAt() != null 
                ? entity.getClientIdIssuedAt().atZone(ZoneId.systemDefault()).toLocalDateTime() 
                : null);
        po.setClientSecret(entity.getClientSecret());
        po.setClientSecretExpiresAt(entity.getClientSecretExpiresAt() != null 
                ? entity.getClientSecretExpiresAt().atZone(ZoneId.systemDefault()).toLocalDateTime() 
                : null);
        po.setClientName(entity.getClientName());
        po.setClientAuthenticationMethods(toJsonArray(entity.getClientAuthenticationMethods()));
        po.setAuthorizationGrantTypes(toJsonArray(entity.getAuthorizationGrantTypes()));
        po.setRedirectUris(toJsonArray(entity.getRedirectUris()));
        po.setPostLogoutRedirectUris(toJsonArray(entity.getPostLogoutRedirectUris()));
        po.setScopes(toJsonArray(entity.getScopes()));
        po.setClientSettings(entity.getClientSettings());
        po.setTokenSettings(entity.getTokenSettings());
        return po;
    }
    
    private Set<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) {
            return new HashSet<>();
        }
        // 简单实现：移除方括号和引号后分割
        String cleaned = json.replaceAll("[\\[\\]\"]", "");
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
    
    private String toJsonArray(Set<String> set) {
        if (set == null || set.isEmpty()) {
            return "[]";
        }
        return "[\"" + String.join("\",\"", set) + "\"]";
    }
}
```

### 3.5 Phase 5: Spring Authorization Server配置

#### 3.5.1 授权服务器核心配置

位置：`/apex-interfaces/src/main/java/com/apex/scaffold/interfaces/config/AuthorizationServerConfig.java`

此配置类是整个OAuth2授权服务器的核心，需要完整实现以下内容：
- RegisteredClientRepository适配器
- AuthorizationService (授权记录存储)
- AuthorizationConsentService (用户同意记录)
- OAuth2 Token生成器配置
- 授权服务器端点配置

由于文档长度限制，完整代码将在下一部分提供。

---

## 四、实现步骤总结

### 步骤清单

1. ✅ **需求调研与方案设计** (当前阶段)
2. ⬜ **数据库迁移脚本**
   - 创建 V15__oauth2_authorization_server_schema.sql
   - 执行Flyway迁移
   
3. ⬜ **Maven依赖升级**
   - 父POM添加spring-authorization-server版本号
   - interfaces模块引入依赖
   
4. ⬜ **领域层实现**
   - OAuth2RegisteredClientEntity
   - OAuth2RegisteredClientRepository接口
   
5. ⬜ **基础设施层实现**
   - OAuth2RegisteredClientPO
   - OAuth2RegisteredClientMapper
   - OAuth2RegisteredClientRepositoryImpl
   
6. ⬜ **Authorization Server配置**
   - AuthorizationServerConfig
   - RegisteredClientRepository适配器
   - OAuth2 Token自定义器
   
7. ⬜ **资源服务器配置**
   - OAuth2ResourceServerConfig
   - OAuth2资源保护端点
   
8. ⬜ **客户端管理接口**
   - OAuth2ClientController
   - OAuth2ClientService
   
9. ⬜ **集成测试**
   - 授权码模式测试
   - 客户端模式测试
   - 密码模式测试
   - Token刷新测试

10. ⬜ **文档编写**
    - OAuth2接入指南
    - API文档更新

---

## 五、验证计划

### 5.1 授权码模式测试

```bash
# 1. 获取授权码
GET http://localhost:8080/oauth2/authorize?
    response_type=code&
    client_id=apex-admin-web&
    redirect_uri=http://localhost:5173/oauth2/callback&
    scope=read%20write&
    state=random_state_value

# 2. 用户登录并同意授权 (跳转到redirect_uri?code=xxx&state=xxx)

# 3. 授权码换取Token
POST http://localhost:8080/oauth2/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic apex-admin-web:client_secret

grant_type=authorization_code&
code=授权码&
redirect_uri=http://localhost:5173/oauth2/callback&
client_id=apex-admin-web
```

### 5.2 客户端模式测试

```bash
POST http://localhost:8080/oauth2/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic apex-m2m-service:client_secret

grant_type=client_credentials&
scope=api:read%20api:write
```

### 5.3 密码模式测试

```bash
POST http://localhost:8080/oauth2/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic apex-admin-web:client_secret

grant_type=password&
username=testuser&
password=test123&
scope=read%20write
```

### 5.4 刷新Token测试

```bash
POST http://localhost:8080/oauth2/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic apex-admin-web:client_secret

grant_type=refresh_token&
refresh_token=刷新令牌
```

---

## 六、关键技术点说明

### 6.1 双认证机制共存

项目将同时支持两种认证方式：

1. **JWT直接登录** (现有机制，保持不变)
   - 路径：/api/v1/auth/login
   - 用于：前端应用 apex-admin-web
   - 流程：用户名密码 → JWT AccessToken + RefreshToken
   
2. **OAuth2授权登录** (新增)
   - 路径：/oauth2/authorize、/oauth2/token
   - 用于：第三方应用接入
   - 流程：授权码 → OAuth2 AccessToken + RefreshToken

过滤器链：`JwtAuthenticationFilter` → `OAuth2ResourceServerFilter`

### 6.2 Token格式兼容

- **现有JWT Token**: 继续使用 JwtUtil 生成，格式不变
- **OAuth2 Token**: 使用 Spring Authorization Server 的 JwtGenerator，符合RFC 7519标准
- **Token验证**: JwtAuthenticationFilter 优先验证，失败则交由OAuth2ResourceServer验证

### 6.3 权限控制增强

```java
// 原有RBAC权限
@PreAuthorize("hasRole('ADMIN')")
public Mono<ServerResponse> adminOnlyEndpoint() { }

// OAuth2 Scope权限
@PreAuthorize("hasAuthority('SCOPE_admin:users')")
public Mono<ServerResponse> oauth2ProtectedEndpoint() { }

// 组合权限
@PreAuthorize("hasRole('ADMIN') or hasAuthority('SCOPE_admin:users')")
public Mono<ServerResponse> hybridEndpoint() { }
```

### 6.4 客户端密钥管理

- 配置文件客户端：密钥明文存储（仅用于开发环境）
- 数据库客户端：密钥BCrypt加密存储
- 密钥轮换：支持定期更新客户端密钥
- 密钥吊销：支持立即失效客户端所有Token

---

## 七、后续优化方向

1. **OIDC支持**: 实现OpenID Connect协议，支持ID Token
2. **PKCE增强**: 公共客户端强制启用PKCE
3. **设备授权流**: 支持设备码授权流程
4. **JWT加密**: 敏感Token使用JWE加密
5. **Token撤销**: 实现Token撤销端点和黑名单机制
6. **审计日志**: 记录所有OAuth2授权操作
7. **监控告警**: OAuth2授权失败率、Token使用情况监控

---

**文档版本**: v1.0  
**最后更新**: 2026-09-08  
**作者**: 开发团队
