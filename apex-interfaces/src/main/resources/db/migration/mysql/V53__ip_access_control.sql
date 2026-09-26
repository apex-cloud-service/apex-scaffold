-- IP访问控制表
CREATE TABLE IF NOT EXISTS ip_access_control (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    ip_address VARCHAR(128) NOT NULL COMMENT 'IP地址或CIDR（支持通配符）',
    type VARCHAR(20) NOT NULL COMMENT '控制类型：WHITELIST-白名单, BLACKLIST-黑名单',
    description VARCHAR(500) COMMENT '规则描述',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    priority INT NOT NULL DEFAULT 100 COMMENT '优先级（数字越小优先级越高）',
    expires_at DATETIME COMMENT '过期时间（黑名单可设置自动解禁）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    updated_at DATETIME COMMENT '更新时间',
    updated_by VARCHAR(64) COMMENT '更新人',
    INDEX idx_tenant_type (tenant_id, type),
    INDEX idx_ip_tenant (ip_address, tenant_id),
    INDEX idx_enabled (enabled),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP访问控制表';

-- 插入示例数据
INSERT INTO ip_access_control (tenant_id, ip_address, type, description, enabled, priority, created_by) VALUES
('default', '127.0.0.1', 'WHITELIST', '本地开发环境', TRUE, 1, 'system'),
('default', '192.168.1.0/24', 'WHITELIST', '内网IP段', TRUE, 10, 'system'),
('default', '10.0.0.*', 'WHITELIST', '办公网络通配符', TRUE, 10, 'system');
