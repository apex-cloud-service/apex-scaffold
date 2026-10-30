-- ============================================================
-- V7: 分片活跃记录唯一性约束（MySQL）
-- ============================================================
-- 防止并发上传同一 file_id/chunk_index 写入多个对象与元数据。
-- deleted 纳入唯一键，允许逻辑删除后新会话复用同一分片序号。
-- 执行前必须清理历史重复活跃记录，否则迁移会失败。
-- ============================================================

ALTER TABLE `file_chunk`
    DROP INDEX `idx_file_chunk_file_index`,
    ADD UNIQUE KEY `uk_file_chunk_file_index_deleted` (`file_id`, `chunk_index`, `deleted`),
    ADD KEY `idx_file_chunk_file_index` (`file_id`, `chunk_index`);
