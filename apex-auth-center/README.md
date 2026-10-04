# Apex 认证授权中心

统一认证授权中心，提供 JWT、OAuth2、SSO 等多协议认证支持。

## 功能特性

当前代码包含 JWT 登录、刷新轮换与撤销、RBAC、会话、审计、MFA 和 OAuth2 端点。接口和服务的存在不代表所有能力已完成运行时验证，验证步骤见 [TESTING.md](./TESTING.md)。

本模块由 `AuthCenterApplication` 独立启动，与主应用的容器、用户仓储及迁移边界分离。主应用仍使用自己的 JWT 登录链；当前集成统一的是授权数据源，通过 HTTPS 授权快照接口完成，不直接调用中心内部 Mapper。

### 统一授权快照

`GET /auth/v1/authorization/users/{userId}` 要求 Bearer ACCESS Token。Token 用户必须等于 `auth-center.authorization-reader-user-id`（环境变量 `AUTH_CENTER_AUTHORIZATION_READER_USER_ID`）；读取账号无需管理员角色。租户来自认证上下文，读取账号与目标账号均须同租户、启用、未锁定且未软删除。

成功响应为 `Result<UnifiedAuthorizationResponse>`，字段为 `code`、`msg`、`data`、`traceId`；`data` 包含中心 `tenantId`、`userId`、权限编码数组 `permissions` 及 `{code, dataScope}` 角色数组 `roles`。登录和刷新接口则直接返回 DTO，不能统一按 `.data` 解析。正整数路径校验失败返回 400，不合资格的已认证调用者返回 403；未认证请求由安全链处理。

主应用开关默认关闭；开启须显式配置跨系统身份映射、HTTPS 地址与读取凭据。请求超时 3 秒，使用 Resilience4j 默认熔断器；缓存默认关闭，TTL 为 0–30 秒，无过期缓存降级和本地权限回退。中心支持 `SELF`、`ALL`、`DEPT`、`DEPT_AND_CHILD` 数据范围；`CUSTOM`、空值和未知范围拒绝。完整参数见[配置说明](../docs/05-operations/configuration.md#认证中心统一授权)。

## 快速开始

### 1. 环境要求
- Java 17 (SDKMAN: `17.0.12-oracle`)
- Maven Daemon 2.0.0-rc-3
- MySQL 8.0+

### 2. 启动应用
```bash
cd /Users/work/apex-scaffold
sdk env
mvnd -pl apex-auth-center -am clean package -DskipTests
cd apex-auth-center
mvnd spring-boot:run
```

### 3. 测试接口
```bash
# 登录
curl -X POST http://localhost:8081/auth/v1/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "password123",
    "tenantId": "default"
  }'
```

## 开发种子账户

| 用户名 | 密码 | 租户 | 角色 |
|--------|------|------|------|
| admin | password123（种子脚本声明） | default | ADMIN |
| user | password123（种子脚本声明） | default | USER |

账户来源为 `V46__insert_test_users.sql`，ID 分别为 1001、1002；本文未验证种子密码哈希或数据库实际迁移状态。部署时配置独立读取账号，不应把示例账户作为生产凭据。

## 项目结构

```
apex-auth-center/
├── src/main/java/
│   ├── application/        # 应用服务层
│   │   ├── dto/           # 数据传输对象
│   │   └── service/       # 应用服务
│   ├── config/            # 配置类
│   ├── domain/            # 领域层
│   │   ├── model/         # 领域模型（实体、值对象）
│   │   ├── repository/    # 仓储接口
│   │   └── service/       # 领域服务接口
│   ├── infrastructure/    # 基础设施层
│   │   └── persistence/   # 持久化实现
│   └── interfaces/        # 接口层
│       ├── handler/       # 请求处理器
│       └── router/        # 路由配置
└── src/main/resources/
    ├── db/migration/      # Flyway 数据库迁移
    └── application.yml    # 应用配置
```

## API 文档

### 认证接口

#### 登录
```
POST /auth/v1/login
Content-Type: application/json

{
  "username": "admin",
  "password": "password123",
  "tenantId": "default",
  "deviceId": "optional-device-id"
}

响应: LoginResponse
{
  "userId": 1001,
  "username": "admin",
  "tenantId": "default",
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 7200
}
```

#### Token 刷新
```
POST /auth/v1/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOi..."
}

响应: TokenPair
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "expiresIn": 7200,
  "tokenType": "Bearer"
}
```

#### 登出
```
POST /auth/v1/logout/{userId}
Authorization: Bearer {accessToken}

响应: HTTP 200，空响应体
```

刷新实现 `TokenServiceImpl.refreshToken` 撤销旧 Refresh Token 并签发新 Token 对，客户端需保存新的两种 Token。登出路径 ID 必须等于认证用户 ID，租户取自认证上下文，并撤销该用户在该租户的全部 Token。

## 配置说明

### application.yml
```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/apex_auth
    username: root
    password: ${DB_PASSWORD:root}

jwt:
  secret: ${JWT_SECRET:your-secret-key-here-must-be-at-least-32-bytes-long}
  expiration: 7200000        # 2 小时
  refresh-expiration: 604800000  # 7 天
```

配置文件为 `src/main/resources/application.yml`，Flyway 当前激活 `classpath:db/migration/mysql`；已移除的 `db/migration/auth/*/V51` 不是当前迁移入口。

### 环境变量
- `DB_PASSWORD`: 数据库密码
- `JWT_SECRET`: JWT 签名密钥（生产环境必须设置）

- `TOKEN_ENCRYPTION_KEY`: Token 存储加密密钥
- `CONFIG_ENCRYPTION_KEY`: 配置加密密钥
- `AUTH_CENTER_AUTHORIZATION_READER_USER_ID`: 专用授权读取账号 ID

主应用的 `APEX_AUTH_CENTER_ACCESS_TOKEN` 由部署侧注入并轮换，远程适配器不自动登录或刷新凭据。

## 开发指南

### 运行测试
```bash
mvnd -pl apex-auth-center test
```

### 代码格式化
```bash
mvnd -pl apex-auth-center spotless:apply
```

### 质量检查
```bash
mvnd -pl apex-auth-center spotless:check pmd:check
```

### 查看覆盖率
```bash
mvnd -pl apex-auth-center test
open apex-auth-center/target/site/jacoco/index.html
```

## 文档

- **测试指南**: [TESTING.md](./TESTING.md)
- **设计方案**: [unified-auth-center-design.md](../docs/_archive/trae-documents/unified-auth-center-design.md)
- **历史阶段报告（不作为当前验证结果）**: [auth-center-stage2-complete.md](../docs/_archive/trae-documents/auth-center-stage2-complete.md)

## 技术栈

- **框架**: Spring Boot 3.5.15, Spring WebFlux
- **认证**: JWT (JJWT), BCrypt
- **数据库**: MyBatis-Plus, Flyway
- **响应式**: Project Reactor
- **工具**: Lombok, MapStruct, Hutool

## 许可证

Copyright © 2026 Apex Scaffold Team
