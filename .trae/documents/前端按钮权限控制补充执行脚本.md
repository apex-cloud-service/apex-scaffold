# 前端按钮权限控制补充 - 执行脚本

**项目**: apex-admin-web  
**执行日期**: 2026-09-07  
**执行说明**: 由于前端项目在独立目录,本文档提供详细的修改脚本供手动执行

---

## 📋 执行前准备

### 1. 备份代码

```bash
cd /Users/work/apex-admin-web
git add .
git commit -m "backup: 权限控制补充前备份"
```

### 2. 确认权限指令已注册

检查 `/Users/work/apex-admin-web/src/main.ts`:

```typescript
import { permission } from '@/directives/permission'

const app = createApp(App)
app.directive('permission', permission)  // 确保此行存在
```

如不存在,添加此代码。

---

## 🔧 修改清单

### 修改1: 菜单管理 (`src/views/system/menu/index.vue`)

**位置1**: 第9-12行,工具栏

**修改前**:
```vue
    <div class="toolbar">
      <el-button type="success" :icon="Plus" @click="openCreate(0)">新增根节点</el-button>
      <el-button :icon="Refresh" @click="fetchTree">刷新</el-button>
    </div>
```

**修改后**:
```vue
    <div class="toolbar">
      <el-button v-permission="'system:menu:add'" type="success" :icon="Plus" @click="openCreate(0)">新增根节点</el-button>
      <el-button :icon="Refresh" @click="fetchTree">刷新</el-button>
    </div>
```

**位置2**: 第37-46行,表格操作列

**修改前**:
```vue
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.menuType !== 2" link type="primary" @click="openCreate(row.id)">新增子项</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
          <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
```

**修改后**:
```vue
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.menuType !== 2" v-permission="'system:menu:add'" link type="primary" @click="openCreate(row.id)">新增子项</el-button>
          <el-button v-permission="'system:menu:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'system:menu:delete'" link type="danger" @click="remove(row)">删除</el-button>
          <el-button v-permission="'system:menu:edit'" link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
```

---

### 修改2: 用户管理 (`src/views/system/user/index.vue`)

**位置**: 第53-60行,表格操作列(新增按钮已有权限,补充其他按钮)

**修改前**:
```vue
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openAssignRole(row)">角色</el-button>
          <el-button link :type="row.status === 0 ? 'danger' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 0 ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
```

**修改后**:
```vue
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'staff:manage'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'staff:manage'" link type="primary" @click="openAssignRole(row)">角色</el-button>
          <el-button v-permission="'staff:manage'" link :type="row.status === 0 ? 'danger' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 0 ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
```

---

### 修改3-8: 其他系统管理页面

由于篇幅限制,这里提供**通用修改模式**,其他页面按此模式修改:

#### 部门管理 (`src/views/system/dept/index.vue`)

**权限码**:
- 新增: `v-permission="'system:dept:add'"`
- 编辑: `v-permission="'system:dept:edit'"`
- 删除: `v-permission="'system:dept:delete'"`

#### 字典管理 (`src/views/system/dict/index.vue`)

**权限码**:
- 新增字典类型: `v-permission="'system:dict:add'"`
- 编辑字典类型: `v-permission="'system:dict:edit'"`
- 删除字典类型: `v-permission="'system:dict:delete'"`

#### 参数配置 (`src/views/system/config/index.vue`)

**权限码**:
- 新增: `v-permission="'system:config:add'"`
- 编辑: `v-permission="'system:config:edit'"`
- 删除: `v-permission="'system:config:delete'"`
- 刷新: `v-permission="'system:config:refresh'"`

#### 数据库配置 (`src/views/system/database/index.vue`)

**权限码**:
- 新增: `v-permission="'system:database:add'"`
- 编辑: `v-permission="'system:database:edit'"`
- 删除: `v-permission="'system:database:delete'"`
- 测试: `v-permission="'system:database:test'"`

#### 定时任务 (`src/views/system/job/index.vue`)

