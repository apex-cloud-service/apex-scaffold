-- ============================================================
-- Apex Scaffold - 办公服务独立库 V9：审批撤销编排状态（MySQL）
-- ============================================================
-- 【用途】
--   为审批撤销链路补充幂等重放与补偿所需的编排状态落库（方案 S4-b 参照实现）。
--   撤销调用涉及"远端平台撤销 + 本地流程状态更新"两步，远程调用无法与本地
--   事务原子提交：远端成功而本地更新失败时必须能识别并补偿，重复请求必须能
--   直接返回既有结果而不是再次触发远端撤销。此前的实现没有幂等键，也没有
--   中间状态，无法满足上述两项要求。
--
-- 【表清单】
--   off_approval_cancel_operation  审批撤销操作编排状态（幂等键 + 阶段状态 + 失败原因）
--
-- 【约束】
--   idempotency_key 在租户内唯一（(tenant_id, idempotency_key, deleted)），用于重放识别；
--   status 取值 PENDING/REMOTE_CANCELLED/COMPLETED/FAILED（应用层枚举约束）；
--     PENDING           已受理，尚未收到远端结果
--     REMOTE_CANCELLED  远端撤销成功但本地流程状态尚未落库（补偿待完成）
--     COMPLETED         远端与本地均已完成，重放直接返回成功
--     FAILED            远端或本地失败，需人工/定时任务重试
--   error_message 保留最近一次失败原因，成功后不清理以保留审计线索；
--   删除采用逻辑删除，历史编排记录保留。
--
-- 【幂等性】
--   CREATE TABLE IF NOT EXISTS，可重复执行。
--
-- 【方言约定】
--   MySQL 统一使用 DATETIME，禁止 TIMESTAMP；字符集 utf8mb4；
--   tenant_id 使用 BIGINT，与 ApprovalProcessPO.tenantId(Long) 对齐。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.4.0
-- ============================================================

-- ------------------------------------------------------------
-- 审批撤销操作编排状态
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `off_approval_cancel_operation` (
    `id`                  BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `tenant_id`           BIGINT       NOT NULL COMMENT '租户ID',
    `process_instance_id` VARCHAR(64)  NOT NULL COMMENT '业务流程实例ID（审批流程业务主键）',
    `idempotency_key`     VARCHAR(128) NOT NULL COMMENT '幂等键（客户端传入，租户内唯一）',
    `status`              VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '编排状态（PENDING/REMOTE_CANCELLED/COMPLETED/FAILED）',
    `error_message`       VARCHAR(512)          DEFAULT NULL COMMENT '最近一次失败原因（成功时为空）',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `version`             INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_off_approval_cancel_operation` (`tenant_id`, `idempotency_key`, `deleted`),
    KEY `idx_off_approval_cancel_operation_process` (`tenant_id`, `process_instance_id`, `deleted`),
    KEY `idx_off_approval_cancel_operation_status` (`tenant_id`, `status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批撤销操作编排状态';
