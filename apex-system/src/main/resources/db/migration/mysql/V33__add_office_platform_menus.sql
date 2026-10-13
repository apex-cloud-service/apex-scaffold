-- ==================== 办公平台菜单和权限配置 ====================
-- 本文件需要追加到后端项目的 Flyway 脚本中
-- MySQL 路径: apex-interfaces/src/main/resources/db/migration/mysql/V30__add_office_platform_menus.sql
-- PostgreSQL 路径: apex-interfaces/src/main/resources/db/migration/postgresql/V30__add_office_platform_menus.sql

-- ==================== 办公平台主菜单 ====================
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort_order, visible, status, perms, create_by, create_time, update_by, update_time, remark)
VALUES 
(2000, 0, '办公平台', 'M', '/office', null, 'office-building', 5, 1, 1, null, 'admin', NOW(), 'admin', NOW(), '办公平台集成管理');

-- ==================== 阶段一：组织通讯录同步 ====================
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort_order, visible, status, perms, create_by, create_time, update_by, update_time, remark)
VALUES 
(2001, 2000, '组织同步', 'C', 'organization', 'office/organization/index', 'user', 1, 1, 1, null, 'admin', NOW(), 'admin', NOW(), '组织通讯录同步'),
(2011, 2001, '同步组织', 'F', '', '', '', 1, 1, 1, 'office:organization:sync', 'admin', NOW(), 'admin', NOW(), '同步组织通讯录权限'),
(2012, 2001, '查询组织', 'F', '', '', '', 2, 1, 1, 'office:organization:query', 'admin', NOW(), 'admin', NOW(), '查询组织信息权限'),
(2013, 2001, '查询部门', 'F', '', '', '', 3, 1, 1, 'office:organization:dept:query', 'admin', NOW(), 'admin', NOW(), '查询部门信息权限'),
(2014, 2001, '查询用户', 'F', '', '', '', 4, 1, 1, 'office:organization:user:query', 'admin', NOW(), 'admin', NOW(), '查询用户信息权限');

-- ==================== 阶段二：消息发送 ====================
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort_order, visible, status, perms, create_by, create_time, update_by, update_time, remark)
VALUES 
(2002, 2000, '消息发送', 'C', 'message', 'office/message/index', 'message', 2, 1, 1, null, 'admin', NOW(), 'admin', NOW(), '办公平台消息发送'),
(2021, 2002, '发送文本', 'F', '', '', '', 1, 1, 1, 'office:message:send:text', 'admin', NOW(), 'admin', NOW(), '发送文本消息权限'),
(2022, 2002, '发送图片', 'F', '', '', '', 2, 1, 1, 'office:message:send:image', 'admin', NOW(), 'admin', NOW(), '发送图片消息权限'),
(2023, 2002, '发送文件', 'F', '', '', '', 3, 1, 1, 'office:message:send:file', 'admin', NOW(), 'admin', NOW(), '发送文件消息权限'),
(2024, 2002, '发送卡片', 'F', '', '', '', 4, 1, 1, 'office:message:send:card', 'admin', NOW(), 'admin', NOW(), '发送卡片消息权限'),
(2025, 2002, '查询消息', 'F', '', '', '', 5, 1, 1, 'office:message:query', 'admin', NOW(), 'admin', NOW(), '查询消息记录权限'),
(2026, 2002, '撤回消息', 'F', '', '', '', 6, 1, 1, 'office:message:recall', 'admin', NOW(), 'admin', NOW(), '撤回消息权限');

-- ==================== 阶段二：审批管理 ====================
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort_order, visible, status, perms, create_by, create_time, update_by, update_time, remark)
VALUES 
(2003, 2000, '审批管理', 'C', 'approval', 'office/approval/index', 'document-checked', 3, 1, 1, null, 'admin', NOW(), 'admin', NOW(), '办公平台审批流程管理'),
(2031, 2003, '发起审批', 'F', '', '', '', 1, 1, 1, 'office:approval:initiate', 'admin', NOW(), 'admin', NOW(), '发起审批流程权限'),
(2032, 2003, '查询审批', 'F', '', '', '', 2, 1, 1, 'office:approval:query', 'admin', NOW(), 'admin', NOW(), '查询审批详情权限'),
(2033, 2003, '执行审批', 'F', '', '', '', 3, 1, 1, 'office:approval:approve', 'admin', NOW(), 'admin', NOW(), '执行审批操作权限'),
(2034, 2003, '撤销审批', 'F', '', '', '', 4, 1, 1, 'office:approval:cancel', 'admin', NOW(), 'admin', NOW(), '撤销审批流程权限'),
(2035, 2003, '待审批列表', 'F', '', '', '', 5, 1, 1, 'office:approval:pending', 'admin', NOW(), 'admin', NOW(), '查询待审批列表权限'),
(2036, 2003, '我的发起', 'F', '', '', '', 6, 1, 1, 'office:approval:initiated', 'admin', NOW(), 'admin', NOW(), '查询我发起的审批权限');

