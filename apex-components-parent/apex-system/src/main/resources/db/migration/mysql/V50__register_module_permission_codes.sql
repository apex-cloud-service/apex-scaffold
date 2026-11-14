-- ============================================================
-- V50: 登记认证配置/数据源管理/OAuth2客户端管理模块权限码（MySQL）
-- ============================================================
-- 脚本用途：将 Handler 层 @RequirePermission 实际使用的权限码登记到
--          sys_permission，并为超级管理员角色（ADMIN，role_id=1）授权，
--          消除"代码引用权限码未在种子登记"导致的 403 缺口。
-- 业务背景：AuthConfigHandler（JWT/会话/安全策略/权限同步）、
--          DatabaseConfigHandler（数据源管理）、OAuth2ClientManagementHandler
--          （OAuth2客户端 CRUD）共 19 个权限码此前未登记。
-- 前置依赖：V1-V49 已完成；sys_permission/sys_role_permission 表存在。
-- 影响范围：新增权限码与超管角色授权，不修改既有权限。
-- 幂等性：INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS（按 code 防重）+
--          授权 INSERT IGNORE，可重复执行。
-- MySQL 兼容性：使用 MySQL 8.0 语法，由 Flyway V50 顺序执行。
-- 数据风险：新增行不覆盖既有数据；权限 ID 使用 2501-2519 保留段，
--          显式 ID 便于后续菜单绑定引用。
-- 运维注意：脚本执行后，非超管角色如需访问相关功能，须在角色管理中手动授权。
-- 迁移元信息：版本 V50；执行阶段为管理模块权限治理阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 认证配置管理（AuthConfigHandler）— 9 个权限码
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2501, 'JWT配置查询', 'system:auth:config:jwt:query', '查询当前租户的 JWT 配置详情', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:auth:config:jwt:query');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2502, 'JWT配置更新', 'system:auth:config:jwt:edit', '更新当前租户的 JWT 签名算法与密钥配置', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:auth:config:jwt:edit');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2503, 'JWT配置测试', 'system:auth:config:jwt:test', '使用当前 JWT 配置生成测试 Token 并验证', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:auth:config:jwt:test');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2504, '会话配置查询', 'system:auth:config:session:query', '查询会话策略配置（并发数、超时等）', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:auth:config:session:query');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2505, '会话配置更新', 'system:auth:config:session:edit', '更新会话策略配置', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:auth:config:session:edit');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2506, '安全策略查询', 'system:auth:config:security:query', '查询登录安全策略配置（密码策略、防爆破等）', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:auth:config:security:query');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2507, '安全策略更新', 'system:auth:config:security:edit', '更新登录安全策略配置', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:auth:config:security:edit');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2508, '权限码同步', 'system:auth:config:permission:sync', '扫描代码中的 @RequirePermission 注解并与数据库权限码比对同步', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:auth:config:permission:sync');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2509, 'OAuth2客户端列表查询', 'system:oauth2:client:list', '在认证配置视图下查询已注册的 OAuth2 客户端列表', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:oauth2:client:list');

-- ============================================================
-- 2. 数据源管理（DatabaseConfigHandler）— 5 个权限码
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2510, '数据源列表查询', 'system:database:datasource:list', '分页查询当前租户注册的数据源配置列表', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:database:datasource:list');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2511, '数据源创建', 'system:database:datasource:create', '创建新的数据源配置（密码加密存储）', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:database:datasource:create');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2512, '数据源更新', 'system:database:datasource:update', '更新数据源连接与连接池配置并重建连接池', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:database:datasource:update');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2513, '数据源删除', 'system:database:datasource:delete', '删除数据源配置并关闭对应连接池', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:database:datasource:delete');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2514, '数据源监控指标查询', 'system:database:datasource:metrics', '查询数据源连接池运行时监控指标', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:database:datasource:metrics');

-- ============================================================
-- 3. OAuth2 客户端管理（OAuth2ClientManagementHandler）— 5 个权限码
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2515, 'OAuth2客户端创建', 'system:oauth2:client:create', '创建 OAuth2 客户端应用并生成 clientId/clientSecret', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:oauth2:client:create');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2516, 'OAuth2客户端更新', 'system:oauth2:client:update', '更新 OAuth2 客户端配置（回调地址、授权范围等）', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:oauth2:client:update');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2517, 'OAuth2客户端删除', 'system:oauth2:client:delete', '删除 OAuth2 客户端及其关联令牌', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:oauth2:client:delete');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2518, 'OAuth2客户端查询', 'system:oauth2:client:query', '查询 OAuth2 客户端详情与分页列表', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:oauth2:client:query');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2519, 'OAuth2客户端密钥重置', 'system:oauth2:client:secret:reset', '重置 OAuth2 客户端密钥（新密钥仅返回一次）', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:oauth2:client:secret:reset');

-- ============================================================
-- 4. 为超级管理员角色（ADMIN，role_id=1）授权全部新增权限码
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'system:auth:config:jwt:query',
    'system:auth:config:jwt:edit',
    'system:auth:config:jwt:test',
    'system:auth:config:session:query',
    'system:auth:config:session:edit',
    'system:auth:config:security:query',
    'system:auth:config:security:edit',
    'system:auth:config:permission:sync',
    'system:oauth2:client:list',
    'system:database:datasource:list',
    'system:database:datasource:create',
    'system:database:datasource:update',
    'system:database:datasource:delete',
    'system:database:datasource:metrics',
    'system:oauth2:client:create',
    'system:oauth2:client:update',
    'system:oauth2:client:delete',
    'system:oauth2:client:query',
    'system:oauth2:client:secret:reset'
)
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增权限码：
--    SELECT id, name, code FROM sys_permission WHERE id BETWEEN 2501 AND 2519 ORDER BY id;
-- 2. 验证超管授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE rp.role_id = 1 AND p.id BETWEEN 2501 AND 2519;
-- ============================================================
