-- 脚本用途：移除所有外键约束，保持功能完整性
-- 版本：v1.4.0
-- 说明：移除外键可以提高写入性能，避免级联删除风险，应用层通过逻辑保证数据完整性

-- 1. 移除部门相关外键约束
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_sys_role_dept_tenant_dept'
    ) THEN
        ALTER TABLE sys_role_dept DROP CONSTRAINT fk_sys_role_dept_tenant_dept;
    END IF;
END $$;

-- 2. 移除工作流引擎外键约束

-- 2.1 移除流程实例 → 工作流定义外键
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'fk_process_workflow_def' 
          AND conrelid = 'wf_process_instance'::regclass
    ) THEN
        ALTER TABLE wf_process_instance DROP CONSTRAINT fk_process_workflow_def;
    END IF;
END $$;

-- 2.2 移除任务 → 流程实例外键
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'fk_task_process_instance' 
          AND conrelid = 'wf_task'::regclass
    ) THEN
        ALTER TABLE wf_task DROP CONSTRAINT fk_task_process_instance;
    END IF;
END $$;

-- 2.3 移除审批记录 → 流程实例外键
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'fk_approval_process_instance' 
          AND conrelid = 'wf_approval_record'::regclass
    ) THEN
        ALTER TABLE wf_approval_record DROP CONSTRAINT fk_approval_process_instance;
    END IF;
END $$;

-- 2.4 移除审批记录 → 任务外键
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'fk_approval_task' 
          AND conrelid = 'wf_approval_record'::regclass
    ) THEN
        ALTER TABLE wf_approval_record DROP CONSTRAINT fk_approval_task;
    END IF;
END $$;

-- 功能保证说明：
-- 1. sys_role_dept 的租户+部门完整性由应用层代码保证，MyBatis-Plus租户拦截器自动过滤
-- 2. 工作流引擎的关联关系由WorkflowService、WorkflowQueryService等服务层逻辑保证
-- 3. 保留所有索引以确保查询性能不受影响
-- 4. 逻辑删除(deleted字段)防止误删数据
