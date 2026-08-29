-- ================================================================
-- Apex Scaffold V12 迁移：删除废弃的 audit_log 空表
-- ================================================================
-- 背景：早期审计写入曾使用 audit_log 表，2026-08-27 重构后操作审计
--       统一收敛为 @OperationLog + OperationLogAspect → sys_operation_log
--       表（写读同源）。经全库扫描确认：已无任何 Java 代码 / Mapper /
--       PO 引用 audit_log 表（仅 V1 基线中遗留空表定义，实际 0 行数据），
--       AuditLog* 命名类均改为查询 sys_operation_log。
-- 处理：以独立迁移 DROP 该空表，不动 V1 基线（避免破坏 Flyway checksum）。
-- 安全：删表前确认无数据（SELECT COUNT(*) = 0），且无外键依赖。
-- ================================================================

DROP TABLE IF EXISTS `audit_log`;
