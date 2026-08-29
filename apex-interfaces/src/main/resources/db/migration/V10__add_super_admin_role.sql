-- 脚本用途：创建超级管理员扩展角色并授予内置管理员账号。
-- 业务背景：满足后台用户和角色管理接口对 SUPER_ADMIN 角色码的强制校验。
-- 前置依赖：V8 已创建种子管理员与基础管理员角色，目标角色及关联表结构可用。
-- 影响范围：写入或更新 sys_role，并向 sys_user_role 增加管理员角色关联。
-- 幂等性：使用 ON DUPLICATE KEY UPDATE 与 INSERT IGNORE，按固定主键可重复执行。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x 幂等 DML，由 Flyway V10 顺序执行。
-- 数据风险：会提升种子管理员权限；主键已被其他业务占用时可能掩盖错误关联。
-- 运维注意：执行后审计超级管理员名单、默认账号状态与最小权限策略。
-- 迁移元信息：版本 V10；执行阶段为后台高权限接口授权补齐阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
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
