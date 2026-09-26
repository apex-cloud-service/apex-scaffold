-- V46: 禁用未实现的系统任务

-- 更新 sys_job 表，禁用未实现的系统任务
UPDATE sys_job
SET status = 0,
    remark = CONCAT(remark, '（待实现）'),
    update_time = CURRENT_TIMESTAMP
WHERE id IN (1, 2, 3, 4)
AND tenant_id = 'default';
