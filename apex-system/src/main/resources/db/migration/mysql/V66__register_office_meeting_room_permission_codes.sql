-- ============================================================
-- V66: 登记办公会议室资源权限码（MySQL）
-- ============================================================
-- 脚本用途：将 OfficeMeetingRoomHandler 上 @RequirePermission 实际使用的
--          office:meeting-room:read/manage/book 权限码登记到 sys_permission，
--          并为超级管理员角色（ADMIN，role_id=1）授权，避免
--          /api/v2/office/meeting-rooms 接口因权限码未登记而恒返回 403。
-- 业务背景：O06b 会议室资源管理提供会议室增删改查、可用性查询与预订/取消；
--          主数据维护、只读查询与预订按权限分权。
-- 前置依赖：V1-V65 已完成；sys_permission/sys_role_permission 表存在。
-- 影响范围：新增 3 个权限码与超管角色授权，不修改既有权限。
-- 幂等性：INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS（按 code 防重）+
--          授权 INSERT IGNORE，可重复执行。
-- MySQL 兼容性：使用 MySQL 8.0 语法，由 Flyway V66 顺序执行。
-- 数据风险：新增行不覆盖既有数据；权限 ID 使用 2611-2613 保留段
--          （2601-2610 已被 V63/V64/V65 使用）。
-- 运维注意：脚本执行后，非超管角色如需使用会议室管理，须在角色管理中手动授权；
--          只读、维护与预订分别授予，保持最小权限。
-- 迁移元信息：版本 V66；执行阶段为办公会议室资源管理阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 会议室资源（OfficeMeetingRoomHandler）— 3 个权限码
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2611, '会议室查询', 'office:meeting-room:read', '查询会议室详情、分页列表、启用会议室与时段可用性', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'office:meeting-room:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2612, '会议室维护', 'office:meeting-room:manage', '创建、更新与删除会议室资源', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'office:meeting-room:manage');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2613, '会议室预订', 'office:meeting-room:book', '预订会议室与取消本人预订', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'office:meeting-room:book');

-- ============================================================
-- 2. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'office:meeting-room:read',
    'office:meeting-room:manage',
    'office:meeting-room:book'
)
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增权限码：
--    SELECT id, name, code FROM sys_permission WHERE id BETWEEN 2611 AND 2613 ORDER BY id;
-- 2. 验证超管授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE rp.role_id = 1 AND p.id BETWEEN 2611 AND 2613;
-- ============================================================
