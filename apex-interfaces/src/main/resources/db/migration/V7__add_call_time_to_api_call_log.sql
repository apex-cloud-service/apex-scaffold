-- ================================================================
-- Apex Scaffold V7 迁移：sys_api_call_log 补齐 request_id / call_time 列
-- ================================================================
-- 背景：业务 PO（ApiCallLogPO）与 API 调用日志查询依赖 request_id、call_time
--       两列，但 V1 基线建表时遗漏（V1 仅含 trace_id 等基础列）；旧 it 测试
--       基于 H2 版 schema-it.sql（含这两列）运行，掩盖了缺陷；切换为
--       「各环境统一 MySQL 迁移集」后暴露（真实 MySQL 上写入/查询
--       sys_api_call_log 会报 Unknown column 'request_id' / 'call_time'）。
-- 修复：新增本迁移补列并加请求ID索引；已发布基线 V1 不做修改
--       （避免破坏 Flyway checksum）。
-- ================================================================

ALTER TABLE `sys_api_call_log`
    ADD COLUMN `request_id` VARCHAR(64) DEFAULT NULL COMMENT '请求唯一标识UUID' AFTER `trace_id`,
    ADD COLUMN `call_time` DATETIME DEFAULT NULL COMMENT '调用发起时间' AFTER `request_id`,
    ADD KEY `idx_request_id` (`request_id`);
