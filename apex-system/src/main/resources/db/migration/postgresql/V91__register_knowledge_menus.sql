-- ============================================================
-- V91: 登记知识库菜单与权限（PostgreSQL）
-- ============================================================
-- 脚本用途：为知识库（apex-knowledge-service）管理后台页面补充
--          sys_permission 与 sys_menu 记录。前端路由由后端菜单驱动，
--          缺菜单则页面不可达；接口鉴权以 Handler 上 @RequirePermission 为准。
--          本次登记 1 个目录 + 4 条菜单 + 10 个权限码（与 MySQL 版本语义一致）。
-- 组件路径：必须逐字匹配前端 VIEW_REGISTRY 键（knowledge/* / index）。
-- 前置依赖：V1-V90 已完成；sys_menu/sys_permission/sys_role_permission/sys_tenant_package 存在。
-- 影响范围：新增 1 目录、4 菜单、10 权限码 + 超管授权 + platform-full 套餐重算。
-- 幂等性：权限按 code 防重（INSERT ... WHERE NOT EXISTS）、菜单按 id
--          ON CONFLICT (id) DO UPDATE、授权 ON CONFLICT DO NOTHING、套餐重算为 UPDATE。
-- 菜单路径约定：目录用绝对路径 /knowledge，子菜单用相对段（space/document/search/chat）。
-- 数据风险：新增行不覆盖既有数据；权限 ID 使用 2900-2909 空闲段；菜单 ID 使用
--          7220-7224 空闲段；授权主键沿用 500000 + p.id 公式。
-- 迁移元信息：版本 V91；执行阶段为知识库功能落地。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 权限码登记（按 code 防重）
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2900, '知识空间查询', 'kb:space:read', '知识库空间列表与详情查询', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'kb:space:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2901, '知识空间创建', 'kb:space:create', '知识库空间创建', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'kb:space:create');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2902, '知识空间更新', 'kb:space:update', '知识库空间更新', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'kb:space:update');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2903, '知识空间删除', 'kb:space:delete', '知识库空间删除', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'kb:space:delete');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2904, '知识文档查询', 'kb:doc:read', '知识文档列表、详情与分块预览查询', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'kb:doc:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2905, '知识文档上传', 'kb:doc:upload', '知识文档上传与在线录入', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'kb:doc:upload');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2906, '知识文档删除', 'kb:doc:delete', '知识文档删除', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'kb:doc:delete');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2907, '知识文档重建索引', 'kb:doc:reindex', '知识文档解析失败后手动重建索引', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'kb:doc:reindex');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2908, '知识检索', 'kb:search:query', '知识库全文/语义检索', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'kb:search:query');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2909, '知识问答', 'kb:chat:ask', '基于知识库的智能问答', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'kb:chat:ask');

-- ============================================================
-- 2. 菜单登记（知识库目录 + 4 个页面）
-- ============================================================
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7220, 0, '知识库', 0, '/knowledge', NULL, NULL, 'Collection', 23, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON CONFLICT (id) DO UPDATE SET menu_name = EXCLUDED.menu_name, parent_id = EXCLUDED.parent_id,
    menu_type = EXCLUDED.menu_type, path = EXCLUDED.path, component = EXCLUDED.component,
    perms = EXCLUDED.perms, icon = EXCLUDED.icon, sort = EXCLUDED.sort,
    visible = EXCLUDED.visible, status = EXCLUDED.status, deleted = 0;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7221, 7220, '知识空间', 1, 'space', 'knowledge/space/index', 'kb:space:read', 'FolderOpened', 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON CONFLICT (id) DO UPDATE SET menu_name = EXCLUDED.menu_name, parent_id = EXCLUDED.parent_id,
    menu_type = EXCLUDED.menu_type, path = EXCLUDED.path, component = EXCLUDED.component,
    perms = EXCLUDED.perms, icon = EXCLUDED.icon, sort = EXCLUDED.sort,
    visible = EXCLUDED.visible, status = EXCLUDED.status, deleted = 0;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7222, 7220, '文档管理', 1, 'document', 'knowledge/document/index', 'kb:doc:read', 'Document', 2, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON CONFLICT (id) DO UPDATE SET menu_name = EXCLUDED.menu_name, parent_id = EXCLUDED.parent_id,
    menu_type = EXCLUDED.menu_type, path = EXCLUDED.path, component = EXCLUDED.component,
    perms = EXCLUDED.perms, icon = EXCLUDED.icon, sort = EXCLUDED.sort,
    visible = EXCLUDED.visible, status = EXCLUDED.status, deleted = 0;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7223, 7220, '知识检索', 1, 'search', 'knowledge/search/index', 'kb:search:query', 'Search', 3, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON CONFLICT (id) DO UPDATE SET menu_name = EXCLUDED.menu_name, parent_id = EXCLUDED.parent_id,
    menu_type = EXCLUDED.menu_type, path = EXCLUDED.path, component = EXCLUDED.component,
    perms = EXCLUDED.perms, icon = EXCLUDED.icon, sort = EXCLUDED.sort,
    visible = EXCLUDED.visible, status = EXCLUDED.status, deleted = 0;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted)
VALUES (7224, 7220, '智能问答', 1, 'chat', 'knowledge/chat/index', 'kb:chat:ask', 'ChatDotRound', 4, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0)
ON CONFLICT (id) DO UPDATE SET menu_name = EXCLUDED.menu_name, parent_id = EXCLUDED.parent_id,
    menu_type = EXCLUDED.menu_type, path = EXCLUDED.path, component = EXCLUDED.component,
    perms = EXCLUDED.perms, icon = EXCLUDED.icon, sort = EXCLUDED.sort,
    visible = EXCLUDED.visible, status = EXCLUDED.status, deleted = 0;

-- ============================================================
-- 3. 超管授权（500000 + p.id 主键公式，ON CONFLICT DO NOTHING 防重）
-- ============================================================
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 500000 + p.id, r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.code = 'ADMIN' AND r.deleted = 0
  AND p.code IN (
    'kb:space:read', 'kb:space:create', 'kb:space:update', 'kb:space:delete',
    'kb:doc:read', 'kb:doc:upload', 'kb:doc:delete', 'kb:doc:reindex',
    'kb:search:query', 'kb:chat:ask'
  )
ON CONFLICT DO NOTHING;

-- ============================================================
-- 4. 重算 platform-full 套餐（保持「全量套餐」语义不漂移）
-- ============================================================
UPDATE sys_tenant_package
SET menu_ids = (
        SELECT COALESCE(json_agg(id), '[]'::json) FROM sys_menu WHERE deleted = 0
    ),
    permission_codes = (
        SELECT COALESCE(json_agg(code), '[]'::json) FROM sys_permission WHERE deleted = 0
    ),
    update_time = NOW()
WHERE package_code = 'platform-full' AND deleted = 0;

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询新增菜单：
--    SELECT id, parent_id, menu_name, path, component FROM sys_menu
--    WHERE id BETWEEN 7220 AND 7224 ORDER BY id;
-- 2. 验证权限登记：
--    SELECT id, code FROM sys_permission WHERE code LIKE 'kb:%' ORDER BY id;
-- 3. 验证超管授权：
--    SELECT p.code FROM sys_role_permission rp
--    JOIN sys_role r ON r.id = rp.role_id AND r.code = 'ADMIN'
--    JOIN sys_permission p ON p.id = rp.permission_id
--    WHERE p.code LIKE 'kb:%';
-- ============================================================
