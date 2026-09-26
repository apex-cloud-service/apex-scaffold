-- ============================================================
-- V40 - Fix database schema inconsistencies (based on PO code)
-- ============================================================
-- Purpose: Align database schema with PO entity classes
-- Based on: Database field consistency verification report 2026-09-17
-- Principle: Code implementation (PO classes) is the source of truth
-- Note: All operations are idempotent
-- ============================================================

-- ============================================================
-- 1. Fix oauth2_authorization_consent table structure
-- ============================================================

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'oauth2_authorization_consent' 
  AND COLUMN_NAME = 'id';

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE oauth2_authorization_consent DROP PRIMARY KEY, ADD COLUMN id VARCHAR(100) NOT NULL FIRST, ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, ADD PRIMARY KEY (id), ADD UNIQUE INDEX uk_client_principal (registered_client_id, principal_name)',
    'SELECT ''oauth2_authorization_consent already has id column'' AS result');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. Fix wf_workflow_definition table structure
-- ============================================================

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'wf_workflow_definition' 
  AND COLUMN_NAME = 'node_definition';

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE wf_workflow_definition CHANGE COLUMN node_config_json node_definition TEXT COMMENT ''节点定义JSON''',
    'SELECT ''wf_workflow_definition node_definition already exists'' AS result');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'wf_workflow_definition' 
  AND COLUMN_NAME = 'enabled';

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE wf_workflow_definition CHANGE COLUMN status enabled VARCHAR(20) DEFAULT ''ACTIVE'' COMMENT ''是否启用''',
    'SELECT ''wf_workflow_definition enabled already exists'' AS result');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. Fix wf_process_instance table structure
-- ============================================================

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'wf_process_instance' 
  AND COLUMN_NAME = 'form_data';

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE wf_process_instance CHANGE COLUMN context_json form_data TEXT COMMENT ''表单数据JSON''',
    'SELECT ''wf_process_instance form_data already exists'' AS result');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 4. Fix office_message table structure
-- ============================================================

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'office_message' 
  AND COLUMN_NAME = 'title';

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE office_message ADD COLUMN title VARCHAR(500) NULL COMMENT ''消息标题'' AFTER message_type',
    'SELECT ''office_message title already exists'' AS result');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'office_message' 
  AND COLUMN_NAME = 'target_type';

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE office_message CHANGE COLUMN recipient_type target_type VARCHAR(50) NOT NULL COMMENT ''目标类型''',
    'SELECT ''office_message target_type already exists'' AS result');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'office_message' 
  AND COLUMN_NAME = 'target_user_ids';

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE office_message CHANGE COLUMN recipient_ids target_user_ids TEXT COMMENT ''目标用户ID列表''',
    'SELECT ''office_message target_user_ids already exists'' AS result');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'office_message' 
  AND COLUMN_NAME = 'send_status';

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE office_message CHANGE COLUMN status send_status VARCHAR(50) DEFAULT ''PENDING'' COMMENT ''发送状态''',
    'SELECT ''office_message send_status already exists'' AS result');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'office_message' 
  AND COLUMN_NAME = 'failure_reason';

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE office_message CHANGE COLUMN result_message failure_reason VARCHAR(1000) NULL COMMENT ''失败原因''',
    'SELECT ''office_message failure_reason already exists'' AS result');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- End of V40 migration
-- ============================================================
