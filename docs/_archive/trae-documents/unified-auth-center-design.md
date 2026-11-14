# 统一认证授权中心设计方案

## 文档信息

- **文档版本**: v1.0.0
- **创建日期**: 2026-09-20
- **作者**: Apex Team
- **状态**: 待审批
- **目标读者**: 架构师、技术负责人、开发团队

## 1. 项目背景与现状分析

### 1.1 项目背景

Apex Scaffold 是一个基于 Java 17、Spring Boot 3、WebFlux 的响应式多租户 SaaS 平台。随着业务发展，系统已集成多种认证授权机制以满足不同场景需求：

- **JWT 认证**: 主流的无状态 Token 认证方式
- **OAuth2 授权服务器**: 支持授权码、客户端凭证、密码模式等标准授权流程
- **SSO 单点登录**: 支持 SAML、CAS、OIDC 等协议的企业级单点登录
- **Keycloak 集成**: 与开源 IAM 平台 Keycloak 的 OIDC 集成
- **RBAC 权限模型**: 基于角色的细粒度权限控制
- **多种登录策略**: 微信授权码、微信一键登录、短信验证码、创蓝短信等

### 1.2 现状分析

#### 1.2.1 认证授权实现分布

**核心模块分布**:
- **apex-interfaces**: 安全配置、JWT 过滤器、OAuth2 过滤器、路由定义
- **apex-application**: 登录策略、OAuth2 服务、SSO 服务、认证配置服务
- **apex-domain**: 用户实体、角色实体、权限实体、Token 服务、仓储接口
- **apex-infrastructure**: 数据持久化、MyBatis-Plus Mapper、缓存实现

**关键组件清单**:

1. **JWT 认证体系**
   - [JwtAuthenticationFilter](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/security/JwtAuthenticationFilter.java): JWT 认证过滤器，支持 Token 解析、黑名单校验、用户权限加载
   - [JwtUtil](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/util/JwtUtil.java): JWT 工具类，支持 Token 签发和验证
   - [TokenService](../../../apex-shared-parent/apex-domain/src/main/java/com/apex/scaffold/domain/service/TokenService.java): Token 领域服务，管理 Access Token 和 Refresh Token
   - [TokenBlacklistService](../../../apex-shared-parent/apex-domain/src/main/java/com/apex/scaffold/domain/service/TokenBlacklistService.java): Token 黑名单服务

2. **OAuth2 授权服务器**
   - [OAuth2AuthorizationServiceImpl](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/OAuth2AuthorizationServiceImpl.java): OAuth2 授权核心逻辑
   - [OAuth2AuthorizationHandler](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/handler/OAuth2AuthorizationHandler.java): 授权端点处理器
   - [OAuth2ClientManagementService](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/OAuth2ClientManagementService.java): 客户端管理
   - [OAuth2ResourceServerFilter](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/security/OAuth2ResourceServerFilter.java): 资源服务器过滤器

3. **SSO 单点登录**
   - [SsoAuthHandler](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/handler/SsoAuthHandler.java): SSO 认证端点
   - [SysSsoProviderService](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/SysSsoProviderService.java): SSO 提供商管理
   - [SsoProtocolAdapter](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/support/sso/SsoProtocolAdapter.java): 协议适配器接口
   - [SamlProtocolAdapter](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/support/sso/SamlProtocolAdapter.java): SAML 协议适配器
   - [OidcProtocolAdapter](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/support/sso/OidcProtocolAdapter.java): OIDC 协议适配器

4. **Keycloak 集成**
   - [KeycloakConfig](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/config/KeycloakConfig.java): Keycloak 配置类
   - [OidcAuthenticationSuccessHandler](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/security/OidcAuthenticationSuccessHandler.java): OIDC 认证成功处理器
   - [KeycloakAdminHandler](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/handler/KeycloakAdminHandler.java): Keycloak 管理接口

5. **RBAC 权限体系**
   - [RbacMethodSecurityConfig](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/config/RbacMethodSecurityConfig.java): 方法级权限配置
   - [RequirePermissionAspect](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/aspect/RequirePermissionAspect.java): 权限校验切面
   - [DataScopeAspect](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/aspect/DataScopeAspect.java): 数据权限切面
   - [RoleService](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/RoleService.java): 角色管理服务
   - [PermissionService](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/PermissionService.java): 权限管理服务
   - [MenuService](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/MenuService.java): 菜单管理服务

6. **登录策略**
   - [LoginStrategy](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/login/LoginStrategy.java): 登录策略接口
   - [WeChatCodeLoginStrategy](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/login/WeChatCodeLoginStrategy.java): 微信授权码登录
   - [WeChatPhoneLoginStrategy](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/login/WeChatPhoneLoginStrategy.java): 微信一键登录
   - [SmsLoginStrategy](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/login/SmsLoginStrategy.java): 短信验证码登录
   - [ChuanglanSmsLoginStrategy](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/login/ChuanglanSmsLoginStrategy.java): 创蓝短信登录

7. **安全配置管理**
   - [SecurityConfig](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/config/SecurityConfig.java): Spring Security 配置
   - [SecurityProperties](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/config/SecurityProperties.java): 安全属性配置
   - [AuthConfigHandler](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/handler/AuthConfigHandler.java): 认证配置管理接口
   - [SysJwtConfigEntity](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/domain/model/entity/SysJwtConfigEntity.java): JWT 配置实体
   - [SysSessionConfigEntity](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/domain/model/entity/SysSessionConfigEntity.java): 会话配置实体
   - [SysSecurityPolicyEntity](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/domain/model/entity/SysSecurityPolicyEntity.java): 安全策略实体

#### 1.2.2 配置分布

认证授权相关配置分散在多个层次：

1. **应用配置文件** (`apex-interfaces/src/main/resources/`)
   - `application.yml`: 全局配置
   - `application-dev.yml`: 开发环境配置
   - `application-prod.yml`: 生产环境配置
   - 配置项包括: JWT 密钥、过期时间、Keycloak 地址、OAuth2 客户端等

2. **环境变量**
   - `JWT_SECRET`: JWT 签名密钥
   - `KEYCLOAK_SERVER_URL`: Keycloak 服务地址
   - `KEYCLOAK_CLIENT_SECRET`: Keycloak 客户端密钥
   - 其他敏感配置

3. **数据库配置表**
   - `sys_jwt_config`: JWT 配置
   - `sys_session_config`: 会话配置
   - `sys_security_policy`: 安全策略
   - `oauth2_registered_client`: OAuth2 客户端
   - `sys_sso_provider`: SSO 提供商

4. **Java 配置类**
   - 安全过滤器链配置
   - 认证管理器配置
   - 权限表达式处理器配置

### 1.3 核心痛点

根据现状分析，当前架构存在以下核心痛点：

#### 1.3.1 配置分散，运维成本高

**问题表现**:
- JWT 密钥分散在环境变量、配置文件、数据库三处
- 修改会话超时时间需要同时更新多个配置文件和数据库
- 安全策略配置散落在代码、配置文件、数据库中
- 缺乏统一的配置管理界面，运维人员需要登录服务器修改配置

**影响**:
- 配置变更风险高，容易遗漏
- 故障排查困难，需要检查多个位置
- 运维效率低，无法快速响应业务需求

#### 1.3.2 协议割裂，集成复杂

**问题表现**:
- JWT、OAuth2、SSO、Keycloak 各自实现，缺乏统一抽象
- 新增认证方式需要大量开发工作：实现过滤器、配置路由、编写服务层代码
- 协议间缺乏互操作性，例如 SSO 登录后无法直接获取 OAuth2 Token
- 代码重复度高，Token 签发逻辑在多处重复实现

**影响**:
- 开发成本高，新协议接入周期长
- 维护成本高，修改一处逻辑需要同步多处代码
- 扩展性差，难以支持新的认证标准（如 WebAuthn、FIDO2）

#### 1.3.3 审计困难，安全风险

**问题表现**:
- 认证日志分散在多个模块，缺乏统一的审计视图
- 登录日志、Token 刷新日志、权限变更日志存储在不同表中
- 缺乏实时监控和告警机制，无法及时发现异常登录
- 审计日志格式不统一，难以进行安全分析和合规审查

**影响**:
- 安全事件响应慢，难以追溯攻击路径
- 合规风险高，无法满足等保、ISO 27001 等审计要求
- 缺乏安全态势感知能力

#### 1.3.4 多租户隔离不彻底

**问题表现**:
- 虽然核心表有 `tenant_id` 字段，但认证授权策略的租户级配置不完善
- JWT 配置、会话策略、安全策略虽有租户字段，但缺乏租户级的动态配置能力
- 租户间的资源隔离依赖应用层校验，缺乏基础设施层的强制隔离
- 缺乏租户级的审计日志隔离和查询能力

**影响**:
- 数据泄露风险，租户 A 的用户可能访问租户 B 的资源
- 无法满足大型企业客户的数据隔离要求
- 难以实现租户级的差异化服务（例如金融租户要求更严格的安全策略）

## 2. 建设目标与原则

### 2.1 建设目标

基于用户反馈，统一认证授权中心的建设目标如下：

#### 2.1.1 统一配置管理

**目标**: 集中管理所有认证授权配置，支持动态调整和热更新

**具体要求**:
- 提供统一的配置管理界面，支持 JWT、OAuth2、SSO、会话、安全策略等所有配置
- 支持配置的版本管理和回滚能力
- 支持配置的热更新，无需重启服务
- 支持配置的导入导出，便于环境迁移
- 提供配置变更审计日志

#### 2.1.2 协议标准化

**目标**: 提供标准化的认证授权协议接口，简化新协议接入流程

**具体要求**:
- 定义统一的认证协议抽象层，支持插件化协议扩展
- 标准化 Token 签发和验证流程
- 提供协议转换能力，例如 SSO 登录后自动签发 OAuth2 Token
- 支持协议组合使用，例如 OAuth2 + OIDC + RBAC
- 降低新协议接入成本，从数周缩短到数天

#### 2.1.3 安全加固与审计

**目标**: 增强安全防护能力，提供完整的审计日志和实时监控

**具体要求**:
- 统一的安全审计日志存储和查询
- 实时异常登录检测和告警
- 支持多因素认证（MFA）
- 支持设备指纹和风险评估
- 提供安全态势感知大盘
- 满足等保、ISO 27001 等合规要求

#### 2.1.4 多租户增强