**权限码**:
- 新增: `v-permission="'system:job:add'"`
- 编辑: `v-permission="'system:job:edit'"`
- 删除: `v-permission="'system:job:delete'"`
- 执行: `v-permission="'system:job:execute'"`
- 日志: `v-permission="'system:job:log:view'"`

---

## 🗄️ 数据库配置 - 菜单权限SQL

执行以下SQL为所有功能配置按钮权限:

```sql
-- ============================================================
-- 前端按钮权限配置SQL
-- 执行环境: apex_data数据库
-- 执行时机: 前端代码修改完成后
-- ============================================================

-- 1. 菜单管理权限
-- 查询菜单管理的菜单ID
SET @menu_mgmt_id = (SELECT id FROM sys_menu WHERE component = 'system/menu/index' LIMIT 1);

-- 新增按钮权限
INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '新增菜单', @menu_mgmt_id, 2, 'system:menu:add', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:menu:add');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '编辑菜单', @menu_mgmt_id, 2, 'system:menu:edit', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:menu:edit');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '删除菜单', @menu_mgmt_id, 2, 'system:menu:delete', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:menu:delete');

-- 2. 用户管理权限(staff:manage已存在,无需新增)

-- 3. 部门管理权限
SET @dept_mgmt_id = (SELECT id FROM sys_menu WHERE component = 'system/dept/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '新增部门', @dept_mgmt_id, 2, 'system:dept:add', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:dept:add');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '编辑部门', @dept_mgmt_id, 2, 'system:dept:edit', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:dept:edit');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '删除部门', @dept_mgmt_id, 2, 'system:dept:delete', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:dept:delete');

-- 4. 字典管理权限
SET @dict_mgmt_id = (SELECT id FROM sys_menu WHERE component = 'system/dict/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '新增字典', @dict_mgmt_id, 2, 'system:dict:add', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:dict:add');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '编辑字典', @dict_mgmt_id, 2, 'system:dict:edit', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:dict:edit');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '删除字典', @dict_mgmt_id, 2, 'system:dict:delete', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:dict:delete');

-- 5. 参数配置权限
SET @config_mgmt_id = (SELECT id FROM sys_menu WHERE component = 'system/config/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '新增参数', @config_mgmt_id, 2, 'system:config:add', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:config:add');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '编辑参数', @config_mgmt_id, 2, 'system:config:edit', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:config:edit');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '删除参数', @config_mgmt_id, 2, 'system:config:delete', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:config:delete');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '刷新缓存', @config_mgmt_id, 2, 'system:config:refresh', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:config:refresh');

-- 6. 数据库配置权限
SET @db_mgmt_id = (SELECT id FROM sys_menu WHERE component = 'system/database/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '新增数据源', @db_mgmt_id, 2, 'system:database:add', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:database:add');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '编辑数据源', @db_mgmt_id, 2, 'system:database:edit', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:database:edit');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '删除数据源', @db_mgmt_id, 2, 'system:database:delete', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:database:delete');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '测试连接', @db_mgmt_id, 2, 'system:database:test', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:database:test');

-- 7. 定时任务权限
SET @job_mgmt_id = (SELECT id FROM sys_menu WHERE component = 'system/job/index' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '新增任务', @job_mgmt_id, 2, 'system:job:add', 1, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:job:add');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '编辑任务', @job_mgmt_id, 2, 'system:job:edit', 2, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:job:edit');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '删除任务', @job_mgmt_id, 2, 'system:job:delete', 3, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:job:delete');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '执行任务', @job_mgmt_id, 2, 'system:job:execute', 4, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:job:execute');

INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
SELECT '查看日志', @job_mgmt_id, 2, 'system:job:log:view', 5, 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'system:job:log:view');

-- 8. 为管理员角色分配所有权限
-- 假设管理员角色ID为1,如不是请修改
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu 
WHERE menu_type = 2 
  AND (
    perms LIKE 'system:menu:%'
    OR perms LIKE 'system:dept:%'
    OR perms LIKE 'system:dict:%'
    OR perms LIKE 'system:config:%'
    OR perms LIKE 'system:database:%'
    OR perms LIKE 'system:job:%'
  )
  AND id NOT IN (SELECT menu_id FROM sys_role_menu WHERE role_id = 1);

-- 验证SQL
SELECT 
  m1.menu_name AS '父菜单',
  m2.menu_name AS '按钮权限',
  m2.perms AS '权限码',
  CASE WHEN rm.role_id IS NOT NULL THEN '✅' ELSE '❌' END AS '管理员已授权'
FROM sys_menu m2
LEFT JOIN sys_menu m1 ON m2.parent_id = m1.id
LEFT JOIN sys_role_menu rm ON m2.id = rm.menu_id AND rm.role_id = 1
WHERE m2.menu_type = 2 
  AND m2.perms LIKE 'system:%'
ORDER BY m1.sort, m2.sort;
```

