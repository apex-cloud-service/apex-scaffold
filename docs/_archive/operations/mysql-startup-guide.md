# MySQL启动与SQL执行指南

**目标**: 启动MySQL服务并执行权限配置SQL脚本  
**日期**: 2026-09-07  
**状态**: 待手动执行

---

## ⚠️ 当前状态

**检查结果**:
- 原历史检查发现 Docker daemon 未运行；当前使用 Podman，请重新检查 `podman machine list` 和 `podman ps`。
- ❌ 本地MySQL进程未运行
- ❌ Homebrew MySQL服务未安装/未启动

**需要手动启动MySQL服务**

---

## 方案A: 启动 Podman MySQL (推荐)

### Step 1: 启动 Podman

**macOS**:
```bash
# 查看已有虚拟机；尚未创建时先执行 podman machine init
podman machine list
podman machine start

# 验证连接，应看到容器列表（可能为空）
podman ps
```

### Step 2: 启动MySQL容器

```bash
cd /Users/work/apex-scaffold

# 启动MySQL容器
podman-compose -f podman-compose.infra.yml up -d mysql

# 查看启动日志
podman-compose -f podman-compose.infra.yml logs -f mysql
# 看到 "ready for connections" 表示MySQL已就绪
# 按Ctrl+C退出日志查看

# 等待MySQL初始化完成(首次启动约10-15秒)
sleep 15
```

### Step 3: 验证MySQL可用

```bash
# 测试连接
podman-compose -f podman-compose.infra.yml exec mysql \
  mysql -u root -papex-root -e "SELECT VERSION();"

# 应该显示MySQL版本号,如: 8.0.x
```

### Step 4: 执行SQL脚本

```bash
# 方式1: 通过Docker容器执行
podman-compose -f podman-compose.infra.yml exec -T mysql \
  mysql -u root -papex-root apex_data < frontend_button_permission_config.sql

# 方式2: 通过本地MySQL客户端连接Docker容器
mysql -h 127.0.0.1 -P 3306 -u root -papex-root apex_data < frontend_button_permission_config.sql
```

### Step 5: 验证SQL执行结果

```bash
# 查询新增的权限记录
podman-compose -f podman-compose.infra.yml exec mysql \
  mysql -u root -papex-root apex_data -e "
SELECT COUNT(*) AS '新增权限数' 
FROM sys_menu 
WHERE menu_type = 2 AND perms LIKE 'system:%';
"

# 应该显示约25条权限记录

# 查看详细权限列表
docker-compose -f docker-compose.infra.yml exec mysql \
  mysql -u root -papex-root apex_data -e "
SELECT 
  m1.menu_name AS '父菜单',
  m2.menu_name AS '按钮权限',
  m2.perms AS '权限码',
  CASE WHEN rm.role_id IS NOT NULL THEN '✅' ELSE '❌' END AS '已授权'
FROM sys_menu m2
LEFT JOIN sys_menu m1 ON m2.parent_id = m1.id
LEFT JOIN sys_role_menu rm ON m2.id = rm.menu_id AND rm.role_id = 1
WHERE m2.menu_type = 2 AND m2.perms LIKE 'system:%'
ORDER BY m1.sort, m2.sort;
"
```

**预期结果**: 所有权限的"已授权"列都应该显示"✅"

---

## 🚀 方案B: 使用本地MySQL服务

如果您本地安装了MySQL(非Docker):

### Step 1: 启动MySQL服务

**macOS (Homebrew安装)**:
```bash
# 启动MySQL服务
brew services start mysql@8.0

# 或者使用特定版本
brew services start mysql@5.7

# 检查服务状态
brew services list | grep mysql
```

**macOS (官方安装包)**:
```bash
# 启动MySQL
sudo /usr/local/mysql/support-files/mysql.server start

# 或使用系统偏好设置中的MySQL面板启动
```

**Linux**:
```bash
# Ubuntu/Debian
sudo systemctl start mysql

# CentOS/RHEL
sudo systemctl start mysqld

# 检查状态
sudo systemctl status mysql
```

### Step 2: 验证MySQL运行

```bash
# 检查MySQL进程
ps aux | grep mysqld | grep -v grep

# 测试连接
mysql -u root -p -e "SELECT VERSION();"
# 输入密码(默认可能是空密码或apex-root)
```

### Step 3: 创建数据库(如不存在)

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS apex_data CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### Step 4: 执行SQL脚本

```bash
cd /Users/work/apex-scaffold

# 执行SQL(会提示输入密码)
mysql -u root -p apex_data < frontend_button_permission_config.sql

# 或直接指定密码(不安全,仅开发环境)
mysql -u root -papex-root apex_data < frontend_button_permission_config.sql
```

### Step 5: 验证执行结果

```bash
mysql -u root -p apex_data -e "
SELECT COUNT(*) AS '新增权限数' 
FROM sys_menu 
WHERE menu_type = 2 AND perms LIKE 'system:%';
"
```

---

## 🚀 方案C: 使用GUI工具

如果您使用MySQL Workbench / Navicat / DBeaver等GUI工具:

### Step 1: 启动MySQL服务

按方案A或方案B启动MySQL服务。

### Step 2: 连接数据库

在GUI工具中创建连接:
- **主机**: 127.0.0.1 (或 localhost)
- **端口**: 3306
- **用户**: root
- **密码**: apex-root (或您的实际密码)
- **数据库**: apex_data

### Step 3: 打开SQL脚本

