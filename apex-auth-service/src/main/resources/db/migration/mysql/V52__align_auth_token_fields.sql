-- ============================================================
-- V51: 对齐 auth_token 字段（MySQL）
-- 变更内容：通过 information_schema 判断列是否存在后幂等地为
--           auth_token 补充撤销审计（revoked_by/revoke_reason）、
--           使用统计（last_used_at/use_count）与 Token 轮换
--           （token_hash/token_iv/token_family/previous_jti/rotation_count）
--           相关字段，与 TokenPO/TokenEntity 字段保持一致。
-- ============================================================

SET @auth_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE auth_token ADD COLUMN revoked_by VARCHAR(64)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'auth_token' AND column_name = 'revoked_by');
PREPARE auth_statement FROM @auth_ddl;
EXECUTE auth_statement;
DEALLOCATE PREPARE auth_statement;

SET @auth_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE auth_token ADD COLUMN revoke_reason VARCHAR(255)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'auth_token' AND column_name = 'revoke_reason');
PREPARE auth_statement FROM @auth_ddl;
EXECUTE auth_statement;
DEALLOCATE PREPARE auth_statement;

SET @auth_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE auth_token ADD COLUMN last_used_at TIMESTAMP NULL', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'auth_token' AND column_name = 'last_used_at');
PREPARE auth_statement FROM @auth_ddl;
EXECUTE auth_statement;
DEALLOCATE PREPARE auth_statement;

SET @auth_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE auth_token ADD COLUMN use_count INT DEFAULT 0', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'auth_token' AND column_name = 'use_count');
PREPARE auth_statement FROM @auth_ddl;
EXECUTE auth_statement;
DEALLOCATE PREPARE auth_statement;

SET @auth_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE auth_token ADD COLUMN token_hash VARCHAR(255)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'auth_token' AND column_name = 'token_hash');
PREPARE auth_statement FROM @auth_ddl;
EXECUTE auth_statement;
DEALLOCATE PREPARE auth_statement;

SET @auth_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE auth_token ADD COLUMN token_iv VARCHAR(128)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'auth_token' AND column_name = 'token_iv');
PREPARE auth_statement FROM @auth_ddl;
EXECUTE auth_statement;
DEALLOCATE PREPARE auth_statement;

SET @auth_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE auth_token ADD COLUMN token_family VARCHAR(64)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'auth_token' AND column_name = 'token_family');
PREPARE auth_statement FROM @auth_ddl;
EXECUTE auth_statement;
DEALLOCATE PREPARE auth_statement;

SET @auth_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE auth_token ADD COLUMN previous_jti VARCHAR(100)', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'auth_token' AND column_name = 'previous_jti');
PREPARE auth_statement FROM @auth_ddl;
EXECUTE auth_statement;
DEALLOCATE PREPARE auth_statement;

SET @auth_ddl = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE auth_token ADD COLUMN rotation_count INT DEFAULT 0', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'auth_token' AND column_name = 'rotation_count');
PREPARE auth_statement FROM @auth_ddl;
EXECUTE auth_statement;
DEALLOCATE PREPARE auth_statement;
