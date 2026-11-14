# Apex Scaffold API 文档预览

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

**版本**：1.2.0  
**基础URL**：http://localhost:8080  
**文档生成时间**：2026-09-07  
**OpenAPI 规范**：3.0

---

## 📚 文档访问方式

### 在线文档（需应用运行）

1. **Swagger UI**（标准界面）
   ```
   http://localhost:8080/swagger-ui.html
   ```
   - 标准的 Swagger UI 界面
   - 支持在线测试 API
   - 自动从代码注解生成

2. **Knife4j**（增强界面，推荐）
   ```
   http://localhost:8080/doc.html
   ```
   - 更美观的中文界面
   - 增强的文档展示
   - 更好的交互体验

3. **OpenAPI JSON**（机器可读）
   ```
   http://localhost:8080/v3/api-docs
   ```
   - OpenAPI 3.0 规范 JSON 格式
   - 可用于生成客户端 SDK
   - 可导入 Postman/Apifox

---

## 📋 API 模块总览

基于代码分析，项目包含以下 API 模块（共 **60+ 个接口**）：

### 1️⃣ 认证与授权（AuthHandler）

**模块**：`/api/v1/auth`  
**接口数**：9 个

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 用户登录 | POST | `/api/v1/auth/login` | 支持密码/微信/手机验证码登录 |
| 刷新令牌 | POST | `/api/v1/auth/refresh` | 使用 Refresh Token 获取新的 Access Token |
| 用户登出 | POST | `/api/v1/auth/logout` | 登出当前会话 |
| 绑定手机号 | POST | `/api/v1/auth/bind-phone` | 绑定手机号到当前账户 |
| 用户注册 | POST | `/api/v1/auth/register` | 新用户注册 |
| 绑定微信手机号 | POST | `/api/v1/auth/bind-wechat-phone` | 微信小程序手机号绑定 |
| 发送短信验证码 | POST | `/api/v1/auth/sms` | 发送短信验证码 |
| 发送闭环短信 | POST | `/api/v1/auth/cl-sms` | 发送闭环短信验证码 |
| 验证手机号 | POST | `/api/v1/auth/validate-mobile` | 验证手机号有效性 |

**示例请求**（用户登录）：
```json
POST /api/v1/auth/login
Content-Type: application/json

{
  "loginType": "PASSWORD",
  "username": "admin",
  "password": "Admin@123",
  "captchaId": "abc123",
  "captchaCode": "1234"
}
```

