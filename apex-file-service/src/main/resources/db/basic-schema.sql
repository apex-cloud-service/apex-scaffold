-- ============================================================
-- Apex Scaffold - 文件服务 basic Profile 初始化脚本（H2）
-- ============================================================
-- 【用途】
--   基础版（basic）单机环境使用 H2 内存库（MODE=MySQL）时，
--   通过 spring.sql.init 加载本脚本初始化表结构，替代 Flyway。
--   表结构与应用代码 PO 保持一致（与 db/migration 下 MySQL/PostgreSQL V1 脚本对齐）。
--
-- 【表清单】（迁移方案 Phase 4.2）
--   file_record    文件元数据表（对应旧 sys_file）
--   file_document  文档表（对应旧 doc_document）
--   file_chart     图表表（对应旧 doc_chart）
--   file_chunk     分片上传表（文件服务新增）
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

-- ------------------------------------------------------------
-- 文件元数据表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS file_record (
    id                BIGINT       NOT NULL,
    file_name         VARCHAR(255),
    original_name     VARCHAR(255),
    file_path         VARCHAR(500),
    file_url          VARCHAR(500),
    file_size         BIGINT,
    file_type         VARCHAR(100),
    content_type      VARCHAR(255),
    bucket_name       VARCHAR(100),
    md5               VARCHAR(64),
    user_id           BIGINT,
    business_type     VARCHAR(50),
    client_request_id VARCHAR(128),
    storage_status    VARCHAR(20)  NOT NULL DEFAULT 'STORED',
    sha256            VARCHAR(64),
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version           INT          NOT NULL DEFAULT 1,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_file_record_user ON file_record (user_id);
CREATE INDEX IF NOT EXISTS idx_file_record_business_type ON file_record (business_type);
CREATE INDEX IF NOT EXISTS idx_file_record_client_request_id ON file_record (client_request_id);
CREATE INDEX IF NOT EXISTS idx_file_record_create_time ON file_record (create_time);

-- ------------------------------------------------------------
-- 文档表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS file_document (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    title           VARCHAR(255),
    doc_type        VARCHAR(50),
    category        VARCHAR(50),
    content         CLOB,
    file_url        VARCHAR(500),
    file_size       BIGINT,
    author_id       BIGINT,
    author_name     VARCHAR(100),
    status          INT          NOT NULL DEFAULT 1,
    tags            CLOB,
    view_count      INT          NOT NULL DEFAULT 0,
    download_count  INT          NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(64),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INT          NOT NULL DEFAULT 1,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_file_document_tenant ON file_document (tenant_id);
CREATE INDEX IF NOT EXISTS idx_file_document_title ON file_document (title);
CREATE INDEX IF NOT EXISTS idx_file_document_author ON file_document (author_id);
CREATE INDEX IF NOT EXISTS idx_file_document_status ON file_document (status);
CREATE INDEX IF NOT EXISTS idx_file_document_create_time ON file_document (create_time);

-- ------------------------------------------------------------
-- 图表表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS file_chart (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    chart_name      VARCHAR(255),
    chart_type      VARCHAR(50),
    category        VARCHAR(50),
    description     VARCHAR(500),
    data_source     VARCHAR(255),
    config          CLOB,
    creator_id      BIGINT,
    creator_name    VARCHAR(100),
    status          INT          NOT NULL DEFAULT 1,
    view_count      INT          NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(64),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INT          NOT NULL DEFAULT 1,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_file_chart_tenant ON file_chart (tenant_id);
CREATE INDEX IF NOT EXISTS idx_file_chart_name ON file_chart (chart_name);
CREATE INDEX IF NOT EXISTS idx_file_chart_creator ON file_chart (creator_id);
CREATE INDEX IF NOT EXISTS idx_file_chart_status ON file_chart (status);
CREATE INDEX IF NOT EXISTS idx_file_chart_create_time ON file_chart (create_time);

-- ------------------------------------------------------------
-- 分片上传表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS file_chunk (
    id              BIGINT       NOT NULL,
    file_id         BIGINT       NOT NULL,
    chunk_index     INT          NOT NULL,
    chunk_size      BIGINT       NOT NULL,
    total_chunks    INT          NOT NULL,
    md5             VARCHAR(64),
    object_name     VARCHAR(500),
    bucket_name     VARCHAR(100),
    storage_status  VARCHAR(20)  NOT NULL DEFAULT 'STORED',
    user_id         BIGINT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INT          NOT NULL DEFAULT 1,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_file_chunk_file ON file_chunk (file_id);
CREATE INDEX IF NOT EXISTS idx_file_chunk_file_index ON file_chunk (file_id, chunk_index);
CREATE INDEX IF NOT EXISTS idx_file_chunk_status ON file_chunk (storage_status);
CREATE INDEX IF NOT EXISTS idx_file_chunk_create_time ON file_chunk (create_time);