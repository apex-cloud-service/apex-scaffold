-- ============================================================
-- V10: 为种子管理员授予 SUPER_ADMIN 扩展角色
-- ============================================================
-- 背景：后台用户/角色管理等接口（AdminUserManagementService.assertSuperAdmin）
--   硬校验操作者具备 SUPER_ADMIN 角色码；V8 仅授予了 ADMIN 角色，
--   导致 /admin-api/v1/users、/admin-api/v1/staff-roles 返回 403。
-- 本脚本补充 SUPER_ADMIN 角色并关联到种子管理员，幂等可重复执行。
-- ============================================================

-- 1. 扩展授权角色：SUPER_ADMIN
INSERT INTO sys_role (id, name, code, description, status)
VALUES (2, '超级管理员', 'SUPER_ADMIN', '系统内置超级管理员扩展授权角色（后台管理接口硬校验）', 1)
ON DUPLICATE KEY UPDATE name = '超级管理员', status = 1;

-- 2. 关联到种子管理员（sys_user_role 无唯一键，用主键幂等插入）
INSERT IGNORE INTO sys_user_role (id, user_id, role_id) VALUES (2, 1, 2);
