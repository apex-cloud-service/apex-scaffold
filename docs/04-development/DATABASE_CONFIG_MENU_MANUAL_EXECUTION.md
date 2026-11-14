# 数据库配置菜单手动执行说明

## 问题说明

Flyway 版本冲突：V22 已被 `V22__seed_search_and_log_permissions.sql` 占用，导致新的数据库配置菜单迁移脚本无法自动执行。

## 解决方案

使用手动 SQL 脚本绕过 Flyway，直接将菜单和权限写入数据库。

## 执行步骤

### 方式一：使用 MySQL 命令行

1. **确认数据库信息**：
   - 数据库名：`apex_db`（或您的实际数据库名）
   - 用户名：`root`（或您的实际用户名）

2. **执行 SQL 脚本**：

```bash
cd /Users/work/apex-scaffold
mysql -u root -p apex_db < add_database_config_menu_manual.sql
```

3. **输入密码后查看输出**，应该看到：

```
=== 菜单验证 ===
id    menu_name    path       component
1013  数据库配置   database   system/database/index

=== 权限验证 ===
id    code                       name
2042  system:database:view       数据库配置页面
2043  system:database:test       测试数据库连接
2044  system:database:generate   生成部署配置
2045  system:database:info       查询数据库信息

=== 角色权限验证 ===
granted_count
4
```

### 方式二：使用 MySQL 客户端工具

如果您使用 MySQL Workbench、DBeaver、Navicat 等工具：

1. 连接到 `apex_db` 数据库
2. 打开 `add_database_config_menu_manual.sql` 文件
3. 执行整个脚本
4. 检查输出结果

### 方式三：逐条执行（可选）

如果需要逐步验证，可以分步执行：

```sql
-- 步骤1：添加菜单
INSERT INTO sys_menu (...) VALUES (...);

-- 步骤2：添加权限
INSERT INTO sys_permission (...) VALUES (...);

-- 步骤3：授予角色权限
INSERT IGNORE INTO sys_role_permission (...);
```

## 验证方法

执行完成后，运行以下查询验证：

```sql
-- 1. 检查菜单
SELECT id, menu_name, path, component
FROM sys_menu
WHERE id = 1013;

-- 2. 检查权限
SELECT id, code, name
FROM sys_permission
WHERE code LIKE 'system:database:%'
ORDER BY id;

-- 3. 检查角色授权
SELECT COUNT(*) AS granted_count
FROM sys_role_permission rp
JOIN sys_permission p ON rp.permission_id = p.id
WHERE rp.role_id = 1
  AND p.code LIKE 'system:database:%';
```

**期望结果**：
- 菜单：1 条记录（id=1013）
- 权限：4 条记录（id=2042-2045）
- 角色授权：4 条记录

## 重新启动应用

SQL 执行成功后，重新启动后端应用：

```bash
cd /Users/work/apex-scaffold
source ./scripts/build/init-build-env.sh
mvnd spring-boot:run -pl :apex-system
```

## 前端验证

1. 启动前端应用：
```bash
cd /Users/work/apex-admin-web
npm run dev
```

2. 登录系统（admin / admin123）
3. 刷新页面
4. 导航到：系统管理 → 数据库配置
5. 页面应该正常显示

## 故障排查

### 问题1：菜单 ID 冲突

如果提示 `Duplicate entry '1013' for key 'PRIMARY'`，说明 ID 已被占用。

**解决方案**：
1. 查询当前最大菜单 ID：
```sql
SELECT MAX(id) FROM sys_menu;
```

2. 修改脚本中的菜单 ID（例如改为 1014）

### 问题2：权限 ID 冲突

同样的方法处理权限 ID 冲突：
```sql
SELECT MAX(id) FROM sys_permission;
```

### 问题3：父菜单不存在

如果提示外键约束错误，检查系统管理目录是否存在：

```sql
SELECT id, menu_name
FROM sys_menu
WHERE id = 1000;
```

如果不存在或 ID 不同，修改脚本中的 `parent_id` 值。

## 文件位置

- **手动 SQL 脚本**：`/Users/work/apex-scaffold/add_database_config_menu_manual.sql`
- **Flyway 脚本**（已改为 V23，暂时不使用）：
  - MySQL: `apex-components-parent/apex-system/src/main/resources/db/migration/mysql/V23__add_database_config_menu.sql`
  - PostgreSQL: `apex-components-parent/apex-system/src/main/resources/db/migration/postgresql/V23__add_database_config_menu.sql`

## 后续工作

待 Flyway 版本号规划完成后，可以考虑：
1. 将手动 SQL 移除
2. 使用正确版本号的 Flyway 迁移脚本
3. 或保持手动 SQL 作为备用方案

## 总结

✅ **手动 SQL 脚本已生成**：`add_database_config_menu_manual.sql`  
✅ **幂等性**：可以重复执行，不会造成数据重复  
✅ **自动验证**：脚本包含验证查询  
✅ **安全性**：只添加数据，不修改或删除现有记录  

执行完 SQL 后，数据库配置功能即可完全使用！
