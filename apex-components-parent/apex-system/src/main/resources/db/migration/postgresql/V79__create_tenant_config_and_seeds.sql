-- V79: 租户级配置与套餐/自助管理权限种子（统一认证收口 / 方案批次 B6）
-- 语义与 MySQL 版本一致。

CREATE TABLE IF NOT EXISTS sys_tenant_config (
    id           BIGINT        NOT NULL PRIMARY KEY,
    tenant_id    VARCHAR(64)   NOT NULL,
    config_key   VARCHAR(100)  NOT NULL,
    config_value VARCHAR(1000) DEFAULT NULL,
    remark       VARCHAR(500)  DEFAULT NULL,
    create_time  TIMESTAMP     NOT NULL DEFAULT NOW(),
    update_time  TIMESTAMP     NOT NULL DEFAULT NOW(),
    version      INT           NOT NULL DEFAULT 0,
    deleted      INT           NOT NULL DEFAULT 0,
    CONSTRAINT uk_tenant_config_key UNIQUE (tenant_id, config_key)
);

CREATE INDEX IF NOT EXISTS idx_tenant_config_tenant ON sys_tenant_config (tenant_id);

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2700, '套餐查询', 'tenant:package:read', '查询租户套餐列表与详情', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:package:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2701, '套餐创建', 'tenant:package:create', '创建租户套餐', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:package:create');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2702, '套餐更新', 'tenant:package:update', '更新套餐菜单/权限/能力裁剪集合', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:package:update');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2703, '租户配置查询', 'tenant:config:read', '查询租户级参数配置', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:config:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2704, '租户配置维护', 'tenant:config:update', '维护租户级参数配置', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:config:update');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2705, '租户用户自助管理', 'tenant:user:manage', '租户管理员管理本租户用户', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:user:manage');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2706, '租户角色自助管理', 'tenant:role:manage', '租户管理员管理本租户角色', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:role:manage');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2707, '租户部门自助管理', 'tenant:dept:manage', '租户管理员管理本租户部门', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:dept:manage');

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 7900 + ROW_NUMBER() OVER (), r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'ADMIN' AND r.deleted = 0
  AND p.code IN ('tenant:package:read','tenant:package:create','tenant:package:update',
                 'tenant:config:read','tenant:config:update')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id);
