INSERT INTO auth_permission
    (id, tenant_id, code, name, resource, action, description, enabled, created_at, updated_at)
VALUES
    (29, 'default', 'mfa:unlock', '解锁MFA', 'mfa', 'manage', '管理员解锁用户多因素认证', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

INSERT INTO auth_role_permission (id, tenant_id, role_id, permission_id, created_at)
VALUES
    (6211, 'default', 1, 29, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
