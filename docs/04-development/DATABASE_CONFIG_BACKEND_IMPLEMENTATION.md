# 数据库配置后端接口实施文档

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

## 概述

已完成数据库配置相关的后端接口实现，提供数据库连接测试、部署配置生成和当前数据库信息查询三个核心功能。

## 实施内容

### 1. DTO 层 (`apex-components-parent/apex-system/dto/admin`)

#### DatabaseConfigRequest.java
数据库配置请求 DTO，包含：
- `dbType`: 数据库类型（mysql/postgresql）
- `host`: 主机地址
- `port`: 端口（1-65535）
- `database`: 数据库名
- `username`: 用户名
- `password`: 密码（仅用于一次性操作）
- `connectionParams`: 连接参数（可选）
- `environment`: 部署环境（dev/test/prod）

**验证规则**：
- 所有必填字段使用 `@NotBlank` 和 `@NotNull` 验证
- 端口使用 `@Min(1)` 和 `@Max(65535)` 验证

#### DatabaseTestResultDTO.java
数据库连接测试结果 DTO：
- `success`: 是否成功
- `message`: 提示信息
- `version`: 数据库版本（成功时返回）

#### DeploymentConfigResultDTO.java
部署配置生成结果 DTO：
- `yaml`: YAML 格式配置
- `env`: 环境变量格式配置
- `dockerCompose`: Podman Compose 配置（保留兼容 API 字段名）

#### CurrentDatabaseInfoDTO.java
当前数据库信息 DTO：
- `type`: 数据库类型
- `version`: 数据库版本
- `driverVersion`: 驱动版本

### 2. Service 层 (`apex-components-parent/apex-system/service`)

#### DatabaseConfigService.java

**核心方法**：

1. **testConnection(DatabaseConfigRequest)**
   - 使用 HikariCP 创建临时连接池测试连接
   - 超时时间 5 秒
   - 成功返回数据库版本信息
   - 失败返回详细错误信息
   - 在 `boundedElastic()` 线程池执行

2. **generateDeploymentConfig(DatabaseConfigRequest)**
   - 生成三种格式的部署配置
   - YAML 配置：Spring Boot 数据源配置，密码使用环境变量
   - 环境变量配置：包含完整连接信息
   - Podman Compose 配置：数据库容器定义
   - 在 `boundedElastic()` 线程池执行

3. **getCurrentDatabaseInfo()**
   - 查询当前运行数据库的类型和版本
   - 使用应用现有 DataSource
   - 在 `boundedElastic()` 线程池执行

**技术实现**：
- 所有数据库操作使用 `Schedulers.boundedElastic()` 避免阻塞 WebFlux 主线程
- 使用 HikariCP 进行临时连接测试
- 支持 MySQL 和 PostgreSQL 驱动自动选择
- JDBC URL 构建支持自定义连接参数

**安全设计**：
- 密码仅用于临时连接测试，不持久化
- 配置导出建议使用环境变量注入密码

### 3. Handler 层 (`apex-components-parent/apex-system/handler`)

#### DatabaseConfigHandler.java

**端点定义**：

1. **POST /admin-api/v1/database/test**
   - 测试数据库连接
   - 权限：`system:database:test`
   - 请求体：`DatabaseConfigRequest`
   - 响应：`Result<DatabaseTestResultDTO>`

2. **POST /admin-api/v1/database/generate-config**
   - 生成部署配置
   - 权限：`system:database:generate`
   - 请求体：`DatabaseConfigRequest`
   - 响应：`Result<DeploymentConfigResultDTO>`

3. **GET /admin-api/v1/database/current**
   - 获取当前数据库信息
   - 权限：`system:database:info`
   - 响应：`Result<CurrentDatabaseInfoDTO>`

**特性**：
- 继承 `BaseHandler` 提供统一响应封装
- 使用 `@RequirePermission` 进行权限控制
- 使用 `@Operation` 提供 OpenAPI 文档
- 使用 Jakarta Bean Validation 自动校验请求参数

### 4. Router 层 (`apex-components-parent/apex-system/router`)

#### ApiRouter.java

已添加路由方法 `adminDatabaseConfigRoutes(DatabaseConfigHandler)`：
```java
@Bean
public RouterFunction<ServerResponse> adminDatabaseConfigRoutes(DatabaseConfigHandler handler) {
    return RouterFunctions.route()
            .POST("/admin-api/v1/database/test", contentType(MediaType.APPLICATION_JSON), handler::testConnection)
            .POST("/admin-api/v1/database/generate-config", contentType(MediaType.APPLICATION_JSON), handler::generateConfig)
            .GET("/admin-api/v1/database/current", handler::getCurrentInfo)
            .build();
}
```

