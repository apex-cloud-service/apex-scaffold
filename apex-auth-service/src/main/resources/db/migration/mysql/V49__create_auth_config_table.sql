-- V49: 创建认证配置表
-- 作者: Apex Scaffold Team
-- 日期: 2024-09-21
-- 说明: 为 V48 的配置数据插入提供表结构支持

CREATE TABLE IF NOT EXISTS auth_config (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    config_type VARCHAR(64) NOT NULL COMMENT '配置类型（PASSWORD_POLICY, SESSION_POLICY, SECURITY_POLICY, MFA_POLICY）',
    config_key VARCHAR(128) NOT NULL COMMENT '配置键',
    config_value TEXT NOT NULL COMMENT '配置值（JSON格式）',
    description VARCHAR(500) COMMENT '配置描述',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用（0-禁用, 1-启用）',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认配置（0-否, 1-是）',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT COMMENT '创建人ID',
    updated_by BIGINT COMMENT '更新人ID',
    UNIQUE KEY uk_auth_config_tenant_type_key (tenant_id, config_type, config_key),
    KEY idx_auth_config_tenant (tenant_id),
    KEY idx_auth_config_type (config_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='认证配置表';
