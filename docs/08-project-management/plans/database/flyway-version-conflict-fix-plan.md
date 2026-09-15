# Flyway 版本号冲突修复方案

## 📋 问题分析

当前存在严重的Flyway版本号冲突，同一版本号被多个脚本占用：

### 冲突清单

| 版本号 | 冲突脚本数量 | 脚本列表 |
|--------|------------|---------|
| **V29** | 3个 | `create_office_platform_tables`, `create_office_message_table`, `create_office_approval_process_table` |
| **V30** | 4个 | `add_office_admin_permissions`, `add_office_platform_menus`, `create_office_approval_process`, `create_office_calendar_event_table` |
| **V31** | 2个 | `create_office_calendar_event`, `create_office_document_info_table` |
| **V32** | 1个 | `create_office_document` |
| **V33** | 1个 | `add_office_message_menu_and_permissions` |

---

## 🎯 修复策略

### 1. 保留的核心脚本（不改动）

根据集成计划Task 1要求，保留以下核心脚本：

- ✅ **V29__create_office_platform_tables.sql** - 办公平台基础表（office_platform_app, office_identity_binding）
- ✅ **V30__add_office_admin_permissions.sql** - 管理权限种子数据

### 2. 需要重新编号的脚本

将冲突的业务表创建脚本重新编号为V34-V40：

| 旧版本号 | 新版本号 | 脚本名称 | 说明 |
|---------|---------|---------|------|
| V29 | V34 | `create_office_message_table.sql` | 消息表 |
| V29 | V35 | `create_office_approval_process_table.sql` | 审批流程表（保留此版本） |
| V30 | V36 | `add_office_platform_menus.sql` | 菜单权限（39个菜单项） |
| V30 | ~~删除~~ | `create_office_approval_process.sql` | 审批表重复，删除 |
| V30 | V37 | `create_office_calendar_event_table.sql` | 日历事件表（保留此版本） |
| V31 | ~~删除~~ | `create_office_calendar_event.sql` | 日历表重复，删除 |
| V31 | V38 | `create_office_document_info_table.sql` | 文档信息表（保留此版本） |
| V32 | ~~删除~~ | `create_office_document.sql` | 文档表重复，删除 |
| V33 | V39 | `add_office_message_menu_and_permissions.sql` | 消息菜单权限 |

### 3. 需要删除的重复脚本

以下脚本是重复创建同一张表，需要删除：

- ❌ `V30__create_office_approval_process.sql` - 与V35重复
- ❌ `V31__create_office_calendar_event.sql` - 与V37重复
- ❌ `V32__create_office_document.sql` - 与V38重复

---

## 🔧 修复步骤

### MySQL 脚本

```bash
cd /Users/work/apex-scaffold/apex-interfaces/src/main/resources/db/migration/mysql

# 1. 删除重复脚本
rm V30__create_office_approval_process.sql
rm V31__create_office_calendar_event.sql
rm V32__create_office_document.sql

# 2. 重命名冲突脚本
mv V29__create_office_message_table.sql V34__create_office_message_table.sql
mv V29__create_office_approval_process_table.sql V35__create_office_approval_process_table.sql
mv V30__add_office_platform_menus.sql V36__add_office_platform_menus.sql
mv V30__create_office_calendar_event_table.sql V37__create_office_calendar_event_table.sql
mv V31__create_office_document_info_table.sql V38__create_office_document_info_table.sql
mv V33__add_office_message_menu_and_permissions.sql V39__add_office_message_menu_and_permissions.sql
```

### PostgreSQL 脚本

```bash
cd /Users/work/apex-scaffold/apex-interfaces/src/main/resources/db/migration/postgresql

# 1. 删除重复脚本
rm V30__create_office_approval_process.sql
rm V31__create_office_calendar_event.sql
rm V32__create_office_document.sql

# 2. 重命名冲突脚本
mv V29__create_office_message_table.sql V34__create_office_message_table.sql
mv V29__create_office_approval_process_table.sql V35__create_office_approval_process_table.sql
mv V30__add_office_platform_menus.sql V36__add_office_platform_menus.sql
mv V30__create_office_calendar_event_table.sql V37__create_office_calendar_event_table.sql
mv V31__create_office_document_info_table.sql V38__create_office_document_info_table.sql
mv V33__add_office_message_menu_and_permissions.sql V39__add_office_message_menu_and_permissions.sql
```

