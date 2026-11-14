-- 历史记录保持 NULL，禁止将无法核实的文件自动归属于当前租户或默认提供商。
ALTER TABLE file_record ADD COLUMN tenant_id VARCHAR(64) NULL;
ALTER TABLE file_record ADD COLUMN storage_type VARCHAR(32) NULL;
ALTER TABLE file_chunk ADD COLUMN tenant_id VARCHAR(64) NULL;
ALTER TABLE file_chunk ADD COLUMN storage_type VARCHAR(32) NULL;
CREATE INDEX idx_file_record_ownership ON file_record (tenant_id, user_id);
CREATE INDEX idx_file_chunk_ownership ON file_chunk (tenant_id, user_id);
