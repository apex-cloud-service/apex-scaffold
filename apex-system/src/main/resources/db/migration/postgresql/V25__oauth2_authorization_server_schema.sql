-- ============================================================================
-- Flyway Migration: V25 - OAuth2 Authorization Server Schema (PostgreSQL)
-- Description: 创建OAuth2授权服务器所需的数据表，支持标准OAuth2协议
-- Author: Apex Team
-- Date: 2026-09-08
-- Dependencies: Spring Authorization Server 1.4.0
-- ============================================================================

-- ============================================================================
-- 1. OAuth2 客户端注册表
-- ============================================================================
CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    client_id_issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret VARCHAR(200),
    client_secret_expires_at TIMESTAMP,
    client_name VARCHAR(200) NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types VARCHAR(1000) NOT NULL,
    redirect_uris TEXT,
    post_logout_redirect_uris TEXT,
    scopes VARCHAR(1000) NOT NULL,
    client_settings TEXT NOT NULL,
    token_settings TEXT NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_client_id ON oauth2_registered_client(client_id);
CREATE INDEX idx_client_name ON oauth2_registered_client(client_name);

COMMENT ON TABLE oauth2_registered_client IS 'OAuth2客户端注册表';
COMMENT ON COLUMN oauth2_registered_client.id IS '客户端唯一标识(UUID)';
COMMENT ON COLUMN oauth2_registered_client.client_id IS '客户端ID(对外暴露的标识)';
COMMENT ON COLUMN oauth2_registered_client.client_secret IS '客户端密钥(BCrypt加密,可为空表示公共客户端)';
COMMENT ON COLUMN oauth2_registered_client.scopes IS '权限作用域(JSON数组)';

-- ============================================================================
-- 2. OAuth2 授权记录表
-- ============================================================================
CREATE TABLE oauth2_authorization (
    id VARCHAR(100) NOT NULL,
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes VARCHAR(1000),
    attributes TEXT,
    state VARCHAR(500),
    
    authorization_code_value TEXT,
    authorization_code_issued_at TIMESTAMP,
    authorization_code_expires_at TIMESTAMP,
    authorization_code_metadata TEXT,
    
    access_token_value TEXT,
    access_token_issued_at TIMESTAMP,
    access_token_expires_at TIMESTAMP,
    access_token_metadata TEXT,
    access_token_type VARCHAR(100),
    access_token_scopes VARCHAR(1000),
    
    oidc_id_token_value TEXT,
    oidc_id_token_issued_at TIMESTAMP,
    oidc_id_token_expires_at TIMESTAMP,
    oidc_id_token_metadata TEXT,
    
    refresh_token_value TEXT,
    refresh_token_issued_at TIMESTAMP,
    refresh_token_expires_at TIMESTAMP,
    refresh_token_metadata TEXT,
    
    user_code_value TEXT,
    user_code_issued_at TIMESTAMP,
    user_code_expires_at TIMESTAMP,
    user_code_metadata TEXT,
    
    device_code_value TEXT,
    device_code_issued_at TIMESTAMP,
    device_code_expires_at TIMESTAMP,
    device_code_metadata TEXT,
    
    PRIMARY KEY (id)
);

CREATE INDEX idx_registered_client_id ON oauth2_authorization(registered_client_id);
CREATE INDEX idx_principal_name ON oauth2_authorization(principal_name);
CREATE INDEX idx_authorization_code_value ON oauth2_authorization(authorization_code_value);
CREATE INDEX idx_access_token_value ON oauth2_authorization(access_token_value);
CREATE INDEX idx_refresh_token_value ON oauth2_authorization(refresh_token_value);
CREATE INDEX idx_access_token_expires_at ON oauth2_authorization(access_token_expires_at);

COMMENT ON TABLE oauth2_authorization IS 'OAuth2授权记录表(存储授权码、Token等)';

-- ============================================================================
-- 3. OAuth2 授权确认表
-- ============================================================================
CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorities VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

COMMENT ON TABLE oauth2_authorization_consent IS 'OAuth2授权确认表(记录用户对客户端的授权同意)';

