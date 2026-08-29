-- 脚本用途：补齐 V1 核心业务表的 tenant_id 字段及配套索引。
-- 业务背景：承接 V14 的首批改造，扩大租户隔离覆盖面并兼容不同分支的实际表结构。
-- 前置依赖：V1-V14 已按序完成，执行账号具备 information_schema 查询及 ALTER、CREATE INDEX 权限。
-- 影响范围：仅对实际存在且缺少目标对象的核心表执行条件 DDL，不处理系统配置类表。
-- 幂等性：列和索引均经元数据判断，设计为可重复执行；并发迁移仍可能发生竞态冲突。
-- MySQL/Flyway 兼容性：面向 MySQL 8.x，使用会话变量及 PREPARE，由 Flyway JDBC 逐句执行。
-- 数据风险：新增列默认 NULL，需另行治理存量租户归属；多表 DDL 可能锁表、耗用空间并延迟复制。
-- 运维注意：在低峰期执行并监控锁等待，完成后逐表核对列、索引、回填及租户隔离效果。
-- 迁移元信息：版本 V15；执行阶段为多租户结构补全与覆盖收口阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- Apex Scaffold V15 迁移：补齐核心业务表租户隔离字段与索引
-- ================================================================
-- 用途：为 V1 基线中的核心业务表补齐 tenant_id 及对应索引。
-- 依赖：V14 已为 sys_user、sys_user_role 创建 tenant_id 和索引；本迁移
--       使用信息_schema 条件判断，兼容已执行 V14 的数据库与不同分支表结构。
-- 兼容性：不使用 DELIMITER、存储过程或客户端脚本扩展；每组 SET/PREPARE/
--       EXECUTE/DEALLOCATE 语句均可由 Flyway JDBC 按单条语句执行。
-- 约束：系统配置类表不在本迁移范围内；biz_admin_review_audit 的 tenant_id
--       位于主键 id 之后，其余表位于 deleted 之后。
-- ================================================================

