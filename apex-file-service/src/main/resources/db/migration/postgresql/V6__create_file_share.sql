-- ============================================================
-- Apex Scaffold - 文件限时只读分享（PostgreSQL）
-- ============================================================
-- 【用途】
--   O05/T18：文件所有者可为 STORED 文件创建限时只读分享，匿名侧通过独立兑换入口访问。
--   本表只保存令牌的 SHA-256 摘要，明文令牌仅在创建响应中返回一次。
--
-- 【约束】
--   兑换时的租户与文件完全由本表记录确定，不信任匿名请求携带的身份信息；
--   分享过期、撤销、文件删除或租户停用后均拒绝新的兑换。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.3.2
-- ============================================================

CREATE TABLE IF NOT EXISTS file_share (
    id          BIGINT       NOT NULL,
    file_id     BIGINT       NOT NULL,
    tenant_id   VARCHAR(64),
    creator_id  BIGINT,
    token_hash  VARCHAR(64)  NOT NULL,
    expire_at   TIMESTAMP    NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     INT          NOT NULL DEFAULT 1,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_file_share_token_hash ON file_share (token_hash);
CREATE INDEX IF NOT EXISTS idx_file_share_file ON file_share (file_id);
CREATE INDEX IF NOT EXISTS idx_file_share_tenant ON file_share (tenant_id);
CREATE INDEX IF NOT EXISTS idx_file_share_creator ON file_share (creator_id);
CREATE INDEX IF NOT EXISTS idx_file_share_expire ON file_share (expire_at);

COMMENT ON TABLE  file_share IS '文件限时只读分享记录';
COMMENT ON COLUMN file_share.token_hash IS '分享令牌 SHA-256 十六进制摘要，不保存明文';
COMMENT ON COLUMN file_share.status IS '分享状态（ACTIVE/REVOKED）';
COMMENT ON COLUMN file_share.expire_at IS '分享失效时间';
