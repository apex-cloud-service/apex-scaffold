-- 认证中心测试用户种子数据
-- 用于开发和测试环境初始化（PostgreSQL版本）

-- 插入测试用户
-- 密码: password123 (BCrypt 加密后)
INSERT INTO auth_user (id, tenant_id, username, email, phone, password_hash, open_id, status, 
                       last_login_at, last_login_ip, failed_login_attempts, locked_until, 
                       created_at, updated_at, deleted)
VALUES 
-- 管理员用户
(1001, 'default', 'admin', 'admin@example.com', '13800138000', 
 '$2a$10$X5wFuNVXz.wFvzMWXl9VcO4KZkm8E.WKLWqJbx6LqD6YPnFDJJ1Ya', 
 'admin-open-id', 1, NULL, NULL, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- 普通用户
(1002, 'default', 'user', 'user@example.com', '13800138001', 
 '$2a$10$X5wFuNVXz.wFvzMWXl9VcO4KZkm8E.WKLWqJbx6LqD6YPnFDJJ1Ya', 
 'user-open-id', 1, NULL, NULL, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),

-- 测试用户（不同租户）
(1003, 'tenant1', 'test', 'test@example.com', '13800138002', 
 '$2a$10$X5wFuNVXz.wFvzMWXl9VcO4KZkm8E.WKLWqJbx6LqD6YPnFDJJ1Ya', 
 'test-open-id', 1, NULL, NULL, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- 插入角色
INSERT INTO auth_role (id, tenant_id, code, name, description, data_scope, enabled, created_at, updated_at, deleted)
VALUES 
(2001, 'default', 'ADMIN', '管理员', '系统管理员角色', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(2002, 'default', 'USER', '普通用户', '普通用户角色', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- 插入权限
INSERT INTO auth_permission (id, tenant_id, code, name, resource, action, description, enabled, created_at, updated_at, deleted)
VALUES 
(3001, 'default', 'user:read', '查询用户', 'user', 'read', '查询用户信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(3002, 'default', 'user:write', '修改用户', 'user', 'write', '修改用户信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
(3003, 'default', 'system:admin', '系统管理', 'system', 'admin', '系统管理权限', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- 分配用户角色
INSERT INTO auth_user_role (id, tenant_id, user_id, role_id, created_at)
SELECT 4001, 'default', u.id, r.id, NOW()
FROM auth_user u
CROSS JOIN auth_role r
WHERE u.username = 'admin' AND u.tenant_id = 'default'
  AND r.code = 'ADMIN' AND r.tenant_id = 'default';

INSERT INTO auth_user_role (id, tenant_id, user_id, role_id, created_at)
SELECT 4002, 'default', u.id, r.id, NOW()
FROM auth_user u
CROSS JOIN auth_role r
WHERE u.username = 'user' AND u.tenant_id = 'default'
  AND r.code = 'USER' AND r.tenant_id = 'default';

-- 分配角色权限
INSERT INTO auth_role_permission (id, tenant_id, role_id, permission_id, created_at)
SELECT 2000 + p.id, 'default', r.id, p.id, NOW()
FROM auth_role r
CROSS JOIN auth_permission p
WHERE r.code = 'ADMIN' AND r.tenant_id = 'default'
  AND p.tenant_id = 'default' AND p.id IN (3001, 3002, 3003);

INSERT INTO auth_role_permission (id, tenant_id, role_id, permission_id, created_at)
SELECT 5004, 'default', r.id, p.id, NOW()
FROM auth_role r
CROSS JOIN auth_permission p
WHERE r.code = 'USER' AND r.tenant_id = 'default'
  AND p.code = 'user:read' AND p.tenant_id = 'default' AND p.id IN (3001, 3002, 3003);
