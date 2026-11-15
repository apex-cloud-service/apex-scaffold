-- ============================================================
-- 知识库文档删除清理生命周期（V2）
-- 新增文档存储/索引定位快照与 kb_document_work、kb_document_cleanup 持久凭证。
-- 不修改 V1 已执行结构；历史未知存储位置不由本迁移推断。
-- ============================================================

ALTER TABLE kb_document
    ADD COLUMN storage_provider VARCHAR(64),
    ADD COLUMN storage_bucket VARCHAR(255),
    ADD COLUMN object_owned TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN index_endpoint VARCHAR(500),
    ADD COLUMN index_name VARCHAR(255),
    ADD COLUMN index_required TINYINT NOT NULL DEFAULT 0;

CREATE TABLE kb_document_work (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    document_id BIGINT NOT NULL,
    owner VARCHAR(64),
    generation BIGINT NOT NULL DEFAULT 0,
    kind VARCHAR(20),
    lease_until DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_kb_work_document (tenant_id, document_id),
    KEY idx_kb_work_lease (tenant_id, lease_until, document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE kb_document_cleanup (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    document_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    storage_provider VARCHAR(64),
    storage_bucket VARCHAR(255),
    object_key VARCHAR(500),
    object_required TINYINT NOT NULL DEFAULT 0,
    object_removed TINYINT NOT NULL DEFAULT 0,
    index_endpoint VARCHAR(500),
    index_name VARCHAR(255),
    index_required TINYINT NOT NULL DEFAULT 0,
    index_confirmed TINYINT NOT NULL DEFAULT 0,
    owner VARCHAR(64),
    generation BIGINT NOT NULL DEFAULT 0,
    lease_until DATETIME,
    attempts INT NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    next_retry_at DATETIME,
    next_review_at DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_kb_cleanup_document (tenant_id, document_id),
    KEY idx_kb_cleanup_retry (tenant_id, status, next_retry_at, document_id),
    KEY idx_kb_cleanup_review (tenant_id, mode, next_review_at, document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_kb_document_cleanup_scan ON kb_document (tenant_id, deleted, id);
CREATE INDEX idx_kb_chunk_cleanup ON kb_chunk (tenant_id, document_id, deleted);

INSERT INTO kb_document_work (id, tenant_id, document_id)
SELECT id, tenant_id, id FROM kb_document WHERE tenant_id IS NOT NULL AND TRIM(tenant_id) <> '';
