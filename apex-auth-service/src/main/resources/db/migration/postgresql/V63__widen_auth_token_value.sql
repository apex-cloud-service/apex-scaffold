-- ============================================================
-- V63: 扩容 auth_token.token_value，修复登录/刷新写入令牌失败
-- ============================================================
-- 背景：token_value 存储 AES-256-GCM 加密后的 JWT（Base64），
--       典型 RS256 JWT 600~1200 字符，加密并 Base64 后约 824~1624 字符，
--       原 VARCHAR(500) 必然触发 "value too long for type character varying(500)"。
-- 影响：登录、刷新、会话登记全部无法完成。
-- 兼容：TEXT 不改变读写语义；本迁移只放宽长度，不回填、不改历史数据。
-- ============================================================

ALTER TABLE auth_token
    ALTER COLUMN token_value TYPE TEXT,
    ALTER COLUMN token_value SET NOT NULL;
