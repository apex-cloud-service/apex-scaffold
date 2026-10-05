-- V49: 新增高风险操作授权权限（MySQL）
-- 变更内容：为高危操作（如解锁 MFA）新增权限编码 mfa:unlock，并绑定到默认管理员角色；
-- 使用 INSERT IGNORE 保证迁移幂等性，避免重复插入造成冲突。
INSERT IGNORE INTO auth_permission
    (id, tenant_id, code, name, resource, action, description, enabled, created_at, updated_at)
VALUES
    (29, 'default', 'mfa:unlock', '解锁MFA', 'mfa', 'manage', '管理员解锁用户多因素认证', 1, NOW(), NOW());

INSERT IGNORE INTO auth_role_permission (id, tenant_id, role_id, permission_id, created_at)
VALUES
    (6211, 'default', 1, 29, NOW());
