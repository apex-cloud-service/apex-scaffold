# Flyway 迁移脚本修复摘要

> **历史快照**：本文记录某一时点的状态，模块数、端口、路径与类名以当前代码为准，不作为现状依据。

**修复时间：** 2024-09-22 00:10  
**影响模块：** apex-auth-center  
**修复人员：** Apex Scaffold Team

---

## 修复的文件

### 新增文件（3个）

1. **MySQL V49 迁移脚本**
   - 路径：`apex-auth-center/src/main/resources/db/migration/mysql/V49__create_auth_config_table.sql`
   - 作用：创建 auth_config 表，为 V48 提供表结构支持
   - 大小：1.4 KB

2. **PostgreSQL V49 迁移脚本**
   - 路径：`apex-auth-center/src/main/resources/db/migration/postgresql/V49__create_auth_config_table.sql`
   - 作用：创建 auth_config 表（PostgreSQL 版本）
   - 大小：1.8 KB

3. **双库迁移回归测试**
   - 实现：`apex-system/src/test/java/com/apex/scaffold/interfaces/test/SystemDualDatabaseMigrationIntegrationTest.java`
   - 作用：以 Testcontainers 在 MySQL 与 PostgreSQL 上逐版本迁移并校验 schema
   - 执行：`sdk env && mvnd -T4 -B clean verify`（Failsafe 阶段）

### 修改文件（1个）

1. **PostgreSQL V48 迁移脚本**
   - 路径：`apex-auth-center/src/main/resources/db/migration/postgresql/V48__add_config_and_audit_menu_permissions.sql`
   - 修改：完全重写，修复所有 PostgreSQL 语法问题
   - 大小：9.0 KB（原 8.0 KB）

### 移动文件（1个）

1. **V50 迁移脚本**
   - 原路径：`apex-auth-center/src/main/resources/db/migration/V50__create_totp_and_mfa_config_tables.sql`
   - 新路径：`apex-auth-center/src/main/resources/db/migration/mysql/V50__create_totp_and_mfa_config_tables.sql`
   - 同时创建：`postgresql/V50__create_totp_and_mfa_config_tables.sql`

### 文档（1个）

1. **修复报告**
   - 路径：`docs/migration/flyway-migration-fix-report-20240922.md`
   - 内容：完整的问题分析、修复方案、验证清单和后续建议

---

## 修复的问题

### 1. V48 表依赖问题 ✓
**根本原因：** V48 插入 auth_config 数据，但表在 V51 才创建  
**解决方案：** 新增 V49 在 V48 之前创建 auth_config 表  
**影响范围：** MySQL + PostgreSQL

### 2. PostgreSQL V48 语法错误 ✓
**根本原因：** 使用了 MySQL 特有语法  
**解决方案：** 重写为 PostgreSQL 标准语法  
**修复内容：**
- SET @变量 → DO $$ DECLARE ... END $$
- NOW() → CURRENT_TIMESTAMP
- 1/0 → true/false
- JSON → ::jsonb
- INSERT IGNORE → ON CONFLICT DO NOTHING

### 3. V50 路径错误 ✓
**根本原因：** 文件放在根目录，Flyway 无法扫描  
**解决方案：** 移动到 mysql/ 并创建 postgresql/ 版本  
**影响范围：** MySQL + PostgreSQL

### 4. V46 ID 问题 ✓
**验证结果：** V46 已使用显式 id，无需修复  
**确认方式：** 检查所有 INSERT 语句都包含 id 列

---

## 迁移顺序

```
V44 → V45 → V46 → V47 → V48 → V49 → V50
 │     │     │     │     │     │     │
 │     │     │     │     │     │     └─ 创建 TOTP 和 MFA 配置表
 │     │     │     │     │     └─────── 创建 auth_config 表（新增）
 │     │     │     │     └─────────── 添加配置管理和审计日志菜单权限
 │     │     │     └─────────────── 插入认证中心种子数据
 │     │     └─────────────────── 插入测试用户
 │     └─────────────────────── 对齐认证领域字段
 └─────────────────────────── 创建核心表
```

---

## 验证方式

### 快速验证（推荐）
```bash
cd /Users/work/apex-scaffold

# 检查迁移文件完整性
./scripts/test/check-migration-integrity.sh

# 双库迁移回归（Testcontainers，需本机容器运行时）
sdk env && mvnd -T4 -B clean verify
```

### 手动验证
```bash
# 检查迁移文件完整性
ls -la apex-auth-center/src/main/resources/db/migration/mysql/*.sql
ls -la apex-auth-center/src/main/resources/db/migration/postgresql/*.sql

# 预期输出：每个目录都应该有 V44-V50 共7个文件
```

---

## 关键表清单

执行完所有迁移后，应该创建以下表：

**认证核心表（V44）：**
- auth_user
- auth_token
- auth_session
- auth_role
- auth_permission
- auth_user_role
- auth_role_permission
- auth_protocol_config
- auth_audit_log

**配置管理表（V49）：**
- auth_config

**MFA 表（V50）：**
- auth_totp
- auth_mfa_config

**系统表（V44）：**
- sys_menu
- sys_permission
- sys_role
- sys_role_permission

---

## 回滚方案

如果迁移失败，按以下顺序回滚：

1. **删除测试数据库**
   ```bash
   # MySQL
   mysql -u root -p -e "DROP DATABASE IF EXISTS apex_auth_test_xxxx;"
   
   # PostgreSQL
   psql -U postgres -c "DROP DATABASE IF EXISTS apex_auth_test_xxxx;"
   ```

2. **恢复原始文件**
   ```bash
   git checkout apex-auth-center/src/main/resources/db/migration/
   ```

3. **重新修复**
   - 查看本摘要和详细报告
   - 按问题清单逐项修复

---

## 注意事项

⚠️ **生产环境迁移前必读：**

1. **备份数据库**
   ```bash
   # MySQL
   mysqldump -u root -p apex_auth > backup_$(date +%Y%m%d_%H%M%S).sql
   
   # PostgreSQL
   pg_dump -U postgres apex_auth > backup_$(date +%Y%m%d_%H%M%S).sql
   ```

2. **在非高峰期执行**
   - 建议时间：凌晨 2:00 - 5:00

3. **准备回滚脚本**
   - 确保备份可恢复
   - 准备快速回滚命令

4. **监控迁移过程**
   - 记录执行时间
   - 监控数据库连接数
   - 检查错误日志

---

## 联系方式

**问题反馈：** 提交 Issue 到项目仓库  
**紧急联系：** Apex Scaffold Team

---

**修复状态：** ✅ 已完成  
**测试状态：** ⏳ 待执行  
**生产部署：** ⏳ 待安排
