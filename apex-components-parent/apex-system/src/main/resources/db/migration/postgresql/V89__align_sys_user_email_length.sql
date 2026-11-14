-- ============================================================
-- V89: 对齐 sys_user.email 长度（PostgreSQL）
-- ============================================================
-- 背景：MySQL 侧 V51__add_keycloak_fields.sql 已把 sys_user.email 扩展为
--       VARCHAR(255) 并建立 idx_email；PostgreSQL 侧停留在 V37 创建的
--       VARCHAR(100)，两方言结构不一致。代码侧 UserPO.email 无长度约束，
--       以 MySQL 的 255 为准对齐。
-- 变更：扩展 email 到 VARCHAR(255)；索引 idx_sys_user_email_v43 已由 V43 建立。
-- 幂等性：ALTER COLUMN TYPE 对同一目标类型可重复执行。
-- ============================================================

ALTER TABLE sys_user ALTER COLUMN email TYPE VARCHAR(255);
