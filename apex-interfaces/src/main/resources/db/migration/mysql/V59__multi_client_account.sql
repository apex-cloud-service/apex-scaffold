-- ============================================================
-- V59: 多端统一账户基础（MySQL）
-- ============================================================
-- 变更内容：
--   1) sys_user 增加 register_channel（注册来源），用于区分账户来源
--   2) 新建 sys_user_social 多端社交身份绑定表：同一用户在微信开放平台
--      App / 小程序 / 公众号下的不同 OpenID 各占一行，以 (channel, open_id) 唯一
--   3) 存量 sys_user.open_id（真实微信 OpenID）迁移为 channel=WECHAT_APP 的绑定行
--   4) sys_login_log 增加 client_type / platform 终端维度字段
-- 幂等性：列/表/索引均带存在性校验，回填语句带 NOT EXISTS 防重，可重复执行。
-- 关联方案：docs/02-architecture/multi-client-integration-plan.md
-- ============================================================

-- 1. sys_user 增加 register_channel（注册来源）
SET @ddl_register_channel = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user')
    AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
              AND COLUMN_NAME = 'register_channel'),
    'ALTER TABLE `sys_user` ADD COLUMN `register_channel` VARCHAR(32) NOT NULL DEFAULT ''UNKNOWN'' COMMENT ''注册来源(UNKNOWN/SYSTEM/PC_WEB/PC_ADMIN/APP_ANDROID/APP_IOS/APP_HARMONY/MINI_PROGRAM/WECHAT_MP/H5)''',
    'SELECT 1'));
PREPARE stmt FROM @ddl_register_channel;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- register_channel 查询索引（低基数，仅用于按来源统计与运营筛选）
SET @ddl_user_channel_idx = (SELECT IF(
    NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
              AND INDEX_NAME = 'idx_sys_user_register_channel'),
    'CREATE INDEX idx_sys_user_register_channel ON `sys_user` (`register_channel`)',
    'SELECT 1'));
PREPARE stmt FROM @ddl_user_channel_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 用户社交身份绑定表
CREATE TABLE IF NOT EXISTS `sys_user_social` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `tenant_id`   VARCHAR(64)  DEFAULT NULL COMMENT '租户ID(多租户隔离)',
  `user_id`     BIGINT       NOT NULL COMMENT '用户ID(sys_user.id)',
  `channel`     VARCHAR(32)  NOT NULL COMMENT '社交渠道(WECHAT_APP/WECHAT_MINI/WECHAT_MP)',
  `open_id`     VARCHAR(100) NOT NULL COMMENT '该渠道下的OpenID',
  `union_id`    VARCHAR(100) DEFAULT NULL COMMENT '微信UnionID',
  `status`      INT(1)       NOT NULL DEFAULT 1 COMMENT '状态(0-解绑,1-正常)',
  `bound_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
  `deleted`     INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 渠道+OpenID 唯一：同一渠道同一微信身份只能绑定一个用户
  UNIQUE KEY `uk_channel_openid` (`channel`, `open_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_union_id` (`union_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户社交身份绑定表(多端微信OpenID)';

-- 3. 存量微信 OpenID 迁移为 WECHAT_APP 绑定行（PHONE_ 前缀为手机号注册占位符，跳过）
INSERT INTO `sys_user_social` (`tenant_id`, `user_id`, `channel`, `open_id`, `union_id`, `status`, `bound_at`)
SELECT u.`tenant_id`, u.`id`, 'WECHAT_APP', u.`open_id`, u.`union_id`, 1,
       COALESCE(u.`create_time`, CURRENT_TIMESTAMP)
FROM `sys_user` u
WHERE u.`open_id` IS NOT NULL
  AND u.`open_id` NOT LIKE 'PHONE\_%'
  AND u.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `sys_user_social` s
      WHERE s.`channel` = 'WECHAT_APP' AND s.`open_id` = u.`open_id`);

-- 4. sys_login_log 增加终端维度字段
SET @ddl_login_log_client = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_log')
    AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_log'
              AND COLUMN_NAME = 'client_type'),
    'ALTER TABLE `sys_login_log` ADD COLUMN `client_type` VARCHAR(32) DEFAULT NULL COMMENT ''客户端类型(PC_ADMIN/PC_WEB/WECHAT_MP/MINI_PROGRAM/APP_ANDROID/APP_IOS/H5/UNKNOWN)''',
    'SELECT 1'));
PREPARE stmt FROM @ddl_login_log_client;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl_login_log_platform = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_log')
    AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_log'
              AND COLUMN_NAME = 'platform'),
    'ALTER TABLE `sys_login_log` ADD COLUMN `platform` VARCHAR(32) DEFAULT NULL COMMENT ''客户端平台(android/ios/h5/harmony/wechat)''',
    'SELECT 1'));
PREPARE stmt FROM @ddl_login_log_platform;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
