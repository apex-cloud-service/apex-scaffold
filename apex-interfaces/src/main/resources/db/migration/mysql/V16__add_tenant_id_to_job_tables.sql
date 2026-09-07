-- 脚本用途：为定时任务及任务日志补充租户隔离字段与索引。
-- 幂等性：通过 information_schema 检查目标表、列和索引后再执行 DDL。
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_job` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job' AND INDEX_NAME = 'idx_job_tenant_id'), 'CREATE INDEX `idx_job_tenant_id` ON `sys_job` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job' AND INDEX_NAME = 'idx_job_tenant_status'), 'CREATE INDEX `idx_job_tenant_status` ON `sys_job` (`tenant_id`, `status`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job_log') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job_log' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_job_log` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job_log' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job_log' AND INDEX_NAME = 'idx_job_log_tenant_id'), 'CREATE INDEX `idx_job_log_tenant_id` ON `sys_job_log` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job_log' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job_log' AND INDEX_NAME = 'idx_job_log_tenant_job_start'), 'CREATE INDEX `idx_job_log_tenant_job_start` ON `sys_job_log` (`tenant_id`, `job_id`, `start_time`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
