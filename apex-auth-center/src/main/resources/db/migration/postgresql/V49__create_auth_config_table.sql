-- V49: 创建认证配置表
-- 作者: Apex Scaffold Team
-- 日期: 2024-09-21
-- 说明: 为 V48 的配置数据插入提供表结构支持

CREATE TABLE IF NOT EXISTS auth_config (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    config_type VARCHAR(64) NOT NULL,
    config_key VARCHAR(128) NOT NULL,
    config_value TEXT NOT NULL,
    description VARCHAR(500),
    enabled SMALLINT NOT NULL DEFAULT 1,
    is_default SMALLINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    UNIQUE (tenant_id, config_type, config_key)
);

CREATE INDEX IF NOT EXISTS auth_config_idx_tenant ON auth_config (tenant_id);
CREATE INDEX IF NOT EXISTS auth_config_idx_type ON auth_config (config_type);

COMMENT ON TABLE auth_config IS '认证配置表';
COMMENT ON COLUMN auth_config.id IS '主键ID';
COMMENT ON COLUMN auth_config.tenant_id IS '租户ID';
COMMENT ON COLUMN auth_config.config_type IS '配置类型（PASSWORD_POLICY, SESSION_POLICY, SECURITY_POLICY, MFA_POLICY）';
COMMENT ON COLUMN auth_config.config_key IS '配置键';
COMMENT ON COLUMN auth_config.config_value IS '配置值（JSON格式）';
COMMENT ON COLUMN auth_config.description IS '配置描述';
COMMENT ON COLUMN auth_config.enabled IS '是否启用（0-禁用, 1-启用）';
COMMENT ON COLUMN auth_config.is_default IS '是否默认配置（0-否, 1-是）';
COMMENT ON COLUMN auth_config.version IS '乐观锁版本号';
COMMENT ON COLUMN auth_config.created_at IS '创建时间';
COMMENT ON COLUMN auth_config.updated_at IS '更新时间';
COMMENT ON COLUMN auth_config.created_by IS '创建人ID';
COMMENT ON COLUMN auth_config.updated_by IS '更新人ID';
