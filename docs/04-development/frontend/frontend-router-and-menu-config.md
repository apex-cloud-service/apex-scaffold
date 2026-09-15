# 前端路由配置和菜单SQL

## 1. 路由配置

将以下内容添加到 `/Users/work/apex-admin-web/src/router/dynamic.ts` 的路由配置中：

```typescript
// 办公平台 - 消息发送
{
  path: '/office/message',
  name: 'OfficeMessage',
  component: () => import('@/views/office/message/index.vue'),
  meta: {
    title: '消息发送',
    icon: 'ChatDotRound',
    permissions: ['office:message:view'],
  },
},

// 办公平台 - 审批对接
{
  path: '/office/approval',
  name: 'OfficeApproval',
  component: () => import('@/views/office/approval/index.vue'),
  meta: {
    title: '审批对接',
    icon: 'Checked',
    permissions: ['office:approval:view'],
  },
},

// 办公平台 - 日历管理
{
  path: '/office/calendar',
  name: 'OfficeCalendar',
  component: () => import('@/views/office/calendar/index.vue'),
  meta: {
    title: '日历管理',
    icon: 'Calendar',
    permissions: ['office:calendar:view'],
  },
},

// 办公平台 - 文档管理
{
  path: '/office/document',
  name: 'OfficeDocument',
  component: () => import('@/views/office/document/index.vue'),
  meta: {
    title: '文档管理',
    icon: 'FolderOpened',
    permissions: ['office:document:view'],
  },
},
```

## 2. 菜单SQL脚本

将以下SQL添加到 `/Users/work/apex-admin-web/database-menu.sql`：

