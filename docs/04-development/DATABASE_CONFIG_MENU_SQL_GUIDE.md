# 数据库配置菜单 SQL 脚本说明

## 概述

本文档说明数据库配置菜单的 SQL 脚本生成、执行和验证方法。

## 文件清单

### 1. Flyway 迁移脚本（推荐）

适用于使用 Flyway 进行数据库版本管理的场景。

#### MySQL 版本
- **文件**: `apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V22__add_database_config_menu.sql`
- **版本**: V22
- **自动执行**: 应用启动时 Flyway 自动执行
- **幂等性**: 使用 `ON DUPLICATE KEY UPDATE`

#### PostgreSQL 版本
- **文件**: `apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V22__add_database_config_menu.sql`
- **版本**: V22
- **自动执行**: 应用启动时 Flyway 自动执行
- **幂等性**: 使用 `ON CONFLICT DO UPDATE`

### 2. 手动执行脚本

适用于手动执行或不使用 Flyway 的场景。

- **文件**: `database_config_menu.sql`
- **位置**: 项目根目录
- **包含**: MySQL 和 PostgreSQL 两个版本（PostgreSQL 版本已注释）

## 脚本内容

### 1. 菜单数据

```sql
菜单 ID: 1013
父菜单 ID: 1000（系统管理）
菜单名称: 数据库配置
菜单类型: 1（菜单）
路径: database
组件: system/database/index
权限码: system:database:view
图标: Database
排序: 8
状态: 0（正常）
可见: 0（显示）
```

### 2. 权限数据

| ID | 名称 | 权限码 | 描述 |
|----|------|--------|------|
| 2042 | 数据库配置页面 | system:database:view | 查看数据库配置页面 |
| 2043 | 测试数据库连接 | system:database:test | 测试数据库连接 |
| 2044 | 生成部署配置 | system:database:generate | 生成部署配置 |
| 2045 | 查询数据库信息 | system:database:info | 查询当前数据库信息 |

### 3. 角色授权

自动为超级管理员角色（role_id = 1, code = 'ADMIN'）授予以上 4 个权限。

## 执行方式

### 方式一：Flyway 自动执行（推荐）

1. 确保脚本文件已放置在正确位置
2. 启动 Spring Boot 应用
3. Flyway 会自动检测并执行 V22 脚本
4. 查看日志确认执行成功

```log
INFO  org.flywaydb.core.internal.command.DbMigrate - Migrating schema `apex_db` to version "22 - add database config menu"
INFO  org.flywaydb.core.internal.command.DbMigrate - Successfully applied 1 migration to schema `apex_db`
```

### 方式二：手动执行

#### MySQL

```bash
# 登录 MySQL
mysql -u root -p

# 切换数据库
USE apex_db;

# 执行脚本
SOURCE /Users/work/apex-scaffold/database_config_menu.sql;

# 或者一行命令
mysql -u root -p apex_db < /Users/work/apex-scaffold/database_config_menu.sql
```

#### PostgreSQL

如果使用 PostgreSQL，需要：

1. 打开 `database_config_menu.sql`
2. 注释掉 MySQL 部分
3. 取消注释 PostgreSQL 部分
4. 执行：

```bash
psql -U postgres -d apex_db -f /Users/work/apex-scaffold/database_config_menu.sql
```

## 验证方法

执行以下 SQL 验证脚本是否成功：

### 1. 检查菜单

```sql
SELECT id, menu_name, path, component, perms, parent_id, sort
FROM sys_menu
WHERE id = 1013;
```

**期望结果**：
```
id   | menu_name    | path     | component                | perms                   | parent_id | sort
-----|--------------|----------|--------------------------|-------------------------|-----------|------
1013 | 数据库配置   | database | system/database/index    | system:database:view    | 1000      | 8
```

### 2. 检查权限

```sql
SELECT id, name, code, description
FROM sys_permission
WHERE code LIKE 'system:database:%'
ORDER BY id;
```

**期望结果**：
```
id   | name             | code                      | description
-----|------------------|---------------------------|-------------------
2042 | 数据库配置页面   | system:database:view      | 查看数据库配置页面
2043 | 测试数据库连接   | system:database:test      | 测试数据库连接
2044 | 生成部署配置     | system:database:generate  | 生成部署配置
2045 | 查询数据库信息   | system:database:info      | 查询当前数据库信息
```

### 3. 检查角色权限

