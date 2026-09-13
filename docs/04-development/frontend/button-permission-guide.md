# 前端按钮权限控制补充指南

**项目**: apex-admin-web  
**创建日期**: 2026-09-07  
**目标**: 为所有操作按钮添加v-permission权限控制

---

## 一、权限指令说明

### 1.1 权限指令位置

**文件**: `/Users/work/apex-admin-web/src/directives/permission.ts`

**状态**: ✅ 已实现

### 1.2 使用方式

```vue
<template>
  <el-button v-permission="'system:user:add'">新增</el-button>
</template>
```

**行为**: 当用户无对应权限时,按钮从DOM中移除

---

## 二、权限码命名规范

### 2.1 命名格式

```
<模块>:<资源>:<操作>
```

### 2.2 示例

| 权限码 | 说明 |
|--------|------|
| `system:user:view` | 查看用户 |
| `system:user:add` | 新增用户 |
| `system:user:edit` | 编辑用户 |
| `system:user:delete` | 删除用户 |
| `system:user:export` | 导出用户 |
| `system:user:assign-role` | 分配角色 |

---

## 三、需补充权限控制的页面清单

### 3.1 系统管理模块

#### 菜单管理 (`src/views/system/menu/index.vue`)

**修改位置1**: 工具栏-新增根节点按钮

```diff
-      <el-button type="success" :icon="Plus" @click="openCreate(0)">新增根节点</el-button>
+      <el-button v-permission="'system:menu:add'" type="success" :icon="Plus" @click="openCreate(0)">新增根节点</el-button>
```

**修改位置2**: 表格操作列

```diff
       <el-table-column label="操作" width="260" fixed="right">
         <template #default="{ row }">
-          <el-button v-if="row.menuType !== 2" link type="primary" @click="openCreate(row.id)">新增子项</el-button>
-          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
-          <el-button link type="danger" @click="remove(row)">删除</el-button>
-          <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
+          <el-button v-if="row.menuType !== 2" v-permission="'system:menu:add'" link type="primary" @click="openCreate(row.id)">新增子项</el-button>
+          <el-button v-permission="'system:menu:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
+          <el-button v-permission="'system:menu:delete'" link type="danger" @click="remove(row)">删除</el-button>
+          <el-button v-permission="'system:menu:edit'" link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
             {{ row.status === 1 ? '停用' : '启用' }}
           </el-button>
         </template>
       </el-table-column>
```

#### 用户管理 (`src/views/system/user/index.vue`)

**修改位置**: 表格操作列(新增按钮已有权限,补充其他按钮)

```diff
       <el-table-column label="操作" width="220" fixed="right">
         <template #default="{ row }">
-          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
-          <el-button link type="primary" @click="openAssignRole(row)">角色</el-button>
-          <el-button link :type="row.status === 0 ? 'danger' : 'success'" @click="toggleStatus(row)">
+          <el-button v-permission="'staff:manage'" link type="primary" @click="openEdit(row)">编辑</el-button>
+          <el-button v-permission="'staff:manage'" link type="primary" @click="openAssignRole(row)">角色</el-button>
+          <el-button v-permission="'staff:manage'" link :type="row.status === 0 ? 'danger' : 'success'" @click="toggleStatus(row)">
             {{ row.status === 0 ? '禁用' : '启用' }}
           </el-button>
         </template>
       </el-table-column>
```

#### 部门管理 (`src/views/system/dept/index.vue`)

**需要补充的按钮**:
- 新增部门: `v-permission="'system:dept:add'"`
- 编辑部门: `v-permission="'system:dept:edit'"`
- 删除部门: `v-permission="'system:dept:delete'"`

**修改模式**: 参考菜单管理

#### 字典管理 (`src/views/system/dict/index.vue`)

**需要补充的按钮**:
- 新增字典类型: `v-permission="'system:dict:add'"`
- 编辑字典类型: `v-permission="'system:dict:edit'"`
- 删除字典类型: `v-permission="'system:dict:delete'"`
- 新增字典数据: `v-permission="'system:dict:data:add'"`
- 编辑字典数据: `v-permission="'system:dict:data:edit'"`
- 删除字典数据: `v-permission="'system:dict:data:delete'"`

