-- 脚本用途：创建轻量工作流引擎的核心表（工作流定义、流程实例、任务、审批记录）
-- 版本：v1.3.0 新增 Enterprise 版商业化卖点
-- 依赖：License 特性门控 workflow（仅 Enterprise 版可用）

-- 工作流定义表
CREATE TABLE `wf_workflow_definition` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `workflow_code` VARCHAR(100) NOT NULL COMMENT '工作流编码（唯一标识）',
    `workflow_name` VARCHAR(200) NOT NULL COMMENT '工作流名称',
    `version` INT NOT NULL DEFAULT 1 COMMENT '版本号',
    `node_config_json` TEXT NOT NULL COMMENT '节点配置JSON（DAG定义）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT/ACTIVE/SUSPENDED/ARCHIVED）',
    `description` VARCHAR(500) COMMENT '描述',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID（多租户隔离）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_workflow_code_version` (`workflow_code`, `version`, `deleted`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定义表';

-- 流程实例表
CREATE TABLE `wf_process_instance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `workflow_definition_id` BIGINT NOT NULL COMMENT '工作流定义ID',
    `business_key` VARCHAR(200) NOT NULL COMMENT '业务主键（外部关联键）',
    `current_node_key` VARCHAR(100) COMMENT '当前节点KEY',
    `status` VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT '状态（RUNNING/COMPLETED/REJECTED/CANCELLED/SUSPENDED）',
    `initiator_id` BIGINT NOT NULL COMMENT '发起人ID',
    `context_json` TEXT COMMENT '流程上下文JSON（变量、条件等）',
    `start_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID（多租户隔离）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_business_key` (`business_key`, `deleted`),
    KEY `idx_workflow_def` (`workflow_definition_id`),
    KEY `idx_tenant_initiator` (`tenant_id`, `initiator_id`),
    KEY `idx_status` (`status`),
    KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例表';

-- 任务表
CREATE TABLE `wf_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `process_instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `node_key` VARCHAR(100) NOT NULL COMMENT '节点KEY',
    `node_name` VARCHAR(200) NOT NULL COMMENT '节点名称',
    `assignee_id` BIGINT NOT NULL COMMENT '办理人ID',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING/COMPLETED/REJECTED/CANCELLED/TRANSFERRED）',
    `due_date` DATETIME COMMENT '到期时间',
    `completed_time` DATETIME COMMENT '完成时间',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID（多租户隔离）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_process_instance` (`process_instance_id`),
    KEY `idx_tenant_assignee_status` (`tenant_id`, `assignee_id`, `status`),
    KEY `idx_due_date` (`due_date`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

-- 审批记录表
CREATE TABLE `wf_approval_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `process_instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `approver_id` BIGINT NOT NULL COMMENT '审批人ID',
    `action` VARCHAR(20) NOT NULL COMMENT '操作（APPROVE/REJECT/TRANSFER/CANCEL）',
    `comment` VARCHAR(1000) COMMENT '审批意见',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID（多租户隔离）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_process_instance` (`process_instance_id`),
    KEY `idx_task` (`task_id`),
    KEY `idx_tenant_approver` (`tenant_id`, `approver_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录表';

-- 添加外键约束
ALTER TABLE `wf_process_instance`
    ADD CONSTRAINT `fk_process_workflow_def`
    FOREIGN KEY (`workflow_definition_id`)
    REFERENCES `wf_workflow_definition` (`id`);

ALTER TABLE `wf_task`
    ADD CONSTRAINT `fk_task_process_instance`
    FOREIGN KEY (`process_instance_id`)
    REFERENCES `wf_process_instance` (`id`);

ALTER TABLE `wf_approval_record`
    ADD CONSTRAINT `fk_approval_process_instance`
    FOREIGN KEY (`process_instance_id`)
    REFERENCES `wf_process_instance` (`id`),
    ADD CONSTRAINT `fk_approval_task`
    FOREIGN KEY (`task_id`)
    REFERENCES `wf_task` (`id`);
