-- ============================================================
-- V26: Add Keycloak integration fields to sys_user
-- ============================================================
-- Description: Add Keycloak SSO integration support fields
-- Author: Apex Team
-- Date: 2026-09-08
-- ============================================================

-- V37 已创建字段及唯一索引，此处扩展字段长度。
ALTER TABLE sys_user
MODIFY COLUMN keycloak_sub VARCHAR(255) NULL COMMENT 'Keycloak用户唯一标识(sub claim)';

-- Add email field if not exists (for Keycloak OIDC user info)
-- Check if email column exists, if not add it
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'sys_user' 
     AND COLUMN_NAME = 'email') = 0,
    'ALTER TABLE sys_user ADD COLUMN email VARCHAR(255) NULL COMMENT ''用户邮箱'' AFTER phone',
    'SELECT ''Column email already exists'' AS message'
));

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add index for email if not exists
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS 
     WHERE TABLE_SCHEMA = DATABASE() 
     AND TABLE_NAME = 'sys_user' 
     AND INDEX_NAME = 'idx_email') = 0,
    'ALTER TABLE sys_user ADD INDEX idx_email (email)',
    'SELECT ''Index idx_email already exists'' AS message'
));

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add comment for sys_user table
ALTER TABLE sys_user COMMENT = '系统用户表（支持本地JWT认证和Keycloak SSO认证）';