**目标**: 完善租户级的认证授权策略配置和资源隔离

**具体要求**:
- 租户级的 JWT、OAuth2、SSO 配置
- 租户级的安全策略差异化配置
- 租户级的审计日志隔离和查询
- 租户级的资源配额和限流
- 支持租户级的 SSO 提供商配置

### 2.2 设计原则

#### 2.2.1 架构原则

1. **服务独立性**: 认证授权中心作为独立微服务，与业务系统解耦
2. **高可用性**: 支持多实例部署，无状态设计，故障自动切换
3. **可扩展性**: 支持水平扩展，满足大规模并发需求
4. **兼容性**: 向后兼容现有认证授权机制，平滑迁移

#### 2.2.2 安全原则

1. **最小权限**: 默认拒绝，显式授权
2. **深度防御**: 多层安全防护，不依赖单点
3. **零信任**: 不信任任何请求，每次请求都需验证
4. **审计可追溯**: 所有操作留痕，支持事后审计

#### 2.2.3 运维原则

1. **可观测性**: 完善的日志、监控、追踪体系
2. **易维护性**: 配置集中管理，操作自动化
3. **容错性**: 优雅降级，避免雪崩效应
4. **可测试性**: 单元测试、集成测试、压力测试全覆盖

## 3. 总体架构设计

### 3.1 架构模式选择

**选择**: 独立微服务架构（推荐）

**理由**:
1. **服务解耦**: 认证授权中心独立演进，不受业务系统影响
2. **技术栈独立**: 可以选择最适合认证授权场景的技术栈
3. **扩展性强**: 支持独立水平扩展，应对高并发场景
4. **故障隔离**: 认证授权中心故障不影响业务系统核心功能
5. **团队协作**: 可以由专门的安全团队维护

**对比其他方案**:

| 维度 | 独立微服务 | 内聚模块 | 混合模式 |
|------|-----------|---------|---------|
| 服务解耦 | ✅ 完全解耦 | ❌ 紧耦合 | ⚠️ 部分解耦 |
| 扩展性 | ✅ 独立扩展 | ❌ 受限于主应用 | ⚠️ 部分独立 |
| 技术栈 | ✅ 自由选择 | ❌ 受限于主应用 | ⚠️ 部分自由 |
| 运维复杂度 | ⚠️ 较高 | ✅ 较低 | ⚠️ 中等 |
| 迁移成本 | ⚠️ 较高 | ✅ 较低 | ⚠️ 中等 |

### 3.2 系统架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                        客户端层（Client Layer）                       │
├─────────────────────────────────────────────────────────────────────┤
│  Web前端    │    移动APP    │   第三方应用   │    管理后台          │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│                      API网关层（API Gateway）                        │
├─────────────────────────────────────────────────────────────────────┤
│  • 请求路由              • 限流熔断                                   │
│  • Token验证             • 日志追踪                                   │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ↓               ↓               ↓
┌─────────────────────────────────────────────────────────────────────┐
│                  统一认证授权中心（Auth Center）                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   认证服务（Authentication）                  │   │
│  ├─────────────────────────────────────────────────────────────┤   │
│  │  • JWT认证         • OAuth2授权      • SSO单点登录           │   │
│  │  • 密码认证        • 短信认证        • 微信登录              │   │
│  │  • 生物识别        • LDAP/AD         • Keycloak集成         │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   授权服务（Authorization）                   │   │
│  ├─────────────────────────────────────────────────────────────┤   │
│  │  • RBAC权限模型    • 数据权限        • 动态权限              │   │
│  │  • 权限缓存        • 权限同步        • 权限审计              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   Token服务（Token Service）                  │   │
│  ├─────────────────────────────────────────────────────────────┤   │
│  │  • Token签发       • Token验证       • Token刷新             │   │
│  │  • Token撤销       • Token黑名单     • Token加密             │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   会话服务（Session Service）                 │   │
│  ├─────────────────────────────────────────────────────────────┤   │
│  │  • 会话管理        • 会话存储        • 会话同步              │   │
│  │  • 并发控制        • 踢人下线        • 在线用户统计          │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   安全服务（Security Service）                │   │
│  ├─────────────────────────────────────────────────────────────┤   │
│  │  • 密码策略        • 登录限制        • 风险检测              │   │
│  │  • MFA多因素       • 设备指纹        • IP白名单              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   审计服务（Audit Service）                   │   │
│  ├─────────────────────────────────────────────────────────────┤   │
│  │  • 登录日志        • 操作日志        • 配置变更日志          │   │
│  │  • 异常告警        • 日志查询        • 日志归档              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                   配置服务（Config Service）                  │   │
│  ├─────────────────────────────────────────────────────────────┤   │
│  │  • JWT配置         • OAuth2配置      • SSO配置               │   │
│  │  • 会话配置        • 安全策略        • 租户配置              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
                                    │
            ┌───────────────────────┼───────────────────────┐
            ↓                       ↓                       ↓
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│   数据存储层     │   │   缓存层        │   │   消息队列      │
├─────────────────┤   ├─────────────────┤   ├─────────────────┤
│  • MySQL/PG    │   │  • Redis       │   │  • RocketMQ    │
│  • 用户数据     │   │  • Token缓存   │   │  • 事件通知     │
│  • 权限数据     │   │  • 会话缓存    │   │  • 审计日志     │
│  • 配置数据     │   │  • 权限缓存    │   │  • 异步任务     │
│  • 审计日志     │   │                 │   │                 │
└─────────────────┘   └─────────────────┘   └─────────────────┘
```

### 3.3 核心模块设计

#### 3.3.1 认证服务（Authentication Service）

**职责**: 负责用户身份验证，支持多种认证协议

**核心能力**:
1. **协议抽象层**: 定义统一的认证协议接口 `AuthenticationProtocol`
2. **协议适配器**: 各协议的具体实现（JWT、OAuth2、SSO、LDAP等）
3. **认证流程编排**: 支持多因素认证、条件认证等复杂场景
4. **认证上下文管理**: 维护认证过程中的上下文信息

**接口设计**:
```java
public interface AuthenticationProtocol {
    String getProtocolName();
    Mono<AuthenticationResult> authenticate(AuthenticationRequest request);
    Mono<Boolean> validate(String token);
    Mono<Void> logout(String token);
}
```

#### 3.3.2 授权服务（Authorization Service）

**职责**: 负责权限控制，支持 RBAC、ABAC 等授权模型

**核心能力**:
1. **权限模型抽象**: 支持 RBAC、ABAC、PBAC 等多种模型
2. **权限计算引擎**: 高效计算用户权限
3. **权限缓存**: 多级缓存提升性能
4. **动态权限**: 支持运行时权限变更

**接口设计**:
```java
public interface AuthorizationService {
    Mono<Boolean> hasPermission(String userId, String resource, String action);
    Mono<List<Permission>> getUserPermissions(String userId);
    Mono<Void> grantPermission(String userId, Permission permission);
    Mono<Void> revokePermission(String userId, Permission permission);
}
```

#### 3.3.3 Token服务（Token Service）

**职责**: 负责 Token 的全生命周期管理

**核心能力**:
1. **Token签发**: 支持 JWT、Opaque Token 等多种格式
2. **Token验证**: 签名验证、过期检查、黑名单校验
3. **Token刷新**: 无感刷新机制
4. **Token撤销**: 主动撤销、批量撤销

**接口设计**:
```java
public interface TokenService {
    Mono<TokenPair> issueToken(TokenRequest request);
    Mono<TokenValidationResult> validateToken(String token);
    Mono<TokenPair> refreshToken(String refreshToken);
    Mono<Void> revokeToken(String token);
}
```

#### 3.3.4 会话服务（Session Service）

**职责**: 负责用户会话管理

**核心能力**:
1. **会话存储**: 分布式会话存储
2. **并发控制**: 单用户最大会话数限制
3. **会话同步**: 跨服务会话状态同步
4. **在线统计**: 实时在线用户统计

#### 3.3.5 安全服务（Security Service）

**职责**: 负责安全策略执行

**核心能力**:
1. **密码策略**: 复杂度、历史、过期等策略
2. **登录限制**: 失败次数、锁定时间等
3. **风险检测**: 异常登录检测、设备指纹、IP白名单
4. **MFA支持**: 短信、邮箱、TOTP、生物识别

#### 3.3.6 审计服务（Audit Service）

**职责**: 负责审计日志记录和查询

**核心能力**:
1. **日志记录**: 登录、操作、配置变更等日志
2. **日志查询**: 多维度查询和统计
3. **异常告警**: 实时异常检测和告警
4. **日志归档**: 定期归档和清理

#### 3.3.7 配置服务（Config Service）

**职责**: 负责认证授权配置管理

**核心能力**:
1. **配置管理**: 增删改查配置
2. **配置版本**: 版本管理和回滚
3. **配置热更新**: 无需重启生效
4. **配置验证**: 配置有效性校验

## 4. 详细设计

### 4.1 数据模型设计

#### 4.1.1 核心数据表

**用户认证表（auth_user）**:
```sql
CREATE TABLE auth_user (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    password_hash VARCHAR(255),
    open_id VARCHAR(100),
    status TINYINT DEFAULT 1,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(50),
    failed_login_attempts INT DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_tenant_username (tenant_id, username),
    INDEX idx_tenant_phone (tenant_id, phone),
    INDEX idx_open_id (open_id)
);
```

**认证协议配置表（auth_protocol_config）**:
```sql
CREATE TABLE auth_protocol_config (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    protocol_type VARCHAR(50) NOT NULL, -- JWT/OAUTH2/SSO/LDAP/KEYCLOAK
    protocol_name VARCHAR(100) NOT NULL,
    config_json TEXT NOT NULL, -- 协议特定配置JSON
    enabled TINYINT DEFAULT 1,
    priority INT DEFAULT 0, -- 协议优先级
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT DEFAULT 1, -- 乐观锁版本号
    INDEX idx_tenant_type (tenant_id, protocol_type)
);
```

**Token表（auth_token）**:
```sql
CREATE TABLE auth_token (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    token_type VARCHAR(20) NOT NULL, -- ACCESS/REFRESH
    token_value VARCHAR(500) NOT NULL,
    jti VARCHAR(100) UNIQUE, -- JWT ID
    expires_at TIMESTAMP NOT NULL,
    revoked TINYINT DEFAULT 0,
    revoked_at TIMESTAMP,
    device_id VARCHAR(100),
    client_ip VARCHAR(50),
    user_agent VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_user (tenant_id, user_id),
    INDEX idx_jti (jti),
    INDEX idx_expires_at (expires_at)
);
```

**会话表（auth_session）**:
```sql
CREATE TABLE auth_session (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(100) UNIQUE NOT NULL,
    device_id VARCHAR(100),
    device_type VARCHAR(50),
    client_ip VARCHAR(50),
    user_agent VARCHAR(500),
    last_active_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_user (tenant_id, user_id),
    INDEX idx_session_id (session_id),
    INDEX idx_last_active (last_active_at)
);
```

**审计日志表（auth_audit_log）**:
```sql
CREATE TABLE auth_audit_log (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id BIGINT,
    event_type VARCHAR(50) NOT NULL, -- LOGIN/LOGOUT/TOKEN_REFRESH/PERMISSION_CHANGE
    event_status VARCHAR(20) NOT NULL, -- SUCCESS/FAILURE
    protocol_type VARCHAR(50),
    client_ip VARCHAR(50),
    user_agent VARCHAR(500),
    device_id VARCHAR(100),
    request_path VARCHAR(500),
    error_message TEXT,
    metadata JSON, -- 额外信息
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_user (tenant_id, user_id),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at)
);
```

**权限表（auth_permission）**:
```sql
CREATE TABLE auth_permission (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    resource VARCHAR(200),
    action VARCHAR(50),
    description TEXT,
    enabled TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_code (tenant_id, code)
);
```

**角色表（auth_role）**:
```sql
CREATE TABLE auth_role (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    data_scope TINYINT DEFAULT 0, -- 0全部/1本部门/2本人
    enabled TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_code (tenant_id, code)
);
```

**用户角色关联表（auth_user_role）**:
```sql
CREATE TABLE auth_user_role (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_tenant_user (tenant_id, user_id)
);
```

**角色权限关联表（auth_role_permission）**:
```sql
CREATE TABLE auth_role_permission (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_permission (role_id, permission_id)
);
```

### 4.2 接口设计

#### 4.2.1 认证接口

**POST /auth/v1/authenticate**
```json
{
  "protocol": "jwt",
  "credentials": {
    "username": "user@example.com",
    "password": "******"
  },
  "deviceId": "device-123",
  "clientIp": "1.2.3.4"
}

