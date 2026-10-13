-- ============================================================
-- V55: auth_user 关联统一账户（PostgreSQL）
-- ============================================================
-- 变更内容：与 mysql/V55__auth_user_account_binding.sql 对齐。
--   auth_user 增加 account_id，关联主应用统一账户 sys_user.id。
-- 幂等性：ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS，可重复执行。
-- ============================================================

ALTER TABLE auth_user ADD COLUMN IF NOT EXISTS account_id BIGINT DEFAULT NULL;

CREATE INDEX IF NOT EXISTS idx_auth_user_account_id ON auth_user (account_id);
