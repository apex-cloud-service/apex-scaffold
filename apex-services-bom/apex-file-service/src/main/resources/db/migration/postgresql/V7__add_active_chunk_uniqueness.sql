-- ============================================================
-- V7: 分片活跃记录唯一性约束（PostgreSQL）
-- ============================================================
-- 防止并发上传同一 file_id/chunk_index 写入多个对象与元数据。
-- deleted 纳入唯一键，允许逻辑删除后新会话复用同一分片序号。
-- 执行前必须清理历史重复活跃记录，否则迁移会失败。
-- ============================================================

DROP INDEX IF EXISTS idx_file_chunk_file_index;
CREATE UNIQUE INDEX IF NOT EXISTS uk_file_chunk_file_index_deleted
    ON file_chunk (file_id, chunk_index, deleted);
CREATE INDEX IF NOT EXISTS idx_file_chunk_file_index
    ON file_chunk (file_id, chunk_index);