-- biz_credit_tx
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_tx') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_tx' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_credit_tx` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_tx' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_tx' AND INDEX_NAME = 'idx_biz_credit_tx_tenant_id'), 'CREATE INDEX `idx_biz_credit_tx_tenant_id` ON `biz_credit_tx` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- sys_operation_log
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_operation_log') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_operation_log' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_operation_log` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_operation_log' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_operation_log' AND INDEX_NAME = 'idx_sys_operation_log_tenant_id'), 'CREATE INDEX `idx_sys_operation_log_tenant_id` ON `sys_operation_log` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- biz_sample
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_sample') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_sample' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_sample` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_sample' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_sample' AND INDEX_NAME = 'idx_biz_sample_tenant_id'), 'CREATE INDEX `idx_biz_sample_tenant_id` ON `biz_sample` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- biz_system_message
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_system_message') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_system_message' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_system_message` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_system_message' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_system_message' AND INDEX_NAME = 'idx_biz_system_message_tenant_id'), 'CREATE INDEX `idx_biz_system_message_tenant_id` ON `biz_system_message` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- sys_user_role
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_user_role` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role' AND INDEX_NAME = 'idx_user_role_tenant_id'), 'CREATE INDEX `idx_user_role_tenant_id` ON `sys_user_role` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- sys_account_deletion_task
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_account_deletion_task') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_account_deletion_task' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_account_deletion_task` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_account_deletion_task' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_account_deletion_task' AND INDEX_NAME = 'idx_sys_account_deletion_task_tenant_id'), 'CREATE INDEX `idx_sys_account_deletion_task_tenant_id` ON `sys_account_deletion_task` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 其余 V1 核心业务表：逐表执行条件 DDL，避免重复列或索引导致迁移失败。
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_account') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_account' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_credit_account` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_account' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_account' AND INDEX_NAME = 'idx_biz_credit_account_tenant_id'), 'CREATE INDEX `idx_biz_credit_account_tenant_id` ON `biz_credit_account` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_consent') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_consent' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_user_consent` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_consent' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_consent' AND INDEX_NAME = 'idx_biz_user_consent_tenant_id'), 'CREATE INDEX `idx_biz_user_consent_tenant_id` ON `biz_user_consent` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_api_call_log') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_api_call_log' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_api_call_log` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_api_call_log' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_api_call_log' AND INDEX_NAME = 'idx_sys_api_call_log_tenant_id'), 'CREATE INDEX `idx_sys_api_call_log_tenant_id` ON `sys_api_call_log` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_content_moderation_review') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_content_moderation_review' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_content_moderation_review` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_content_moderation_review' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_content_moderation_review' AND INDEX_NAME = 'idx_biz_content_moderation_review_tenant_id'), 'CREATE INDEX `idx_biz_content_moderation_review_tenant_id` ON `biz_content_moderation_review` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_quiz_question') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_quiz_question' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_quiz_question` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_quiz_question' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_quiz_question' AND INDEX_NAME = 'idx_biz_quiz_question_tenant_id'), 'CREATE INDEX `idx_biz_quiz_question_tenant_id` ON `biz_quiz_question` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report_ticket') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report_ticket' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_report_ticket` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report_ticket' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report_ticket' AND INDEX_NAME = 'idx_biz_report_ticket_tenant_id'), 'CREATE INDEX `idx_biz_report_ticket_tenant_id` ON `biz_report_ticket` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_user` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_sys_user_tenant_id'), 'CREATE INDEX `idx_sys_user_tenant_id` ON `sys_user` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_compliance') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_compliance' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_user_compliance` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_compliance' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_compliance' AND INDEX_NAME = 'idx_biz_user_compliance_tenant_id'), 'CREATE INDEX `idx_biz_user_compliance_tenant_id` ON `biz_user_compliance` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_policy_version') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_policy_version' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_policy_version` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_policy_version' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_policy_version' AND INDEX_NAME = 'idx_biz_policy_version_tenant_id'), 'CREATE INDEX `idx_biz_policy_version_tenant_id` ON `biz_policy_version` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_subscription') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_subscription' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_subscription` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_subscription' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_subscription' AND INDEX_NAME = 'idx_biz_subscription_tenant_id'), 'CREATE INDEX `idx_biz_subscription_tenant_id` ON `biz_subscription` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_blacklist') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_blacklist' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_blacklist` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_blacklist' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_blacklist' AND INDEX_NAME = 'idx_biz_blacklist_tenant_id'), 'CREATE INDEX `idx_biz_blacklist_tenant_id` ON `biz_blacklist` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_profile') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_profile' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_user_profile` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_profile' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_profile' AND INDEX_NAME = 'idx_biz_user_profile_tenant_id'), 'CREATE INDEX `idx_biz_user_profile_tenant_id` ON `biz_user_profile` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_reason') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_reason' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_admin_review_reason` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_reason' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_reason' AND INDEX_NAME = 'idx_biz_admin_review_reason_tenant_id'), 'CREATE INDEX `idx_biz_admin_review_reason_tenant_id` ON `biz_admin_review_reason` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_report` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report' AND INDEX_NAME = 'idx_biz_report_tenant_id'), 'CREATE INDEX `idx_biz_report_tenant_id` ON `biz_report` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_file` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND INDEX_NAME = 'idx_sys_file_tenant_id'), 'CREATE INDEX `idx_sys_file_tenant_id` ON `sys_file` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_log') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_log' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_login_log` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_log' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_log' AND INDEX_NAME = 'idx_sys_login_log_tenant_id'), 'CREATE INDEX `idx_sys_login_log_tenant_id` ON `sys_login_log` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- biz_admin_review_audit 的 tenant_id 紧随主键 id，单独处理列顺序。
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_audit') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_audit' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_admin_review_audit` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `id`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_audit' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_audit' AND INDEX_NAME = 'idx_biz_admin_review_audit_tenant_id'), 'CREATE INDEX `idx_biz_admin_review_audit_tenant_id` ON `biz_admin_review_audit` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