#### 参数配置 (`src/views/system/config/index.vue`)

**需要补充的按钮**:
- 新增参数: `v-permission="'system:config:add'"`
- 编辑参数: `v-permission="'system:config:edit'"`
- 删除参数: `v-permission="'system:config:delete'"`
- 刷新缓存: `v-permission="'system:config:refresh'"`

#### 数据库配置 (`src/views/system/database/index.vue`)

**需要补充的按钮**:
- 新增数据源: `v-permission="'system:database:add'"`
- 编辑数据源: `v-permission="'system:database:edit'"`
- 删除数据源: `v-permission="'system:database:delete'"`
- 测试连接: `v-permission="'system:database:test'"`

#### 定时任务 (`src/views/system/job/index.vue`)

**需要补充的按钮**:
- 新增任务: `v-permission="'system:job:add'"`
- 编辑任务: `v-permission="'system:job:edit'"`
- 删除任务: `v-permission="'system:job:delete'"`
- 执行任务: `v-permission="'system:job:execute'"`
- 查看日志: `v-permission="'system:job:log:view'"`

### 3.2 监控审计模块

#### 登录日志 (`src/views/monitor/loginlog/index.vue`)

**需要补充的按钮**:
- 查看详情: `v-permission="'monitor:loginlog:view'"`
- 导出日志: `v-permission="'monitor:loginlog:export'"`

#### 操作日志 (`src/views/monitor/audit/index.vue`)

**需要补充的按钮**:
- 查看详情: `v-permission="'monitor:audit:view'"`
- 导出日志: `v-permission="'monitor:audit:export'"`

#### API调用日志 (`src/views/monitor/api-call-log/index.vue`)

**需要补充的按钮**:
- 查看详情: `v-permission="'monitor:api-call-log:view'"`
- 导出日志: `v-permission="'monitor:api-call-log:export'"`

#### 运行时日志 (`src/views/monitor/runtime-log/index.vue`)

**需要补充的按钮**:
- 查看详情: `v-permission="'monitor:runtime-log:view'"`
- 导出日志: `v-permission="'monitor:runtime-log:export'"`

#### 业务日志 (`src/views/monitor/business-log/index.vue`)

**需要补充的按钮**:
- 查看详情: `v-permission="'monitor:business-log:view'"`
- 导出日志: `v-permission="'monitor:business-log:export'"`

#### 在线用户 (`src/views/monitor/online/index.vue`)

**需要补充的按钮**:
- 强制下线: `v-permission="'monitor:online:force-logout'"`

### 3.3 工作流模块

#### 工作流监控 (`src/views/workflow/monitor/index.vue`)

**需要补充的按钮**:
- 查看详情: `v-permission="'workflow:monitor:view'"`
- 终止流程: `v-permission="'workflow:instance:terminate'"`

---

## 四、权限码与后端菜单配置对应

### 4.1 后端菜单表结构

**表名**: `sys_menu`

**关键字段**:
- `menu_type`: 0=目录, 1=菜单, 2=按钮
- `perms`: 权限标识符(如`system:user:add`)

### 4.2 示例SQL

**菜单管理权限配置**:

```sql
-- 菜单管理(一级菜单)
INSERT INTO sys_menu (menu_name, parent_id, menu_type, path, component, perms, icon, sort, visible, status)
VALUES ('菜单管理', 1, 1, 'menu', 'system/menu/index', 'system:menu:view', 'menu', 3, 1, 1);

SET @menu_parent_id = LAST_INSERT_ID();

-- 新增按钮
INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status)
VALUES ('新增菜单', @menu_parent_id, 2, 'system:menu:add', 1, 1, 1);

-- 编辑按钮
INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status)
VALUES ('编辑菜单', @menu_parent_id, 2, 'system:menu:edit', 2, 1, 1);

-- 删除按钮
INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status)
VALUES ('删除菜单', @menu_parent_id, 2, 'system:menu:delete', 3, 1, 1);
```