## 验证结果

✅ **编译验证**：所有模块编译成功  
✅ **模块数量**：10 个模块全部通过  
✅ **警告处理**：Lombok @NoArgsConstructor/@AllArgsConstructor 警告已知，符合项目配置  

## 使用示例

### 1. 测试数据库连接

**请求**：
```bash
POST /admin-api/v1/database/test
Content-Type: application/json

{
  "dbType": "mysql",
  "host": "localhost",
  "port": 3306,
  "database": "apex_db",
  "username": "root",
  "password": "password",
  "connectionParams": "useSSL=false&serverTimezone=Asia/Shanghai",
  "environment": "dev"
}
```

**响应**：
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "success": true,
    "message": "连接成功",
    "version": "MySQL 8.0.35"
  },
  "traceId": "abc123"
}
```

### 2. 生成部署配置

**请求**：
```bash
POST /admin-api/v1/database/generate-config
Content-Type: application/json

{
  "dbType": "postgresql",
  "host": "localhost",
  "port": 5432,
  "database": "apex_db",
  "username": "postgres",
  "password": "password",
  "environment": "prod"
}
```

**响应**：
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "yaml": "spring:\n  datasource:\n    url: jdbc:postgresql://localhost:5432/apex_db\n    ...",
    "env": "DB_URL=jdbc:postgresql://localhost:5432/apex_db\nDB_USERNAME=postgres\n...",
    "dockerCompose": "version: '3.8'\nservices:\n  database:\n    image: postgres:15\n..."
  },
  "traceId": "def456"
}
```

### 3. 获取当前数据库信息

**请求**：
```bash
GET /admin-api/v1/database/current
```

**响应**：
```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "type": "MySQL",
    "version": "8.0.35",
    "driverVersion": "mysql-connector-j-8.0.33 (Revision: 5)"
  },
  "traceId": "ghi789"
}
```

## 技术栈

- Java 17
- Spring Boot 3.5.15
- Spring WebFlux
- HikariCP
- Reactor
- Jakarta Validation
- Lombok

## 文件清单

```
apex-scaffold/
├── apex-components-parent/apex-system/
│   ├── dto/admin/
│   │   ├── DatabaseConfigRequest.java           # 新增
│   │   ├── DatabaseTestResultDTO.java           # 新增
│   │   ├── DeploymentConfigResultDTO.java       # 新增
│   │   └── CurrentDatabaseInfoDTO.java          # 新增
│   └── service/
│       └── DatabaseConfigService.java            # 新增
├── apex-components-parent/apex-system/
│   ├── handler/
│   │   └── DatabaseConfigHandler.java           # 新增
│   └── router/
│       └── ApiRouter.java                       # 修改（添加路由）
└── docs/
    └── DATABASE_CONFIG_BACKEND_IMPLEMENTATION.md # 本文档
```

## 权限配置

需要在 `sys_menu` 表中添加以下权限：

- `system:database:view` - 查看数据库配置页面
- `system:database:test` - 测试数据库连接
- `system:database:generate` - 生成部署配置
- `system:database:info` - 查询当前数据库信息

## 注意事项

1. **密码安全**：密码仅用于一次性操作，不会被保存到数据库或日志
2. **线程安全**：所有数据库操作在 `boundedElastic()` 线程池执行
3. **连接池隔离**：测试连接使用独立的 HikariCP 连接池，不影响应用主连接池
4. **错误处理**：连接失败时返回详细错误信息而非抛出异常
5. **数据库支持**：当前支持 MySQL 和 PostgreSQL，其他数据库需扩展

## 下一步

1. **前端集成**：前端页面已实现，可直接调用这些接口
2. **菜单配置**：执行 `database-menu.sql` 添加菜单和权限
3. **功能测试**：使用 Postman 或前端页面测试三个接口
4. **生产配置**：确保生产环境的连接池配置和超时设置合理

## 参考文档

- 前端实施文档：`/Users/work/apex-admin-web/DATABASE_CONFIG_IMPLEMENTATION.md`
- 方案文档：`/Users/work/apex-scaffold/.trae/documents/前端配置数据库多适配方案.md`（原文未入库）
