# Apex 认证授权中心

统一认证授权中心，提供 JWT、OAuth2、SSO 等多协议认证支持。

## 功能特性

### ✅ 已实现
- **JWT 认证**: 完整的 Token 生命周期管理（签发、验证、刷新、撤销）
- **用户登录**: 用户名密码登录、BCrypt 加密、登录失败锁定
- **安全机制**: 5 次失败锁定 30 分钟、账户状态检查、租户隔离
- **响应式设计**: 基于 Spring WebFlux 和 Reactor
- **数据持久化**: MyBatis-Plus + Flyway 数据库迁移

### ⏳ 开发中
- 权限管理 (RBAC)
- 会话管理
- 审计日志
- OAuth2 / SSO

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

## 测试账户

| 用户名 | 密码 | 租户 | 角色 |
|--------|------|------|------|
| admin | password123 | default | ADMIN |
| user | password123 | default | USER |

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
  "userId": 1,
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
POST /auth/v1/logout/{userId}?tenantId=default

响应: "登出成功"
```

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
  secret: ${JWT_SECRET:your-secret-key}
  expiration: 7200000        # 2 小时
  refresh-expiration: 604800000  # 7 天
```

### 环境变量
- `DB_PASSWORD`: 数据库密码
- `JWT_SECRET`: JWT 签名密钥（生产环境必须设置）

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
- **设计方案**: [unified-auth-center-design.md](../.trae/documents/unified-auth-center-design.md)
- **完成报告**: [auth-center-stage2-complete.md](../.trae/documents/auth-center-stage2-complete.md)

## 技术栈

- **框架**: Spring Boot 3.5.15, Spring WebFlux
- **认证**: JWT (JJWT), BCrypt
- **数据库**: MyBatis-Plus, Flyway
- **响应式**: Project Reactor
- **工具**: Lombok, MapStruct, Hutool

## 许可证

Copyright © 2026 Apex Scaffold Team