1. 在GUI工具中: File → Open SQL Script
2. 选择文件: `/Users/work/apex-scaffold/frontend_button_permission_config.sql`

### Step 4: 执行脚本

1. 确认连接到 `apex_data` 数据库
2. 点击 "Execute" 或 "Run" 按钮
3. 查看执行结果

### Step 5: 验证

在SQL编辑器中执行验证查询:
```sql
SELECT 
  m1.menu_name AS '父菜单',
  m2.menu_name AS '按钮权限',
  m2.perms AS '权限码',
  CASE WHEN rm.role_id IS NOT NULL THEN '✅ 已授权' ELSE '❌ 未授权' END AS '状态'
FROM sys_menu m2
LEFT JOIN sys_menu m1 ON m2.parent_id = m1.id
LEFT JOIN sys_role_menu rm ON m2.id = rm.menu_id AND rm.role_id = 1
WHERE m2.menu_type = 2 AND m2.perms LIKE 'system:%'
ORDER BY m1.sort, m2.sort;
```

---

## 🔍 故障排查

### 问题1: Podman 虚拟机或连接不可用

**检查**: `podman machine list`、`podman system connection list`。

**解决**:
1. 确认已安装 Podman。
2. macOS 尚无虚拟机时执行 `podman machine init`。
3. 执行 `podman machine start`。
4. 使用 `podman ps` 确认连接正常后重试。

### 问题2: MySQL连接被拒绝

**错误**: `ERROR 2003 (HY000): Can't connect to MySQL server`

**解决**:
```bash
# 检查MySQL是否运行
ps aux | grep mysql

# 检查端口是否被占用
lsof -i :3306

# 如果Docker容器运行但连接失败
docker-compose -f docker-compose.infra.yml ps mysql
docker-compose -f docker-compose.infra.yml logs mysql | tail -50
```

### 问题3: 密码错误

**错误**: `ERROR 1045 (28000): Access denied for user 'root'`

**解决**:
1. 确认密码: 默认应该是 `apex-root`
2. 如果忘记密码,重置MySQL root密码:
   ```bash
   # Docker方式
   docker-compose -f docker-compose.infra.yml exec mysql \
     mysql -u root -e "ALTER USER 'root'@'%' IDENTIFIED BY 'apex-root';"
   ```

### 问题4: 数据库不存在

**错误**: `ERROR 1049 (42000): Unknown database 'apex_data'`

**解决**:
```bash
# 创建数据库
docker-compose -f docker-compose.infra.yml exec mysql \
  mysql -u root -papex-root -e "CREATE DATABASE IF NOT EXISTS apex_data CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 或通过应用Flyway自动创建
cd /Users/work/apex-scaffold
mvnd spring-boot:run -pl apex-interfaces
# 应用启动时Flyway会自动创建数据库和表结构
```

### 问题5: 权限表不存在

**错误**: `Table 'apex_data.sys_menu' doesn't exist`

**原因**: 数据库结构未初始化

**解决**:
1. 启动应用让Flyway初始化数据库:
   ```bash
   cd /Users/work/apex-scaffold
   mvnd clean spring-boot:run -pl apex-interfaces
   ```

2. 等待应用完全启动后,再执行权限配置SQL

---

## ✅ 执行成功检查清单

- [ ] MySQL服务已启动(Docker或本地)
- [ ] 可以通过mysql客户端连接
- [ ] 数据库`apex_data`存在
- [ ] 表`sys_menu`和`sys_role_menu`存在
- [ ] SQL脚本执行无错误
- [ ] 查询显示约25条新增权限记录
- [ ] 所有权限的"已授权"状态为"✅"

---

## 📝 执行日志模板

请记录执行过程:

```
执行时间: 2026-09-07 __:__:__
执行人: ___________
执行方式: [方案A/方案B/方案C]

MySQL启动:
- 启动方式: Docker / 本地服务 / GUI工具
- 启动命令: ___________
- 启动成功: 是 / 否

SQL执行:
- 执行命令: ___________
- 执行结果: 成功 / 失败
- 新增权限数: ___条

验证结果:
[粘贴验证SQL的输出]

遇到的问题:
[记录遇到的问题和解决方法]
```

---

## 🎯 下一步

SQL脚本执行成功后:

1. **修改前端代码**
   - 打开 `/Users/work/apex-admin-web` 项目
   - 为Vue组件添加 `v-permission` 指令
   - 参考: [前端按钮权限控制补充执行脚本.md](../reports/archive/task-implementations/frontend-button-permission-script.md)

2. **测试验证**
   ```bash
   # 启动后端
   cd /Users/work/apex-scaffold
   mvnd spring-boot:run -pl apex-interfaces
   
   # 启动前端
   cd /Users/work/apex-admin-web
   npm run dev
   
   # 访问并测试
   # http://localhost:5173
   ```

3. **验证权限控制**
   - 管理员登录: 所有按钮显示
   - 测试用户登录: 无权限按钮隐藏

---

## 📚 相关文档

- [数据库SQL脚本执行指南.md](sql-execution-guide.md) - 详细SQL执行指南
- [前端按钮权限控制补充执行脚本.md](../reports/archive/task-implementations/frontend-button-permission-script.md) - 前端修改指南
- [任务执行完成报告.md](../reports/archive/project-deliveries/task-completion-report.md) - 整体任务报告

---

**文档创建**: 2026-09-07  
**最后更新**: 2026-09-07  
**状态**: 待执行  
**预计耗时**: 10-15分钟
