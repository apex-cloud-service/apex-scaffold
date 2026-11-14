-- ============================================================
-- V59: 多端统一账户基础（PostgreSQL）
-- ============================================================
-- 变更内容：与 mysql/V59__multi_client_account.sql 对齐。
--   1) sys_user 增加 register_channel（注册来源）
--   2) 新建 sys_user_social 多端社交身份绑定表
--   3) 存量 sys_user.open_id 迁移为 channel=WECHAT_APP 的绑定行
--   4) sys_login_log 增加 client_type / platform 终端维度字段
-- 幂等性：ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS /
--         CREATE INDEX IF NOT EXISTS / 回填带 NOT EXISTS 防重，可重复执行。
-- ============================================================

-- 1. sys_user 增加 register_channel（注册来源）
ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS register_channel VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN';

CREATE INDEX IF NOT EXISTS idx_sys_user_register_channel ON sys_user (register_channel);

-- 2. 用户社交身份绑定表
CREATE TABLE IF NOT EXISTS sys_user_social (
  id          BIGINT       NOT NULL,
  tenant_id   VARCHAR(64)  DEFAULT NULL,
  user_id     BIGINT       NOT NULL,
  channel     VARCHAR(32)  NOT NULL,
  open_id     VARCHAR(100) NOT NULL,
  union_id    VARCHAR(100) DEFAULT NULL,
  status      INTEGER      NOT NULL DEFAULT 1,
  bound_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version     INTEGER      NOT NULL DEFAULT 0,
  deleted     INTEGER      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 渠道+OpenID 唯一：同一渠道同一微信身份只能绑定一个用户
  CONSTRAINT uk_channel_openid UNIQUE (channel, open_id)
);

CREATE INDEX IF NOT EXISTS idx_user_social_user_id ON sys_user_social (user_id);
CREATE INDEX IF NOT EXISTS idx_user_social_union_id ON sys_user_social (union_id);

-- 3. 存量微信 OpenID 迁移为 WECHAT_APP 绑定行（PHONE_ 前缀为手机号注册占位符，跳过）
INSERT INTO sys_user_social (id, tenant_id, user_id, channel, open_id, union_id, status, bound_at)
SELECT u.id, u.tenant_id, u.id, 'WECHAT_APP', u.open_id, u.union_id, 1,
       COALESCE(u.create_time, CURRENT_TIMESTAMP)
FROM sys_user u
WHERE u.open_id IS NOT NULL
  AND u.open_id NOT LIKE 'PHONE\_%'
  AND u.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_social s
      WHERE s.channel = 'WECHAT_APP' AND s.open_id = u.open_id);

-- 4. sys_login_log 增加终端维度字段
ALTER TABLE sys_login_log ADD COLUMN IF NOT EXISTS client_type VARCHAR(32) DEFAULT NULL;
ALTER TABLE sys_login_log ADD COLUMN IF NOT EXISTS platform VARCHAR(32) DEFAULT NULL;
