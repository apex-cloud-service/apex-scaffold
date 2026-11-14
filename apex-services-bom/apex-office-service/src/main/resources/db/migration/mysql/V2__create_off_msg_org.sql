-- ============================================================
-- Apex Scaffold - 办公服务独立库 V2：办公组织与消息域迁入（MySQL）
-- ============================================================
-- 【用途】
--   将旧单体 V57__add_office_platform_tables.sql 中归属办公领域的
--   六张 off_* 表迁入办公服务独立库 apex_office（迁移方案 §9.2/§9.3、
--   office-off-star-extraction-plan.md Phase 1）。新库版本链从 V1 起
--   与单体解耦，本脚本在 V1 之后追加。
--
-- 【表清单】
--   off_message_template     办公消息模板
--   off_message              办公消息
--   off_message_recipient    办公消息接收人
--   off_department           办公组织部门
--   off_employee             办公组织员工
--   off_sync_history         办公组织同步历史
--
-- 【字段来源原则】
--   以旧单体 V57 的 DDL 为唯一事实来源，列名/类型/可空性/默认值/索引/
--   唯一键严格保持一致；仅迁移表结构，不含 sys_menu / sys_permission
--   种子数据（系统域种子仍归属单体，不在本库承载范围内）。
--
-- 【幂等性】
--   全部 CREATE TABLE IF NOT EXISTS，可重复执行。
--
-- 【方言约定】
--   MySQL 统一使用 DATETIME，禁止 TIMESTAMP；字符集 utf8mb4。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

-- ------------------------------------------------------------
-- 办公消息模板
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `off_message_template` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `template_code`    VARCHAR(64)  NOT NULL COMMENT '模板编码',
    `template_name`    VARCHAR(128) NOT NULL COMMENT '模板名称',
    `template_type`    VARCHAR(32)  NOT NULL COMMENT '模板类型',
    `template_content` TEXT         NOT NULL COMMENT '模板内容',
    `variables`        JSON                  DEFAULT NULL COMMENT '模板变量定义（JSON）',
    `enabled`          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '启用状态（0-禁用 1-启用）',
    `tenant_id`        VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_off_message_template_code_tenant` (`template_code`, `tenant_id`, `deleted`),
    KEY `idx_off_message_template_tenant_enabled` (`tenant_id`, `enabled`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公消息模板';

-- ------------------------------------------------------------
-- 办公消息
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `off_message` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `message_code` VARCHAR(64)  NOT NULL COMMENT '消息编码',
    `template_id`  BIGINT                DEFAULT NULL COMMENT '模板ID',
    `subject`      VARCHAR(255) NOT NULL COMMENT '消息主题',
    `content`      TEXT         NOT NULL COMMENT '消息内容',
    `message_type` VARCHAR(32)  NOT NULL COMMENT '消息类型',
    `priority`     VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT '优先级',
    `sender_id`    BIGINT                DEFAULT NULL COMMENT '发送人ID',
    `sender_name`  VARCHAR(64)           DEFAULT NULL COMMENT '发送人姓名',
    `send_time`    DATETIME     NOT NULL COMMENT '发送时间',
    `expire_time`  DATETIME              DEFAULT NULL COMMENT '过期时间',
    `status`       VARCHAR(16)  NOT NULL DEFAULT 'SENT' COMMENT '消息状态（SENT/...）',
    `tenant_id`    VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `version`      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_off_message_code_tenant` (`message_code`, `tenant_id`, `deleted`),
    KEY `idx_off_message_tenant_status` (`tenant_id`, `status`, `deleted`),
    KEY `idx_off_message_sender_time` (`sender_id`, `send_time`),
    KEY `idx_off_message_template` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公消息';

-- ------------------------------------------------------------
-- 办公消息接收人
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `off_message_recipient` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `message_id`     BIGINT      NOT NULL COMMENT '消息ID',
    `recipient_id`   BIGINT      NOT NULL COMMENT '接收人ID',
    `recipient_name` VARCHAR(64)          DEFAULT NULL COMMENT '接收人姓名',
    `recipient_type` VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT '接收人类型',
    `read_status`    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '已读状态（0-未读 1-已读）',
    `read_time`      DATETIME             DEFAULT NULL COMMENT '已读时间',
    `tenant_id`      VARCHAR(64) NOT NULL COMMENT '租户ID',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `version`        INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_off_message_recipient` (`message_id`, `recipient_id`, `deleted`),
    KEY `idx_off_recipient_status` (`recipient_id`, `read_status`, `deleted`),
    KEY `idx_off_recipient_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公消息接收人';