**用户管理权限配置**:

```sql
-- 用户管理(一级菜单) - 假设ID为1001
SET @user_menu_id = 1001;

-- 编辑按钮
INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status)
VALUES ('编辑用户', @user_menu_id, 2, 'staff:manage', 2, 1, 1);

-- 启用/禁用按钮
INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status)
VALUES ('启用禁用用户', @user_menu_id, 2, 'staff:manage', 3, 1, 1);

-- 分配角色按钮
INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status)
VALUES ('分配角色', @user_menu_id, 2, 'staff:manage', 4, 1, 1);
```

---

## 五、实施步骤

### 5.1 准备工作

1. 确认权限指令已注册

**文件**: `/Users/work/apex-admin-web/src/main.ts`

```typescript
import { permission } from '@/directives/permission'

app.directive('permission', permission)
```

2. 确认权限Store可用

**文件**: `/Users/work/apex-admin-web/src/stores/permission.ts`

应包含`hasPermission`方法。

### 5.2 修改Vue组件

**步骤**:
1. 打开对应Vue文件
2. 找到操作按钮
3. 添加`v-permission`指令
4. 配置权限码

**示例**:

```vue
<!-- 修改前 -->
<el-button @click="handleAdd">新增</el-button>

<!-- 修改后 -->
<el-button v-permission="'system:user:add'" @click="handleAdd">新增</el-button>
```

### 5.3 配置后端权限

**步骤**:
1. 连接数据库
2. 查询`sys_menu`表,找到对应菜单的ID
3. 为该菜单新增按钮类型子菜单(menu_type=2)
4. 配置`perms`字段为权限码
5. 为管理员角色分配权限

**SQL模板**:

```sql
-- 查询菜单ID
SELECT id, menu_name, perms FROM sys_menu WHERE menu_name = '用户管理';

-- 假设菜单ID为1001,新增按钮权限
INSERT INTO sys_menu (menu_name, parent_id, menu_type, perms, sort, visible, status, create_time)
VALUES 
('新增用户', 1001, 2, 'system:user:add', 1, 1, 1, NOW()),
('编辑用户', 1001, 2, 'system:user:edit', 2, 1, 1, NOW()),
('删除用户', 1001, 2, 'system:user:delete', 3, 1, 1, NOW());

-- 为管理员角色(role_id=1)分配权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE parent_id = 1001 AND menu_type = 2;
```

### 5.4 验证测试

1. 启动前端项目: `cd /Users/work/apex-admin-web && npm run dev`
2. 登录系统
3. 检查按钮是否正确显示/隐藏
4. 使用非管理员账号测试权限

---

## 六、完整权限码清单

### 6.1 系统管理

```typescript
const SYSTEM_PERMISSIONS = {
  // 用户管理
  USER_VIEW: 'staff:manage',
  USER_ADD: 'staff:manage',
  USER_EDIT: 'staff:manage',
  USER_DELETE: 'staff:manage',  // 预留
  USER_ASSIGN_ROLE: 'staff:manage',
  USER_TOGGLE_STATUS: 'staff:manage',
  
  // 菜单管理
  MENU_VIEW: 'system:menu:view',
  MENU_ADD: 'system:menu:add',
  MENU_EDIT: 'system:menu:edit',
  MENU_DELETE: 'system:menu:delete',
  
  // 角色管理
  ROLE_VIEW: 'system:role:view',
  ROLE_ADD: 'system:role:add',
  ROLE_EDIT: 'system:role:edit',
  ROLE_DELETE: 'system:role:delete',
  
  // 部门管理
  DEPT_VIEW: 'system:dept:view',
  DEPT_ADD: 'system:dept:add',
  DEPT_EDIT: 'system:dept:edit',
  DEPT_DELETE: 'system:dept:delete',
  
  // 字典管理
  DICT_VIEW: 'system:dict:view',
  DICT_ADD: 'system:dict:add',
  DICT_EDIT: 'system:dict:edit',
  DICT_DELETE: 'system:dict:delete',
  DICT_DATA_ADD: 'system:dict:data:add',
  DICT_DATA_EDIT: 'system:dict:data:edit',
  DICT_DATA_DELETE: 'system:dict:data:delete',
  
  // 参数配置
  CONFIG_VIEW: 'system:config:view',
  CONFIG_ADD: 'system:config:add',
  CONFIG_EDIT: 'system:config:edit',
  CONFIG_DELETE: 'system:config:delete',
  CONFIG_REFRESH: 'system:config:refresh',
  
  // 数据库配置
  DATABASE_VIEW: 'system:database:view',
  DATABASE_ADD: 'system:database:add',
  DATABASE_EDIT: 'system:database:edit',
  DATABASE_DELETE: 'system:database:delete',
  DATABASE_TEST: 'system:database:test',
  
  // 定时任务
  JOB_VIEW: 'system:job:view',
  JOB_ADD: 'system:job:add',
  JOB_EDIT: 'system:job:edit',
  JOB_DELETE: 'system:job:delete',
  JOB_EXECUTE: 'system:job:execute',
  JOB_LOG_VIEW: 'system:job:log:view',
}
```

