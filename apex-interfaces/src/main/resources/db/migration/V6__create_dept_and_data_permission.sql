-- ================================================================
-- Apex Scaffold V6 迁移：部门管理与数据权限
-- ================================================================
-- 说明：新增部门表（sys_dept，树形结构）+ sys_user.dept_id 归属字段 +
--      部门管理权限码（C3 数据权限/部门体系）。
-- 设计：
--   - sys_dept 树形（parent_id 自关联），ancestors 存祖级链便于层级过滤
--   - sys_user.dept_id 关联部门，配合 @DataScope 注解实现数据权限过滤
-- 依赖：V2 种子迁移（权限码 1000-2024、V4 2025-2026、V5 2027 已用）
-- ================================================================

CREATE TABLE IF NOT EXISTS `sys_dept` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门ID(0为根部门)',
  `ancestors`   VARCHAR(255) NOT NULL DEFAULT '' COMMENT '祖级列表(逗号分隔,如 0,100)',
  `name`        VARCHAR(50)  NOT NULL COMMENT '部门名称',
  `order_num`   INT(4)       NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `leader`      VARCHAR(50)  DEFAULT NULL COMMENT '负责人',
  `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
  `email`       VARCHAR(50)  DEFAULT NULL COMMENT '邮箱',
  `status`      INT(1)       NOT NULL DEFAULT 1 COMMENT '状态(0-停用,1-正常)',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`     INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表(树形,数据权限基础)';

-- sys_user 增加部门归属字段
ALTER TABLE `sys_user`
    ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT '所属部门ID(数据权限,可空)' AFTER `username`;

INSERT INTO `sys_permission` (`id`, `name`, `code`, `description`, `type`, `status`, `create_time`, `update_time`, `version`, `deleted`) VALUES
(2028, '部门列表', 'system:dept:list', '查询部门列表', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2029, '部门详情', 'system:dept:query', '查询部门详情', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2030, '部门新增', 'system:dept:add', '新增部门', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2031, '部门编辑', 'system:dept:edit', '编辑部门', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2032, '部门删除', 'system:dept:remove', '删除部门', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `path`, `component`, `permission`, `icon`, `menu_type`, `status`, `visible`, `create_time`, `update_time`, `version`, `deleted`) VALUES
(1008, 1000, '部门管理', 1, 'system/dept', 'system/dept/index', 'system:dept:list', 'Cluster', 2, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

-- 根部门示例（可选，生产按需调整）
INSERT INTO `sys_dept` (`id`, `parent_id`, `ancestors`, `name`, `order_num`, `status`, `create_time`, `update_time`, `version`, `deleted`) VALUES
(1, 0, '0', 'Apex 科技', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);
