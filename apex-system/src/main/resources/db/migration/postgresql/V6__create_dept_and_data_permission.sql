-- 脚本用途：创建部门树结构并为用户增加部门归属，同时初始化部门管理权限。
-- 业务背景：为组织架构管理和基于部门的数据范围过滤提供数据库基础。
-- 前置依赖：V1 已创建 sys_user、权限及菜单相关表，V2-V5 已按序完成。
-- 影响范围：创建 sys_dept，变更 sys_user，并写入部门权限及菜单种子数据。
-- 幂等性：建表使用 IF NOT EXISTS，但 ALTER TABLE 与普通种子 INSERT 非幂等，整体不可重复执行。
-- MySQL/Flyway 兼容性：面向 MySQL 8.x、InnoDB 与 Flyway V6 顺序迁移。
-- 数据风险：用户新增列与索引可能锁表；固定 ID 冲突会中断迁移并留下部分已执行 DDL。
-- 运维注意：执行前备份并评估 sys_user 表规模，执行后核对部门树、字段及权限菜单。
-- 迁移元信息：版本 V6；执行阶段为组织架构与数据权限基础建设阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
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

CREATE TABLE IF NOT EXISTS sys_dept (
  id          BIGINT       NOT NULL,
  parent_id   BIGINT       NOT NULL DEFAULT 0,
  ancestors   VARCHAR(255) NOT NULL DEFAULT '',
  name        VARCHAR(50)  NOT NULL,
  order_num   INTEGER       NOT NULL DEFAULT 0,
  leader      VARCHAR(50)  DEFAULT NULL,
  phone       VARCHAR(20)  DEFAULT NULL,
  email       VARCHAR(50)  DEFAULT NULL,
  status      INTEGER       NOT NULL DEFAULT 1,
  create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version     INTEGER      NOT NULL DEFAULT 0,
  deleted     INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_parent_id ON sys_dept (parent_id);
CREATE INDEX IF NOT EXISTS idx_status ON sys_dept (status);

-- sys_user 增加部门归属字段
ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS dept_id BIGINT DEFAULT NULL;

INSERT INTO sys_permission (id, name, code, description, type, status, create_time, update_time, version, deleted) VALUES
(2028, '部门列表', 'system:dept:list', '查询部门列表', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2029, '部门详情', 'system:dept:query', '查询部门详情', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2030, '部门新增', 'system:dept:add', '新增部门', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2031, '部门编辑', 'system:dept:edit', '编辑部门', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2032, '部门删除', 'system:dept:remove', '删除部门', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted) VALUES
(1008, 1000, '部门管理', 1, 'system/dept', 'system/dept/index', 'system:dept:list', 'Cluster', 8, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

-- 根部门示例（可选，生产按需调整）
INSERT INTO sys_dept (id, parent_id, ancestors, name, order_num, status, create_time, update_time, version, deleted) VALUES
(1, 0, '0', 'Apex 科技', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);