```sql
-- =====================================================
-- 办公平台集成菜单配置（阶段二和阶段三）
-- =====================================================

-- 变量定义（根据实际情况调整父菜单ID）
SET @office_parent_id = (SELECT id FROM sys_menu WHERE name = '办公平台' LIMIT 1);
SET @admin_role_id = (SELECT id FROM sys_role WHERE code = 'admin' LIMIT 1);

-- 如果找不到办公平台父菜单，先创建
INSERT INTO sys_menu (parent_id, name, type, route_path, component, permission, icon, sort, visible, status, created_at, updated_at)
SELECT 0, '办公平台', 'MENU', '/office', 'Layout', 'office:view', 'OfficeBuilding', 40, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE name = '办公平台');

SET @office_parent_id = (SELECT id FROM sys_menu WHERE name = '办公平台' LIMIT 1);

-- =====================================================
-- 消息发送菜单及权限
-- =====================================================

-- 消息发送主菜单
INSERT INTO sys_menu (parent_id, name, type, route_path, component, permission, icon, sort, visible, status, created_at, updated_at)
VALUES (@office_parent_id, '消息发送', 'MENU', '/office/message', 'office/message/index', 'office:message:view', 'ChatDotRound', 3, 1, 1, NOW(), NOW());

SET @message_menu_id = LAST_INSERT_ID();

-- 消息发送权限按钮
INSERT INTO sys_menu (parent_id, name, type, route_path, component, permission, icon, sort, visible, status, created_at, updated_at)
VALUES
  (@message_menu_id, '发送文本消息', 'BUTTON', NULL, NULL, 'office:message:sendText', NULL, 1, 0, 1, NOW(), NOW()),
  (@message_menu_id, '发送图片消息', 'BUTTON', NULL, NULL, 'office:message:sendImage', NULL, 2, 0, 1, NOW(), NOW()),
  (@message_menu_id, '发送文件消息', 'BUTTON', NULL, NULL, 'office:message:sendFile', NULL, 3, 0, 1, NOW(), NOW()),
  (@message_menu_id, '发送卡片消息', 'BUTTON', NULL, NULL, 'office:message:sendCard', NULL, 4, 0, 1, NOW(), NOW()),
  (@message_menu_id, '查询消息记录', 'BUTTON', NULL, NULL, 'office:message:query', NULL, 5, 0, 1, NOW(), NOW());

-- 为管理员角色分配消息权限
INSERT INTO sys_role_menu (role_id, menu_id, created_at)
SELECT @admin_role_id, id, NOW()
FROM sys_menu
WHERE parent_id = @office_parent_id AND name = '消息发送'
   OR parent_id = @message_menu_id;

-- =====================================================
-- 审批对接菜单及权限
-- =====================================================

-- 审批对接主菜单
INSERT INTO sys_menu (parent_id, name, type, route_path, component, permission, icon, sort, visible, status, created_at, updated_at)
VALUES (@office_parent_id, '审批对接', 'MENU', '/office/approval', 'office/approval/index', 'office:approval:view', 'Checked', 4, 1, 1, NOW(), NOW());

SET @approval_menu_id = LAST_INSERT_ID();

-- 审批对接权限按钮
INSERT INTO sys_menu (parent_id, name, type, route_path, component, permission, icon, sort, visible, status, created_at, updated_at)
VALUES
  (@approval_menu_id, '发起审批', 'BUTTON', NULL, NULL, 'office:approval:initiate', NULL, 1, 0, 1, NOW(), NOW()),
  (@approval_menu_id, '查询审批详情', 'BUTTON', NULL, NULL, 'office:approval:detail', NULL, 2, 0, 1, NOW(), NOW()),
  (@approval_menu_id, '执行审批操作', 'BUTTON', NULL, NULL, 'office:approval:approve', NULL, 3, 0, 1, NOW(), NOW()),
  (@approval_menu_id, '撤销审批', 'BUTTON', NULL, NULL, 'office:approval:cancel', NULL, 4, 0, 1, NOW(), NOW()),
  (@approval_menu_id, '查询待审批列表', 'BUTTON', NULL, NULL, 'office:approval:pending', NULL, 5, 0, 1, NOW(), NOW()),
  (@approval_menu_id, '查询我发起的审批', 'BUTTON', NULL, NULL, 'office:approval:initiated', NULL, 6, 0, 1, NOW(), NOW());

-- 为管理员角色分配审批权限
INSERT INTO sys_role_menu (role_id, menu_id, created_at)
SELECT @admin_role_id, id, NOW()
FROM sys_menu
WHERE parent_id = @office_parent_id AND name = '审批对接'
   OR parent_id = @approval_menu_id;

-- =====================================================
-- 日历管理菜单及权限
-- =====================================================

-- 日历管理主菜单
INSERT INTO sys_menu (parent_id, name, type, route_path, component, permission, icon, sort, visible, status, created_at, updated_at)
VALUES (@office_parent_id, '日历管理', 'MENU', '/office/calendar', 'office/calendar/index', 'office:calendar:view', 'Calendar', 5, 1, 1, NOW(), NOW());

SET @calendar_menu_id = LAST_INSERT_ID();

-- 日历管理权限按钮
INSERT INTO sys_menu (parent_id, name, type, route_path, component, permission, icon, sort, visible, status, created_at, updated_at)
VALUES
  (@calendar_menu_id, '创建日历事件', 'BUTTON', NULL, NULL, 'office:calendar:create', NULL, 1, 0, 1, NOW(), NOW()),
  (@calendar_menu_id, '查询事件详情', 'BUTTON', NULL, NULL, 'office:calendar:detail', NULL, 2, 0, 1, NOW(), NOW()),
  (@calendar_menu_id, '更新日历事件', 'BUTTON', NULL, NULL, 'office:calendar:update', NULL, 3, 0, 1, NOW(), NOW()),
  (@calendar_menu_id, '删除日历事件', 'BUTTON', NULL, NULL, 'office:calendar:delete', NULL, 4, 0, 1, NOW(), NOW()),
  (@calendar_menu_id, '查询我的事件', 'BUTTON', NULL, NULL, 'office:calendar:my', NULL, 5, 0, 1, NOW(), NOW()),
  (@calendar_menu_id, '查询即将到来的事件', 'BUTTON', NULL, NULL, 'office:calendar:upcoming', NULL, 6, 0, 1, NOW(), NOW());

-- 为管理员角色分配日历权限
INSERT INTO sys_role_menu (role_id, menu_id, created_at)
SELECT @admin_role_id, id, NOW()
FROM sys_menu
WHERE parent_id = @office_parent_id AND name = '日历管理'
   OR parent_id = @calendar_menu_id;

-- =====================================================
-- 文档管理菜单及权限
-- =====================================================

-- 文档管理主菜单
INSERT INTO sys_menu (parent_id, name, type, route_path, component, permission, icon, sort, visible, status, created_at, updated_at)
VALUES (@office_parent_id, '文档管理', 'MENU', '/office/document', 'office/document/index', 'office:document:view', 'FolderOpened', 6, 1, 1, NOW(), NOW());

SET @document_menu_id = LAST_INSERT_ID();

-- 文档管理权限按钮
INSERT INTO sys_menu (parent_id, name, type, route_path, component, permission, icon, sort, visible, status, created_at, updated_at)
VALUES
  (@document_menu_id, '创建文档', 'BUTTON', NULL, NULL, 'office:document:create', NULL, 1, 0, 1, NOW(), NOW()),
  (@document_menu_id, '上传文件', 'BUTTON', NULL, NULL, 'office:document:upload', NULL, 2, 0, 1, NOW(), NOW()),
  (@document_menu_id, '查询文档详情', 'BUTTON', NULL, NULL, 'office:document:detail', NULL, 3, 0, 1, NOW(), NOW()),
  (@document_menu_id, '下载文档', 'BUTTON', NULL, NULL, 'office:document:download', NULL, 4, 0, 1, NOW(), NOW()),
  (@document_menu_id, '删除文档', 'BUTTON', NULL, NULL, 'office:document:delete', NULL, 5, 0, 1, NOW(), NOW()),
  (@document_menu_id, '移动文档', 'BUTTON', NULL, NULL, 'office:document:move', NULL, 6, 0, 1, NOW(), NOW()),
  (@document_menu_id, '复制文档', 'BUTTON', NULL, NULL, 'office:document:copy', NULL, 7, 0, 1, NOW(), NOW()),
  (@document_menu_id, '查询我的文档', 'BUTTON', NULL, NULL, 'office:document:my', NULL, 8, 0, 1, NOW(), NOW());

-- 为管理员角色分配文档权限
INSERT INTO sys_role_menu (role_id, menu_id, created_at)
SELECT @admin_role_id, id, NOW()
FROM sys_menu
WHERE parent_id = @office_parent_id AND name = '文档管理'
   OR parent_id = @document_menu_id;

-- =====================================================
-- 验证SQL
-- =====================================================

-- 查看新增的菜单
SELECT id, parent_id, name, type, route_path, permission, icon, sort, visible
FROM sys_menu
WHERE parent_id = @office_parent_id OR id = @office_parent_id
ORDER BY parent_id, sort;

-- 查看权限分配情况
SELECT r.name AS role_name, m.name AS menu_name, m.permission
FROM sys_role_menu rm
JOIN sys_role r ON rm.role_id = r.id
JOIN sys_menu m ON rm.menu_id = m.id
WHERE m.parent_id IN (@office_parent_id, @message_menu_id, @approval_menu_id, @calendar_menu_id, @document_menu_id)
ORDER BY r.name, m.sort;
```

