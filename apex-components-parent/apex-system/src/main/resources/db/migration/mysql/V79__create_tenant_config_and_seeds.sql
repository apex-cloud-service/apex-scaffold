-- V79: 租户级配置与套餐/自助管理权限种子（统一认证收口 / 方案批次 B6）
-- 1) sys_tenant_config：租户级参数（tenant_id, config_key 唯一），随租户拦截器隔离（不进白名单）。
-- 2) 权限种子：套餐管理（tenant:package:*）、租户配置（tenant:config:*）、
--    租户自助管理（tenant:user:manage/tenant:role:manage/tenant:dept:manage，B8 消费）。
-- 权限 ID 使用 2605-2613 保留段（避开 V63 的 2601-2604 与 V50/V57 已用段）。
-- 幂等性：WHERE NOT EXISTS + INSERT IGNORE，可安全重放。

CREATE TABLE IF NOT EXISTS `sys_tenant_config` (
    `id`           BIGINT        NOT NULL COMMENT '主键ID（雪花算法）',
    `tenant_id`    VARCHAR(64)   NOT NULL COMMENT '租户编码',
    `config_key`   VARCHAR(100)  NOT NULL COMMENT '配置键',
    `config_value` VARCHAR(1000) DEFAULT NULL COMMENT '配置值',
    `remark`       VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`      INT           NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    `deleted`      INT           NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0-未删除，1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_config_key` (`tenant_id`, `config_key`),
    KEY `idx_tenant_config_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户级参数配置表（随租户行级隔离）';

-- ============ 权限种子 ============
INSERT IGNORE INTO sys_permission (id, name, code, description, type, status)
SELECT 2700, '套餐查询', 'tenant:package:read', '查询租户套餐列表与详情', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:package:read');

INSERT IGNORE INTO sys_permission (id, name, code, description, type, status)
SELECT 2701, '套餐创建', 'tenant:package:create', '创建租户套餐', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:package:create');

INSERT IGNORE INTO sys_permission (id, name, code, description, type, status)
SELECT 2702, '套餐更新', 'tenant:package:update', '更新套餐菜单/权限/能力裁剪集合', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:package:update');

INSERT IGNORE INTO sys_permission (id, name, code, description, type, status)
SELECT 2703, '租户配置查询', 'tenant:config:read', '查询租户级参数配置', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:config:read');

INSERT IGNORE INTO sys_permission (id, name, code, description, type, status)
SELECT 2704, '租户配置维护', 'tenant:config:update', '维护租户级参数配置', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:config:update');

INSERT IGNORE INTO sys_permission (id, name, code, description, type, status)
SELECT 2705, '租户用户自助管理', 'tenant:user:manage', '租户管理员管理本租户用户', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:user:manage');

INSERT IGNORE INTO sys_permission (id, name, code, description, type, status)
SELECT 2706, '租户角色自助管理', 'tenant:role:manage', '租户管理员管理本租户角色', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:role:manage');

INSERT IGNORE INTO sys_permission (id, name, code, description, type, status)
SELECT 2707, '租户部门自助管理', 'tenant:dept:manage', '租户管理员管理本租户部门', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'tenant:dept:manage');

-- 套餐管理/租户配置管理权限授予平台 ADMIN 角色（沿用 V63 的关联范式，role_id=1）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'ADMIN' AND r.deleted = 0
  AND p.code IN ('tenant:package:read','tenant:package:create','tenant:package:update',
                 'tenant:config:read','tenant:config:update');
