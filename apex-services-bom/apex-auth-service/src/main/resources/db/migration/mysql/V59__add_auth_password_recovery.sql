-- 自助密码找回恢复单（jdk21-unified-auth-multitenancy 实施计划 O01）
-- 匿名跨租户可信服务端流程：联系方式与来源IP只存SHA-256摘要，不存明文；
-- 状态机 REQUESTED -> VERIFIED -> CONSUMED，异常路径 EXPIRED / MANUAL_REQUIRED，
-- 状态跃迁由应用层条件更新保证并发下最多一次成功。
CREATE TABLE auth_password_recovery (
    id              VARCHAR(64)  NOT NULL COMMENT '恢复单ID（UUID）',
    user_id         BIGINT       NULL     COMMENT '解析命中的账号ID；多命中或人工恢复场景为空',
    tenant_id       VARCHAR(64)  NULL     COMMENT '解析命中账号所属租户（服务端解析，客户端不可指定）',
    username        VARCHAR(64)  NULL     COMMENT '命中账号用户名（用于密码策略校验）',
    channel         VARCHAR(16)  NOT NULL COMMENT '恢复通道：EMAIL/SMS',
    contact_hash    CHAR(64)     NOT NULL COMMENT '联系方式SHA-256摘要（不存明文）',
    status          VARCHAR(32)  NOT NULL COMMENT '状态：REQUESTED/VERIFIED/CONSUMED/EXPIRED/MANUAL_REQUIRED',
    attempt_count   INT          NOT NULL DEFAULT 0 COMMENT '验证码校验失败次数',
    request_ip_hash CHAR(64)     NULL     COMMENT '申请来源IP摘要（限流与审计）',
    expires_at      DATETIME     NOT NULL COMMENT '验证码过期时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_apr_contact_time (contact_hash, created_at),
    KEY idx_apr_ip_time (request_ip_hash, created_at),
    KEY idx_apr_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '自助密码找回恢复单';
