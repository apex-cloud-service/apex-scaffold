-- 自助密码找回恢复单（jdk21-unified-auth-multitenancy 实施计划 O01）
-- 匿名跨租户可信服务端流程：联系方式与来源IP只存SHA-256摘要，不存明文；
-- 状态机 REQUESTED -> VERIFIED -> CONSUMED，异常路径 EXPIRED / MANUAL_REQUIRED，
-- 状态跃迁由应用层条件更新保证并发下最多一次成功。
CREATE TABLE auth_password_recovery (
    id              VARCHAR(64)  NOT NULL,
    user_id         BIGINT       NULL,
    tenant_id       VARCHAR(64)  NULL,
    username        VARCHAR(64)  NULL,
    channel         VARCHAR(16)  NOT NULL,
    contact_hash    CHAR(64)     NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    attempt_count   INT          NOT NULL DEFAULT 0,
    request_ip_hash CHAR(64)     NULL,
    expires_at      TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_apr_contact_time ON auth_password_recovery (contact_hash, created_at);
CREATE INDEX idx_apr_ip_time ON auth_password_recovery (request_ip_hash, created_at);
CREATE INDEX idx_apr_user ON auth_password_recovery (user_id);

COMMENT ON TABLE auth_password_recovery IS '自助密码找回恢复单';
COMMENT ON COLUMN auth_password_recovery.id IS '恢复单ID（UUID）';
COMMENT ON COLUMN auth_password_recovery.user_id IS '解析命中的账号ID；多命中或人工恢复场景为空';
COMMENT ON COLUMN auth_password_recovery.tenant_id IS '解析命中账号所属租户（服务端解析，客户端不可指定）';
COMMENT ON COLUMN auth_password_recovery.username IS '命中账号用户名（用于密码策略校验）';
COMMENT ON COLUMN auth_password_recovery.channel IS '恢复通道：EMAIL/SMS';
COMMENT ON COLUMN auth_password_recovery.contact_hash IS '联系方式SHA-256摘要（不存明文）';
COMMENT ON COLUMN auth_password_recovery.status IS '状态：REQUESTED/VERIFIED/CONSUMED/EXPIRED/MANUAL_REQUIRED';
COMMENT ON COLUMN auth_password_recovery.attempt_count IS '验证码校验失败次数';
COMMENT ON COLUMN auth_password_recovery.request_ip_hash IS '申请来源IP摘要（限流与审计）';
COMMENT ON COLUMN auth_password_recovery.expires_at IS '验证码过期时间';
