-- ============================================================
-- V81: 登记统一通知管理权限码（PostgreSQL）
-- ============================================================
-- 脚本用途：将 OfficeNotifyHandler 上 @RequirePermission 实际使用的
--          office:notify:template:read/manage 与 office:notify:record:read
--          权限码登记到 sys_permission，并为超级管理员角色（ADMIN，role_id=1）
--          授权，避免 /api/v2/office/notify 接口因权限码未登记而恒返回 403。
-- 业务背景：统一消息门面（批次 D1）提供通知模板维护与投递留痕查询；
--          模板只读、模板维护与留痕查询按权限分权；按模板投递由
--          内部契约接口 POST /internal/v1/notify 承载，不经网关、不设权限码。
-- 菜单说明：参照 V73 的菜单登记模式，前端「通知模板管理」页面尚未实现，
--          本版本只登记权限码不建菜单；待前端页面落地后补充 sys_menu
--          登记及菜单-权限关联。
-- 前置依赖：V1-V80 已完成；sys_permission/sys_role_permission 表存在。
-- 影响范围：新增 3 个权限码与超管角色授权，不修改既有权限。
-- 幂等性：INSERT ... SELECT ... WHERE NOT EXISTS（按 code 防重）+ 授权
--          ON CONFLICT DO NOTHING，可重复执行。
-- PostgreSQL/Flyway 兼容性：使用 PostgreSQL 9.5+ 语法，由 Flyway V81 顺序执行。
-- 数据风险：新增行不覆盖既有数据；权限 ID 使用 2750-2752 保留段
--          （2700-2707 已被 V79 使用，2710-2740 段暂无占用）。
-- 运维注意：脚本执行后，非超管角色如需使用通知模板管理，须在角色管理中
--          手动授权；查询、维护与留痕查询分别授予，保持最小权限。
-- 迁移元信息：版本 V81；执行阶段为统一消息门面（批次 D1）阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 统一通知管理（OfficeNotifyHandler）— 3 个权限码
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2750, '通知模板查询', 'office:notify:template:read', '查询通知模板详情与分页列表', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'office:notify:template:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2751, '通知模板维护', 'office:notify:template:manage', '创建、更新与删除通知模板', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'office:notify:template:manage');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2752, '通知留痕查询', 'office:notify:record:read', '分页查询统一通知投递留痕（成功与失败记录）', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'office:notify:record:read');

-- ============================================================
-- 2. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 500000 + p.id, 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'office:notify:template:read',
    'office:notify:template:manage',
    'office:notify:record:read'
)
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增权限码：
--    SELECT id, name, code FROM sys_permission WHERE id BETWEEN 2750 AND 2752 ORDER BY id;
-- 2. 验证超管授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE rp.role_id = 1 AND p.id BETWEEN 2750 AND 2752;
-- ============================================================