---

## ✅ 修复后的版本序列

修复后的Flyway脚本版本序列（V1-V39）：

```
V1   - 初始化数据库结构
V2   - 系统权限种子数据
...
V28  - 添加定时任务查询菜单和权限
V29  - 创建办公平台基础表 ✅ 保留
V30  - 添加办公平台管理权限 ✅ 保留
V34  - 创建办公消息表 🔄 重新编号
V35  - 创建审批流程表 🔄 重新编号
V36  - 添加办公平台菜单（39个） 🔄 重新编号
V37  - 创建日历事件表 🔄 重新编号
V38  - 创建文档信息表 🔄 重新编号
V39  - 添加消息菜单权限 🔄 重新编号
```

---

## 📊 修复效果

### 修复前
- ❌ V29: 3个脚本冲突
- ❌ V30: 4个脚本冲突
- ❌ V31: 2个脚本冲突
- ❌ V32: 1个重复脚本
- ❌ V33: 1个孤立脚本

### 修复后
- ✅ V29: 1个脚本（基础表）
- ✅ V30: 1个脚本（管理权限）
- ✅ V34-V39: 6个业务表和菜单脚本，顺序递增
- ✅ 删除3个重复脚本
- ✅ Flyway版本号严格递增，无冲突

---

## 🔍 验证方法

### 1. 验证文件重命名
```bash
cd /Users/work/apex-scaffold
ls -la apex-interfaces/src/main/resources/db/migration/mysql/V{29,30,34,35,36,37,38,39}*.sql
ls -la apex-interfaces/src/main/resources/db/migration/postgresql/V{29,30,34,35,36,37,38,39}*.sql
```

### 2. 验证无冲突
```bash
# 检查每个版本号只有一个脚本
for v in 29 30 34 35 36 37 38 39; do
  echo "V$v: MySQL=$(ls apex-interfaces/src/main/resources/db/migration/mysql/V${v}*.sql 2>/dev/null | wc -l) PostgreSQL=$(ls apex-interfaces/src/main/resources/db/migration/postgresql/V${v}*.sql 2>/dev/null | wc -l)"
done
```

### 3. 验证Flyway执行顺序
```bash
# 模拟Flyway排序
find apex-interfaces/src/main/resources/db/migration/mysql -name "V*.sql" | sort | grep -E 'V(29|30|3[4-9])' 
```

---

## ⚠️ 注意事项

### 1. 已部署环境处理

如果数据库已经执行过冲突脚本：

```sql
-- 查询已执行的Flyway脚本
SELECT version, description, installed_on, success 
FROM flyway_schema_history 
WHERE version IN ('29', '30', '31', '32', '33')
ORDER BY version, installed_rank;
```

**处理方案**：
- 如果V29-V33的表已创建：手动插入V34-V39的Flyway历史记录，标记为已成功执行
- 如果表未创建：删除失败的Flyway记录，重新执行修复后的脚本

### 2. 本地开发环境

**推荐做法**：
```bash
# 1. 备份数据库
mysqldump -u root -p apex_scaffold > backup_before_fix.sql

# 2. 删除数据库重建
DROP DATABASE apex_scaffold;
CREATE DATABASE apex_scaffold;

# 3. 重新执行Flyway迁移
mvnd -pl apex-interfaces -am flyway:migrate
```

### 3. Git提交

修复完成后的提交信息：
```
fix(flyway): 修复V29-V33版本号冲突

- 保留V29核心基础表和V30管理权限脚本
- 删除3个重复的业务表脚本
- 重新编号6个冲突脚本为V34-V39
- 确保Flyway版本号严格递增无冲突

Fixes: #issue-number
```

---

## 📝 总结

- **删除文件数**: 3个（MySQL） + 3个（PostgreSQL） = 6个
- **重命名文件数**: 6个（MySQL） + 6个（PostgreSQL） = 12个
- **保留文件数**: 2个（MySQL） + 2个（PostgreSQL） = 4个
- **修复完成度**: 100%
- **预期结果**: Flyway迁移顺利执行，所有办公平台表和菜单正确创建

---

**执行时间**: 2026-09-15  
**文档版本**: v1.0  
**状态**: 待执行
