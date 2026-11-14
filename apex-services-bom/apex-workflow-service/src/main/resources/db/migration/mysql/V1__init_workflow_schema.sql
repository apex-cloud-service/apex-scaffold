-- ============================================================
-- Apex Scaffold - 工作流服务独立库初始化脚本（MySQL）
-- ============================================================
-- 【用途】
--   工作流服务（apex-workflow-service）独立数据库 apex_workflow 的 Flyway 基线脚本，
--   与旧单体的迁移版本链解耦，新库从 V1 重新开始（迁移方案 §9.3 规则）。
--
-- 【表清单】（迁移方案 Phase 3.3）
--   wf_workflow_definition  工作流定义表
--   wf_process_instance     流程实例表
--   wf_task                 流程任务表
--   wf_approval_record      审批记录表
--
-- 【字段来源原则】
--   以代码实现（PO 类）为唯一事实来源（对齐 apex-interfaces V40 迁移原则）。
--   列名与以下类字段严格一一对应（MyBatis-Plus map-underscore-to-camel-case）：
--     com.apex.scaffold.workflow.infrastructure.persistence.po.WorkflowDefinitionPO
--     com.apex.scaffold.workflow.infrastructure.persistence.po.ProcessInstancePO
--     com.apex.scaffold.workflow.infrastructure.persistence.po.TaskPO
--     com.apex.scaffold.workflow.infrastructure.persistence.po.ApprovalRecordPO
--   并包含 Mapper 原生 SQL 依赖的列（如 wf_task.timeout_handled）。
--
-- 【方言约定】
--   MySQL 统一使用 DATETIME，禁止 TIMESTAMP；字符集 utf8mb4。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

-- ------------------------------------------------------------
-- 工作流定义表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wf_workflow_definition` (
    `id`              BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `tenant_id`       VARCHAR(64)  NOT NULL COMMENT '租户ID（多租户行级隔离）',
    `workflow_code`   VARCHAR(100) NOT NULL COMMENT '流程编码（同一编码可多版本）',
    `workflow_name`   VARCHAR(200) NOT NULL COMMENT '流程名称',
    `description`     VARCHAR(500)          DEFAULT NULL COMMENT '流程描述',
    `node_definition` TEXT                  DEFAULT NULL COMMENT '节点定义JSON（DAG）',
    `version`         INT          NOT NULL DEFAULT 1 COMMENT '流程版本号',
    `enabled`         INT          NOT NULL DEFAULT 1 COMMENT '启用标志（1-启用 0-停用）',
    `remark`          VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wf_def_code_version` (`workflow_code`, `version`, `deleted`),
    KEY `idx_wf_def_tenant` (`tenant_id`),
    KEY `idx_wf_def_enabled` (`enabled`),
    KEY `idx_wf_def_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定义表';

-- ------------------------------------------------------------
-- 流程实例表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wf_process_instance` (
    `id`                     BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `tenant_id`              VARCHAR(64)  NOT NULL COMMENT '租户ID（多租户行级隔离）',
    `workflow_definition_id` BIGINT       NOT NULL COMMENT '工作流定义ID',
    `business_key`           VARCHAR(200) NOT NULL COMMENT '业务主键（外部关联键）',
    `current_node_key`       VARCHAR(100)          DEFAULT NULL COMMENT '当前节点KEY',
    `status`                 VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT '状态（RUNNING/SUSPENDED/COMPLETED/REJECTED/TERMINATED）',
    `initiator_id`           BIGINT       NOT NULL COMMENT '发起人ID',
    `form_data`              TEXT                  DEFAULT NULL COMMENT '表单数据JSON（流程变量）',
    `start_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    `end_time`               DATETIME              DEFAULT NULL COMMENT '结束时间',
    `remark`                 VARCHAR(500)          DEFAULT NULL COMMENT '备注（复用为流程标题）',
    `suspended`              TINYINT      NOT NULL DEFAULT 0 COMMENT '是否挂起（1-是 0-否）',
    `create_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wf_instance_business_key` (`business_key`, `deleted`),
    KEY `idx_wf_instance_def` (`workflow_definition_id`),
    KEY `idx_wf_instance_tenant_initiator` (`tenant_id`, `initiator_id`),
    KEY `idx_wf_instance_status` (`status`),
    KEY `idx_wf_instance_suspended` (`suspended`),
    KEY `idx_wf_instance_start_time` (`start_time`),
    KEY `idx_wf_instance_end_time` (`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例表';

-- ------------------------------------------------------------
-- 流程任务表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wf_task` (
    `id`                  BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `tenant_id`           VARCHAR(64)  NOT NULL COMMENT '租户ID（多租户行级隔离）',
    `process_instance_id` BIGINT       NOT NULL COMMENT '流程实例ID',
    `node_code`           VARCHAR(100) NOT NULL COMMENT '节点编码',
    `task_name`           VARCHAR(200) NOT NULL COMMENT '任务名称（节点展示名）',
    `assignee_id`         BIGINT       NOT NULL COMMENT '待办人用户ID',
    `status`              VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING/COMPLETED/REJECTED/ROLLED_BACK/DELEGATED/TRANSFERRED/CANCELLED）',
    `due_time`            DATETIME              DEFAULT NULL COMMENT '处理截止时间',
    `complete_time`       DATETIME              DEFAULT NULL COMMENT '完成时间',
    `remark`              VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    `timeout_at`          DATETIME              DEFAULT NULL COMMENT '超时时间点',
    `timeout_strategy`    VARCHAR(50)           DEFAULT NULL COMMENT '超时策略（ESCALATE/AUTO_COMPLETE/NOTIFY）',
    `timeout_handled`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已处理超时（1-是 0-否）',
    `transferred_from`    BIGINT                DEFAULT NULL COMMENT '转办来源用户ID',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_wf_task_instance` (`process_instance_id`),
    KEY `idx_wf_task_tenant_assignee_status` (`tenant_id`, `assignee_id`, `status`),
    KEY `idx_wf_task_status_assignee` (`status`, `assignee_id`),
    KEY `idx_wf_task_due_time` (`due_time`),
    KEY `idx_wf_task_timeout` (`timeout_at`, `timeout_handled`),
    KEY `idx_wf_task_transferred_from` (`transferred_from`),
    KEY `idx_wf_task_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程任务表';

-- ------------------------------------------------------------
-- 审批记录表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wf_approval_record` (
    `id`                  BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `tenant_id`           VARCHAR(64)  NOT NULL COMMENT '租户ID（多租户行级隔离）',
    `process_instance_id` BIGINT       NOT NULL COMMENT '流程实例ID',
    `task_id`             BIGINT       NOT NULL COMMENT '关联任务ID',
    `node_code`           VARCHAR(100)          DEFAULT NULL COMMENT '节点编码',
    `approver_id`         BIGINT                DEFAULT NULL COMMENT '操作人用户ID',
    `action`              VARCHAR(20)           DEFAULT NULL COMMENT '处理动作（APPROVE/REJECT/TRANSFER/DELEGATE/ROLLBACK）',
    `opinion`             VARCHAR(1000)         DEFAULT NULL COMMENT '处理意见',
    `remark`              VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_wf_approval_instance` (`process_instance_id`),
    KEY `idx_wf_approval_task` (`task_id`),
    KEY `idx_wf_approval_tenant_approver` (`tenant_id`, `approver_id`),
    KEY `idx_wf_approval_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录表';