```sql
SELECT rp.role_id, r.name AS role_name, p.code AS permission_code, p.name AS permission_name
FROM sys_role_permission rp
JOIN sys_role r ON rp.role_id = r.id
JOIN sys_permission p ON rp.permission_id = p.id
WHERE r.code = 'ADMIN'
  AND p.code LIKE 'system:database:%'
ORDER BY p.code;
```

**期望结果**：
```
role_id | role_name    | permission_code           | permission_name
--------|--------------|---------------------------|------------------
1       | 超级管理员   | system:database:generate  | 生成部署配置
1       | 超级管理员   | system:database:info      | 查询数据库信息
1       | 超级管理员   | system:database:test      | 测试数据库连接
1       | 超级管理员   | system:database:view      | 数据库配置页面
```

## 前端验证

### 1. 菜单可见性

1. 使用 admin 账号登录系统
2. 刷新页面或重新登录
3. 导航到：系统管理
4. 应该能看到"数据库配置"菜单项（图标：Database）

### 2. 页面访问

1. 点击"数据库配置"菜单
2. URL 应该是：`http://localhost/system/database`
3. 页面应该正常加载，显示数据库配置表单

### 3. 功能验证

1. 填写数据库配置信息
2. 点击"测试连接"按钮（需要 `system:database:test` 权限）
3. 点击"生成配置"按钮（需要 `system:database:generate` 权限）
4. 查看当前数据库信息（需要 `system:database:info` 权限）

## 故障排查

### 问题1：菜单不显示

**可能原因**：
- SQL 未执行或执行失败
- 用户没有对应权限
- 前端路由未注册

**解决方案**：
1. 检查数据库中是否存在 id=1013 的菜单记录
2. 检查当前用户的角色是否有 `system:database:view` 权限
3. 检查前端 `src/router/dynamic.ts` 中是否注册了 `system/database/index`

### 问题2：点击菜单返回 404

**可能原因**：
- 前端组件文件不存在
- 路由注册错误

**解决方案**：
1. 确认 `/Users/work/apex-admin-web/src/views/system/database/index.vue` 文件存在
2. 确认前端路由注册表中有对应的映射

### 问题3：接口返回 403

**可能原因**：
- 权限配置错误
- 后端 Handler 未注册

**解决方案**：
1. 检查 `sys_role_permission` 表中是否有对应权限关联
2. 检查后端 `ApiRouter.java` 中是否有对应路由

### 问题4：Flyway 执行失败

**可能原因**：
- 版本号冲突
- Checksum 校验失败
- 数据库已有相同 ID 记录

**解决方案**：
1. 检查 `flyway_schema_history` 表，确认没有 V22 记录
2. 如果有冲突，调整脚本版本号（如 V23）
3. 删除冲突记录后重新执行

## 回滚方法

如果需要回滚菜单和权限，执行以下 SQL：

```sql
-- 删除菜单
DELETE FROM sys_menu WHERE id = 1013;

-- 删除权限
DELETE FROM sys_permission WHERE id IN (2042, 2043, 2044, 2045);

-- 删除角色-权限关联
DELETE FROM sys_role_permission WHERE permission_id IN (2042, 2043, 2044, 2045);

-- 如果使用 Flyway，还需要删除迁移记录
DELETE FROM flyway_schema_history WHERE version = '22';
```

## 注意事项

1. **幂等性**：脚本使用 `ON DUPLICATE KEY UPDATE`（MySQL）或 `ON CONFLICT DO UPDATE`（PostgreSQL），可以重复执行
2. **ID 冲突**：菜单 ID 1013 和权限 ID 2042-2045 是固定的，如果已被占用需要调整
3. **父菜单 ID**：脚本假设系统管理目录的 ID 是 1000，如果不同需要修改 `parent_id`
4. **角色授权**：脚本假设超级管理员角色的 ID 是 1，如果不同需要修改 `role_id`
5. **Flyway 版本**：确保 V22 版本号在当前数据库中尚未使用

## 相关文档

- **前端实施文档**: `/Users/work/apex-admin-web/DATABASE_CONFIG_IMPLEMENTATION.md`
- **后端实施文档**: `/Users/work/apex-scaffold/docs/DATABASE_CONFIG_BACKEND_IMPLEMENTATION.md`
- **对接说明文档**: `/Users/work/apex-admin-web/FRONTEND_BACKEND_INTEGRATION.md`
- **快速参考文档**: `/Users/work/apex-scaffold/DATABASE_CONFIG_QUICK_REFERENCE.md`
