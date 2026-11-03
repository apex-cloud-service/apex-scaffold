-- ============================================================
-- V86: 登记前端新增页面菜单（MySQL）
-- ============================================================
-- 脚本用途：为方案 §12 页面清单中已在前端实现（apex-admin-web）的页面补充
--          sys_menu 记录。前端路由由后端菜单驱动，缺菜单则页面不可达。
--          本次登记 9 条菜单 + 1 个目录：
--   1) 平台治理（70001）下：租户套餐（tenant-package）、开放应用（open-app）、
--      能力运营（capability）
--   2) 监控中心（1100）下：在线会话（session）
--   3) 办公平台（2000）下：统一通知（notify）
--   4) 新建「集成中心」目录（7210）下：连接器与凭据（connector）、
--      物联网设备（iot-device）、物联网规则（iot-rule）
-- 组件路径：必须逐字匹配前端 VIEW_REGISTRY：
--          system/tenant-package/index、system/open-app/index、
--          system/capability/index、monitor/session/index、office/notify/index、
--          integration/connector/index、iot/device/index、iot/device-rule/index。
-- 权限码：菜单 perms 仅作展示过滤；接口鉴权以 Handler 上 @RequirePermission 为准。
-- 前置依赖：V1-V85 已完成；sys_menu 表存在，上述父菜单 ID 已由 V73/V2 建立。
-- 影响范围：新增 1 个目录与 8 条菜单；不新增权限码（V79/V81/V82/V83/V85 已登记）。
-- 幂等性：INSERT ... ON DUPLICATE KEY UPDATE（按 id），可重复执行。
-- 菜单路径约定：目录用绝对路径（如 /integration），子菜单用相对段（如 connector），
--          由前端 resolvePath 与 Vue Router 共同拼接。
-- 数据风险：新增行不覆盖既有数据；菜单 ID 使用 7210-7218 空闲段
--          （7200-7202 已被 V80 使用）。
-- 迁移元信息：版本 V86；执行阶段为方案 §12 前端页面落地。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 平台治理：租户套餐 / 开放应用 / 能力运营
-- ============================================================
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7211, 70001, '租户套餐', 1, 'tenant-package', 'system/tenant-package/index', 'tenant:package:read', 'Files', 3, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '租户套餐', parent_id = 70001, menu_type = 1, path = 'tenant-package',
                        component = 'system/tenant-package/index', perms = 'tenant:package:read', icon = 'Files',
                        sort = 3, visible = 1, status = 1, deleted = 0;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7212, 70001, '开放应用', 1, 'open-app', 'system/open-app/index', 'open:app:read', 'Connection', 4, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '开放应用', parent_id = 70001, menu_type = 1, path = 'open-app',
                        component = 'system/open-app/index', perms = 'open:app:read', icon = 'Connection',
                        sort = 4, visible = 1, status = 1, deleted = 0;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7213, 70001, '能力运营', 1, 'capability', 'system/capability/index', 'tenant:read', 'Grid', 5, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '能力运营', parent_id = 70001, menu_type = 1, path = 'capability',
                        component = 'system/capability/index', perms = 'tenant:read', icon = 'Grid',
                        sort = 5, visible = 1, status = 1, deleted = 0;

-- ============================================================
-- 2. 监控中心：在线会话（A4 会话管理页）
-- ============================================================
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7214, 1100, '在线会话', 1, 'session', 'monitor/session/index', NULL, 'User', 30, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '在线会话', parent_id = 1100, menu_type = 1, path = 'session',
                        component = 'monitor/session/index', perms = NULL, icon = 'User',
                        sort = 30, visible = 1, status = 1, deleted = 0;

-- ============================================================
-- 3. 办公平台：统一通知（D1 模板与投递留痕）
-- ============================================================
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7215, 2000, '统一通知', 1, 'notify', 'office/notify/index', 'office:notify:template:read', 'Message', 20, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '统一通知', parent_id = 2000, menu_type = 1, path = 'notify',
                        component = 'office/notify/index', perms = 'office:notify:template:read', icon = 'Message',
                        sort = 20, visible = 1, status = 1, deleted = 0;

-- ============================================================
-- 4. 集成中心（新目录）：连接器 / 物联网设备 / 物联网规则
-- ============================================================
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7210, 0, '集成中心', 0, '/integration', NULL, NULL, 'Link', 22, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '集成中心', parent_id = 0, menu_type = 0, path = '/integration',
                        component = NULL, perms = NULL, icon = 'Link',
                        sort = 22, visible = 1, status = 1, deleted = 0;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7216, 7210, '连接器与凭据', 1, 'connector', 'integration/connector/index', 'integration:connector:read', 'SetUp', 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '连接器与凭据', parent_id = 7210, menu_type = 1, path = 'connector',
                        component = 'integration/connector/index', perms = 'integration:connector:read', icon = 'SetUp',
                        sort = 1, visible = 1, status = 1, deleted = 0;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7217, 7210, '物联网设备', 1, 'iot-device', 'iot/device/index', 'iot:device:read', 'Monitor', 2, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '物联网设备', parent_id = 7210, menu_type = 1, path = 'iot-device',
                        component = 'iot/device/index', perms = 'iot:device:read', icon = 'Monitor',
                        sort = 2, visible = 1, status = 1, deleted = 0;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7218, 7210, '物联网规则', 1, 'iot-rule', 'iot/device-rule/index', 'iot:rule:read', 'Operation', 3, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '物联网规则', parent_id = 7210, menu_type = 1, path = 'iot-rule',
                        component = 'iot/device-rule/index', perms = 'iot:rule:read', icon = 'Operation',
                        sort = 3, visible = 1, status = 1, deleted = 0;

-- ============================================================
-- 5. 授予平台 ADMIN 角色（与 V73/V80 一致：菜单可见性由权限码驱动）
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'ADMIN' AND r.deleted = 0
  AND p.code IN (
    'tenant:package:read', 'tenant:package:create', 'tenant:package:update',
    'open:app:read', 'open:app:manage',
    'capability:usage:read',
    'office:notify:template:read', 'office:notify:template:manage', 'office:notify:record:read',
    'integration:connector:read', 'integration:credential:read',
    'iot:device:read', 'iot:telemetry:read', 'iot:rule:read', 'iot:command:send'
  );

-- ============================================================
-- 6. 重算 platform-full 套餐（保持「全量套餐」语义不漂移）
-- ============================================================
UPDATE `sys_tenant_package`
SET `menu_ids` = (SELECT COALESCE(CAST(JSON_ARRAYAGG(`id`) AS JSON), JSON_ARRAY()) FROM `sys_menu` WHERE `deleted` = 0),
    `permission_codes` = (SELECT COALESCE(CAST(JSON_ARRAYAGG(`code`) AS JSON), JSON_ARRAY()) FROM `sys_permission` WHERE `deleted` = 0),
    `update_time` = NOW()
WHERE `package_code` = 'platform-full' AND `deleted` = 0;

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增菜单：
--    SELECT id, parent_id, menu_name, path, component FROM sys_menu
--    WHERE id BETWEEN 7210 AND 7218 ORDER BY id;
-- 2. 验证超管授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_role r ON r.id = rp.role_id AND r.code = 'ADMIN'
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE p.code IN ('iot:rule:read','iot:command:send','open:app:read');
-- ============================================================