### 6.2 监控审计

```typescript
const MONITOR_PERMISSIONS = {
  // 监控大盘
  DASHBOARD_VIEW: 'monitor:dashboard:view',
  
  // 登录日志
  LOGIN_LOG_VIEW: 'monitor:loginlog:view',
  LOGIN_LOG_EXPORT: 'monitor:loginlog:export',
  
  // 操作日志
  AUDIT_LOG_VIEW: 'monitor:audit:view',
  AUDIT_LOG_EXPORT: 'monitor:audit:export',
  
  // API调用日志
  API_CALL_LOG_VIEW: 'monitor:api-call-log:view',
  API_CALL_LOG_EXPORT: 'monitor:api-call-log:export',
  
  // 运行时日志
  RUNTIME_LOG_VIEW: 'monitor:runtime-log:view',
  RUNTIME_LOG_EXPORT: 'monitor:runtime-log:export',
  
  // 业务日志
  BUSINESS_LOG_VIEW: 'monitor:business-log:view',
  BUSINESS_LOG_EXPORT: 'monitor:business-log:export',
  
  // 在线用户
  ONLINE_USER_VIEW: 'monitor:online:view',
  ONLINE_USER_FORCE_LOGOUT: 'monitor:online:force-logout',
}
```

### 6.3 工作流

```typescript
const WORKFLOW_PERMISSIONS = {
  // 工作流监控
  MONITOR_VIEW: 'workflow:monitor:view',
  INSTANCE_VIEW: 'workflow:instance:view',
  INSTANCE_TERMINATE: 'workflow:instance:terminate',
}
```

---

## 七、注意事项

### 7.1 权限码一致性

- ⚠️ 前端`v-permission`的权限码必须与后端`sys_menu.perms`字段一致
- ⚠️ 后端权限校验通过`@RequirePermission`注解实现

### 7.2 性能考虑

- ✅ 权限指令性能良好,无需优化
- ✅ 权限检查在组件挂载时执行,不影响运行时性能

### 7.3 用户体验

- 建议: 权限不足时,不要完全隐藏按钮,可以禁用并显示提示
- 当前实现: 直接从DOM移除(符合主流做法)

---

## 八、验收标准

### 8.1 前端代码

- [ ] 所有操作按钮已添加`v-permission`指令
- [ ] 权限码命名符合规范
- [ ] 权限指令正常工作

### 8.2 后端配置

- [ ] 所有权限码已添加到`sys_menu`表
- [ ] 管理员角色已分配所有权限
- [ ] 普通用户测试权限生效

### 8.3 功能测试

- [ ] 管理员登录,所有按钮显示
- [ ] 普通用户登录,无权按钮隐藏
- [ ] 权限变更后,页面刷新生效

---

**文档维护者**: Apex Team  
**创建日期**: 2026-09-07  
**适用版本**: apex-admin-web v1.0.0  
**下次更新**: 实施完成后补充实际效果截图
