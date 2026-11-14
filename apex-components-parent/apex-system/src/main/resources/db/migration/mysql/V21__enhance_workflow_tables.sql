-- ============================================================
-- Flyway 迁移脚本 V21：增强工作流表
-- ============================================================
-- 执行日期: 2026-09-04
-- 目标: 为工作流表添加高级功能支持（回退、转办、挂起、超时处理）
-- ============================================================

-- 为流程实例表添加挂起相关字段
ALTER TABLE wf_process_instance
    ADD COLUMN suspended TINYINT(1) DEFAULT 0 COMMENT '是否挂起: 0-否 1-是',
    ADD COLUMN suspend_reason VARCHAR(500) COMMENT '挂起原因',
    ADD COLUMN suspend_time DATETIME COMMENT '挂起时间',
    ADD COLUMN resume_time DATETIME COMMENT '恢复时间';

-- 为流程实例表添加状态索引（加速按状态查询）
CREATE INDEX idx_process_instance_status ON wf_process_instance(status);

-- 为流程实例表添加挂起状态索引
CREATE INDEX idx_process_instance_suspended ON wf_process_instance(suspended);

-- 为任务表添加超时相关字段
ALTER TABLE wf_task
    ADD COLUMN timeout_at DATETIME COMMENT '超时时间',
    ADD COLUMN timeout_action VARCHAR(50) COMMENT '超时动作: AUTO_APPROVE/AUTO_REJECT/REMIND',
    ADD COLUMN timeout_handled TINYINT(1) DEFAULT 0 COMMENT '是否已处理超时: 0-否 1-是';

-- 为任务表添加转办/委派相关字段
ALTER TABLE wf_task
    ADD COLUMN transferred_from BIGINT COMMENT '转办来源用户ID',
    ADD COLUMN transferred_time DATETIME COMMENT '转办时间',
    ADD COLUMN delegated_from BIGINT COMMENT '委派来源用户ID',
    ADD COLUMN delegated_time DATETIME COMMENT '委派时间',
    ADD COLUMN delegation_completed TINYINT(1) DEFAULT 0 COMMENT '委派是否已完成: 0-否 1-是';

-- 为任务表添加复合索引（加速待办任务查询）
CREATE INDEX idx_task_status_assignee ON wf_task(status, assignee_id);

-- 为任务表添加超时任务查询索引
CREATE INDEX idx_task_timeout ON wf_task(timeout_at, timeout_handled);

-- 为任务表添加转办来源索引
CREATE INDEX idx_task_transferred_from ON wf_task(transferred_from);

-- 为审批记录表添加索引（加速按实例ID查询）
CREATE INDEX idx_approval_record_instance ON wf_approval_record(process_instance_id);

-- 为审批记录表添加审批人索引
CREATE INDEX idx_approval_record_approver ON wf_approval_record(approver_id);

-- 创建任务历史表（用于记录任务流转历史）
CREATE TABLE IF NOT EXISTS wf_task_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    process_instance_id BIGINT NOT NULL COMMENT '流程实例ID',
    action_type VARCHAR(50) NOT NULL COMMENT '动作类型: CREATE/COMPLETE/REJECT/TRANSFER/DELEGATE/TIMEOUT',
    operator_id BIGINT COMMENT '操作人ID',
    operator_name VARCHAR(100) COMMENT '操作人姓名',
    from_status VARCHAR(50) COMMENT '原状态',
    to_status VARCHAR(50) COMMENT '目标状态',
    comment VARCHAR(500) COMMENT '操作备注',
    action_time DATETIME NOT NULL COMMENT '操作时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    INDEX idx_task_history_task_id (task_id),
    INDEX idx_task_history_instance_id (process_instance_id),
    INDEX idx_task_history_action_time (action_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务历史记录表';

-- 创建流程回退历史表
CREATE TABLE IF NOT EXISTS wf_rollback_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    process_instance_id BIGINT NOT NULL COMMENT '流程实例ID',
    from_node_id VARCHAR(100) NOT NULL COMMENT '回退前节点ID',
    to_node_id VARCHAR(100) NOT NULL COMMENT '回退目标节点ID',
    rollback_reason VARCHAR(500) COMMENT '回退原因',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(100) COMMENT '操作人姓名',
    rollback_time DATETIME NOT NULL COMMENT '回退时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    INDEX idx_rollback_history_instance (process_instance_id),
    INDEX idx_rollback_history_time (rollback_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程回退历史表';

-- 为 wf_workflow_definition 表添加版本管理字段（version 已由 V20 创建）
ALTER TABLE wf_workflow_definition
    ADD COLUMN is_latest TINYINT(1) DEFAULT 1 COMMENT '是否最新版本: 0-否 1-是',
    ADD COLUMN previous_version_id BIGINT COMMENT '前一版本ID';

-- 为 wf_workflow_definition 表添加版本索引
CREATE INDEX idx_definition_code_version ON wf_workflow_definition(workflow_code, version);
CREATE INDEX idx_definition_is_latest ON wf_workflow_definition(is_latest);

-- 插入配置说明数据（可选，用于记录迁移信息）
INSERT INTO sys_config (id, config_name, config_key, config_value, config_type, remark, create_time, update_time)
VALUES (21001, '工作流引擎版本', 'workflow.version', 'v1.4.0', 1, '工作流引擎版本：支持回退、转办、挂起、超时处理', NOW(), NOW())
ON DUPLICATE KEY UPDATE config_value = 'v1.4.0', remark = '工作流引擎版本：支持回退、转办、挂起、超时处理', update_time = NOW();

-- ============================================================
-- 迁移说明
-- ============================================================
-- 1. 本迁移脚本为工作流引擎添加了高级功能支持
-- 2. 所有 ALTER TABLE 操作使用 ADD COLUMN，对现有数据无影响
-- 3. 索引优化可显著提升查询性能（特别是待办任务列表）
-- 4. 新增的历史表用于审计和问题排查
-- 5. 版本管理字段为未来流程定义版本管理预留扩展空间
-- ============================================================
