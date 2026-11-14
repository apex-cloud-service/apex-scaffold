-- psql 专用：先执行 consent_before_v40.sql，再在同一停写窗口执行本文件。
-- 从资源路径包含原始 V40（不改 checksum）；事务中暂存 consent 后清空表，
-- 使原始 ADD NOT NULL 可执行，再恢复所有 ID/业务数据/时间。故障全事务回滚。
-- 非 Flyway：不会更新历史表。DBA 必须审核完整 V40 与历史状态，不能自动 repair。
\set ON_ERROR_STOP on
BEGIN;
LOCK TABLE oauth2_authorization_consent IN ACCESS EXCLUSIVE MODE;
SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema()
    AND table_name='wf_workflow_definition' AND column_name='node_config_json') AS needs_v40 \gset
\if :needs_v40
CREATE TEMP TABLE apex_consent_v40_backup ON COMMIT DROP AS TABLE oauth2_authorization_consent;
TRUNCATE oauth2_authorization_consent;
ALTER TABLE oauth2_authorization_consent DROP CONSTRAINT oauth2_authorization_consent_pkey;
DROP INDEX uk_client_principal;
ALTER TABLE oauth2_authorization_consent DROP COLUMN id, DROP COLUMN created_at, DROP COLUMN updated_at;
ALTER TABLE oauth2_authorization_consent ADD PRIMARY KEY(registered_client_id,principal_name);
\ir ../../migration/postgresql/V40__fix_schema_inconsistencies_based_on_po.sql
INSERT INTO oauth2_authorization_consent(registered_client_id,principal_name,authorities,id,created_at,updated_at)
SELECT registered_client_id,principal_name,authorities,id,created_at,updated_at FROM apex_consent_v40_backup;
\else
-- 已转换数据库不重复执行无条件历史 DDL；不据此推定 Flyway 历史有效。
SELECT 'V40 source columns absent; review existing schema/history before proceeding' AS result;
\endif
COMMIT;
