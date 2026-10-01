# 数据库SQL脚本执行指南

**SQL文件**: `/Users/work/apex-scaffold/frontend_button_permission_config.sql`  
**目标数据库**: apex_data  
**执行日期**: 2026-09-07

---

## ⚠️ 执行前提

**数据库状态检查**: MySQL服务当前未运行,需要先启动数据库。

---

## 🚀 执行方式

### 方式1: 通过Docker Compose启动数据库并执行 (推荐)

#### Step 1: 启动基础设施

```bash
cd /Users/work/apex-scaffold

# 启动MySQL、Redis等基础设施
docker-compose -f docker-compose.infra.yml up -d mysql

# 等待MySQL启动完成(约10-15秒)
sleep 15

# 检查MySQL是否运行
docker-compose -f docker-compose.infra.yml ps mysql
```

#### Step 2: 执行SQL脚本

```bash
# 通过Docker容器执行SQL
docker-compose -f docker-compose.infra.yml exec mysql \
  mysql -u root -papex-root apex_data < frontend_button_permission_config.sql

# 或者通过本地MySQL客户端连接Docker容器
mysql -h 127.0.0.1 -P 3306 -u root -papex-root apex_data < frontend_button_permission_config.sql
```

#### Step 3: 验证执行结果

```bash
# 查询新增的权限记录
docker-compose -f docker-compose.infra.yml exec mysql \
  mysql -u root -papex-root apex_data -e "
SELECT 
  m1.menu_name AS '父菜单',
  m2.menu_name AS '按钮权限',
  m2.perms AS '权限码',
  CASE WHEN rm.role_id IS NOT NULL THEN '✅ 已授权' ELSE '❌ 未授权' END AS '管理员授权状态'
FROM sys_menu m2
LEFT JOIN sys_menu m1 ON m2.parent_id = m1.id
LEFT JOIN sys_role_menu rm ON m2.id = rm.menu_id AND rm.role_id = 1
WHERE m2.menu_type = 2 
  AND m2.perms LIKE 'system:%'
ORDER BY m1.sort, m2.sort;
"
```

**预期结果**: 应显示约25条权限记录,且"管理员授权状态"均为"✅ 已授权"

---

### 方式2: 使用本地MySQL服务

如果您本地安装了MySQL服务:

#### Step 1: 启动MySQL服务

```bash
# macOS (Homebrew安装)
brew services start mysql

# 或者通过系统服务管理
sudo /usr/local/mysql/support-files/mysql.server start

# 检查MySQL是否运行
mysql.server status
```

#### Step 2: 创建数据库(如不存在)

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS apex_data CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

#### Step 3: 执行SQL脚本

```bash
cd /Users/work/apex-scaffold

# 执行SQL脚本(会提示输入密码)
mysql -u root -p apex_data < frontend_button_permission_config.sql

# 或直接指定密码(不安全,仅用于开发环境)
mysql -u root -papex-root apex_data < frontend_button_permission_config.sql
```

#### Step 4: 验证结果

```bash
mysql -u root -p apex_data -e "
SELECT 
  COUNT(*) AS '新增权限数',
  SUM(CASE WHEN rm.role_id = 1 THEN 1 ELSE 0 END) AS '已授权管理员'
FROM sys_menu m
LEFT JOIN sys_role_menu rm ON m.id = rm.menu_id AND rm.role_id = 1
WHERE m.menu_type = 2 
  AND m.perms LIKE 'system:%'
  AND m.create_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR);
"
```

**预期输出**: `新增权限数: 25, 已授权管理员: 25`

---

### 方式3: 通过MySQL Workbench/Navicat等GUI工具

#### Step 1: 连接数据库

- **主机**: 127.0.0.1
- **端口**: 3306
- **用户**: root
- **密码**: apex-root
- **数据库**: apex_data

#### Step 2: 打开SQL脚本

1. 打开MySQL Workbench/Navicat
2. File → Open SQL Script
3. 选择: `/Users/work/apex-scaffold/frontend_button_permission_config.sql`

#### Step 3: 执行脚本

1. 确认连接到 `apex_data` 数据库
2. 点击 "Execute" 或 "Run" 按钮
3. 查看执行结果

