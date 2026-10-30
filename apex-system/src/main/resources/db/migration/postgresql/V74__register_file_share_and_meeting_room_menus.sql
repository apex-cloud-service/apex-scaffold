-- ============================================================
-- V74: 登记文件分享与会议室菜单及超管授权（PostgreSQL）
-- ============================================================
-- 脚本用途：为前端已实现但缺少后端菜单记录的两个办公/数据页面补充 sys_menu：
--   1) 文件分享（/api/v2/files/{fileId}/shares、/api/v2/file-shares/**）
--      — 创建/撤销需登录，exchange/download 为匿名 token 接口，
--        后端无 @RequirePermission 注解，故不登记 sys_permission 权限码；
--   2) 会议室（/api/v2/office/meeting-rooms/**）
--      — 使用 office:meeting-room:read/manage/book，权限码已由 V66 登记，
--        本脚本仅补菜单与其超管授权。
-- 前置依赖：V1-V73 已完成；sys_menu/sys_permission/sys_role_permission 表存在。
-- 影响范围：新增 2 条菜单；仅新增超管授权，无新权限码。
-- 幂等性：菜单 INSERT ... ON CONFLICT (id) DO UPDATE；授权 ON CONFLICT DO NOTHING。
-- 菜单路径约定：目录用绝对路径（/data、/office），子菜单用相对段
--          （file-share、meeting-room），由前端 resolvePath 与 Vue Router 拼接。
-- 组件路径：必须逐字匹配前端 VIEW_REGISTRY：
--          data/file-share/index、office/meeting-room/index。
-- 数据风险：新增行不覆盖既有数据；菜单 ID 使用 70007-70008 保留段。
-- 迁移元信息：版本 V74；执行阶段为办公协作能力补全阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 菜单记录（2 条）
-- ============================================================

-- 1.1 文件分享（数据管理 /data → 文件分享）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (70007, 2000, '文件分享', 1, 'file-share', 'data/file-share/index', NULL, 'Share', 10, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON CONFLICT (id) DO UPDATE SET
    menu_name = '文件分享', parent_id = 2000, menu_type = 1, path = 'file-share',
    component = 'data/file-share/index', perms = NULL, icon = 'Share',
    sort = 10, visible = 1, status = 1, deleted = 0, update_time = CURRENT_TIMESTAMP;

-- 1.2 会议室（Office管理 /office → 会议室）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (70008, 3000, '会议室', 1, 'meeting-room', 'office/meeting-room/index', 'office:meeting-room:read', 'Calendar', 10, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON CONFLICT (id) DO UPDATE SET
    menu_name = '会议室', parent_id = 3000, menu_type = 1, path = 'meeting-room',
    component = 'office/meeting-room/index', perms = 'office:meeting-room:read',
    icon = 'Calendar', sort = 10, visible = 1, status = 1, deleted = 0, update_time = CURRENT_TIMESTAMP;

-- ============================================================
-- 2. 为超级管理员角色授权（会议室权限码由 V66 登记，此处补齐授权）
-- ============================================================
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 500000 + p.id, 1, p.id
FROM sys_permission p
WHERE p.code IN ('office:meeting-room:read', 'office:meeting-room:manage', 'office:meeting-room:book')
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN')
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 3600000 + r.id * 100000 + p.id, r.id, p.id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.code = 'SUPER_ADMIN'
  AND p.code IN ('office:meeting-room:read', 'office:meeting-room:manage', 'office:meeting-room:book')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增菜单：
--    SELECT id, parent_id, menu_name, path, component FROM sys_menu
--    WHERE id IN (70007, 70008);
-- 2. 验证超管会议室授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE rp.role_id = 1 AND p.code LIKE 'office:meeting-room:%';
-- 3. 前端验证：侧边栏出现「数据管理 → 文件分享」「Office管理 → 会议室」。
-- ============================================================