Response:
{
  "code": 200,
  "msg": "认证成功",
  "data": {
    "accessToken": "eyJhbGc...",
    "refreshToken": "eyJhbGc...",
    "expiresIn": 7200,
    "tokenType": "Bearer",
    "user": {
      "userId": "123",
      "username": "user@example.com",
      "tenantId": "tenant-001"
    }
  },
  "traceId": "trace-123"
}
```

**POST /auth/v1/token/refresh**
```json
{
  "refreshToken": "eyJhbGc..."
}

Response:
{
  "code": 200,
  "msg": "Token刷新成功",
  "data": {
    "accessToken": "eyJhbGc...",
    "expiresIn": 7200
  },
  "traceId": "trace-124"
}
```

**POST /auth/v1/token/validate**
```json
{
  "accessToken": "eyJhbGc..."
}

Response:
{
  "code": 200,
  "msg": "Token有效",
  "data": {
    "valid": true,
    "userId": "123",
    "tenantId": "tenant-001",
    "expiresAt": "2026-09-20T18:00:00Z"
  },
  "traceId": "trace-125"
}
```

**POST /auth/v1/logout**
```json
{
  "accessToken": "eyJhbGc..."
}

Response:
{
  "code": 200,
  "msg": "登出成功",
  "data": null,
  "traceId": "trace-126"
}
```

#### 4.2.2 授权接口

**POST /auth/v1/authorize**
```json
{
  "userId": "123",
  "resource": "/api/v1/users",
  "action": "create"
}

Response:
{
  "code": 200,
  "msg": "授权检查成功",
  "data": {
    "authorized": true,
    "permissions": ["user:create", "user:read"]
  },
  "traceId": "trace-127"
}
```

**GET /auth/v1/users/{userId}/permissions**
```
Response:
{
  "code": 200,
  "msg": "查询成功",
  "data": {
    "permissions": [
      {
        "code": "user:create",
        "name": "创建用户",
        "resource": "/api/v1/users",
        "action": "create"
      }
    ]
  },
  "traceId": "trace-128"
}
```

#### 4.2.3 配置管理接口

**GET /auth/v1/config/protocols**
```
Response:
{
  "code": 200,
  "msg": "查询成功",
  "data": {
    "protocols": [
      {
        "id": "1",
        "protocolType": "JWT",
        "protocolName": "JWT认证",
        "enabled": true,
        "config": {
          "issuer": "apex-auth",
          "algorithm": "HS256",
          "accessTokenTtl": 7200,
          "refreshTokenTtl": 604800
        }
      }
    ]
  },
  "traceId": "trace-129"
}
```

**PUT /auth/v1/config/protocols/{id}**
```json
{
  "enabled": true,
  "config": {
    "accessTokenTtl": 3600
  }
}

Response:
{
  "code": 200,
  "msg": "更新成功",
  "data": null,
  "traceId": "trace-130"
}
```

#### 4.2.4 审计日志接口

**GET /auth/v1/audit/logs**
```
Query: page=1&pageSize=20&eventType=LOGIN&startTime=2026-09-01&endTime=2026-09-20

Response:
{
  "code": 200,
  "msg": "查询成功",
  "data": {
    "total": 150,
    "items": [
      {
        "id": "1",
        "userId": "123",
        "username": "user@example.com",
        "eventType": "LOGIN",
        "eventStatus": "SUCCESS",
        "clientIp": "1.2.3.4",
        "deviceId": "device-123",
        "createdAt": "2026-09-20T10:30:00Z"
      }
    ]
  },
  "traceId": "trace-131"
}
```

### 4.3 技术选型

#### 4.3.1 开发框架

| 技术栈 | 选型 | 说明 |
|--------|------|------|
| 开发语言 | Java 17 | 保持与现有项目一致 |
| 应用框架 | Spring Boot 3.5.x | 最新稳定版 |
| 响应式框架 | Spring WebFlux | 支持高并发场景 |
| 安全框架 | Spring Security 6.x | 提供完善的安全能力 |
| ORM框架 | MyBatis-Plus | 保持与现有项目一致 |
| 数据迁移 | Flyway | 数据库版本管理 |

#### 4.3.2 中间件

| 中间件 | 选型 | 说明 |
|--------|------|------|
| 数据库 | MySQL 8.0 / PostgreSQL 14 | 支持双写，按需选择 |
| 缓存 | Redis 7.x | Token缓存、会话缓存、权限缓存 |
| 消息队列 | RocketMQ 5.x | 审计日志异步写入 |
| 配置中心 | Nacos 2.x | 配置动态刷新 |
| 服务注册 | Nacos 2.x | 服务发现 |
| 链路追踪 | Zipkin / SkyWalking | 分布式追踪 |

#### 4.3.3 安全组件

| 组件 | 选型 | 说明 |
|------|------|------|
| JWT库 | JJWT 0.12.x | JWT签发和验证 |
| 密码加密 | BCrypt / Argon2 | 密码哈希 |
| 敏感数据加密 | AES-256-GCM | 配置数据加密 |
| OAuth2库 | Spring Authorization Server | 标准OAuth2实现 |
| SSO协议 | OpenSAML / Pac4j | SAML/CAS支持 |

### 4.4 部署架构

#### 4.4.1 容器化部署

```yaml
# docker-compose.yml
version: '3.8'

services:
  auth-center:
    image: apex/auth-center:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/auth_center
      - SPRING_REDIS_HOST=redis
      - ROCKETMQ_NAME_SERVER=rocketmq:9876
    depends_on:
      - mysql
      - redis
      - rocketmq
    deploy:
      replicas: 3
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G

  mysql:
    image: mysql:8.0
    environment:
      - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - MYSQL_DATABASE=auth_center
    volumes:
      - mysql-data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    volumes:
      - redis-data:/data

  rocketmq:
    image: apache/rocketmq:5.0.0
    volumes:
      - rocketmq-data:/home/rocketmq/store

volumes:
  mysql-data:
  redis-data:
  rocketmq-data:
```

#### 4.4.2 Kubernetes部署

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-center
  namespace: apex-prod
spec:
  replicas: 3
  selector:
    matchLabels:
      app: auth-center
  template:
    metadata:
      labels:
        app: auth-center
    spec:
      containers:
      - name: auth-center
        image: apex/auth-center:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            secretKeyRef:
              name: auth-center-secrets
              key: datasource-url
        resources:
          limits:
            cpu: "2"
            memory: "2Gi"
          requests:
            cpu: "1"
            memory: "1Gi"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5

---
apiVersion: v1
kind: Service
metadata:
  name: auth-center-service
  namespace: apex-prod
spec:
  selector:
    app: auth-center
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: ClusterIP

---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: auth-center-hpa
  namespace: apex-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: auth-center
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### 4.5 监控与告警

#### 4.5.1 监控指标

**业务指标**:
- 认证成功率、失败率
- 平均响应时间（P50/P95/P99）
- QPS、TPS
- Token签发量、验证量
- 在线用户数
- 异常登录次数

**技术指标**:
- JVM内存使用率
- GC频率和时间
- 线程池队列长度
- 数据库连接池使用率
- Redis命中率
- 接口错误率

#### 4.5.2 告警规则

```yaml
# prometheus-rules.yml
groups:
  - name: auth-center-alerts
    rules:
      - alert: HighAuthFailureRate
        expr: rate(auth_failure_total[5m]) > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "认证失败率过高"
          description: "最近5分钟认证失败率超过10次/秒"

      - alert: HighResponseTime
        expr: histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m])) > 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "响应时间过长"
          description: "P95响应时间超过1秒"

      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "JVM堆内存使用率过高"
          description: "堆内存使用率超过85%"