**示例响应**：
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "tokenType": "Bearer"
  },
  "traceId": "abc-123-def"
}
```

---

### 2️⃣ 用户管理（UserHandler）

**模块**：`/api/v1/user`  
**接口数**：8+ 个

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 获取当前用户信息 | GET | `/api/v1/user/me` | 获取当前登录用户详细信息 |
| 更新用户资料 | PUT | `/api/v1/user/profile` | 更新当前用户资料 |
| 修改密码 | POST | `/api/v1/user/change-password` | 修改当前用户密码 |
| 获取用户信用信息 | GET | `/api/v1/user/credit` | 获取用户信用积分 |
| 账户注销申请 | POST | `/api/v1/user/deletion` | 申请注销账户 |
| 取消注销申请 | DELETE | `/api/v1/user/deletion/{requestId}` | 取消账户注销申请 |
| 获取注销状态 | GET | `/api/v1/user/deletion/status` | 查询账户注销状态 |

**示例请求**（获取当前用户）：
```http
GET /api/v1/user/me
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**示例响应**：
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "username": "admin",
    "nickname": "管理员",
    "phone": "138****8888",
    "email": "admin@example.com",
    "avatar": "https://cdn.example.com/avatar/admin.jpg",
    "status": "ACTIVE",
    "roles": ["ADMIN", "USER"],
    "permissions": ["user:read", "user:write"],
    "createdAt": "2026-01-01T00:00:00Z"
  },
  "traceId": "xyz-456-abc"
}
```

---

### 3️⃣ 角色与权限（AuthorizationHandler）

**模块**：`/api/v1/roles`, `/api/v1/permissions`  
**接口数**：12 个

#### 角色管理（7 个接口）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 获取当前用户角色 | GET | `/api/v1/roles/my` | 获取当前用户的角色列表 |
| 获取所有角色 | GET | `/api/v1/roles` | 获取系统所有角色（分页） |
| 创建角色 | POST | `/api/v1/roles` | 创建新角色 |
| 更新角色 | PUT | `/api/v1/roles/{id}` | 更新角色信息 |
| 删除角色 | DELETE | `/api/v1/roles/{id}` | 删除角色 |
| 分配角色给用户 | POST | `/api/v1/roles/assign` | 给用户分配角色 |
| 移除用户角色 | DELETE | `/api/v1/roles/{userId}/{roleId}` | 移除用户的角色 |

#### 权限管理（5 个接口）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 获取当前用户权限 | GET | `/api/v1/permissions/my` | 获取当前用户的权限列表 |
| 获取所有权限 | GET | `/api/v1/permissions` | 获取系统所有权限 |
| 创建权限 | POST | `/api/v1/permissions` | 创建新权限 |
| 更新权限 | PUT | `/api/v1/permissions/{id}` | 更新权限信息 |
| 删除权限 | DELETE | `/api/v1/permissions/{id}` | 删除权限 |

---

### 4️⃣ 系统管理

#### 菜单管理（MenuHandler）- 6 个接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 菜单树查询 | GET | `/api/v1/admin/menus/tree` | 获取菜单树结构 |
| 菜单详情 | GET | `/api/v1/admin/menus/{id}` | 获取菜单详细信息 |
| 创建菜单 | POST | `/api/v1/admin/menus` | 创建新菜单 |
| 更新菜单 | PUT | `/api/v1/admin/menus/{id}` | 更新菜单信息 |
| 删除菜单 | DELETE | `/api/v1/admin/menus/{id}` | 删除菜单 |
| 更新菜单状态 | PATCH | `/api/v1/admin/menus/{id}/status` | 启用/禁用菜单 |

#### 部门管理（DeptHandler）- 5 个接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 部门树查询 | GET | `/api/v1/admin/depts/tree` | 获取部门树结构 |
| 部门详情 | GET | `/api/v1/admin/depts/{id}` | 获取部门详细信息 |
| 创建部门 | POST | `/api/v1/admin/depts` | 创建新部门 |
| 更新部门 | PUT | `/api/v1/admin/depts/{id}` | 更新部门信息 |
| 删除部门 | DELETE | `/api/v1/admin/depts/{id}` | 删除部门 |

#### 字典管理（DictHandler）- 12 个接口

**字典类型（6 个）**：
- GET `/api/v1/admin/dict/types` - 字典类型列表
- GET `/api/v1/admin/dict/types/{id}` - 字典类型详情
- POST `/api/v1/admin/dict/types` - 创建字典类型
- PUT `/api/v1/admin/dict/types/{id}` - 更新字典类型
- DELETE `/api/v1/admin/dict/types/{id}` - 删除字典类型
- PATCH `/api/v1/admin/dict/types/{id}/status` - 更新状态

**字典数据（6 个）**：
- GET `/api/v1/admin/dict/data` - 字典数据列表
- GET `/api/v1/admin/dict/data/{id}` - 字典数据详情
- POST `/api/v1/admin/dict/data` - 创建字典数据
- PUT `/api/v1/admin/dict/data/{id}` - 更新字典数据
- DELETE `/api/v1/admin/dict/data/{id}` - 删除字典数据
- PATCH `/api/v1/admin/dict/data/{id}/status` - 更新状态

#### 系统配置（ConfigHandler）- 6 个接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 配置列表 | GET | `/api/v1/admin/configs` | 分页查询系统配置 |
| 配置详情 | GET | `/api/v1/admin/configs/{id}` | 获取配置详情 |
| 创建配置 | POST | `/api/v1/admin/configs` | 创建新配置项 |
| 更新配置 | PUT | `/api/v1/admin/configs/{id}` | 更新配置项 |
| 删除配置 | DELETE | `/api/v1/admin/configs/{id}` | 删除配置项 |
| 更新配置状态 | PATCH | `/api/v1/admin/configs/{id}/status` | 启用/禁用配置 |

#### 定时任务（JobHandler）- 8 个接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 任务列表 | GET | `/api/v1/admin/jobs` | 分页查询定时任务 |
| 任务详情 | GET | `/api/v1/admin/jobs/{id}` | 获取任务详情 |
| 创建任务 | POST | `/api/v1/admin/jobs` | 创建新定时任务 |
| 更新任务 | PUT | `/api/v1/admin/jobs/{id}` | 更新任务配置 |
| 删除任务 | DELETE | `/api/v1/admin/jobs/{id}` | 删除任务 |
| 更新任务状态 | PATCH | `/api/v1/admin/jobs/{id}/status` | 启用/禁用任务 |
| 立即执行 | POST | `/api/v1/admin/jobs/{id}/run` | 立即执行一次任务 |
| 任务日志 | GET | `/api/v1/admin/jobs/{id}/logs` | 查询任务执行日志 |

---

### 5️⃣ 文件管理（FileHandler）

**模块**：`/api/v1/files`  
**接口数**：6 个

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| ~~文件上传~~ | POST | `/api/v1/files/upload` | ⚠️ 已废弃，使用通用上传 |
| 通用文件上传 | POST | `/api/v1/files/upload-generic` | 上传文件到对象存储 |
| 获取文件URL | GET | `/api/v1/files/url` | 获取文件访问URL（预签名） |
| 删除文件 | DELETE | `/api/v1/files` | 删除已上传的文件 |
| 检查文件存在性 | GET | `/api/v1/files/exists` | 检查文件是否存在 |
| 获取文件内容 | GET | `/api/v1/files/content` | 获取文件内容（小文件） |

**对象存储代理（AbstractStorageProxyHandler）**：
- GET `/tos/{bucket}/{objectKey}` - 火山引擎 TOS 文件代理
- GET `/minio/{bucket}/{objectKey}` - MinIO 文件代理

**示例请求**（文件上传）：
```http
POST /api/v1/files/upload-generic
Content-Type: multipart/form-data
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

