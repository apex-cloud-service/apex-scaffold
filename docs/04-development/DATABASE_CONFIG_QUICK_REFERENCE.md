# 数据库配置功能快速参考

## 功能概述

数据库配置采集页：部署配置生成工具，支持 MySQL 和 PostgreSQL。

## 访问路径

- **前端页面**: `/system/database`
- **菜单位置**: 系统管理 → 数据库配置

## API 接口

| 方法 | 路径 | 权限 | 功能 |
|------|------|------|------|
| POST | `/admin-api/v1/database/test` | `system:database:test` | 测试连接 |
| POST | `/admin-api/v1/database/generate-config` | `system:database:generate` | 生成配置 |
| GET | `/admin-api/v1/database/current` | `system:database:info` | 查询当前数据库 |

## 文件清单

### 前端

```
apex-admin-web/
├── src/
│   ├── types/index.ts                        # 类型定义（已添加）
│   ├── api/database.ts                       # API 接口（已创建）
│   ├── views/system/database/index.vue       # 页面组件（已创建）
│   └── router/dynamic.ts                     # 路由注册（已添加）
├── database-menu.sql                          # 菜单 SQL（已创建）
├── DATABASE_CONFIG_IMPLEMENTATION.md          # 前端实施文档
└── FRONTEND_BACKEND_INTEGRATION.md            # 对接说明文档
```

### 后端

```
apex-scaffold/
├── apex-components-parent/apex-system/
│   ├── dto/admin/
│   │   ├── DatabaseConfigRequest.java         # 请求 DTO（已创建）
│   │   ├── DatabaseTestResultDTO.java         # 测试结果（已创建）
│   │   ├── DeploymentConfigResultDTO.java     # 配置结果（已创建）
│   │   └── CurrentDatabaseInfoDTO.java        # 当前数据库（已创建）
│   └── service/
│       └── DatabaseConfigService.java         # 业务服务（已创建）
├── apex-components-parent/apex-system/
│   ├── handler/
│   │   └── DatabaseConfigHandler.java         # 请求处理器（已创建）
│   └── router/
│       └── ApiRouter.java                     # 路由配置（已修改）
└── docs/
    ├── DATABASE_CONFIG_BACKEND_IMPLEMENTATION.md   # 后端实施文档
    └── DATABASE_CONFIG_QUICK_REFERENCE.md          # 本文档
```

## 部署步骤

### 1. 数据库准备

执行菜单注册 SQL：

```bash
cd /Users/work/apex-admin-web
mysql -u root -p apex_db < database-menu.sql
```

**重要**：根据实际的系统管理目录 ID 调整 SQL 中的 `parent_id` 值。

### 2. 后端部署

已完成编译验证，直接启动即可：

```bash
cd /Users/work/apex-scaffold
mvnd spring-boot:run
```

### 3. 前端部署

已完成构建验证，直接启动即可：

```bash
cd /Users/work/apex-admin-web
npm run dev
```

### 4. 权限配置

确保目标角色拥有以下权限：
- `system:database:view` - 查看页面
- `system:database:test` - 测试连接
- `system:database:generate` - 生成配置
- `system:database:info` - 查询信息

## 使用流程

1. 登录系统
2. 导航到：系统管理 → 数据库配置
3. 选择数据库类型（MySQL / PostgreSQL）
4. 填写连接信息
5. 点击"测试连接"验证
6. 点击"生成配置"导出
7. 复制需要的配置格式

## 配置输出示例

### YAML 格式
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/apex_db
    username: root
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 环境变量格式
```bash
DB_URL=jdbc:mysql://localhost:3306/apex_db
DB_USERNAME=root
DB_PASSWORD=password123
DB_DRIVER=com.mysql.cj.jdbc.Driver
```

### Podman Compose 格式
```yaml
version: '3.8'
services:
  database:
    image: mysql:9.0
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: password123
      MYSQL_DATABASE: apex_db
```

## 技术栈

### 前端
- Vue 3 + TypeScript
- Element Plus
- Vite

### 后端
- Java 21
- Spring Boot 3.5.15
- Spring WebFlux
- HikariCP

## 验证状态

✅ **前端**：类型检查通过，构建成功  
✅ **后端**：编译通过，10 个模块全部成功  
✅ **对接**：API 接口完全对应，类型一致  
✅ **文档**：完整的实施和对接文档  

## 故障排查

### 常见问题

1. **页面 404**：检查菜单 SQL 是否执行
2. **接口 403**：检查用户权限配置
3. **连接超时**：检查数据库网络可达性
4. **类型错误**：运行 `vue-tsc --noEmit` 检查

### 日志查看

**后端日志**：
```bash
tail -f logs/apex-scaffold.log
```

**前端控制台**：
打开浏览器开发者工具 → Console 标签

## 参考文档

1. **前端实施文档**：`/Users/work/apex-admin-web/DATABASE_CONFIG_IMPLEMENTATION.md`
2. **后端实施文档**：`/Users/work/apex-scaffold/docs/DATABASE_CONFIG_BACKEND_IMPLEMENTATION.md`
3. **对接说明文档**：`/Users/work/apex-admin-web/FRONTEND_BACKEND_INTEGRATION.md`
4. **方案设计文档**：`/Users/work/apex-scaffold/.trae/documents/前端配置数据库多适配方案.md`（原文未入库）

## 下一步扩展

如需支持更多数据库类型（如 Oracle、SQL Server），需要：

1. 前端：在 `dbType` 枚举中添加新类型
2. 后端：在 `DatabaseConfigService` 中添加驱动映射
3. 测试：验证新数据库的连接和配置生成

---

**状态**：✅ 完整实现并验证通过  
**版本**：1.2.0  
**更新时间**：2026-09-07