-- ==================== 阶段三：日历管理 ====================
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort_order, visible, status, perms, create_by, create_time, update_by, update_time, remark)
VALUES 
(2004, 2000, '日历管理', 'C', 'calendar', 'office/calendar/index', 'calendar', 4, 1, 1, null, 'admin', NOW(), 'admin', NOW(), '办公平台日历事件管理'),
(2041, 2004, '创建事件', 'F', '', '', '', 1, 1, 1, 'office:calendar:create', 'admin', NOW(), 'admin', NOW(), '创建日历事件权限'),
(2042, 2004, '查询事件', 'F', '', '', '', 2, 1, 1, 'office:calendar:query', 'admin', NOW(), 'admin', NOW(), '查询日历事件权限'),
(2043, 2004, '更新事件', 'F', '', '', '', 3, 1, 1, 'office:calendar:update', 'admin', NOW(), 'admin', NOW(), '更新日历事件权限'),
(2044, 2004, '删除事件', 'F', '', '', '', 4, 1, 1, 'office:calendar:delete', 'admin', NOW(), 'admin', NOW(), '删除日历事件权限'),
(2045, 2004, '我的日历', 'F', '', '', '', 5, 1, 1, 'office:calendar:my', 'admin', NOW(), 'admin', NOW(), '查询我的日历事件权限');

-- ==================== 阶段三：文档管理 ====================
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort_order, visible, status, perms, create_by, create_time, update_by, update_time, remark)
VALUES 
(2005, 2000, '文档管理', 'C', 'document', 'office/document/index', 'document', 5, 1, 1, null, 'admin', NOW(), 'admin', NOW(), '办公平台文档操作管理'),
(2051, 2005, '创建文档', 'F', '', '', '', 1, 1, 1, 'office:document:create', 'admin', NOW(), 'admin', NOW(), '创建文档权限'),
(2052, 2005, '查询文档', 'F', '', '', '', 2, 1, 1, 'office:document:query', 'admin', NOW(), 'admin', NOW(), '查询文档权限'),
(2053, 2005, '更新文档', 'F', '', '', '', 3, 1, 1, 'office:document:update', 'admin', NOW(), 'admin', NOW(), '更新文档权限'),
(2054, 2005, '删除文档', 'F', '', '', '', 4, 1, 1, 'office:document:delete', 'admin', NOW(), 'admin', NOW(), '删除文档权限'),
(2055, 2005, '复制文档', 'F', '', '', '', 5, 1, 1, 'office:document:copy', 'admin', NOW(), 'admin', NOW(), '复制文档权限'),
(2056, 2005, '移动文档', 'F', '', '', '', 6, 1, 1, 'office:document:move', 'admin', NOW(), 'admin', NOW(), '移动文档权限'),
(2057, 2005, '分享文档', 'F', '', '', '', 7, 1, 1, 'office:document:share', 'admin', NOW(), 'admin', NOW(), '分享文档权限'),
(2058, 2005, '我的文档', 'F', '', '', '', 8, 1, 1, 'office:document:my', 'admin', NOW(), 'admin', NOW(), '查询我的文档权限');

-- ==================== 角色菜单关联（示例：为管理员角色分配所有权限）====================
-- 假设管理员角色ID为1，需要根据实际情况调整
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 2000 AND 2058;

-- ==================== 说明 ====================
-- 1. 菜单ID规则：
--    - 2000: 办公平台主菜单
--    - 2001-2010: 一级子菜单（功能模块）
--    - 2011-2018: 组织同步权限
--    - 2021-2028: 消息发送权限
--    - 2031-2038: 审批管理权限
--    - 2041-2048: 日历管理权限
--    - 2051-2058: 文档管理权限
--
-- 2. 菜单类型：
--    - M: 目录
--    - C: 菜单（页面）
--    - F: 按钮（权限）
--
-- 3. component 字段说明：
--    - 需要与前端 VIEW_REGISTRY 中的路径完全一致
--    - 格式：office/{模块}/{页面}
--
-- 4. perms 字段说明：
--    - 权限标识符，用于后端接口权限校验
--    - 格式：office:{模块}:{操作}
--
-- 5. 注意事项：
--    - MySQL 和 PostgreSQL 脚本需要分别创建
--    - PostgreSQL 使用 CURRENT_TIMESTAMP 代替 NOW()
--    - 确保 ID 不与现有菜单冲突
--    - 根据实际角色ID调整角色菜单关联
