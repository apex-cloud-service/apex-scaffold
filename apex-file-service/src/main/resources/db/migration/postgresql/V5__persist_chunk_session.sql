-- 初始化约定不可从客户端后续请求或已上传分片反推；历史未知会话需重新初始化。
ALTER TABLE file_record ADD COLUMN total_chunks INT NULL;
ALTER TABLE file_record ADD COLUMN chunk_size BIGINT NULL;