## 3. PostgreSQL版本菜单SQL

```sql
-- =====================================================
-- 办公平台集成菜单配置（PostgreSQL版本）
-- =====================================================

-- 获取办公平台父菜单ID
DO $$
DECLARE
  office_parent_id BIGINT;
  admin_role_id BIGINT;
  message_menu_id BIGINT;
  approval_menu_id BIGINT;
  calendar_menu_id BIGINT;
  document_menu_id BIGINT;
BEGIN
  -- 获取父菜单ID
  SELECT id INTO office_parent_id FROM sys_menu WHERE name = '办公平台' LIMIT 1;
  
  -- 如果不存在则创建
  IF office_parent_id IS NULL THEN
    INSERT INTO sys_menu (parent_id, name, type, route_path, component, permission, icon, sort, visible, status, created_at, updated_at)
    VALUES (0, '办公平台', 'MENU', '/office', 'Layout', 'office:view', 'OfficeBuilding', 40, TRUE, TRUE, NOW(), NOW())
    RETURNING id INTO office_parent_id;
  END IF;
  
  -- 获取管理员角色ID
  SELECT id INTO admin_role_id FROM sys_role WHERE code = 'admin' LIMIT 1;
  
  -- 消息发送菜单
  INSERT INTO sys_menu (parent_id, name, type, route_path, component, permission, icon, sort, visible, status, created_at, updated_at)
  VALUES (office_parent_id, '消息发送', 'MENU', '/office/message', 'office/message/index', 'office:message:view', 'ChatDotRound', 3, TRUE, TRUE, NOW(), NOW())
  RETURNING id INTO message_menu_id;
  
  -- 消息发送权限
  INSERT INTO sys_menu (parent_id, name, type, permission, sort, visible, status, created_at, updated_at)
  VALUES
    (message_menu_id, '发送文本消息', 'BUTTON', 'office:message:sendText', 1, FALSE, TRUE, NOW(), NOW()),
    (message_menu_id, '发送图片消息', 'BUTTON', 'office:message:sendImage', 2, FALSE, TRUE, NOW(), NOW()),
    (message_menu_id, '发送文件消息', 'BUTTON', 'office:message:sendFile', 3, FALSE, TRUE, NOW(), NOW()),
    (message_menu_id, '发送卡片消息', 'BUTTON', 'office:message:sendCard', 4, FALSE, TRUE, NOW(), NOW()),
    (message_menu_id, '查询消息记录', 'BUTTON', 'office:message:query', 5, FALSE, TRUE, NOW(), NOW());
  
  -- 审批对接菜单
  INSERT INTO sys_menu (parent_id, name, type, route_path, component, permission, icon, sort, visible, status, created_at, updated_at)
  VALUES (office_parent_id, '审批对接', 'MENU', '/office/approval', 'office/approval/index', 'office:approval:view', 'Checked', 4, TRUE, TRUE, NOW(), NOW())
  RETURNING id INTO approval_menu_id;
  
  -- 审批对接权限
  INSERT INTO sys_menu (parent_id, name, type, permission, sort, visible, status, created_at, updated_at)
  VALUES
    (approval_menu_id, '发起审批', 'BUTTON', 'office:approval:initiate', 1, FALSE, TRUE, NOW(), NOW()),
    (approval_menu_id, '查询审批详情', 'BUTTON', 'office:approval:detail', 2, FALSE, TRUE, NOW(), NOW()),
    (approval_menu_id, '执行审批操作', 'BUTTON', 'office:approval:approve', 3, FALSE, TRUE, NOW(), NOW()),
    (approval_menu_id, '撤销审批', 'BUTTON', 'office:approval:cancel', 4, FALSE, TRUE, NOW(), NOW()),
    (approval_menu_id, '查询待审批列表', 'BUTTON', 'office:approval:pending', 5, FALSE, TRUE, NOW(), NOW()),
    (approval_menu_id, '查询我发起的审批', 'BUTTON', 'office:approval:initiated', 6, FALSE, TRUE, NOW(), NOW());
  
  -- 日历管理菜单
  INSERT INTO sys_menu (parent_id, name, type, route_path, component, permission, icon, sort, visible, status, created_at, updated_at)
  VALUES (office_parent_id, '日历管理', 'MENU', '/office/calendar', 'office/calendar/index', 'office:calendar:view', 'Calendar', 5, TRUE, TRUE, NOW(), NOW())
  RETURNING id INTO calendar_menu_id;
  
  -- 日历管理权限
  INSERT INTO sys_menu (parent_id, name, type, permission, sort, visible, status, created_at, updated_at)
  VALUES
    (calendar_menu_id, '创建日历事件', 'BUTTON', 'office:calendar:create', 1, FALSE, TRUE, NOW(), NOW()),
    (calendar_menu_id, '查询事件详情', 'BUTTON', 'office:calendar:detail', 2, FALSE, TRUE, NOW(), NOW()),
    (calendar_menu_id, '更新日历事件', 'BUTTON', 'office:calendar:update', 3, FALSE, TRUE, NOW(), NOW()),
    (calendar_menu_id, '删除日历事件', 'BUTTON', 'office:calendar:delete', 4, FALSE, TRUE, NOW(), NOW()),
    (calendar_menu_id, '查询我的事件', 'BUTTON', 'office:calendar:my', 5, FALSE, TRUE, NOW(), NOW()),
    (calendar_menu_id, '查询即将到来的事件', 'BUTTON', 'office:calendar:upcoming', 6, FALSE, TRUE, NOW(), NOW());
  
  -- 文档管理菜单
  INSERT INTO sys_menu (parent_id, name, type, route_path, component, permission, icon, sort, visible, status, created_at, updated_at)
  VALUES (office_parent_id, '文档管理', 'MENU', '/office/document', 'office/document/index', 'office:document:view', 'FolderOpened', 6, TRUE, TRUE, NOW(), NOW())
  RETURNING id INTO document_menu_id;
  
  -- 文档管理权限
  INSERT INTO sys_menu (parent_id, name, type, permission, sort, visible, status, created_at, updated_at)
  VALUES
    (document_menu_id, '创建文档', 'BUTTON', 'office:document:create', 1, FALSE, TRUE, NOW(), NOW()),
    (document_menu_id, '上传文件', 'BUTTON', 'office:document:upload', 2, FALSE, TRUE, NOW(), NOW()),
    (document_menu_id, '查询文档详情', 'BUTTON', 'office:document:detail', 3, FALSE, TRUE, NOW(), NOW()),
    (document_menu_id, '下载文档', 'BUTTON', 'office:document:download', 4, FALSE, TRUE, NOW(), NOW()),
    (document_menu_id, '删除文档', 'BUTTON', 'office:document:delete', 5, FALSE, TRUE, NOW(), NOW()),
    (document_menu_id, '移动文档', 'BUTTON', 'office:document:move', 6, FALSE, TRUE, NOW(), NOW()),
    (document_menu_id, '复制文档', 'BUTTON', 'office:document:copy', 7, FALSE, TRUE, NOW(), NOW()),
    (document_menu_id, '查询我的文档', 'BUTTON', 'office:document:my', 8, FALSE, TRUE, NOW(), NOW());
  
  -- 为管理员分配所有权限
  IF admin_role_id IS NOT NULL THEN
    INSERT INTO sys_role_menu (role_id, menu_id, created_at)
    SELECT admin_role_id, id, NOW()
    FROM sys_menu
    WHERE parent_id IN (office_parent_id, message_menu_id, approval_menu_id, calendar_menu_id, document_menu_id)
       OR id IN (message_menu_id, approval_menu_id, calendar_menu_id, document_menu_id);
  END IF;
  
END $$;
```

