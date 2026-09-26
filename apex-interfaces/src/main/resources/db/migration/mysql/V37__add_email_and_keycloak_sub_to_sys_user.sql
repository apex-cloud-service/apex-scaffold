-- V37: 为 sys_user 表添加 email 和 keycloak_sub 字段（幂等性处理）

-- 添加 email 字段（如果不存在）
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'sys_user' 
  AND COLUMN_NAME = 'email';

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE sys_user ADD COLUMN email VARCHAR(100) NULL COMMENT ''用户邮箱'' AFTER phone',
    'SELECT ''Column email already exists'' AS result');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 keycloak_sub 字段（如果不存在）
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'sys_user' 
  AND COLUMN_NAME = 'keycloak_sub';

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE sys_user ADD COLUMN keycloak_sub VARCHAR(100) NULL COMMENT ''Keycloak用户唯一标识'' AFTER email',
    'SELECT ''Column keycloak_sub already exists'' AS result');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 keycloak_sub 唯一索引（如果不存在）
SET @idx_exists = 0;
SELECT COUNT(*) INTO @idx_exists 
FROM information_schema.STATISTICS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'sys_user' 
  AND INDEX_NAME = 'uk_keycloak_sub';

SET @sql = IF(@idx_exists = 0, 
    'ALTER TABLE sys_user ADD UNIQUE INDEX uk_keycloak_sub (keycloak_sub)',
    'SELECT ''Index uk_keycloak_sub already exists'' AS result');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
