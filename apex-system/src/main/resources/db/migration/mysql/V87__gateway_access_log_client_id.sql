-- ============================================================
-- V87: 网关访问日志补充 client 维度（MySQL）
-- ============================================================
-- 变更内容：
--   sys_gateway_access_log 新增 client_id 列与索引：开放接口（/open-api/**，
--   方案 D2）访问审计要求按应用（OAuth2 client）维度追溯，网关在组装访问日志时
--   从已验签令牌的 client_id claim 取值写入；普通用户请求为 NULL。
-- 兼容性：条件式 ALTER（列已存在则跳过），可重复执行；历史行 client_id 为 NULL。
-- 关联方案：docs/08-project-management/plans/unified-auth-tenant-platform-remediation-plan.md D2
-- ============================================================

SET @ddl = (
    SELECT IF(
        NOT EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sys_gateway_access_log'
              AND COLUMN_NAME = 'client_id'
        ),
        'ALTER TABLE `sys_gateway_access_log` ADD COLUMN `client_id` VARCHAR(100) DEFAULT NULL COMMENT ''客户端ID(开放应用,可空)'' AFTER `tenant_id`',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        NOT EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'sys_gateway_access_log'
              AND INDEX_NAME = 'idx_sys_gateway_access_log_client'
        ),
        'CREATE INDEX `idx_sys_gateway_access_log_client` ON `sys_gateway_access_log` (`client_id`, `create_time`)',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