## 4. 使用说明

### 步骤1：集成API
将 `frontend-api-implementation.ts` 的内容添加到前端项目的 `src/api/office.ts` 文件中。

### 步骤2：创建页面组件
1. 创建目录 `/Users/work/apex-admin-web/src/views/office/message/`
2. 将 `frontend-message-send-page.vue` 保存为 `index.vue`
3. 创建目录 `/Users/work/apex-admin-web/src/views/office/approval/`
4. 将 `frontend-approval-page.vue` 保存为 `index.vue`

### 步骤3：配置路由
将上述路由配置添加到 `src/router/dynamic.ts` 的办公平台路由组中。

### 步骤4：执行菜单SQL
1. 连接到MySQL/PostgreSQL数据库
2. 根据数据库类型执行相应的SQL脚本
3. 验证菜单是否正确创建

### 步骤5：测试功能
1. 重启前端项目
2. 登录系统，在左侧菜单中查看新增的功能菜单
3. 测试消息发送和审批对接功能

## 5. 注意事项

1. **数据库表结构**：确保数据库中存在 `sys_menu`、`sys_role`、`sys_role_menu` 表
2. **字段类型**：根据实际表结构调整字段类型（如 `visible` 和 `status` 可能是 TINYINT 或 BOOLEAN）
3. **父菜单ID**：SQL中使用变量查找父菜单ID，如果数据库中没有"办公平台"菜单，会自动创建
4. **角色权限**：SQL默认为管理员角色分配权限，如需为其他角色分配，请修改 `@admin_role_id` 的查询条件
5. **图标**：Element Plus图标名称需要与前端项目实际使用的图标库匹配

## 6. 权限验证

在前端页面中使用 `v-permission` 指令验证权限：

```vue
<el-button v-permission="['office:message:sendText']">发送消息</el-button>
<el-button v-permission="['office:approval:approve']">审批通过</el-button>
```
