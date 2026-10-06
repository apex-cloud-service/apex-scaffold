-- ============================================================
-- V55: auth_user 关联统一账户（MySQL）
-- ============================================================
-- 变更内容：auth_user 增加 account_id，关联主应用统一账户 sys_user.id。
-- 认证中心保留独立凭证/MFA/风控能力，账户主数据收敛到 sys_user；
-- 登录签发的 JWT 携带 account_id，主应用据此装配统一身份。
-- 存量数据回填（按 username/phone 匹配 sys_user）在批次 5 以迁移步骤执行。
-- 幂等性：列/索引均带存在性校验，可重复执行。
-- 关联方案：docs/02-architecture/multi-client-integration-plan.md
-- ============================================================

SET @ddl_account_id = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'auth_user')
    AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'auth_user'
              AND COLUMN_NAME = 'account_id'),
    'ALTER TABLE `auth_user` ADD COLUMN `account_id` BIGINT DEFAULT NULL COMMENT ''统一账户ID(sys_user.id，主应用账户体系)''',
    'SELECT 1'));
PREPARE stmt FROM @ddl_account_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl_account_idx = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'auth_user'
              AND INDEX_NAME = 'idx_auth_user_account_id'),
    'SELECT 1',
    'CREATE INDEX idx_auth_user_account_id ON `auth_user` (`account_id`)'));
PREPARE stmt FROM @ddl_account_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
