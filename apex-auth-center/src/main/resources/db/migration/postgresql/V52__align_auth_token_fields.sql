-- ============================================================
-- V51: 对齐 auth_token 字段（PostgreSQL）
-- 变更内容：为 auth_token 补充撤销审计（revoked_by/revoke_reason）、
--           使用统计（last_used_at/use_count）与 Token 轮换
--           （token_hash/token_iv/token_family/previous_jti/rotation_count）
--           相关字段，与 TokenPO/TokenEntity 字段保持一致。
-- ============================================================
ALTER TABLE auth_token ADD COLUMN IF NOT EXISTS revoked_by VARCHAR(64);
ALTER TABLE auth_token ADD COLUMN IF NOT EXISTS revoke_reason VARCHAR(255);
ALTER TABLE auth_token ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMP NULL;
ALTER TABLE auth_token ADD COLUMN IF NOT EXISTS use_count INT DEFAULT 0;
ALTER TABLE auth_token ADD COLUMN IF NOT EXISTS token_hash VARCHAR(255);
ALTER TABLE auth_token ADD COLUMN IF NOT EXISTS token_iv VARCHAR(128);
ALTER TABLE auth_token ADD COLUMN IF NOT EXISTS token_family VARCHAR(64);
ALTER TABLE auth_token ADD COLUMN IF NOT EXISTS previous_jti VARCHAR(100);
ALTER TABLE auth_token ADD COLUMN IF NOT EXISTS rotation_count INT DEFAULT 0;
