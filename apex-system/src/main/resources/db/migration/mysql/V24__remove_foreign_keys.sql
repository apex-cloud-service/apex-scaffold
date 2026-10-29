-- 脚本用途：移除所有外键约束，保持功能完整性
-- 版本：v1.4.0
-- 说明：移除外键可以提高写入性能，避免级联删除风险，应用层通过逻辑保证数据完整性

-- 1. 移除 V19 创建的部门外键约束
ALTER TABLE `sys_role_dept`
    DROP FOREIGN KEY `fk_sys_role_dept_tenant_dept`;

-- 2. 移除 V20 创建的工作流引擎外键约束
ALTER TABLE `wf_process_instance`
    DROP FOREIGN KEY `fk_process_workflow_def`;

ALTER TABLE `wf_task`
    DROP FOREIGN KEY `fk_task_process_instance`;

ALTER TABLE `wf_approval_record`
    DROP FOREIGN KEY `fk_approval_process_instance`,
    DROP FOREIGN KEY `fk_approval_task`;

-- 功能保证说明：
-- 1. sys_role_dept 的租户+部门完整性由应用层代码保证，MyBatis-Plus租户拦截器自动过滤
-- 2. 工作流引擎的关联关系由WorkflowService、WorkflowQueryService等服务层逻辑保证
-- 3. 保留所有索引以确保查询性能不受影响
-- 4. 逻辑删除(deleted字段)防止误删数据
