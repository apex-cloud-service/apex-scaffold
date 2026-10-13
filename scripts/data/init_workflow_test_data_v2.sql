-- 工作流测试数据初始化脚本（修复版）
-- 用途：为工作流监控页面提供测试数据

USE apex_system;

-- 清理旧数据
DELETE FROM wf_approval_record WHERE deleted = 0;
DELETE FROM wf_task WHERE deleted = 0;
DELETE FROM wf_process_instance WHERE deleted = 0;
DELETE FROM wf_workflow_definition WHERE deleted = 0;

-- 1. 插入工作流定义（请假审批流程）
INSERT INTO wf_workflow_definition (
    workflow_code, 
    workflow_name, 
    version, 
    node_config_json, 
    status, 
    description, 
    tenant_id,
    deleted
) VALUES (
    'leave_approval',
    '请假审批流程',
    1,
    '{
        "nodes": [
            {"key": "start", "name": "开始", "type": "START"},
            {"key": "dept_manager", "name": "部门经理审批", "type": "APPROVAL"},
            {"key": "hr_manager", "name": "人事审批", "type": "APPROVAL"},
            {"key": "end", "name": "结束", "type": "END"}
        ],
        "edges": [
            {"from": "start", "to": "dept_manager"},
            {"from": "dept_manager", "to": "hr_manager", "condition": "days > 3"},
            {"from": "dept_manager", "to": "end", "condition": "days <= 3"},
            {"from": "hr_manager", "to": "end"}
        ]
    }',
    'ACTIVE',
    '员工请假审批流程，3天以内部门经理审批，超过3天需要人事审批',
    'default',
    0
);

SET @leave_workflow_id = LAST_INSERT_ID();

-- 2. 插入工作流定义（报销审批流程）
INSERT INTO wf_workflow_definition (
    workflow_code, 
    workflow_name, 
    version, 
    node_config_json, 
    status, 
    description, 
    tenant_id,
    deleted
) VALUES (
    'expense_approval',
    '报销审批流程',
    1,
    '{
        "nodes": [
            {"key": "start", "name": "开始", "type": "START"},
            {"key": "dept_manager", "name": "部门经理审批", "type": "APPROVAL"},
            {"key": "finance_manager", "name": "财务审批", "type": "APPROVAL"},
            {"key": "end", "name": "结束", "type": "END"}
        ],
        "edges": [
            {"from": "start", "to": "dept_manager"},
            {"from": "dept_manager", "to": "finance_manager", "condition": "amount > 1000"},
            {"from": "dept_manager", "to": "end", "condition": "amount <= 1000"},
            {"from": "finance_manager", "to": "end"}
        ]
    }',
    'ACTIVE',
    '员工报销审批流程，1000元以内部门经理审批，超过1000元需要财务审批',
    'default',
    0
);

SET @expense_workflow_id = LAST_INSERT_ID();

-- 3. 插入流程实例（已完成）
INSERT INTO wf_process_instance (
    workflow_definition_id,
    business_key,
    current_node_key,
    status,
    initiator_id,
    context_json,
    start_time,
    end_time,
    tenant_id,
    deleted
) VALUES 
(@leave_workflow_id, 'LEAVE-2026090701', 'end', 'COMPLETED', 1, '{"days": 2, "reason": "病假"}', '2026-09-01 09:00:00', '2026-09-01 15:30:00', 'default', 0),
(@leave_workflow_id, 'LEAVE-2026090702', 'end', 'COMPLETED', 2, '{"days": 1, "reason": "事假"}', '2026-09-02 10:00:00', '2026-09-02 14:00:00', 'default', 0),
(@expense_workflow_id, 'EXP-2026090701', 'end', 'COMPLETED', 1, '{"amount": 500, "type": "交通"}', '2026-09-03 11:00:00', '2026-09-03 16:00:00', 'default', 0);