```

## 5. 实施方案

### 5.1 迁移策略

#### 5.1.1 分阶段迁移

**阶段一：基础设施搭建（2周）**
- 搭建认证授权中心开发环境
- 创建数据库表结构
- 配置Redis、RocketMQ等中间件
- 搭建CI/CD流水线

**阶段二：核心功能开发（6周）**
- 开发认证服务（JWT、OAuth2、SSO）
- 开发授权服务（RBAC、数据权限）
- 开发Token服务
- 开发配置服务
- 开发审计服务

**阶段三：现有系统对接（4周）**
- 提供API网关插件，支持Token验证
- 提供SDK，简化业务系统集成
- 迁移现有用户数据
- 迁移现有权限配置

**阶段四：灰度发布（2周）**
- 选择低风险业务进行灰度测试
- 监控性能和稳定性
- 收集反馈并优化

**阶段五：全量上线（1周）**
- 全量切换到认证授权中心
- 关闭现有认证授权模块
- 数据清理和归档

#### 5.1.2 兼容性方案

**向后兼容**:
- 认证授权中心继续支持现有的JWT格式
- 保留现有的登录接口路径
- 支持双写模式，同时更新新旧两套系统

**渐进式迁移**:
- 新业务优先使用认证授权中心
- 老业务逐步迁移，不强制切换
- 提供迁移工具和脚本

### 5.2 数据迁移

#### 5.2.1 用户数据迁移

```sql
-- 从现有用户表迁移到认证中心
INSERT INTO auth_user (
    id, tenant_id, username, email, phone, 
    password_hash, open_id, status, 
    last_login_at, last_login_ip, created_at, updated_at
)
SELECT 
    id, tenant_id, username, email, phone,
    password, open_id, status,
    last_login_time, last_login_ip, create_time, update_time
FROM user
WHERE deleted = 0;
```

#### 5.2.2 权限数据迁移

```sql
-- 迁移权限
INSERT INTO auth_permission (
    id, tenant_id, code, name, resource, action, 
    description, enabled, created_at, updated_at
)
SELECT 
    id, tenant_id, code, name, NULL, NULL,
    description, status, create_time, update_time
FROM permission
WHERE deleted = 0;

-- 迁移角色
INSERT INTO auth_role (
    id, tenant_id, code, name, description, 
    data_scope, enabled, created_at, updated_at
)
SELECT 
    id, tenant_id, code, name, description,
    data_scope, status, create_time, update_time
FROM role
WHERE deleted = 0;

-- 迁移用户角色关联
INSERT INTO auth_user_role (id, tenant_id, user_id, role_id, created_at)
SELECT id, tenant_id, user_id, role_id, create_time
FROM user_role;

