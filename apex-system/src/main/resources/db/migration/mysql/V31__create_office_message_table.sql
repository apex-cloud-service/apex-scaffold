-- V29__create_office_message_table.sql
-- 创建办公消息表

CREATE TABLE IF NOT EXISTS office_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID（主键）',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    platform VARCHAR(50) NOT NULL COMMENT '办公平台类型（FEISHU、DINGTALK、WECOM）',
    app_id BIGINT NOT NULL COMMENT '应用ID',
    message_type VARCHAR(50) NOT NULL COMMENT '消息类型（TEXT、MARKDOWN、CARD、IMAGE、FILE）',
    recipient_type VARCHAR(50) NOT NULL COMMENT '接收者类型（USER、DEPARTMENT、GROUP、USER_LIST）',
    recipient_ids TEXT NOT NULL COMMENT '接收者ID列表（JSON数组）',
    title VARCHAR(500) COMMENT '消息标题',
    content TEXT NOT NULL COMMENT '消息内容',
    extra_data TEXT COMMENT '消息附加数据（JSON格式）',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '发送状态（PENDING、SENDING、SUCCESS、FAILED、PARTIAL_SUCCESS）',
    platform_message_id VARCHAR(200) COMMENT '平台返回的消息ID',
    result_message VARCHAR(1000) COMMENT '发送结果消息',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    max_retry_count INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    sent_at DATETIME COMMENT '发送时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by BIGINT COMMENT '创建者ID',
    updated_by BIGINT COMMENT '更新者ID',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0-未删除，1-已删除）',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    INDEX idx_tenant_platform (tenant_id, platform),
    INDEX idx_tenant_status (tenant_id, status),
    INDEX idx_platform_message_id (platform_message_id),
    INDEX idx_created_at (created_at),
    INDEX idx_sent_at (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公消息表';
