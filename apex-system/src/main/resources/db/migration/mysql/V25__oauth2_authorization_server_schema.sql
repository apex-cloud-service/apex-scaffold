-- ============================================================================
-- Flyway Migration: V25 - OAuth2 Authorization Server Schema
-- Description: 创建OAuth2授权服务器所需的数据表，支持标准OAuth2协议
-- Author: Apex Team
-- Date: 2026-09-08
-- Dependencies: Spring Authorization Server 1.4.0
-- ============================================================================

-- ============================================================================
-- 1. OAuth2 客户端注册表
-- ============================================================================
CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) NOT NULL COMMENT '客户端唯一标识(UUID)',
    client_id VARCHAR(100) NOT NULL COMMENT '客户端ID(对外暴露的标识)',
    client_id_issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '客户端ID签发时间',
    client_secret VARCHAR(200) COMMENT '客户端密钥(BCrypt加密,可为空表示公共客户端)',
    client_secret_expires_at TIMESTAMP NULL COMMENT '客户端密钥过期时间(NULL表示永不过期)',
    client_name VARCHAR(200) NOT NULL COMMENT '客户端名称(用于展示)',
    client_authentication_methods VARCHAR(1000) NOT NULL COMMENT '客户端认证方式(JSON数组): client_secret_basic, client_secret_post, none',
    authorization_grant_types VARCHAR(1000) NOT NULL COMMENT '授权类型(JSON数组): authorization_code, client_credentials, password, refresh_token',
    redirect_uris TEXT COMMENT '授权成功后重定向URI(JSON数组,授权码模式必填)',
    post_logout_redirect_uris TEXT COMMENT '登出后重定向URI(JSON数组)',
    scopes VARCHAR(1000) NOT NULL COMMENT '权限作用域(JSON数组): read, write, user:profile, admin:users等',
    client_settings TEXT NOT NULL COMMENT '客户端配置(JSON对象): require-proof-key, require-authorization-consent等',
    token_settings TEXT NOT NULL COMMENT 'Token配置(JSON对象): access-token-time-to-live, refresh-token-time-to-live等',
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_id (client_id),
    INDEX idx_client_name (client_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2客户端注册表';

-- ============================================================================
-- 2. OAuth2 授权记录表
-- ============================================================================
CREATE TABLE oauth2_authorization (
    id VARCHAR(100) NOT NULL COMMENT '授权记录唯一标识',
    registered_client_id VARCHAR(100) NOT NULL COMMENT '关联的客户端ID(外键)',
    principal_name VARCHAR(200) NOT NULL COMMENT '用户标识(OpenID或用户名)',
    authorization_grant_type VARCHAR(100) NOT NULL COMMENT '授权类型: authorization_code, client_credentials, password, refresh_token',
    authorized_scopes VARCHAR(1000) COMMENT '已授权的作用域(逗号分隔)',
    attributes TEXT COMMENT '授权属性(JSON对象,存储额外元数据)',
    state VARCHAR(500) COMMENT 'OAuth2 state参数(防CSRF)',
    
    -- 授权码相关字段
    authorization_code_value TEXT COMMENT '授权码值(SHA-256哈希后存储)',
    authorization_code_issued_at TIMESTAMP NULL COMMENT '授权码签发时间',
    authorization_code_expires_at TIMESTAMP NULL COMMENT '授权码过期时间(通常5分钟)',
    authorization_code_metadata TEXT COMMENT '授权码元数据(JSON)',
    
    -- 访问令牌相关字段
    access_token_value TEXT COMMENT '访问令牌值(JWT或Opaque Token)',
    access_token_issued_at TIMESTAMP NULL COMMENT '访问令牌签发时间',
    access_token_expires_at TIMESTAMP NULL COMMENT '访问令牌过期时间(通常2小时)',
    access_token_metadata TEXT COMMENT '访问令牌元数据(JSON)',
    access_token_type VARCHAR(100) COMMENT '访问令牌类型: Bearer',
    access_token_scopes VARCHAR(1000) COMMENT '访问令牌作用域',
    
    -- OIDC ID Token相关字段
    oidc_id_token_value TEXT COMMENT 'OIDC ID令牌值(JWT格式)',
    oidc_id_token_issued_at TIMESTAMP NULL COMMENT 'OIDC ID令牌签发时间',
    oidc_id_token_expires_at TIMESTAMP NULL COMMENT 'OIDC ID令牌过期时间',
    oidc_id_token_metadata TEXT COMMENT 'OIDC ID令牌元数据(JSON)',
    
    -- 刷新令牌相关字段
    refresh_token_value TEXT COMMENT '刷新令牌值',
    refresh_token_issued_at TIMESTAMP NULL COMMENT '刷新令牌签发时间',
    refresh_token_expires_at TIMESTAMP NULL COMMENT '刷新令牌过期时间(通常7天)',
    refresh_token_metadata TEXT COMMENT '刷新令牌元数据(JSON)',
    
    -- 设备授权流相关字段(Device Authorization Flow)
    user_code_value TEXT COMMENT '用户码值(8位短码,用户输入)',
    user_code_issued_at TIMESTAMP NULL COMMENT '用户码签发时间',
    user_code_expires_at TIMESTAMP NULL COMMENT '用户码过期时间',
    user_code_metadata TEXT COMMENT '用户码元数据(JSON)',
    
    device_code_value TEXT COMMENT '设备码值(设备轮询使用)',
    device_code_issued_at TIMESTAMP NULL COMMENT '设备码签发时间',
    device_code_expires_at TIMESTAMP NULL COMMENT '设备码过期时间',
    device_code_metadata TEXT COMMENT '设备码元数据(JSON)',
    
    PRIMARY KEY (id),
    INDEX idx_registered_client_id (registered_client_id),
    INDEX idx_principal_name (principal_name),
    INDEX idx_authorization_code_value (authorization_code_value(255)),
    INDEX idx_access_token_value (access_token_value(255)),
    INDEX idx_refresh_token_value (refresh_token_value(255)),
    INDEX idx_access_token_expires_at (access_token_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2授权记录表(存储授权码、Token等)';

-- ============================================================================
-- 3. OAuth2 授权确认表
-- ============================================================================
CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL COMMENT '客户端ID',
    principal_name VARCHAR(200) NOT NULL COMMENT '用户标识(OpenID)',
    authorities VARCHAR(1000) NOT NULL COMMENT '已授权的权限(逗号分隔的Scope列表)',
    PRIMARY KEY (registered_client_id, principal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2授权确认表(记录用户对客户端的授权同意)';

-- ============================================================================
-- 4. OAuth2 Scope权限映射表
-- ============================================================================
CREATE TABLE oauth2_scope_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    scope VARCHAR(100) NOT NULL COMMENT 'OAuth2作用域(如: read, user:profile)',
    permission_code VARCHAR(100) NOT NULL COMMENT '系统权限标识(如: SCOPE_READ)',
    description VARCHAR(500) COMMENT '权限描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_permission (scope, permission_code),
    INDEX idx_scope (scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2作用域权限映射表';

-- ============================================================================
-- 5. 初始化内置OAuth2客户端
-- ============================================================================

-- 5.1 生成内置客户端密钥的BCrypt哈希
-- apex-admin-web密钥: apex-admin-web-secret-2026 -> $2a$10$...
-- apex-m2m-service密钥: apex-m2m-secret-2026 -> $2a$10$...
-- 注意: 生产环境请使用强密钥并通过环境变量注入

-- 5.2 前端管理后台客户端(apex-admin-web)
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
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- 密钥: apex-admin-web-secret-2026
    'Apex Admin Web后台管理系统',
    '["client_secret_basic","client_secret_post"]',
    '["authorization_code","refresh_token"]',
    '["http://localhost:5173/oauth2/callback","http://localhost:3000/oauth2/callback","https://admin.apex.example.com/oauth2/callback"]',
    '["http://localhost:5173/","https://admin.apex.example.com/"]',
    '["read","write","user:profile","user:update","admin:users","admin:roles"]',
    '{"@class":"java.util.HashMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.HashMap","settings.token.authorization-code-time-to-live":["java.time.Duration",300],"settings.token.access-token-time-to-live":["java.time.Duration",7200],"settings.token.refresh-token-time-to-live":["java.time.Duration",604800],"settings.token.reuse-refresh-tokens":false}'
);

-- 5.3 服务间调用客户端(M2M: Machine-to-Machine)
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
    '$2a$10$8S.z5vl7f5xP3qhD6rZYGuEU5q6nPp0VmRFLLGPHN8MKq8VZ8xV3S', -- 密钥: apex-m2m-secret-2026
    'Apex M2M服务客户端(用于服务间调用)',
    '["client_secret_basic","client_secret_post"]',
    '["client_credentials"]',
    '["api:read","api:write"]',
    '{"@class":"java.util.HashMap","settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.HashMap","settings.token.access-token-time-to-live":["java.time.Duration",3600]}'
);

-- 5.4 移动应用客户端(公共客户端,使用PKCE)
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
-- 基础读写权限
('read', 'SCOPE_READ', '读取基本资源(只读访问)'),
('write', 'SCOPE_WRITE', '写入基本资源(创建、更新)'),

-- 用户相关权限
('user:profile', 'SCOPE_USER_PROFILE', '读取用户个人信息(昵称、头像、邮箱等)'),
('user:update', 'SCOPE_USER_UPDATE', '更新用户个人信息'),
('user:avatar', 'SCOPE_USER_AVATAR', '上传和修改用户头像'),

-- 管理员权限
('admin:users', 'SCOPE_ADMIN_USERS', '管理用户(查看、创建、更新、删除用户)'),
('admin:roles', 'SCOPE_ADMIN_ROLES', '管理角色权限(查看、分配角色)'),
('admin:system', 'SCOPE_ADMIN_SYSTEM', '系统管理(配置、字典、菜单等)'),

-- API访问权限(用于服务间调用)
('api:read', 'SCOPE_API_READ', 'API只读访问(所有GET接口)'),
('api:write', 'SCOPE_API_WRITE', 'API读写访问(所有POST/PUT/DELETE接口)'),

-- 文件操作权限
('file:upload', 'SCOPE_FILE_UPLOAD', '文件上传权限'),
('file:download', 'SCOPE_FILE_DOWNLOAD', '文件下载权限');

-- ============================================================================
-- 7. 为OAuth2客户端管理添加菜单权限
-- ============================================================================

-- 7.1 创建OAuth2客户端管理菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time)
VALUES
(300, 1000, 'OAuth2客户端', 1, '/system/oauth2-clients', '/system/oauth2-clients/index', 'system:oauth2client:list', 'key', 8, 1, 1, NOW(), NOW());

-- 7.2 创建OAuth2客户端管理按钮权限
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, sort, visible, status, create_time, update_time)
VALUES
(3001, 300, 'OAuth2客户端查询', 2, 'system:oauth2client:query', 1, 0, 1, NOW(), NOW()),
(3002, 300, 'OAuth2客户端新增', 2, 'system:oauth2client:add', 2, 0, 1, NOW(), NOW()),
(3003, 300, 'OAuth2客户端修改', 2, 'system:oauth2client:edit', 3, 0, 1, NOW(), NOW()),
(3004, 300, 'OAuth2客户端删除', 2, 'system:oauth2client:remove', 4, 0, 1, NOW(), NOW()),
(3005, 300, 'OAuth2客户端重置密钥', 2, 'system:oauth2client:reset', 5, 0, 1, NOW(), NOW());

-- 7.3 为超级管理员角色授予OAuth2客户端管理权限
INSERT INTO sys_permission (id, name, code, type, status)
SELECT 250000 + id, menu_name, perms, 1, 1 FROM sys_menu
WHERE id IN (300, 3001, 3002, 3003, 3004, 3005);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 2500000 + m.id, r.id, 250000 + m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.code = 'SUPER_ADMIN'
  AND m.id IN (300, 3001, 3002, 3003, 3004, 3005);

-- ============================================================================
-- 8. 创建OAuth2审计日志表(可选,用于审计)
-- ============================================================================
CREATE TABLE oauth2_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    client_id VARCHAR(100) NOT NULL COMMENT '客户端ID',
    principal_name VARCHAR(200) COMMENT '用户标识',
    event_type VARCHAR(50) NOT NULL COMMENT '事件类型: AUTHORIZATION, TOKEN_ISSUED, TOKEN_REVOKED, CLIENT_CREATED等',
    grant_type VARCHAR(50) COMMENT '授权类型',
    scopes VARCHAR(1000) COMMENT '请求的作用域',
    client_ip VARCHAR(50) COMMENT '客户端IP',
    user_agent VARCHAR(500) COMMENT 'User-Agent',
    success TINYINT(1) DEFAULT 1 COMMENT '是否成功: 1-成功, 0-失败',
    error_code VARCHAR(50) COMMENT '错误码',
    error_message VARCHAR(500) COMMENT '错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_client_id (client_id),
    INDEX idx_principal_name (principal_name),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2审计日志表';

-- ============================================================================
-- 9. 添加注释说明
-- ============================================================================

-- 客户端密钥生成命令(BCrypt):
-- echo -n 'your-secret-key' | htpasswd -bnBC 10 "" | tr -d ':\n' | sed 's/$2y/$2a/'

-- 授权码模式流程:
-- 1. 客户端引导用户访问 /oauth2/authorize?response_type=code&client_id=xxx&redirect_uri=xxx&scope=xxx
-- 2. 用户登录并同意授权
-- 3. 重定向到 redirect_uri?code=xxx
-- 4. 客户端使用授权码调用 /oauth2/token 换取access_token

-- 客户端模式流程:
-- 1. 客户端直接调用 /oauth2/token?grant_type=client_credentials&scope=xxx
-- 2. 返回access_token(无refresh_token)

-- Token刷新流程:
-- 1. 客户端调用 /oauth2/token?grant_type=refresh_token&refresh_token=xxx
-- 2. 返回新的access_token和refresh_token

-- ============================================================================
-- Migration Complete
-- ============================================================================