------WebKitFormBoundary
Content-Disposition: form-data; name="file"; filename="example.jpg"
Content-Type: image/jpeg

[二进制文件数据]
------WebKitFormBoundary--
```

---

### 6️⃣ 审计与日志

#### 审计日志（AuditLogHandler）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 审计日志列表 | GET | `/api/v1/admin/audit-logs` | 分页查询审计日志 |
| 审计日志详情 | GET | `/api/v1/admin/audit-logs/{id}` | 获取日志详情 |

#### 登录日志（LoginLogHandler）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 登录日志列表 | GET | `/api/v1/admin/login-logs` | 分页查询登录日志 |

#### API 调用日志（ApiCallLogHandler）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| API 调用日志 | GET | `/api/v1/admin/api-call-logs` | 查询外部 API 调用日志 |

#### 统一业务日志（UnifiedBusinessLogHandler）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 业务日志查询 | GET | `/api/v1/logs/business` | 统一业务日志查询 |

#### 运行日志（RuntimeLogHandler）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 运行日志查询 | GET | `/api/v1/logs/runtime` | 查询应用运行日志（通过 Loki） |

---

### 7️⃣ 监控与健康检查

#### 健康检查（HealthHandler）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 健康检查 | GET | `/api/v1/health` | 应用健康状态 |
| 详细健康检查 | GET | `/actuator/health` | Spring Actuator 健康端点 |

#### 监控面板（MonitorDashboardHandler）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 监控数据 | GET | `/api/v1/monitor/dashboard` | 获取监控面板数据 |

#### 在线用户（OnlineUserHandler）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 在线用户列表 | GET | `/api/v1/admin/online-users` | 查询当前在线用户 |
| 强制下线 | DELETE | `/api/v1/admin/online-users/{token}` | 强制用户下线 |

---

### 8️⃣ 其他功能

#### 验证码（CaptchaHandler）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 获取验证码 | GET | `/api/v1/auth/captcha` | 生成图形验证码 |

#### Excel 导出（ExcelExportHandler）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 导出用户数据 | GET | `/api/v1/admin/users/export` | 导出用户列表为 Excel |

#### 数据库配置（DatabaseConfigHandler）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 测试连接 | POST | `/api/v1/admin/database/test` | 测试数据库连接 |
| 生成配置 | POST | `/api/v1/admin/database/generate` | 生成代码配置 |
| 当前数据库信息 | GET | `/api/v1/admin/database/current` | 获取当前数据库信息 |

#### 统一搜索（UnifiedSearchHandler）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 全局搜索 | GET | `/api/v1/search` | 全局全文搜索（Meilisearch） |

#### 工作流（WorkflowHandler）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 工作流列表 | GET | `/api/v1/workflows` | 查询工作流实例 |
| 工作流趋势 | GET | `/api/v1/workflows/trend` | 工作流趋势统计 |

#### SSE 通知（NotificationSseHandler）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| SSE 连接 | GET | `/api/v1/notifications/sse` | 服务器推送事件订阅 |

---

## 🔐 认证与授权

### 认证方式

**Bearer Token（JWT）**
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMTIzIiw...
```

