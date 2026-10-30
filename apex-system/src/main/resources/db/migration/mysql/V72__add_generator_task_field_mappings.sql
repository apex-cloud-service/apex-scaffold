-- ============================================================
-- V72: 代码生成异步任务补充字段映射快照（MySQL）
-- ============================================================
-- 脚本用途：为 gen_task 增加 field_mappings_json 列，保存提交时的字段映射，
--          使后台执行不依赖请求上下文即可还原列改名/类型覆盖。
-- 业务背景：O08b 异步生成进度。V71 建立 gen_task 时只固化了配置、预览令牌
--          与覆盖开关，字段映射仍留在请求体里。提交返回后请求上下文即释放，
--          后台线程必须从任务表读回同一份映射，否则生成结果与预览不一致。
-- 前置依赖：V71 已完成，gen_task 表存在。
-- 影响范围：仅 gen_task 新增 1 个可空列，不修改既有列与索引。
-- 幂等性：MySQL 8.0 无 ADD COLUMN IF NOT EXISTS，这里使用 information_schema
--          判断列是否存在后动态执行，可重复运行。
-- MySQL 兼容性：MySQL 8.0 语法，由 Flyway V72 顺序执行。
-- 数据风险：存量为空表示「提交时未指定映射」，与不加覆盖的语义一致，无需回填。
-- 运维注意：映射可能较长，使用 TEXT；不写入审计日志明文。
-- 迁移元信息：版本 V72；执行阶段为代码生成异步进度阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'gen_task'
      AND COLUMN_NAME = 'field_mappings_json');

SET @ddl := IF(
    @column_exists = 0,
    'ALTER TABLE `gen_task` ADD COLUMN `field_mappings_json` TEXT DEFAULT NULL COMMENT ''提交时的字段映射JSON快照'' AFTER `preview_token`',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 执行结果验证建议：
-- 1. SHOW COLUMNS FROM gen_task LIKE 'field_mappings_json';
-- ============================================================
