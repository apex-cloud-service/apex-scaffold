-- ============================================================
-- V57: 添加办公平台相关表及菜单权限（MySQL）
-- 变更内容：创建消息模板、消息、消息接收人、部门、员工、
--           同步历史等办公平台表及配套索引；以 ON DUPLICATE KEY
--           幂等地初始化办公平台菜单与权限种子数据。
-- ============================================================
CREATE TABLE IF NOT EXISTS `off_message_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `template_code` VARCHAR(64) NOT NULL,
    `template_name` VARCHAR(128) NOT NULL,
    `template_type` VARCHAR(32) NOT NULL,
    `template_content` TEXT NOT NULL,
    `variables` JSON,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `tenant_id` VARCHAR(64) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_off_message_template_code_tenant` (`template_code`, `tenant_id`, `deleted`),
    KEY `idx_off_message_template_tenant_enabled` (`tenant_id`, `enabled`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公消息模板';

CREATE TABLE IF NOT EXISTS `off_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `message_code` VARCHAR(64) NOT NULL,
    `template_id` BIGINT DEFAULT NULL,
    `subject` VARCHAR(255) NOT NULL,
    `content` TEXT NOT NULL,
    `message_type` VARCHAR(32) NOT NULL,
    `priority` VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    `sender_id` BIGINT DEFAULT NULL,
    `sender_name` VARCHAR(64) DEFAULT NULL,
    `send_time` DATETIME NOT NULL,
    `expire_time` DATETIME DEFAULT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'SENT',
    `tenant_id` VARCHAR(64) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_off_message_code_tenant` (`message_code`, `tenant_id`, `deleted`),
    KEY `idx_off_message_tenant_status` (`tenant_id`, `status`, `deleted`),
    KEY `idx_off_message_sender_time` (`sender_id`, `send_time`),
    KEY `idx_off_message_template` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公消息';

CREATE TABLE IF NOT EXISTS `off_message_recipient` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `message_id` BIGINT NOT NULL,
    `recipient_id` BIGINT NOT NULL,
    `recipient_name` VARCHAR(64) DEFAULT NULL,
    `recipient_type` VARCHAR(16) NOT NULL DEFAULT 'USER',
    `read_status` TINYINT(1) NOT NULL DEFAULT 0,
    `read_time` DATETIME DEFAULT NULL,
    `tenant_id` VARCHAR(64) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_off_message_recipient` (`message_id`, `recipient_id`, `deleted`),
    KEY `idx_off_recipient_status` (`recipient_id`, `read_status`, `deleted`),
    KEY `idx_off_recipient_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公消息接收人';

CREATE TABLE IF NOT EXISTS `off_department` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `dept_code` VARCHAR(64) NOT NULL,
    `dept_name` VARCHAR(128) NOT NULL,
    `parent_id` BIGINT DEFAULT NULL,
    `dept_path` VARCHAR(512) DEFAULT NULL,
    `dept_level` INT NOT NULL DEFAULT 1,
    `sort_order` INT NOT NULL DEFAULT 0,
    `leader_id` BIGINT DEFAULT NULL,
    `leader_name` VARCHAR(64) DEFAULT NULL,
    `phone` VARCHAR(32) DEFAULT NULL,
    `email` VARCHAR(128) DEFAULT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    `sync_source` VARCHAR(32) DEFAULT NULL,
    `external_id` VARCHAR(128) DEFAULT NULL,
    `tenant_id` VARCHAR(64) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_off_department_code_tenant` (`dept_code`, `tenant_id`, `deleted`),
    KEY `idx_off_department_parent_tenant` (`parent_id`, `tenant_id`, `deleted`),
    KEY `idx_off_department_sync` (`sync_source`, `external_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公组织部门';

CREATE TABLE IF NOT EXISTS `off_employee` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `employee_code` VARCHAR(64) NOT NULL,
    `employee_name` VARCHAR(64) NOT NULL,
    `dept_id` BIGINT NOT NULL,
    `dept_name` VARCHAR(128) DEFAULT NULL,
    `position` VARCHAR(64) DEFAULT NULL,
    `job_title` VARCHAR(64) DEFAULT NULL,
    `mobile` VARCHAR(32) DEFAULT NULL,
    `email` VARCHAR(128) DEFAULT NULL,
    `gender` VARCHAR(8) DEFAULT NULL,
    `birthday` DATE DEFAULT NULL,
    `hire_date` DATE DEFAULT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    `user_id` BIGINT DEFAULT NULL,
    `sync_source` VARCHAR(32) DEFAULT NULL,
    `external_id` VARCHAR(128) DEFAULT NULL,
    `tenant_id` VARCHAR(64) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_off_employee_code_tenant` (`employee_code`, `tenant_id`, `deleted`),
    KEY `idx_off_employee_dept` (`dept_id`, `tenant_id`, `deleted`),
    KEY `idx_off_employee_user` (`user_id`, `tenant_id`),
    KEY `idx_off_employee_sync` (`sync_source`, `external_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公组织员工';

CREATE TABLE IF NOT EXISTS `off_sync_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `sync_type` VARCHAR(32) NOT NULL,
    `sync_source` VARCHAR(32) NOT NULL,
    `sync_status` VARCHAR(16) NOT NULL,
    `start_time` DATETIME NOT NULL,
    `end_time` DATETIME DEFAULT NULL,
    `duration` INT DEFAULT NULL,
    `dept_count` INT DEFAULT 0,
    `employee_count` INT DEFAULT 0,
    `error_message` TEXT,
    `tenant_id` VARCHAR(64) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `version` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_off_sync_tenant_time` (`tenant_id`, `start_time`),
    KEY `idx_off_sync_status_time` (`sync_status`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公组织同步历史';

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`, `deleted`)
VALUES (2100, 2000, '办公消息', 1, 'message', 'office/message/index', 'office:message:query', 'message', 1, 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `menu_name` = VALUES(`menu_name`), `component` = VALUES(`component`), `perms` = VALUES(`perms`), `status` = 1, `deleted` = 0, `update_time` = NOW();

INSERT INTO `sys_permission` (`id`, `name`, `code`, `description`, `type`, `status`, `create_time`, `update_time`, `deleted`)
VALUES
    (3300, '查询办公消息', 'office:message:query', '查询办公消息', 1, 1, NOW(), NOW(), 0),
    (3301, '发送办公消息', 'office:message:create', '创建办公消息', 1, 1, NOW(), NOW(), 0),
    (3302, '删除办公消息', 'office:message:delete', '删除办公消息', 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `description` = VALUES(`description`), `status` = 1, `deleted` = 0, `update_time` = NOW();
