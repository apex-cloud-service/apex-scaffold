-- 脚本用途：补齐 API 调用日志的请求标识、调用时间字段及请求标识索引。
-- 业务背景：修复持久化模型与 V1 表结构不一致导致的日志写入和查询异常。
-- 前置依赖：V1 已创建 sys_api_call_log，且目标列与 idx_request_id 尚不存在。
-- 影响范围：变更 sys_api_call_log 表结构并新增普通索引，不改写存量日志值。
-- 幂等性：ALTER TABLE 非幂等，重复执行会因列或索引已存在而失败。
-- MySQL/Flyway 兼容性：面向 MySQL 8.x DDL，由 Flyway V7 在前序迁移后执行。
-- 数据风险：大日志表执行 DDL 可能锁表、占用额外空间并造成复制延迟。
-- 运维注意：生产执行前评估在线 DDL 与磁盘余量，执行后验证字段、索引及日志链路。
-- 迁移元信息：版本 V7；执行阶段为 API 调用日志结构修复阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- Apex Scaffold V7 迁移：sys_api_call_log 补齐 request_id / call_time 列
-- ================================================================
-- 背景：业务 PO（ApiCallLogPO）与 API 调用日志查询依赖 request_id、call_time
--       两列，但 V1 基线建表时遗漏（V1 仅含 trace_id 等基础列）；旧 it 测试
--       基于历史 schema-it.sql（含这两列）运行，掩盖了缺陷；切换为
--       「各环境统一 MySQL 迁移集」后暴露（真实 MySQL 上写入/查询
--       sys_api_call_log 会报 Unknown column 'request_id' / 'call_time'）。
-- 修复：新增本迁移补列并加请求ID索引；已发布基线 V1 不做修改
--       （避免破坏 Flyway checksum）。
-- ================================================================

ALTER TABLE `sys_api_call_log`
    ADD COLUMN `request_id` VARCHAR(64) DEFAULT NULL COMMENT '请求唯一标识UUID' AFTER `trace_id`,
    ADD COLUMN `call_time` DATETIME DEFAULT NULL COMMENT '调用发起时间' AFTER `request_id`,
    ADD KEY `idx_request_id` (`request_id`);