### Token 生命周期

- **Access Token**：2 小时（可自动续期）
- **Refresh Token**：7 天

### 自动续期机制

当 Access Token 剩余有效期 < 30 分钟时，且请求携带有效的 Refresh Token，系统自动签发新 Token 并通过响应头返回：

```http
X-New-Access-Token: eyJhbGciOiJIUzI1NiJ9...
X-New-Refresh-Token: eyJhbGciOiJIUzI1NiJ9...
```

---

## 📝 统一响应格式

### 成功响应

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    // 业务数据
  },
  "traceId": "abc-123-def-456"
}
```

### 错误响应

```json
{
  "code": 1001,
  "message": "用户名或密码错误",
  "data": null,
  "traceId": "abc-123-def-456"
}
```

### 分页响应

```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "records": [
      // 数据列表
    ],
    "total": 100,
    "page": 1,
    "pageSize": 20,
    "pages": 5
  },
  "traceId": "abc-123-def-456"
}
```

---

## 🚀 快速开始

### 1. 启动应用

```bash
cd /Users/work/apex-scaffold

# 确保 Podman 和 Redis 正在运行
podman-compose -f podman-compose.infra.yml up -d redis

# 启动应用
mvnd spring-boot:run -pl :apex-system
```

### 2. 访问 API 文档

等待应用启动完成（约 1-2 分钟），然后访问：

- **Knife4j（推荐）**：http://localhost:8080/doc.html
- **Swagger UI**：http://localhost:8080/swagger-ui.html

### 3. 测试 API

在 Knife4j 界面中：

1. 点击"认证管理"模块
2. 展开"用户登录"接口
3. 点击"调试"按钮
4. 填写登录信息：
   ```json
   {
     "loginType": "PASSWORD",
     "username": "admin",
     "password": "Admin@123"
   }
   ```
5. 点击"发送"
6. 复制返回的 `accessToken`
7. 点击右上角"文档管理" → "全局参数设置"
8. 添加 Authorization 头：`Bearer {accessToken}`
9. 现在可以测试需要认证的接口了

---

## 📊 API 统计

| 模块 | 接口数 | 说明 |
|------|--------|------|
| 认证与授权 | 21 | 登录、角色、权限管理 |
| 用户管理 | 8 | 用户信息、资料、注销 |
| 系统管理 | 37 | 菜单、部门、字典、配置、任务 |
| 文件管理 | 8 | 上传、下载、代理访问 |
| 审计日志 | 5 | 审计、登录、API 调用日志 |
| 监控健康 | 4 | 健康检查、监控面板、在线用户 |
| 其他功能 | 7 | 验证码、搜索、工作流、SSE |
| **总计** | **90+** | **完整的企业级功能** |

---

## 🔗 相关资源

### 在线文档

- Swagger UI: http://localhost:8080/swagger-ui.html
- Knife4j: http://localhost:8080/doc.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

### 监控面板

- Grafana: http://localhost:3000
- Prometheus: http://localhost:9090
- Zipkin: http://localhost:9411

### 源代码

- Handler 类：`apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/handler/`
- Router 配置：`apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/router/`

---

**文档说明**：本文档基于代码中的 SpringDoc 注解自动生成。所有接口的详细参数、请求体、响应格式请访问在线文档查看。

**最后更新**：2026-09-07