-- ============================================================================
-- 4. OAuth2 Scope权限映射表
-- ============================================================================
CREATE TABLE oauth2_scope_permission (
    id BIGSERIAL NOT NULL,
    scope VARCHAR(100) NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_scope_permission ON oauth2_scope_permission(scope, permission_code);
CREATE INDEX idx_scope ON oauth2_scope_permission(scope);

COMMENT ON TABLE oauth2_scope_permission IS 'OAuth2作用域权限映射表';

-- ============================================================================
-- 5. 初始化内置OAuth2客户端
-- ============================================================================

INSERT INTO oauth2_registered_client (
    id, 
    client_id, 
    client_secret,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    post_logout_redirect_uris,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'apex-admin-web-client-001',
    'apex-admin-web',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Apex Admin Web后台管理系统',
    '["client_secret_basic","client_secret_post"]',
    '["authorization_code","refresh_token"]',
    '["http://localhost:5173/oauth2/callback","http://localhost:3000/oauth2/callback","https://admin.apex.example.com/oauth2/callback"]',
    '["http://localhost:5173/","https://admin.apex.example.com/"]',
    '["read","write","user:profile","user:update","admin:users","admin:roles"]',
    '{"@class":"java.util.HashMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.HashMap","settings.token.authorization-code-time-to-live":["java.time.Duration",300],"settings.token.access-token-time-to-live":["java.time.Duration",7200],"settings.token.refresh-token-time-to-live":["java.time.Duration",604800],"settings.token.reuse-refresh-tokens":false}'
);

INSERT INTO oauth2_registered_client (
    id, 
    client_id, 
    client_secret,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'apex-m2m-service-001',
    'apex-m2m-service',
    '$2a$10$8S.z5vl7f5xP3qhD6rZYGuEU5q6nPp0VmRFLLGPHN8MKq8VZ8xV3S',
    'Apex M2M服务客户端(用于服务间调用)',
    '["client_secret_basic","client_secret_post"]',
    '["client_credentials"]',
    '["api:read","api:write"]',
    '{"@class":"java.util.HashMap","settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.HashMap","settings.token.access-token-time-to-live":["java.time.Duration",3600]}'
);

INSERT INTO oauth2_registered_client (
    id, 
    client_id, 
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'apex-mobile-app-001',
    'apex-mobile-app',
    'Apex移动应用(公共客户端)',
    '["none"]',
    '["authorization_code","refresh_token"]',
    '["com.apex.app://oauth2/callback"]',
    '["read","write","user:profile"]',
    '{"@class":"java.util.HashMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":true}',
    '{"@class":"java.util.HashMap","settings.token.authorization-code-time-to-live":["java.time.Duration",300],"settings.token.access-token-time-to-live":["java.time.Duration",3600],"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000],"settings.token.reuse-refresh-tokens":false}'
);

-- ============================================================================
-- 6. 初始化标准OAuth2 Scope权限映射
-- ============================================================================
INSERT INTO oauth2_scope_permission (scope, permission_code, description) VALUES
('read', 'SCOPE_READ', '读取基本资源(只读访问)'),
('write', 'SCOPE_WRITE', '写入基本资源(创建、更新)'),
('user:profile', 'SCOPE_USER_PROFILE', '读取用户个人信息(昵称、头像、邮箱等)'),
('user:update', 'SCOPE_USER_UPDATE', '更新用户个人信息'),
('user:avatar', 'SCOPE_USER_AVATAR', '上传和修改用户头像'),
('admin:users', 'SCOPE_ADMIN_USERS', '管理用户(查看、创建、更新、删除用户)'),
('admin:roles', 'SCOPE_ADMIN_ROLES', '管理角色权限(查看、分配角色)'),
('admin:system', 'SCOPE_ADMIN_SYSTEM', '系统管理(配置、字典、菜单等)'),
('api:read', 'SCOPE_API_READ', 'API只读访问(所有GET接口)'),
('api:write', 'SCOPE_API_WRITE', 'API读写访问(所有POST/PUT/DELETE接口)'),
('file:upload', 'SCOPE_FILE_UPLOAD', '文件上传权限'),
('file:download', 'SCOPE_FILE_DOWNLOAD', '文件下载权限');

-- ============================================================================
-- 7. 为OAuth2客户端管理添加菜单权限
-- ============================================================================

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, created_by, created_at, updated_at)
VALUES 
(300, 1, 'OAuth2客户端', 1, '/system/oauth2-clients', '/system/oauth2-clients/index', 'system:oauth2client:list', 'key', 8, 1, 1, 'system', NOW(), NOW());

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, sort_order, visible, status, created_by, created_at, updated_at)
VALUES 
(3001, 300, 'OAuth2客户端查询', 2, 'system:oauth2client:query', 1, 0, 1, 'system', NOW(), NOW()),
(3002, 300, 'OAuth2客户端新增', 2, 'system:oauth2client:add', 2, 0, 1, 'system', NOW(), NOW()),
(3003, 300, 'OAuth2客户端修改', 2, 'system:oauth2client:edit', 3, 0, 1, 'system', NOW(), NOW()),
(3004, 300, 'OAuth2客户端删除', 2, 'system:oauth2client:remove', 4, 0, 1, 'system', NOW(), NOW()),
(3005, 300, 'OAuth2客户端重置密钥', 2, 'system:oauth2client:reset', 5, 0, 1, 'system', NOW(), NOW());

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id 
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_key = 'SUPER_ADMIN' 
  AND m.id IN (300, 3001, 3002, 3003, 3004, 3005);

-- ============================================================================
-- 8. 创建OAuth2审计日志表
-- ============================================================================
CREATE TABLE oauth2_audit_log (
    id BIGSERIAL NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200),
    event_type VARCHAR(50) NOT NULL,
    grant_type VARCHAR(50),
    scopes VARCHAR(1000),
    client_ip VARCHAR(50),
    user_agent VARCHAR(500),
    success BOOLEAN DEFAULT TRUE,
    error_code VARCHAR(50),
    error_message VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_oauth2_audit_client_id ON oauth2_audit_log(client_id);
CREATE INDEX idx_oauth2_audit_principal_name ON oauth2_audit_log(principal_name);
CREATE INDEX idx_oauth2_audit_event_type ON oauth2_audit_log(event_type);
CREATE INDEX idx_oauth2_audit_created_at ON oauth2_audit_log(created_at);

COMMENT ON TABLE oauth2_audit_log IS 'OAuth2审计日志表';

-- ============================================================================
-- Migration Complete
-- ============================================================================