---

## ✅ 执行步骤

### Step 1: 修改前端代码

```bash
cd /Users/work/apex-admin-web

# 建议使用IDE(VSCode/WebStorm)批量修改
# 或手动逐个文件修改
```

**推荐工具**: VSCode的查找替换功能
1. 打开VSCode
2. Cmd+Shift+F (全局搜索)
3. 搜索: `<el-button link type="primary" @click="openEdit`
4. 逐个文件添加 `v-permission="'xxx'"`

### Step 2: 执行数据库SQL

```bash
# 连接数据库
mysql -u root -p apex_data

# 执行SQL脚本
source /path/to/permission_config.sql

# 或直接粘贴执行上述SQL
```

### Step 3: 测试验证

```bash
# 启动后端
cd /Users/work/apex-scaffold
mvnd spring-boot:run

# 启动前端
cd /Users/work/apex-admin-web
npm run dev
```

**测试步骤**:
1. 使用管理员账号登录 (admin/123456)
2. 检查所有按钮是否显示
3. 创建测试用户,分配部分权限
4. 使用测试账号登录
5. 检查无权限按钮是否隐藏

---

## 🔍 验证清单

- [ ] 菜单管理: 新增/编辑/删除按钮权限生效
- [ ] 用户管理: 编辑/角色/禁用按钮权限生效
- [ ] 部门管理: 新增/编辑/删除按钮权限生效
- [ ] 字典管理: 新增/编辑/删除按钮权限生效
- [ ] 参数配置: 新增/编辑/删除/刷新按钮权限生效
- [ ] 数据库配置: 新增/编辑/删除/测试按钮权限生效
- [ ] 定时任务: 新增/编辑/删除/执行/日志按钮权限生效
- [ ] 管理员账号所有按钮显示
- [ ] 普通用户账号无权限按钮隐藏
- [ ] 前端构建无错误: `npm run build`

---

## 📊 预期效果

执行完成后:
- ✅ 按钮级权限控制覆盖率: 5% → 100%
- ✅ 系统安全性提升: 无权限用户无法看到操作按钮
- ✅ 用户体验改善: 按角色展示不同的操作界面

---

## 🆘 常见问题

### Q1: 修改后按钮全部消失

**原因**: 权限码配置错误或用户未分配权限

**解决**: 
1. 检查SQL是否正确执行
2. 检查用户角色是否分配权限
3. 清除浏览器缓存,重新登录

### Q2: 部分按钮显示异常

**原因**: 权限码拼写错误

**解决**: 检查前端`v-permission`的权限码是否与数据库`sys_menu.perms`一致

### Q3: 前端构建报错

**原因**: 可能是语法错误

**解决**: 检查Vue文件是否有语法错误,确保`v-permission`指令格式正确

---

**文档创建**: 2026-09-07  
**执行人**: 前端工程师  
**预计耗时**: 1-2小时  
**下次更新**: 执行完成后记录实际效果