-- 保存已完成实例的 ID
SET @leave_instance_1 = LAST_INSERT_ID();
SET @leave_instance_2 = @leave_instance_1 + 1;
SET @expense_instance_1 = @leave_instance_1 + 2;

-- 4. 插入流程实例（进行中）
INSERT INTO wf_process_instance (
    workflow_definition_id,
    business_key,
    current_node_key,
    status,
    initiator_id,
    context_json,
    start_time,
    tenant_id,
    deleted
) VALUES 
(@leave_workflow_id, 'LEAVE-2026090703', 'dept_manager', 'RUNNING', 3, '{"days": 5, "reason": "年假"}', '2026-09-07 09:00:00', 'default', 0),
(@expense_workflow_id, 'EXP-2026090702', 'finance_manager', 'RUNNING', 2, '{"amount": 2000, "type": "差旅"}', '2026-09-07 10:00:00', 'default', 0);

SET @leave_instance_running = LAST_INSERT_ID();
SET @expense_instance_running = @leave_instance_running + 1;

-- 5. 插入流程实例（已拒绝）
INSERT INTO wf_process_instance (
    workflow_definition_id,
    business_key,
    current_node_key,
    status,
    initiator_id,
    context_json,
    start_time,
    end_time,
    tenant_id,
    deleted
) VALUES 
(@leave_workflow_id, 'LEAVE-2026090704', 'dept_manager', 'REJECTED', 4, '{"days": 10, "reason": "旅游"}', '2026-09-04 09:00:00', '2026-09-04 11:00:00', 'default', 0);

-- 6. 插入任务（待办任务）
INSERT INTO wf_task (
    process_instance_id,
    node_key,
    node_name,
    assignee_id,
    status,
    tenant_id,
    deleted
) VALUES 
(@leave_instance_running, 'dept_manager', '部门经理审批', 10, 'PENDING', 'default', 0),
(@expense_instance_running, 'finance_manager', '财务审批', 20, 'PENDING', 'default', 0);

-- 7. 插入任务（已完成任务）
INSERT INTO wf_task (
    process_instance_id,
    node_key,
    node_name,
    assignee_id,
    status,
    completed_time,
    tenant_id,
    deleted
) VALUES 
(@leave_instance_1, 'dept_manager', '部门经理审批', 10, 'APPROVED', '2026-09-01 15:30:00', 'default', 0),
(@leave_instance_2, 'dept_manager', '部门经理审批', 10, 'APPROVED', '2026-09-02 14:00:00', 'default', 0),
(@expense_instance_1, 'dept_manager', '部门经理审批', 10, 'APPROVED', '2026-09-03 16:00:00', 'default', 0);

SET @task_1 = LAST_INSERT_ID();
SET @task_2 = @task_1 + 1;
SET @task_3 = @task_1 + 2;

-- 8. 插入审批记录
INSERT INTO wf_approval_record (
    task_id,
    process_instance_id,
    approver_id,
    action,
    comment,
    tenant_id,
    deleted
) VALUES 
(@task_1, @leave_instance_1, 10, 'APPROVE', '同意请假', 'default', 0),
(@task_2, @leave_instance_2, 10, 'APPROVE', '同意请假', 'default', 0),
(@task_3, @expense_instance_1, 10, 'APPROVE', '同意报销', 'default', 0);

-- 验证数据
SELECT 
    '工作流定义' as table_name, 
    COUNT(*) as count 
FROM wf_workflow_definition WHERE deleted = 0
UNION ALL
SELECT 
    '流程实例', 
    COUNT(*) 
FROM wf_process_instance WHERE deleted = 0
UNION ALL
SELECT 
    '待办任务', 
    COUNT(*) 
FROM wf_task WHERE deleted = 0 AND status = 'PENDING'
UNION ALL
SELECT 
    '已完成任务', 
    COUNT(*) 
FROM wf_task WHERE deleted = 0 AND status = 'APPROVED'
UNION ALL
SELECT 
    '审批记录',
    COUNT(*)
FROM wf_approval_record WHERE deleted = 0;
