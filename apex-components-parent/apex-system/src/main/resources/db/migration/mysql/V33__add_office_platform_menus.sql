-- 办公平台菜单和权限配置，菜单类型：0=目录、1=页面、2=按钮。
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, status, perms) VALUES
(2000, 0, '办公平台', 0, '/office', NULL, 'office-building', 5, 1, 1, NULL),
(2001, 2000, '组织同步', 1, 'organization', 'office/organization/index', 'user', 1, 1, 1, NULL),
(2011, 2001, '同步组织', 2, '', '', '', 1, 1, 1, 'office:organization:sync'),
(2012, 2001, '查询组织', 2, '', '', '', 2, 1, 1, 'office:organization:query'),
(2013, 2001, '查询部门', 2, '', '', '', 3, 1, 1, 'office:organization:dept:query'),
(2014, 2001, '查询用户', 2, '', '', '', 4, 1, 1, 'office:organization:user:query'),
(2002, 2000, '消息发送', 1, 'message', 'office/message/index', 'message', 2, 1, 1, NULL),
(2021, 2002, '发送文本', 2, '', '', '', 1, 1, 1, 'office:message:send:text'),
(2022, 2002, '发送图片', 2, '', '', '', 2, 1, 1, 'office:message:send:image'),
(2023, 2002, '发送文件', 2, '', '', '', 3, 1, 1, 'office:message:send:file'),
(2024, 2002, '发送卡片', 2, '', '', '', 4, 1, 1, 'office:message:send:card'),
(2025, 2002, '查询消息', 2, '', '', '', 5, 1, 1, 'office:message:query'),
(2026, 2002, '撤回消息', 2, '', '', '', 6, 1, 1, 'office:message:recall'),
(2003, 2000, '审批管理', 1, 'approval', 'office/approval/index', 'document-checked', 3, 1, 1, NULL),
(2031, 2003, '发起审批', 2, '', '', '', 1, 1, 1, 'office:approval:initiate'),
(2032, 2003, '查询审批', 2, '', '', '', 2, 1, 1, 'office:approval:query'),
(2033, 2003, '执行审批', 2, '', '', '', 3, 1, 1, 'office:approval:approve'),
(2034, 2003, '撤销审批', 2, '', '', '', 4, 1, 1, 'office:approval:cancel'),
(2035, 2003, '待审批列表', 2, '', '', '', 5, 1, 1, 'office:approval:pending'),
(2036, 2003, '我的发起', 2, '', '', '', 6, 1, 1, 'office:approval:initiated'),
(2004, 2000, '日历管理', 1, 'calendar', 'office/calendar/index', 'calendar', 4, 1, 1, NULL),
(2041, 2004, '创建事件', 2, '', '', '', 1, 1, 1, 'office:calendar:create'),
(2042, 2004, '查询事件', 2, '', '', '', 2, 1, 1, 'office:calendar:query'),
(2043, 2004, '更新事件', 2, '', '', '', 3, 1, 1, 'office:calendar:update'),
(2044, 2004, '删除事件', 2, '', '', '', 4, 1, 1, 'office:calendar:delete'),
(2045, 2004, '我的日历', 2, '', '', '', 5, 1, 1, 'office:calendar:my'),
(2005, 2000, '文档管理', 1, 'document', 'office/document/index', 'document', 5, 1, 1, NULL),
(2051, 2005, '创建文档', 2, '', '', '', 1, 1, 1, 'office:document:create'),
(2052, 2005, '查询文档', 2, '', '', '', 2, 1, 1, 'office:document:query'),
(2053, 2005, '更新文档', 2, '', '', '', 3, 1, 1, 'office:document:update'),
(2054, 2005, '删除文档', 2, '', '', '', 4, 1, 1, 'office:document:delete'),
(2055, 2005, '复制文档', 2, '', '', '', 5, 1, 1, 'office:document:copy'),
(2056, 2005, '移动文档', 2, '', '', '', 6, 1, 1, 'office:document:move'),
(2057, 2005, '分享文档', 2, '', '', '', 7, 1, 1, 'office:document:share'),
(2058, 2005, '我的文档', 2, '', '', '', 8, 1, 1, 'office:document:my');

INSERT INTO sys_permission (id, name, code, type, status)
SELECT 330000 + id, menu_name, perms, 1, 1 FROM sys_menu
WHERE id BETWEEN 2000 AND 2058 AND perms IS NOT NULL;

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 3300000 + id, 1, 330000 + id FROM sys_menu
WHERE id BETWEEN 2000 AND 2058 AND perms IS NOT NULL;
