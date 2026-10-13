-- V51: 新增高风险操作授权权限（PostgreSQL）
-- 作者: Apex Scaffold Team
-- 说明: 为高危操作（如解锁 MFA）新增权限编码 mfa:unlock，并绑定到默认管理员角色。
--       本脚本原编号为 V49，与 V49__create_auth_config_table.sql 版本号重复，
--       导致 Flyway 版本冲突；且 mfa:unlock 权限依赖 V50 的 MFA 配置表，
--       故重编号为 V51（迁移方案 Phase 2.2 Flyway 版本链治理）。
-- 幂等性: 使用 ON CONFLICT DO NOTHING 保证迁移可重复执行，避免重复插入造成冲突。
INSERT INTO auth_permission
    (id, tenant_id, code, name, resource, action, description, enabled, created_at, updated_at)
VALUES
    (29, 'default', 'mfa:unlock', '解锁MFA', 'mfa', 'manage', '管理员解锁用户多因素认证', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO auth_role_permission (id, tenant_id, role_id, permission_id, created_at)
VALUES
    (6211, 'default', 1, 29, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
