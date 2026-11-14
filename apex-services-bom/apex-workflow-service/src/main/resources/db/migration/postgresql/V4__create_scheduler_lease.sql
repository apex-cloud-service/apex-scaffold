-- ============================================================
-- Apex Scaffold - 工作流服务 V4：后台扫描租约表（PostgreSQL）
-- ============================================================
-- 【用途】
--   WorkflowTimeoutHandler 的进程内 AtomicBoolean 只能阻止同 JVM 并发扫描；
--   多实例部署时需要一个跨进程互斥。工作流服务未引入 Redis，故使用数据库租约：
--   抢占方写入 owner 与 lease_until，其余实例在租约到期前无法抢占。
--
-- 【语义】
--   - 单行固定记录（lock_name = 'workflow-timeout-scan'），由本脚本预置；
--   - 抢占 = 条件 UPDATE（owner 相同或租约已过期），影响 1 行即成功；
--   - 释放 = 将 lease_until 置为当前时间，任何实例可立即接管；
--   - 租约到期即自动失效，避免实例崩溃后永久锁死。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

CREATE TABLE IF NOT EXISTS wf_scheduler_lease (
    lock_name   VARCHAR(100) NOT NULL,
    owner       VARCHAR(100) NOT NULL DEFAULT '',
    lease_until TIMESTAMP    NOT NULL DEFAULT TIMESTAMP '1970-01-01 00:00:00',
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (lock_name)
);

COMMENT ON TABLE  wf_scheduler_lease IS '后台调度租约表';
COMMENT ON COLUMN wf_scheduler_lease.owner IS '持有者实例标识';
COMMENT ON COLUMN wf_scheduler_lease.lease_until IS '租约到期时间';

INSERT INTO wf_scheduler_lease (lock_name, owner, lease_until, update_time)
VALUES ('workflow-timeout-scan', '', TIMESTAMP '1970-01-01 00:00:00', CURRENT_TIMESTAMP)
ON CONFLICT (lock_name) DO NOTHING;
