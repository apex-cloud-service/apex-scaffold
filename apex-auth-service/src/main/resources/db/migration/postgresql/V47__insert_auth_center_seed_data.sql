-- V47: 认证中心种子数据
-- 包含：管理员角色、基础权限、角色权限关联

-- 插入管理员角色
INSERT INTO auth_role (id, tenant_id, code, name, description, data_scope, enabled, created_at, updated_at)
VALUES
    (1, 'default', 'ROLE_ADMIN', '系统管理员', '系统管理员角色，拥有所有权限', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'default', 'ROLE_USER', '普通用户', '普通用户角色，拥有基础权限', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'default', 'ROLE_GUEST', '访客', '访客角色，只读权限', 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- 插入基础权限
INSERT INTO auth_permission (id, tenant_id, code, name, resource, action, description, enabled, created_at, updated_at)
VALUES
    -- 用户管理权限
    (1, 'default', 'user:read', '查看用户', 'user', 'read', '查看用户信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'default', 'user:create', '创建用户', 'user', 'create', '创建新用户', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'default', 'user:update', '更新用户', 'user', 'update', '更新用户信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4, 'default', 'user:delete', '删除用户', 'user', 'delete', '删除用户', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    
    -- 角色管理权限
    (5, 'default', 'role:read', '查看角色', 'role', 'read', '查看角色信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (6, 'default', 'role:create', '创建角色', 'role', 'create', '创建新角色', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (7, 'default', 'role:update', '更新角色', 'role', 'update', '更新角色信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (8, 'default', 'role:delete', '删除角色', 'role', 'delete', '删除角色', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (9, 'default', 'role:assign', '分配权限', 'role', 'assign', '为角色分配权限', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    
    -- 权限管理权限
    (10, 'default', 'permission:read', '查看权限', 'permission', 'read', '查看权限信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (11, 'default', 'permission:create', '创建权限', 'permission', 'create', '创建新权限', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12, 'default', 'permission:update', '更新权限', 'permission', 'update', '更新权限信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (13, 'default', 'permission:delete', '删除权限', 'permission', 'delete', '删除权限', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    
    -- 会话管理权限
    (14, 'default', 'session:read', '查看会话', 'session', 'read', '查看会话信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (15, 'default', 'session:terminate', '终止会话', 'session', 'terminate', '强制终止用户会话', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (16, 'default', 'session:statistics', '会话统计', 'session', 'statistics', '查看在线统计信息', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    
    -- 审计日志权限
    (17, 'default', 'audit:read', '查看审计日志', 'audit', 'read', '查看审计日志', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (18, 'default', 'audit:export', '导出审计日志', 'audit', 'export', '导出审计日志', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    
    -- 协议配置权限
    (19, 'default', 'protocol:read', '查看协议配置', 'protocol', 'read', '查看认证协议配置', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (20, 'default', 'protocol:update', '更新协议配置', 'protocol', 'update', '更新认证协议配置', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- 为管理员角色分配所有权限
INSERT INTO auth_role_permission (id, tenant_id, role_id, permission_id, created_at)
SELECT 6100 + id, 'default', 1, id, CURRENT_TIMESTAMP
FROM auth_permission
WHERE tenant_id = 'default'
ON CONFLICT DO NOTHING;

-- 为普通用户角色分配基础权限
INSERT INTO auth_role_permission (id, tenant_id, role_id, permission_id, created_at)
VALUES
    (6121, 'default', 2, 1, CURRENT_TIMESTAMP),  -- user:read
    (6122, 'default', 2, 5, CURRENT_TIMESTAMP),  -- role:read
    (6123, 'default', 2, 10, CURRENT_TIMESTAMP), -- permission:read
    (6124, 'default', 2, 14, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING; -- session:read

-- 为访客角色分配只读权限
INSERT INTO auth_role_permission (id, tenant_id, role_id, permission_id, created_at)
VALUES
    (6131, 'default', 3, 1, CURRENT_TIMESTAMP),  -- user:read
    (6132, 'default', 3, 5, CURRENT_TIMESTAMP),  -- role:read
    (6133, 'default', 3, 10, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING; -- permission:read

-- 插入默认测试用户（密码为 admin123 的 BCrypt 哈希值）
INSERT INTO auth_user (id, tenant_id, username, email, password_hash, status, created_at, updated_at, deleted)
VALUES
    (1001, 'default', 'admin', 'admin@apex.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1002, 'default', 'user', 'user@apex.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT DO NOTHING;

-- 为测试用户分配角色
INSERT INTO auth_user_role (id, tenant_id, user_id, role_id, created_at)
VALUES
    (6201, 'default', 1001, 1, CURRENT_TIMESTAMP), -- admin用户分配管理员角色
    (6202, 'default', 1002, 2, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING; -- user用户分配普通用户角色

-- 插入默认OAuth2协议配置
INSERT INTO auth_protocol_config (id, tenant_id, protocol_type, protocol_name, config_json, enabled, priority, created_at, updated_at, version)
VALUES
    (7001, 'default', 'JWT', 'JWT认证', '{"algorithm":"HS256","issuer":"apex-auth-center","accessTokenExpiry":7200,"refreshTokenExpiry":604800}', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (7002, 'default', 'OAUTH2', 'OAuth2认证', '{"authorizeEndpoint":"/oauth2/authorize","tokenEndpoint":"/oauth2/token","grantTypes":["authorization_code","refresh_token"]}', 1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT DO NOTHING;
