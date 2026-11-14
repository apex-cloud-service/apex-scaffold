-- V77: 主库 RBAC 租户化（统一认证收口 A6 / 方案批次 B2）
-- sys_role 增加 tenant_id 并重建唯一键（全局 uk_code → 租户内唯一 uk_tenant_code）；
-- sys_role_permission 增加租户列并与角色对齐。
-- 幂等性：information_schema 条件 ALTER + UPDATE 对齐，可安全重放。
-- 前置依赖：V76 已将默认租户编码统一为 default；存量角色全部归属 default 租户。

SET @sql = IF(NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'tenant_id'),
  'ALTER TABLE `sys_role` ADD COLUMN `tenant_id` VARCHAR(64) NOT NULL DEFAULT ''default'' COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND INDEX_NAME = 'uk_code')
  AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND INDEX_NAME = 'uk_tenant_code'),
  'ALTER TABLE `sys_role` DROP INDEX `uk_code`, ADD UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_permission' AND COLUMN_NAME = 'tenant_id'),
  'ALTER TABLE `sys_role_permission` ADD COLUMN `tenant_id` VARCHAR(64) NOT NULL DEFAULT ''default'' COMMENT ''租户ID（多租户隔离，随角色）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 授权关系租户与角色对齐（覆盖任何漂移；孤儿行保持 default 兜底）
UPDATE `sys_role_permission` rp
  INNER JOIN `sys_role` r ON rp.`role_id` = r.`id`
SET rp.`tenant_id` = r.`tenant_id`
WHERE rp.`tenant_id` <> r.`tenant_id`;

SET @sql = IF(NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role_permission' AND INDEX_NAME = 'idx_sys_role_permission_tenant'),
  'CREATE INDEX `idx_sys_role_permission_tenant` ON `sys_role_permission` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 角色数据权限关系表（V18 已含租户列）与角色对齐
UPDATE `sys_role_dept` rd
  INNER JOIN `sys_role` r ON rd.`role_id` = r.`id`
SET rd.`tenant_id` = r.`tenant_id`
WHERE rd.`tenant_id` <> r.`tenant_id`;