-- ------------------------------------------------------------
-- 办公组织部门
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `off_department` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `dept_code`    VARCHAR(64)  NOT NULL COMMENT '部门编码',
    `dept_name`    VARCHAR(128) NOT NULL COMMENT '部门名称',
    `parent_id`    BIGINT                DEFAULT NULL COMMENT '父部门ID',
    `dept_path`    VARCHAR(512)          DEFAULT NULL COMMENT '部门路径',
    `dept_level`   INT          NOT NULL DEFAULT 1 COMMENT '部门层级',
    `sort_order`   INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `leader_id`    BIGINT                DEFAULT NULL COMMENT '负责人ID',
    `leader_name`  VARCHAR(64)           DEFAULT NULL COMMENT '负责人姓名',
    `phone`        VARCHAR(32)           DEFAULT NULL COMMENT '联系电话',
    `email`        VARCHAR(128)          DEFAULT NULL COMMENT '联系邮箱',
    `status`       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE/...）',
    `sync_source`  VARCHAR(32)           DEFAULT NULL COMMENT '同步来源',
    `external_id`  VARCHAR(128)          DEFAULT NULL COMMENT '外部系统ID',
    `tenant_id`    VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `version`      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_off_department_code_tenant` (`dept_code`, `tenant_id`, `deleted`),
    KEY `idx_off_department_parent_tenant` (`parent_id`, `tenant_id`, `deleted`),
    KEY `idx_off_department_sync` (`sync_source`, `external_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公组织部门';

-- ------------------------------------------------------------
-- 办公组织员工
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `off_employee` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `employee_code` VARCHAR(64)  NOT NULL COMMENT '员工编码',
    `employee_name` VARCHAR(64)  NOT NULL COMMENT '员工姓名',
    `dept_id`       BIGINT       NOT NULL COMMENT '部门ID',
    `dept_name`     VARCHAR(128)          DEFAULT NULL COMMENT '部门名称',
    `position`      VARCHAR(64)           DEFAULT NULL COMMENT '岗位',
    `job_title`     VARCHAR(64)           DEFAULT NULL COMMENT '职位',
    `mobile`        VARCHAR(32)           DEFAULT NULL COMMENT '手机号',
    `email`         VARCHAR(128)          DEFAULT NULL COMMENT '邮箱',
    `gender`        VARCHAR(8)            DEFAULT NULL COMMENT '性别',
    `birthday`      DATE                  DEFAULT NULL COMMENT '生日',
    `hire_date`     DATE                  DEFAULT NULL COMMENT '入职日期',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE/...）',
    `user_id`       BIGINT                DEFAULT NULL COMMENT '关联本地用户ID',
    `sync_source`   VARCHAR(32)           DEFAULT NULL COMMENT '同步来源',
    `external_id`   VARCHAR(128)          DEFAULT NULL COMMENT '外部系统ID',
    `tenant_id`     VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `version`       INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_off_employee_code_tenant` (`employee_code`, `tenant_id`, `deleted`),
    KEY `idx_off_employee_dept` (`dept_id`, `tenant_id`, `deleted`),
    KEY `idx_off_employee_user` (`user_id`, `tenant_id`),
    KEY `idx_off_employee_sync` (`sync_source`, `external_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公组织员工';

-- ------------------------------------------------------------
-- 办公组织同步历史
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `off_sync_history` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `sync_type`      VARCHAR(32)  NOT NULL COMMENT '同步类型',
    `sync_source`    VARCHAR(32)  NOT NULL COMMENT '同步来源',
    `sync_status`    VARCHAR(16)  NOT NULL COMMENT '同步状态',
    `start_time`     DATETIME     NOT NULL COMMENT '开始时间',
    `end_time`       DATETIME              DEFAULT NULL COMMENT '结束时间',
    `duration`       INT                   DEFAULT NULL COMMENT '耗时（毫秒）',
    `dept_count`     INT                   DEFAULT 0 COMMENT '部门数量',
    `employee_count` INT                   DEFAULT 0 COMMENT '员工数量',
    `error_message`  TEXT                  DEFAULT NULL COMMENT '错误信息',
    `tenant_id`      VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `version`        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_off_sync_tenant_time` (`tenant_id`, `start_time`),
    KEY `idx_off_sync_status_time` (`sync_status`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公组织同步历史';