#### Step 4: 验证结果

执行验证SQL(脚本末尾的SELECT语句),检查权限是否正确插入。

---

## 📊 执行后验证

### 1. 检查权限码数量

```sql
SELECT COUNT(*) AS '总权限码数' 
FROM sys_menu 
WHERE menu_type = 2 AND perms LIKE 'system:%';
```

**预期**: 应该比执行前多约25条

### 2. 检查各模块权限

```sql
SELECT 
  SUBSTRING_INDEX(perms, ':', 2) AS '模块',
  COUNT(*) AS '权限数'
FROM sys_menu 
WHERE menu_type = 2 AND perms LIKE 'system:%'
GROUP BY SUBSTRING_INDEX(perms, ':', 2)
ORDER BY 模块;
```

**预期输出**:
```
模块                | 权限数
--------------------|-------
system:config       | 4
system:database     | 4
system:dept         | 3
system:dict         | 3
system:job          | 5
system:menu         | 3
```

### 3. 检查管理员权限分配

```sql
SELECT 
  r.name AS '角色名',
  COUNT(rm.menu_id) AS '权限数'
FROM sys_role r
JOIN sys_role_menu rm ON r.id = rm.role_id
JOIN sys_menu m ON rm.menu_id = m.id
WHERE r.id = 1 AND m.menu_type = 2 AND m.perms LIKE 'system:%'
GROUP BY r.id, r.name;
```

**预期**: 管理员角色应该有约25个system相关权限

---

## 🔍 故障排查

### 问题1: 数据库连接失败

**错误**: `ERROR 2003 (HY000): Can't connect to MySQL server`

**解决**:
```bash
# 检查MySQL是否运行
ps aux | grep mysql

# 或检查Docker容器
docker ps | grep mysql

# 如未运行,启动MySQL
docker-compose -f docker-compose.infra.yml up -d mysql
# 或
brew services start mysql
```

### 问题2: 权限不足

**错误**: `ERROR 1045 (28000): Access denied for user 'root'`

**解决**:
1. 确认密码是否正确(默认: apex-root)
2. 尝试重置MySQL root密码
3. 检查是否需要授权远程访问

### 问题3: 数据库不存在

**错误**: `ERROR 1049 (42000): Unknown database 'apex_data'`

**解决**:
```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE apex_data CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 或通过Flyway自动创建(启动应用)
cd /Users/work/apex-scaffold
mvnd spring-boot:run -pl apex-interfaces
```

### 问题4: 菜单ID查询失败

**错误**: SQL执行成功但验证查询无结果

**原因**: `sys_menu`表中可能不存在对应的父菜单

**解决**:
1. 先启动应用,让Flyway迁移创建基础菜单
2. 然后再执行权限配置SQL

---

## ✅ 执行完成检查清单

- [ ] MySQL服务已启动
- [ ] 数据库`apex_data`存在
- [ ] SQL脚本执行成功(无错误信息)
- [ ] 验证SQL显示约25条权限记录
- [ ] 所有权限的"管理员授权状态"为"✅ 已授权"
- [ ] 没有重复的权限码

---

## 📝 执行日志模板

请将执行结果记录如下:

```
执行时间: 2026-09-07 HH:MM:SS
执行人: [您的名字]
执行方式: [方式1/方式2/方式3]

执行结果:
- 新增权限码数量: __条
- 管理员已授权数量: __条
- 执行错误数: __个

验证查询结果:
[粘贴验证SQL的输出]

备注:
[任何需要说明的问题或观察]
```

---

## 🔗 相关文档

- [前端按钮权限控制补充执行脚本.md](./.trae/documents/前端按钮权限控制补充执行脚本.md)
- [前端按钮权限控制补充指南.md](./.trae/documents/前端按钮权限控制补充指南.md)

---

## 💡 后续步骤

SQL执行成功后:

1. **修改前端代码**: 按照执行脚本文档为Vue组件添加`v-permission`指令
2. **测试验证**: 启动前后端服务,测试权限控制是否生效
3. **清除缓存**: 提醒用户清除浏览器缓存并重新登录

---

**文档创建**: 2026-09-07  
**最后更新**: 2026-09-07  
**状态**: 待执行
