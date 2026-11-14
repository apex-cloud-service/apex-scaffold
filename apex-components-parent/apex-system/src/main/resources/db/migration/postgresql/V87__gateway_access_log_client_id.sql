-- ============================================================
-- V87: 网关访问日志补充 client 维度（PostgreSQL）
-- ============================================================
-- 变更内容：
--   sys_gateway_access_log 新增 client_id 列与索引：开放接口（/open-api/**，
--   方案 D2）访问审计要求按应用（OAuth2 client）维度追溯，网关在组装访问日志时
--   从已验签令牌的 client_id claim 取值写入；普通用户请求为 NULL。
-- 兼容性：IF NOT EXISTS 条件变更，可重复执行；历史行 client_id 为 NULL。
-- 关联方案：docs/08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md D2
-- ============================================================

DO $$
BEGIN
    IF to_regclass('sys_gateway_access_log') IS NOT NULL THEN
        ALTER TABLE sys_gateway_access_log
            ADD COLUMN IF NOT EXISTS client_id VARCHAR(100) DEFAULT NULL;
        CREATE INDEX IF NOT EXISTS idx_sys_gateway_access_log_client
            ON sys_gateway_access_log (client_id, create_time);
    END IF;
END $$;