-- 迁移角色权限关联
INSERT INTO auth_role_permission (id, tenant_id, role_id, permission_id, created_at)
SELECT id, tenant_id, role_id, permission_id, create_time
FROM role_permission;
```

### 5.3 风险控制

#### 5.3.1 技术风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| 性能不达标 | 高 | 中 | 压测验证、性能调优、缓存优化 |
| 数据迁移失败 | 高 | 低 | 数据校验、回滚脚本、备份恢复 |
| 兼容性问题 | 中 | 中 | 充分测试、灰度发布、快速回滚 |
| 服务不稳定 | 高 | 低 | 多副本部署、熔断降级、监控告警 |

#### 5.3.2 业务风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| 用户登录失败 | 高 | 低 | 双写模式、快速回滚、24小时值班 |
| 权限错乱 | 高 | 低 | 权限校验、灰度验证、应急预案 |
| 业务中断 | 高 | 低 | 高可用部署、故障自愈、应急流程 |

### 5.4 回滚方案

**快速回滚**:
1. 切换API网关路由，恢复使用原认证授权模块
2. 停止认证授权中心服务
3. 清理Redis中的新Token数据
4. 通知业务方使用旧接口

**数据回滚**:
1. 从备份恢复原数据库
2. 删除认证授权中心的增量数据
3. 重新同步权限配置

## 6. 预期收益

### 6.1 配置管理收益

**现状**:
- 修改JWT密钥需要改3处：环境变量、配置文件、数据库
- 修改会话超时需要重启服务
- 缺乏配置变更记录

**优化后**:
- 统一配置管理界面，一处修改全局生效
- 支持热更新，无需重启
- 完整的配置变更审计日志
- **运维效率提升80%**

### 6.2 协议集成收益

**现状**:
- 新增认证协议需要2-3周开发
- 需要修改过滤器、路由、服务层等多处代码
- 协议间无法互操作

**优化后**:
- 新增认证协议只需实现协议适配器，3-5天完成
- 协议插件化，无需修改核心代码
- 支持协议组合和转换
- **开发效率提升60%**

### 6.3 安全审计收益

**现状**:
- 审计日志分散在多个表，查询困难
- 缺乏实时监控和告警
- 无法快速定位安全事件

**优化后**:
- 统一的审计日志存储和查询
- 实时异常检测和告警
- 安全态势感知大盘
- **安全响应时间缩短70%**

### 6.4 多租户收益

**现状**:
- 租户配置依赖应用层校验
- 无法实现租户级差异化策略
- 审计日志租户隔离不完善

**优化后**:
- 完善的租户级配置和隔离
- 支持租户级安全策略定制
- 租户级审计日志查询
- **满足大型企业客户需求**

## 7. 后续演进

### 7.1 短期规划（3-6个月）

1. **WebAuthn / FIDO2支持**: 支持无密码登录
2. **生物识别集成**: 支持指纹、人脸识别
3. **风控增强**: 设备指纹、行为分析、威胁情报
4. **性能优化**: Token缓存优化、权限计算加速

### 7.2 中期规划（6-12个月）

1. **零信任架构**: 基于身份的动态访问控制
2. **联邦身份**: 支持跨组织的身份联合
3. **AI风控**: 基于机器学习的异常检测
4. **合规认证**: 通过等保三级、ISO 27001认证

### 7.3 长期规划（12个月以上）

1. **身份治理**: 身份生命周期管理、权限自动审查
2. **多云支持**: 支持AWS、阿里云、腾讯云等多云环境
3. **区块链集成**: 基于区块链的分布式身份验证
4. **开放平台**: 提供SaaS服务，支持第三方集成

## 8. 总结与建议

### 8.1 核心价值

统一认证授权中心的建设将为Apex Scaffold带来以下核心价值：

1. **降低运维成本**: 配置集中管理，运维效率提升80%
2. **提升开发效率**: 协议插件化，新协议接入时间缩短60%
3. **增强安全能力**: 统一审计，安全响应时间缩短70%
4. **支撑业务增长**: 完善多租户能力，满足大型企业需求

### 8.2 实施建议

1. **分阶段推进**: 建议按5个阶段实施，每个阶段有明确交付物和验收标准
2. **灰度发布**: 先在低风险业务灰度测试，验证稳定性后再全量上线
3. **充分测试**: 重点关注性能测试、兼容性测试、压力测试
4. **应急预案**: 准备完善的回滚方案，确保可快速恢复

### 8.3 代码与文档规范

#### 8.3.1 代码注释规范

**JavaDoc注释要求**:

所有代码必须遵循AGENTS.md中的注释规范：

1. **类级注释**：所有public/protected类必须添加类级别中文JavaDoc
   ```java
   /**
    * 认证协议抽象接口
    *
    * <p>定义统一的认证协议规范，所有认证协议适配器必须实现此接口。
    * 支持JWT、OAuth2、SSO、LDAP等多种认证协议的插件化扩展。
    *
    * <h2>核心功能</h2>
    * <ul>
    *   <li>用户身份认证</li>
    *   <li>Token验证</li>
    *   <li>登出处理</li>
    * </ul>
    *
    * <h2>使用示例</h2>
    * <pre>{@code
    * AuthenticationProtocol protocol = new JwtAuthenticationProtocol();
    * AuthenticationResult result = protocol.authenticate(request).block();
    * }</pre>
    *
    * @author Apex Team
    * @version 1.0.0
    * @since 2026-09-20
    * @see JwtAuthenticationProtocol
    * @see OAuth2AuthenticationProtocol
    */
   public interface AuthenticationProtocol {
   ```

2. **方法级注释**：所有public/protected方法必须添加完整JavaDoc
   ```java
   /**
    * 执行用户身份认证
    *
    * <p>根据认证请求验证用户身份，验证通过后返回认证结果，包含用户信息和Token。
    * 支持多种认证方式：用户名密码、手机验证码、第三方授权等。
    *
    * <p>认证流程：
    * <ol>
    *   <li>提取认证凭证（用户名密码、授权码等）</li>
    *   <li>调用对应的认证逻辑验证凭证有效性</li>
    *   <li>验证通过后签发Token</li>
    *   <li>记录审计日志</li>
    * </ol>
    *
    * @param request 认证请求，包含认证凭证、设备信息、IP地址等
    * @return 认证结果的Mono，包含Token、用户信息和过期时间
    * @throws AuthenticationException 当认证失败时抛出，包含失败原因
    * @throws BusinessException 当业务逻辑错误时抛出
    */
   Mono<AuthenticationResult> authenticate(AuthenticationRequest request);
   ```

3. **字段注释**：所有字段添加说明
   ```java
   /**
    * JWT签发者标识
    *
    * <p>用于标识Token的签发方，通常是应用名称或服务域名。
    * 接收方通过验证issuer声明确认Token来源的合法性。
    *
    * <p>示例：apex-auth-center、https://auth.example.com
    */
   private String issuer;
   ```

**代码注释禁止事项**:
- ❌ 禁止添加样板注释或废话注释
- ❌ 禁止使用@author标注个人名字（统一使用Apex Team）
- ❌ 禁止在重写方法中重复父类注释（除非有特殊实现逻辑）

#### 8.3.2 配置文件注释规范

**application.yml注释要求**:

```yaml
# ============================================================
# 统一认证授权中心配置
# ============================================================
# 应用基本信息
spring:
  application:
    # 服务名称，用于服务注册和追踪
    name: apex-auth-center
  
  # 数据源配置
  datasource:
    # 数据库连接URL
    # 生产环境通过环境变量DB_URL注入
    url: ${DB_URL:jdbc:mysql://localhost:3306/auth_center}
    # 数据库用户名（生产环境通过环境变量注入）
    username: ${DB_USERNAME:root}
    # 数据库密码（生产环境通过环境变量注入，禁止明文配置）
    password: ${DB_PASSWORD:}

# ============================================================
# JWT配置
# ============================================================
apex:
  auth:
    jwt:
      # JWT签发者标识
      issuer: apex-auth-center
      # JWT签名算法：HS256（对称）或RS256（非对称）
      algorithm: HS256
      # 访问令牌有效期（秒），默认2小时
      access-token-ttl: 7200
      # 刷新令牌有效期（秒），默认7天
      refresh-token-ttl: 604800
```

**Flyway迁移脚本注释要求**:

```sql
-- ============================================================
-- Flyway迁移脚本：V1__create_auth_center_tables.sql
-- 描述：创建统一认证授权中心核心数据表
-- 作者：Apex Team
-- 日期：2026-09-20
-- 版本：1.0.0
-- ============================================================

-- 用户认证表
-- 存储用户基本信息和认证凭证
CREATE TABLE auth_user (
    -- 主键ID，雪花算法生成
    id BIGINT PRIMARY KEY COMMENT '用户ID',
    -- 租户标识，用于多租户数据隔离
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    -- 用户名，租户内唯一
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    -- 邮箱地址
    email VARCHAR(100) COMMENT '邮箱',
    -- 索引：租户+用户名组合查询
    INDEX idx_tenant_username (tenant_id, username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户认证表';
```

#### 8.3.3 测试代码规范

**单元测试要求**:

```java
/**
 * JWT认证协议单元测试
 *
 * <p>测试JWT认证协议的核心功能，包括Token签发、验证、刷新等。
 * 使用Mockito模拟依赖，确保测试隔离性和可重复性。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2026-09-20
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationProtocolTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JwtAuthenticationProtocol protocol;

    private AuthenticationRequest request;

    /**
     * 测试前置：初始化测试数据
     */
    @BeforeEach
    void setUp() {
        request = new AuthenticationRequest();
        request.setUsername("test@example.com");
        request.setPassword("Password123!");
    }

    /**
     * 测试场景：使用有效凭证认证成功
     *
     * <p>验证点：
     * <ul>
     *   <li>能够成功验证用户凭证</li>
     *   <li>返回有效的访问令牌和刷新令牌</li>
     *   <li>Token包含正确的用户信息</li>
     * </ul>
     */
    @Test
    @DisplayName("认证成功 - 有效用户名密码")
    void authenticate_WithValidCredentials_ReturnsToken() {
        // Given
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("test@example.com");
        
        when(userRepository.findByUsername("test@example.com"))
            .thenReturn(Mono.just(user));
        when(tokenService.issueToken(any()))
            .thenReturn(Mono.just(new TokenPair("access-token", "refresh-token")));

        // When
        AuthenticationResult result = protocol.authenticate(request).block();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(result.getUserId()).isEqualTo(1L);
        
        verify(userRepository).findByUsername("test@example.com");
        verify(tokenService).issueToken(any());
    }

    /**
     * 测试场景：使用无效密码认证失败
     *
     * <p>验证点：
     * <ul>
     *   <li>能够识别错误密码</li>
     *   <li>抛出AuthenticationException异常</li>
     *   <li>记录认证失败日志</li>
     * </ul>
     */
    @Test
    @DisplayName("认证失败 - 无效密码")
    void authenticate_WithInvalidPassword_ThrowsException() {
        // Given
        UserEntity user = new UserEntity();
        user.setPassword("$2a$10$correctHash");
        
        when(userRepository.findByUsername("test@example.com"))
            .thenReturn(Mono.just(user));

        // When & Then
        assertThatThrownBy(() -> protocol.authenticate(request).block())
            .isInstanceOf(AuthenticationException.class)
            .hasMessageContaining("密码错误");
    }
}
```

**集成测试要求**:

```java
/**
 * 认证接口集成测试
 *
 * <p>测试认证接口的端到端流程，包括请求验证、业务逻辑、数据库操作等。
 * 使用TestContainers启动真实的MySQL和Redis环境，确保测试真实性。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2026-09-20
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("it")
class AuthenticationIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @Autowired
    private WebTestClient webTestClient;

    /**
     * 测试场景：完整的用户登录流程
     *
     * <p>验证点：
     * <ul>
     *   <li>能够接收登录请求并返回Token</li>
     *   <li>Token能够成功验证</li>
     *   <li>Token能够成功刷新</li>
     *   <li>登录日志正确记录</li>
     * </ul>
     */
    @Test
    @DisplayName("完整登录流程 - 登录、验证、刷新")
    void fullAuthenticationFlow() {
        // 1. 登录获取Token
        AuthenticationRequest loginRequest = new AuthenticationRequest();
        loginRequest.setUsername("test@example.com");
        loginRequest.setPassword("Password123!");

        webTestClient.post()
            .uri("/auth/v1/authenticate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(loginRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.code").isEqualTo(200)
            .jsonPath("$.data.accessToken").exists()
            .jsonPath("$.data.refreshToken").exists();

        // 2. 使用Token验证
        String accessToken = extractAccessToken(response);
        
        webTestClient.post()
            .uri("/auth/v1/token/validate")
            .header("Authorization", "Bearer " + accessToken)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.data.valid").isEqualTo(true);

        // 3. 刷新Token
        String refreshToken = extractRefreshToken(response);
        
        webTestClient.post()
            .uri("/auth/v1/token/refresh")
            .bodyValue(Map.of("refreshToken", refreshToken))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.data.accessToken").exists();
    }
}
```

#### 8.3.4 测试覆盖率要求

**覆盖率目标**:
- 核心服务类：≥ 90%
- 工具类：≥ 85%
- Handler/Controller：≥ 80%
- 整体覆盖率：≥ 80%

**测试报告生成**:

```bash
# 运行测试并生成覆盖率报告
mvnd clean test jacoco:report

# 生成HTML格式报告
# 报告路径：target/site/jacoco/index.html

# 查看覆盖率
mvnd jacoco:check
```

#### 8.3.5 文档交付清单

**必须交付的文档**:

1. **设计文档**（本文档）
   - 路径：`docs/architecture/unified-auth-center-design.md`
   - 内容：架构设计、接口设计、数据模型等

2. **API文档**
   - 路径：自动生成，通过Swagger UI访问
   - 地址：`http://localhost:8080/swagger-ui.html`

3. **部署文档**
   - 路径：`docs/deployment/auth-center-deployment.md`
   - 内容：环境要求、部署步骤、配置说明

4. **运维文档**
   - 路径：`docs/operations/auth-center-operations.md`
   - 内容：监控指标、告警规则、故障处理

5. **测试报告**
   - 路径：`docs/testing/auth-center-test-report.md`
   - 内容：测试用例、覆盖率、性能测试结果

6. **用户手册**
   - 路径：`docs/user-guide/auth-center-user-guide.md`
   - 内容：配置管理、权限管理、审计查询

7. **开发指南**
   - 路径：`docs/development/auth-center-development-guide.md`
   - 内容：环境搭建、编码规范、调试技巧

### 8.4 下一步行动

**立即行动**:
- [ ] 组建项目团队（架构师1人、后端开发2人、测试1人、运维1人）
- [ ] 确认项目排期和里程碑
- [ ] 搭建开发环境和基础设施
- [ ] 学习和确认代码注释规范

**本周完成**:
- [ ] 详细技术方案评审
- [ ] 确认数据库表设计
- [ ] 确认接口设计规范
- [ ] 编写代码注释模板和示例

**2周内完成**:
- [ ] 完成基础设施搭建
- [ ] 完成核心框架开发（含完整注释）
- [ ] 完成第一个协议适配器（JWT）
- [ ] 编写单元测试并达到90%覆盖率
- [ ] 生成第一版API文档

## 9. 前端项目调整方案

### 9.1 前端调整必要性分析

统一认证授权中心的建设对前端项目有以下影响：

**必须调整的部分**:
1. ✅ **认证接口调整**：登录、登出、Token刷新接口地址和参数变更
2. ✅ **Token管理调整**：Token存储、刷新机制、过期处理逻辑
3. ✅ **权限校验调整**：权限获取方式、权限缓存策略
4. ✅ **配置管理界面**：新增认证授权配置管理页面
5. ✅ **审计日志界面**：新增审计日志查询页面

**可选调整的部分**:
- ⚠️ **多协议登录**：根据租户配置动态展示不同登录方式
- ⚠️ **SSO登录**：SSO登录流程优化
- ⚠️ **安全增强**：设备指纹、风险检测提示

### 9.2 前端技术栈

假设前端使用常见技术栈：
- Vue 3 + TypeScript + Vite
- Axios（HTTP客户端）
- Pinia（状态管理）
- Vue Router（路由管理）
- Element Plus（UI组件库）

### 9.3 核心调整内容

#### 9.3.1 API接口层调整

**新建：`src/api/auth-center.ts`**

```typescript
/**
 * 统一认证授权中心API接口
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2026-09-20
 */
import request from '@/utils/request'

/**
 * 认证请求参数
 */
export interface AuthenticationRequest {
  /** 认证协议类型：jwt/oauth2/sso */
  protocol: string
  /** 认证凭证 */
  credentials: {
    username?: string
    password?: string
    code?: string
    phoneCode?: string
  }
  /** 设备ID */
  deviceId?: string
  /** 客户端IP（由后端自动获取） */
  clientIp?: string
}

/**
 * 认证响应结果
 */
export interface AuthenticationResponse {
  /** 访问令牌 */
  accessToken: string
  /** 刷新令牌 */
  refreshToken: string
  /** 过期时间（秒） */
  expiresIn: number
  /** 令牌类型 */
  tokenType: string
  /** 用户信息 */
  user: {
    userId: string
    username: string
    tenantId: string
  }
}

/**
 * Token刷新请求
 */
export interface TokenRefreshRequest {
  /** 刷新令牌 */
  refreshToken: string
}

/**
 * Token刷新响应
 */
export interface TokenRefreshResponse {
  /** 新的访问令牌 */
  accessToken: string
  /** 过期时间（秒） */
  expiresIn: number
}

/**
 * 用户认证
 *
 * @param data 认证请求参数
 * @returns 认证结果
 */
export function authenticate(data: AuthenticationRequest) {
  return request<AuthenticationResponse>({
    url: '/auth/v1/authenticate',
    method: 'post',
    data
  })
}

/**
 * Token刷新
 *
 * @param data 刷新请求参数
 * @returns 新的Token
 */
export function refreshToken(data: TokenRefreshRequest) {
  return request<TokenRefreshResponse>({
    url: '/auth/v1/token/refresh',
    method: 'post',
    data
  })
}

/**
 * 用户登出
 *
 * @returns 登出结果
 */
export function logout() {
  return request({
    url: '/auth/v1/logout',
    method: 'post'
  })
}

/**
 * 获取用户权限列表
 *
 * @param userId 用户ID
 * @returns 权限列表
 */
export function getUserPermissions(userId: string) {
  return request<{ permissions: Permission[] }>({
    url: `/auth/v1/users/${userId}/permissions`,
    method: 'get'
  })
}

/**
 * 权限对象
 */
export interface Permission {
  code: string
  name: string
  resource: string
  action: string
}
```

#### 9.3.2 认证状态管理调整

**修改：`src/stores/auth.ts`**

```typescript
/**
 * 认证状态管理Store
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2026-09-20
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { 
  authenticate, 
  refreshToken, 
  logout as logoutApi,
  getUserPermissions 
} from '@/api/auth-center'
import type { AuthenticationRequest, Permission } from '@/api/auth-center'
import { getDeviceId } from '@/utils/device'

export const useAuthStore = defineStore('auth', () => {
  // 状态
  const accessToken = ref<string | null>(null)
  const refreshTokenValue = ref<string | null>(null)
  const tokenExpiresAt = ref<number | null>(null)
  const user = ref<any>(null)
  const permissions = ref<Permission[]>([])

  // 计算属性
  const isAuthenticated = computed(() => !!accessToken.value)
  const isTokenExpired = computed(() => {
    if (!tokenExpiresAt.value) return true
    return Date.now() > tokenExpiresAt.value
  })

  /**
   * 用户登录
   *
   * @param credentials 登录凭证
   * @returns 登录结果
   */
  async function login(credentials: AuthenticationRequest['credentials']) {
    try {
      const response = await authenticate({
        protocol: 'jwt', // 根据实际选择的协议类型
        credentials,
        deviceId: getDeviceId()
      })

      // 保存Token和用户信息
      setTokens(response.accessToken, response.refreshToken, response.expiresIn)
      user.value = response.user

      // 获取用户权限
      await loadPermissions(response.user.userId)

      return response
    } catch (error) {
      console.error('登录失败:', error)
      throw error
    }
  }

  /**
   * 保存Token信息
   *
   * @param access 访问令牌
   * @param refresh 刷新令牌
   * @param expiresIn 过期时间（秒）
   */
  function setTokens(access: string, refresh: string, expiresIn: number) {
    accessToken.value = access
    refreshTokenValue.value = refresh
    tokenExpiresAt.value = Date.now() + expiresIn * 1000

    // 持久化到localStorage
    localStorage.setItem('access_token', access)
    localStorage.setItem('refresh_token', refresh)
    localStorage.setItem('token_expires_at', String(tokenExpiresAt.value))
  }

  /**
   * 刷新Token
   *
   * @returns 新的Token
   */
  async function refresh() {
    if (!refreshTokenValue.value) {
      throw new Error('没有刷新令牌')
    }

    try {
      const response = await refreshToken({
        refreshToken: refreshTokenValue.value
      })

      // 更新访问令牌
      accessToken.value = response.accessToken
      tokenExpiresAt.value = Date.now() + response.expiresIn * 1000

      // 更新localStorage
      localStorage.setItem('access_token', response.accessToken)
      localStorage.setItem('token_expires_at', String(tokenExpiresAt.value))

      return response
    } catch (error) {
      // 刷新失败，清除所有状态，跳转到登录页
      await logout()
      throw error
    }
  }

  /**
   * 加载用户权限
   *
   * @param userId 用户ID
   */
  async function loadPermissions(userId: string) {
    try {
      const response = await getUserPermissions(userId)
      permissions.value = response.permissions
      
      // 缓存到localStorage
      localStorage.setItem('permissions', JSON.stringify(response.permissions))
    } catch (error) {
      console.error('加载权限失败:', error)
    }
  }

  /**
   * 检查是否有指定权限
   *
   * @param permissionCode 权限码
   * @returns 是否有权限
   */
  function hasPermission(permissionCode: string): boolean {
    return permissions.value.some(p => p.code === permissionCode)
  }

  /**
   * 用户登出
   */
  async function logout() {
    try {
      // 调用后端登出接口
      await logoutApi()
    } catch (error) {
      console.error('登出失败:', error)
    } finally {
      // 清除所有状态
      accessToken.value = null
      refreshTokenValue.value = null
      tokenExpiresAt.value = null
      user.value = null
      permissions.value = []

      // 清除localStorage
      localStorage.removeItem('access_token')
      localStorage.removeItem('refresh_token')
      localStorage.removeItem('token_expires_at')
      localStorage.removeItem('permissions')
      localStorage.removeItem('user')

      // 跳转到登录页
      window.location.href = '/login'
    }
  }

  /**
   * 从localStorage恢复状态
   */
  function restoreFromStorage() {
    const storedAccessToken = localStorage.getItem('access_token')
    const storedRefreshToken = localStorage.getItem('refresh_token')
    const storedExpiresAt = localStorage.getItem('token_expires_at')
    const storedPermissions = localStorage.getItem('permissions')
    const storedUser = localStorage.getItem('user')

    if (storedAccessToken && storedRefreshToken && storedExpiresAt) {
      accessToken.value = storedAccessToken
      refreshTokenValue.value = storedRefreshToken
      tokenExpiresAt.value = parseInt(storedExpiresAt)
    }

    if (storedPermissions) {
      permissions.value = JSON.parse(storedPermissions)
    }

    if (storedUser) {
      user.value = JSON.parse(storedUser)
    }
  }

  // 初始化时恢复状态
  restoreFromStorage()

  return {
    // 状态
    accessToken,
    user,
    permissions,
    isAuthenticated,
    isTokenExpired,
    
    // 方法
    login,
    logout,
    refresh,
    hasPermission,
    loadPermissions
  }
})
```

#### 9.3.3 请求拦截器调整

**修改：`src/utils/request.ts`**

```typescript
/**
 * Axios请求封装
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2026-09-20
 */
import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

/**
 * 创建axios实例
 */
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json'
  }
})

/**
 * 是否正在刷新Token
 */
let isRefreshing = false

/**
 * 刷新Token失败后的待重试请求队列
 */
let pendingRequests: Array<() => void> = []

/**
 * 请求拦截器
 */
service.interceptors.request.use(
  async (config) => {
    const authStore = useAuthStore()

    // 公开接口，无需Token
    const publicPaths = ['/auth/v1/authenticate', '/auth/v1/token/refresh']
    if (publicPaths.some(path => config.url?.includes(path))) {
      return config
    }

    // 检查Token是否过期
    if (authStore.isTokenExpired && !isRefreshing) {
      isRefreshing = true
      
      try {
        // 刷新Token
        await authStore.refresh()
        isRefreshing = false
        
        // 重试所有pending的请求
        pendingRequests.forEach(callback => callback())
        pendingRequests = []
      } catch (error) {
        isRefreshing = false
        pendingRequests = []
        
        // 刷新失败，跳转到登录页
        authStore.logout()
        return Promise.reject(error)
      }
    }

    // Token正在刷新中，将请求加入队列
    if (isRefreshing) {
      return new Promise((resolve) => {
        pendingRequests.push(() => {
          resolve(config)
        })
      })
    }

    // 添加Token到请求头
    if (authStore.accessToken) {
      config.headers.Authorization = `Bearer ${authStore.accessToken}`
    }

    return config
  },
  (error) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

/**
 * 响应拦截器
 */
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const { code, msg, data } = response.data

    // 业务成功
    if (code === 200) {
      return data
    }

    // Token过期（专用错误码40101）
    if (code === 40101) {
      const authStore = useAuthStore()
      authStore.logout()
      ElMessage.error('登录已过期，请重新登录')
      return Promise.reject(new Error(msg || 'Token已过期'))
    }

    // 其他业务错误
    ElMessage.error(msg || '请求失败')
    return Promise.reject(new Error(msg || '请求失败'))
  },
  (error) => {
    // HTTP错误
    if (error.response) {
      const status = error.response.status
      
      switch (status) {
        case 401:
          ElMessage.error('未授权，请登录')
          useAuthStore().logout()
          break
        case 403:
          ElMessage.error('没有权限访问')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 500:
          ElMessage.error('服务器错误')
          break
        default:
          ElMessage.error(error.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误，请检查网络连接')
    }

    return Promise.reject(error)
  }
)

export default service
```

#### 9.3.4 权限指令调整

**新建：`src/directives/permission.ts`**

```typescript
/**
 * 权限指令
 *
 * <p>用于控制页面元素的显示和隐藏，基于用户权限。
 *
 * <h2>使用示例</h2>
 * <pre>
 * <!-- 单个权限 -->
 * <el-button v-permission="'user:create'">创建用户</el-button>
 *
 * <!-- 多个权限（满足其一即可） -->
 * <el-button v-permission="['user:create', 'user:update']">编辑</el-button>
 * </pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2026-09-20
 */
import type { Directive, DirectiveBinding } from 'vue'
import { useAuthStore } from '@/stores/auth'

export const permission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const { value } = binding
    const authStore = useAuthStore()

    if (value) {
      const permissions = Array.isArray(value) ? value : [value]
      const hasPermission = permissions.some(permission => 
        authStore.hasPermission(permission)
      )

      if (!hasPermission) {
        // 移除元素
        el.parentNode?.removeChild(el)
      }
    } else {
      throw new Error('权限指令需要传入权限码')
    }
  }
}

/**
 * 注册权限指令
 */
export function setupPermissionDirective(app: any) {
  app.directive('permission', permission)
}
```

#### 9.3.5 设备指纹工具

**新建：`src/utils/device.ts`**

```typescript
/**
 * 设备指纹工具
 *
 * <p>生成浏览器设备的唯一标识，用于安全审计和设备管理。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2026-09-20
 */

/**
 * 获取设备ID
 *
 * <p>优先从localStorage读取，不存在则生成新的设备ID并保存。
 * 设备ID包含浏览器指纹信息，保证设备唯一性。
 *
 * @returns 设备ID
 */
export function getDeviceId(): string {
  const storageKey = 'device_id'
  let deviceId = localStorage.getItem(storageKey)

  if (!deviceId) {
    // 生成新的设备ID
    deviceId = generateDeviceId()
    localStorage.setItem(storageKey, deviceId)
  }

  return deviceId
}

/**
 * 生成设备ID
 *
 * <p>基于浏览器特征生成唯一标识：
 * <ul>
 *   <li>User Agent</li>
 *   <li>屏幕分辨率</li>
 *   <li>时区</li>
 *   <li>语言</li>
 *   <li>Canvas指纹</li>
 * </ul>
 *
 * @returns 设备ID
 */
function generateDeviceId(): string {
  const fingerprint = [
    navigator.userAgent,
    screen.width + 'x' + screen.height,
    new Date().getTimezoneOffset(),
    navigator.language,
    getCanvasFingerprint()
  ].join('|')

  // 生成hash
  return hashCode(fingerprint).toString(36)
}

/**
 * 获取Canvas指纹
 *
 * @returns Canvas指纹
 */
function getCanvasFingerprint(): string {
  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')
  
  if (!ctx) return ''

  ctx.textBaseline = 'top'
  ctx.font = '14px Arial'
  ctx.fillText('Apex Scaffold', 2, 2)

  return canvas.toDataURL()
}

/**
 * 字符串哈希函数
 *
 * @param str 输入字符串
 * @returns 哈希值
 */
function hashCode(str: string): number {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash // Convert to 32bit integer
  }
  return Math.abs(hash)
}
```

### 9.4 前端新增页面

#### 9.4.1 认证配置管理页面

**新建：`src/views/admin/auth-config/index.vue`**

```vue
<template>
  <div class="auth-config-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>认证授权配置</span>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- JWT配置 -->
        <el-tab-pane label="JWT配置" name="jwt">
          <jwt-config-form />
        </el-tab-pane>

        <!-- 会话配置 -->
        <el-tab-pane label="会话配置" name="session">
          <session-config-form />
        </el-tab-pane>

        <!-- 安全策略 -->
        <el-tab-pane label="安全策略" name="security">
          <security-policy-form />
        </el-tab-pane>

        <!-- 协议管理 -->
        <el-tab-pane label="协议管理" name="protocol">
          <protocol-list />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 认证授权配置管理页面
 *
 * <p>提供JWT、会话、安全策略、协议等配置的管理界面。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2026-09-20
 */
import { ref } from 'vue'
import JwtConfigForm from './components/JwtConfigForm.vue'
import SessionConfigForm from './components/SessionConfigForm.vue'
import SecurityPolicyForm from './components/SecurityPolicyForm.vue'
import ProtocolList from './components/ProtocolList.vue'

const activeTab = ref('jwt')
</script>

<style scoped>
.auth-config-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
```

#### 9.4.2 审计日志查询页面

**新建：`src/views/admin/audit-log/index.vue`**

```vue
<template>
  <div class="audit-log-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>审计日志</span>
        </div>
      </template>

      <!-- 搜索表单 -->
      <el-form :model="searchForm" :inline="true">
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="事件类型">
          <el-select v-model="searchForm.eventType" placeholder="请选择" clearable>
            <el-option label="登录" value="LOGIN" />
            <el-option label="登出" value="LOGOUT" />
            <el-option label="Token刷新" value="TOKEN_REFRESH" />
            <el-option label="权限变更" value="PERMISSION_CHANGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="searchForm.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="eventType" label="事件类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getEventTypeColor(row.eventType)">
              {{ getEventTypeName(row.eventType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="eventStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.eventStatus === 'SUCCESS' ? 'success' : 'danger'">
              {{ row.eventStatus === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="clientIp" label="IP地址" width="150" />
        <el-table-column prop="deviceId" label="设备ID" width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="handleSearch"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 审计日志查询页面
 *
 * <p>提供审计日志的查询、筛选和详情查看功能。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2026-09-20
 */
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { getAuditLogs } from '@/api/auth-center'

// 搜索表单
const searchForm = reactive({
  username: '',
  eventType: '',
  timeRange: []
})

// 分页
const pagination = reactive({
  page: 1,
  pageSize: 20,
  total: 0
})

// 表格数据
const tableData = ref([])

/**
 * 查询数据
 */
async function handleSearch() {
  try {
    const response = await getAuditLogs({
      page: pagination.page,
      pageSize: pagination.pageSize,
      username: searchForm.username,
      eventType: searchForm.eventType,
      startTime: searchForm.timeRange[0],
      endTime: searchForm.timeRange[1]
    })

    tableData.value = response.items
    pagination.total = response.total
  } catch (error) {
    ElMessage.error('查询失败')
  }
}

/**
 * 重置搜索
 */
function handleReset() {
  searchForm.username = ''
  searchForm.eventType = ''
  searchForm.timeRange = []
  pagination.page = 1
  handleSearch()
}

/**
 * 查看详情
 */
function handleDetail(row: any) {
  // TODO: 打开详情对话框
  console.log('详情:', row)
}

/**
 * 获取事件类型颜色
 */
function getEventTypeColor(type: string): string {
  const colorMap: Record<string, string> = {
    LOGIN: 'success',
    LOGOUT: 'info',
    TOKEN_REFRESH: 'warning',
    PERMISSION_CHANGE: 'danger'
  }
  return colorMap[type] || ''
}

/**
 * 获取事件类型名称
 */
function getEventTypeName(type: string): string {
  const nameMap: Record<string, string> = {
    LOGIN: '登录',
    LOGOUT: '登出',
    TOKEN_REFRESH: 'Token刷新',
    PERMISSION_CHANGE: '权限变更'
  }
  return nameMap[type] || type
}

// 初始化查询
handleSearch()
</script>

<style scoped>
.audit-log-container {
  padding: 20px;
}
</style>
```

### 9.5 前端路由配置

**修改：`src/router/index.ts`**

```typescript
/**
 * 路由配置
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2026-09-20
 */
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: 'auth-config',
        name: 'AuthConfig',
        component: () => import('@/views/admin/auth-config/index.vue'),
        meta: { 
          title: '认证配置', 
          requiresAuth: true,
          permission: 'auth:config:read'
        }
      },
      {
        path: 'audit-log',
        name: 'AuditLog',
        component: () => import('@/views/admin/audit-log/index.vue'),
        meta: { 
          title: '审计日志',
          requiresAuth: true,
          permission: 'audit:log:read'
        }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 路由守卫
 */
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  // 需要认证
  if (to.meta.requiresAuth) {
    if (!authStore.isAuthenticated) {
      // 未登录，跳转到登录页
      next({ name: 'Login', query: { redirect: to.fullPath } })
      return
    }

    // 检查权限
    if (to.meta.permission && !authStore.hasPermission(to.meta.permission as string)) {
      // 无权限，跳转到403页面
      next({ name: 'Forbidden' })
      return
    }
  }

  next()
})

export default router
```

### 9.6 前端环境变量配置

**新建：`.env.development`**

```bash
# 开发环境配置

# API基础URL - 指向认证授权中心
VITE_API_BASE_URL=http://localhost:8080

# 认证授权中心地址（如果独立部署）
VITE_AUTH_CENTER_URL=http://localhost:8081
```

**新建：`.env.production`**

```bash
# 生产环境配置

# API基础URL - 通过API网关访问
VITE_API_BASE_URL=https://api.example.com

# 认证授权中心地址（如果独立部署）
VITE_AUTH_CENTER_URL=https://auth.example.com
```

### 9.7 前端构建配置调整

**修改：`vite.config.ts`**

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      // 代理认证授权中心接口
      '/auth': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      // 代理业务接口
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

### 9.8 前端测试代码

**新建：`tests/unit/stores/auth.spec.ts`**

```typescript
/**
 * 认证Store单元测试
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2026-09-20
 */
import { setActivePinia, createPinia } from 'pinia'
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/auth-center'

// Mock API
vi.mock('@/api/auth-center')

describe('认证Store', () => {
  beforeEach(() => {
    // 创建新的Pinia实例
    setActivePinia(createPinia())
    // 清除localStorage
    localStorage.clear()
  })

  it('登录成功后保存Token和用户信息', async () => {
    const authStore = useAuthStore()
    
    // Mock登录响应
    vi.mocked(authApi.authenticate).mockResolvedValue({
      accessToken: 'test-access-token',
      refreshToken: 'test-refresh-token',
      expiresIn: 7200,
      tokenType: 'Bearer',
      user: {
        userId: '123',
        username: 'test@example.com',
        tenantId: 'tenant-001'
      }
    })

    // 执行登录
    await authStore.login({
      username: 'test@example.com',
      password: 'password'
    })

    // 验证状态
    expect(authStore.accessToken).toBe('test-access-token')
    expect(authStore.user?.userId).toBe('123')
    expect(authStore.isAuthenticated).toBe(true)
    
    // 验证localStorage
    expect(localStorage.getItem('access_token')).toBe('test-access-token')
  })

  it('Token过期时自动刷新', async () => {
    const authStore = useAuthStore()
    
    // 设置过期的Token
    authStore.accessToken = 'expired-token'
    authStore.refreshTokenValue = 'refresh-token'
    authStore.tokenExpiresAt = Date.now() - 1000 // 已过期

    // Mock刷新响应
    vi.mocked(authApi.refreshToken).mockResolvedValue({
      accessToken: 'new-access-token',
      expiresIn: 7200
    })

    // 执行刷新
    await authStore.refresh()

    // 验证新Token
    expect(authStore.accessToken).toBe('new-access-token')
    expect(authStore.isTokenExpired).toBe(false)
  })

  it('登出时清除所有状态', async () => {
    const authStore = useAuthStore()
    
    // 设置初始状态
    authStore.accessToken = 'test-token'
    authStore.user = { userId: '123' }

    // Mock登出API
    vi.mocked(authApi.logout).mockResolvedValue()

    // 执行登出
    await authStore.logout()

    // 验证状态已清除
    expect(authStore.accessToken).toBeNull()
    expect(authStore.user).toBeNull()
    expect(localStorage.getItem('access_token')).toBeNull()
  })
})
```

### 9.9 前端文档交付清单

**必须交付的前端文档**:

1. **前端集成指南**
   - 路径：`docs/frontend/auth-center-integration.md`
   - 内容：API调用方式、状态管理、权限控制

2. **前端组件文档**
   - 路径：`docs/frontend/components.md`
   - 内容：认证配置组件、审计日志组件使用说明

3. **前端开发规范**
   - 路径：`docs/frontend/development-standards.md`
   - 内容：TypeScript规范、Vue3规范、测试规范

## 附录

### 附录A：现有系统组件清单

详见第1.2.1节"认证授权实现分布"。

### 附录B：参考资料

1. **OAuth 2.0规范**: https://oauth.net/2/
2. **OpenID Connect规范**: https://openid.net/connect/
3. **SAML 2.0规范**: http://saml.xml.org/saml-specifications
4. **Spring Authorization Server**: https://spring.io/projects/spring-authorization-server
5. **Keycloak文档**: https://www.keycloak.org/documentation
6. **等保2.0标准**: GB/T 22239-2019
7. **ISO 27001标准**: ISO/IEC 27001:2013

### 附录C：术语表

| 术语 | 说明 |
|------|------|
| JWT | JSON Web Token，一种无状态Token格式 |
| OAuth2 | 开放授权标准协议 |
| OIDC | OpenID Connect，基于OAuth2的认证协议 |
| SSO | Single Sign-On，单点登录 |
| SAML | Security Assertion Markup Language，安全断言标记语言 |
| RBAC | Role-Based Access Control，基于角色的访问控制 |
| ABAC | Attribute-Based Access Control，基于属性的访问控制 |
| MFA | Multi-Factor Authentication，多因素认证 |
| TOTP | Time-based One-Time Password，基于时间的一次性密码 |
| JTI | JWT ID，JWT令牌的唯一标识符 |

---

## 文档信息

- **文档版本**: v1.0.0
- **创建日期**: 2026-09-20
- **作者**: Apex Team
- **状态**: 待审批
- **目标读者**: 架构师、技术负责人、开发团队
- **文档位置**: `/Users/work/apex-scaffold/.trae/documents/unified-auth-center-design.md`

## 变更记录

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|---------|
| v1.0.0 | 2026-09-20 | Apex Team | 初始版本，完成统一认证授权中心详细设计 |

## 1. 项目背景与现状分析

### 1.1 项目背景

Apex Scaffold 是一个基于 Java 17、Spring Boot 3、WebFlux 的响应式多租户 SaaS 平台。随着业务发展，系统已集成多种认证授权机制以满足不同场景需求：

- **JWT 认证**: 主流的无状态 Token 认证方式
- **OAuth2 授权服务器**: 支持授权码、客户端凭证、密码模式等标准授权流程
- **SSO 单点登录**: 支持 SAML、CAS、OIDC 等协议的企业级单点登录
- **Keycloak 集成**: 与开源 IAM 平台 Keycloak 的 OIDC 集成
- **RBAC 权限模型**: 基于角色的细粒度权限控制
- **多种登录策略**: 微信授权码、微信一键登录、短信验证码、创蓝短信等

### 1.2 现状分析

#### 1.2.1 认证授权实现分布

**核心模块分布**:
- **apex-interfaces**: 安全配置、JWT 过滤器、OAuth2 过滤器、路由定义
- **apex-application**: 登录策略、OAuth2 服务、SSO 服务、认证配置服务
- **apex-domain**: 用户实体、角色实体、权限实体、Token 服务、仓储接口
- **apex-infrastructure**: 数据持久化、MyBatis-Plus Mapper、缓存实现

**关键组件清单**:

1. **JWT 认证体系**
   - [JwtAuthenticationFilter](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/security/JwtAuthenticationFilter.java): JWT 认证过滤器，支持 Token 解析、黑名单校验、用户权限加载
   - [JwtUtil](../../../apex-shared-parent/apex-common/src/main/java/com/apex/scaffold/common/util/JwtUtil.java): JWT 工具类，支持 Token 签发和验证
   - [TokenService](../../../apex-shared-parent/apex-domain/src/main/java/com/apex/scaffold/domain/service/TokenService.java): Token 领域服务，管理 Access Token 和 Refresh Token
   - [TokenBlacklistService](../../../apex-shared-parent/apex-domain/src/main/java/com/apex/scaffold/domain/service/TokenBlacklistService.java): Token 黑名单服务

2. **OAuth2 授权服务器**
   - [OAuth2AuthorizationServiceImpl](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/OAuth2AuthorizationServiceImpl.java): OAuth2 授权核心逻辑
   - [OAuth2AuthorizationHandler](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/handler/OAuth2AuthorizationHandler.java): 授权端点处理器
   - [OAuth2ClientManagementService](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/OAuth2ClientManagementService.java): 客户端管理
   - [OAuth2ResourceServerFilter](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/security/OAuth2ResourceServerFilter.java): 资源服务器过滤器

3. **SSO 单点登录**
   - [SsoAuthHandler](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/handler/SsoAuthHandler.java): SSO 认证端点
   - [SysSsoProviderService](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/SysSsoProviderService.java): SSO 提供商管理
   - [SsoProtocolAdapter](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/support/sso/SsoProtocolAdapter.java): 协议适配器接口
   - [SamlProtocolAdapter](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/support/sso/SamlProtocolAdapter.java): SAML 协议适配器
   - [OidcProtocolAdapter](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/support/sso/OidcProtocolAdapter.java): OIDC 协议适配器

4. **Keycloak 集成**
   - [KeycloakConfig](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/config/KeycloakConfig.java): Keycloak 配置类
   - [OidcAuthenticationSuccessHandler](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/security/OidcAuthenticationSuccessHandler.java): OIDC 认证成功处理器
   - [KeycloakAdminHandler](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/handler/KeycloakAdminHandler.java): Keycloak 管理接口

5. **RBAC 权限体系**
   - [RbacMethodSecurityConfig](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/config/RbacMethodSecurityConfig.java): 方法级权限配置
   - [RequirePermissionAspect](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/aspect/RequirePermissionAspect.java): 权限校验切面
   - [DataScopeAspect](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/aspect/DataScopeAspect.java): 数据权限切面
   - [RoleService](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/RoleService.java): 角色管理服务
   - [PermissionService](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/PermissionService.java): 权限管理服务
   - [MenuService](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/MenuService.java): 菜单管理服务

6. **登录策略**
   - [LoginStrategy](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/login/LoginStrategy.java): 登录策略接口
   - [WeChatCodeLoginStrategy](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/login/WeChatCodeLoginStrategy.java): 微信授权码登录
   - [WeChatPhoneLoginStrategy](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/login/WeChatPhoneLoginStrategy.java): 微信一键登录
   - [SmsLoginStrategy](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/login/SmsLoginStrategy.java): 短信验证码登录
   - [ChuanglanSmsLoginStrategy](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/application/service/login/ChuanglanSmsLoginStrategy.java): 创蓝短信登录

7. **安全配置管理**
   - [SecurityConfig](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/config/SecurityConfig.java): Spring Security 配置
   - [SecurityProperties](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/config/SecurityProperties.java): 安全属性配置
   - [AuthConfigHandler](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/handler/AuthConfigHandler.java): 认证配置管理接口
   - [SysJwtConfigEntity](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/domain/model/entity/SysJwtConfigEntity.java): JWT 配置实体
   - [SysSessionConfigEntity](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/domain/model/entity/SysSessionConfigEntity.java): 会话配置实体
   - [SysSecurityPolicyEntity](../../../apex-components-parent/apex-system/src/main/java/com/apex/scaffold/domain/model/entity/SysSecurityPolicyEntity.java): 安全策略实体

#### 1.2.2 配置分布

认证授权相关配置分散在多个层次：

1. **应用配置文件** (`apex-interfaces/src/main/resources/`)
   - `application.yml`: 全局配置
   - `application-dev.yml`: 开发环境配置
   - `application-prod.yml`: 生产环境配置
   - 配置项包括: JWT 密钥、过期时间、Keycloak 地址、OAuth2 客户端等

2. **环境变量**
   - `JWT_SECRET`: JWT 签名密钥
   - `KEYCLOAK_SERVER_URL`: Keycloak 服务地址
   - `KEYCLOAK_CLIENT_SECRET`: Keycloak 客户端密钥
   - 其他敏感配置

3. **数据库配置表**
   - `sys_jwt_config`: JWT 配置
   - `sys_session_config`: 会话配置
   - `sys_security_policy`: 安全策略
   - `oauth2_registered_client`: OAuth2 客户端
   - `sys_sso_provider`: SSO 提供商

4. **Java 配置类**
   - 安全过滤器链配置
   - 认证管理器配置
   - 权限表达式处理器配置

### 1.3 核心痛点

根据用户反馈，当前架构存在以下核心痛点：

#### 1.3.1 配置分散，运维成本高

**问题表现**:
- JWT 密钥分散在环境变量、配置文件、数据库三处
- 修改会话超时时间需要同时更新多个配置文件和数据库
- 安全策略配置散落在代码、配置文件、数据库中
- 缺乏统一的配置管理界面，运维人员需要登录服务器修改配置

**影响**:
- 配置变更风险高，容易遗漏
- 故障排查困难，需要检查多个位置
- 运维效率低，无法快速响应业务需求

#### 1.3.2 协议割裂，集成复杂

**问题表现**:
- JWT、OAuth2、SSO、Keycloak 各自实现，缺乏统一抽象
- 新增认证方式需要大量开发工作：实现过滤器、配置路由、编写服务层代码
- 协议间缺乏互操作性，例如 SSO 登录后无法直接获取 OAuth2 Token
- 代码重复度高，Token 签发逻辑在多处重复实现

**影响**:
- 开发成本高，新协议接入周期长
- 维护成本高，修改一处逻辑需要同步多处代码
- 扩展性差，难以支持新的认证标准（如 WebAuthn、FIDO2）

#### 1.3.3 审计困难，安全风险

**问题表现**:
- 认证日志分散在多个模块，缺乏统一的审计视图
- 登录日志、Token 刷新日志、权限变更日志存储在不同表中
- 缺乏实时监控和告警机制，无法及时发现异常登录
- 审计日志格式不统一，难以进行安全分析和合规审查

**影响**:
- 安全事件响应慢，难以追溯攻击路径
- 合规风险高，无法满足等保、ISO 27001 等审计要求
- 缺乏安全态势感知能力

#### 1.3.4 多租户隔离不彻底

**问题表现**:
- 虽然核心表有 `tenant_id` 字段，但认证授权策略的租户级配置不完善
- JWT 配置、会话策略、安全策略虽有租户字段，但缺乏租户级的动态配置能力
- 租户间的资源隔离依赖应用层校验，缺乏基础设施层的强制隔离
- 缺乏租户级的审计日志隔离和查询能力

**影响**:
- 数据泄露风险，租户 A 的用户可能访问租户 B 的资源
- 无法满足大型企业客户的数据隔离要求
- 难以实现租户级的差异化服务（例如金融租户要求更严格的安全策略）

##