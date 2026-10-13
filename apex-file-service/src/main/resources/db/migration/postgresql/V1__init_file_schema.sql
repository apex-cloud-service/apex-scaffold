-- ============================================================
-- Apex Scaffold - 文件服务独立库初始化脚本（PostgreSQL）
-- ============================================================
-- 【用途】
--   文件服务（apex-file-service）独立数据库 apex_file 的 Flyway 基线脚本，
--   与旧单体的迁移版本链解耦，新库从 V1 重新开始（迁移方案 §9.3 规则）。
--
-- 【表清单】（迁移方案 Phase 4.2）
--   file_record    文件元数据表（对应旧 sys_file）
--   file_document  文档表（对应旧 doc_document）
--   file_chart     图表表（对应旧 doc_chart）
--   file_chunk     分片上传表（文件服务新增，支撑分片上传/断点续传）
--
-- 【字段来源原则】
--   以代码实现（PO 类）为唯一事实来源（对齐 apex-interfaces V40 迁移原则）。
--   列名与以下类字段严格一一对应（MyBatis-Plus map-underscore-to-camel-case）：
--     com.apex.scaffold.file.infrastructure.persistence.po.FilePO
--     com.apex.scaffold.file.infrastructure.persistence.po.DocumentPO
--     com.apex.scaffold.file.infrastructure.persistence.po.ChartPO
--   并包含 Mapper 原生 SQL 依赖的列（如 file_record.storage_status）。
--
-- 【主键策略】
--   file_record / file_chunk：雪花算法（应用侧 IdType.ASSIGN_ID）生成，
--                             列定义为 BIGINT 且不自增。
--   file_document / file_chart：数据库自增（应用侧 IdType.AUTO），使用 BIGSERIAL。
--
-- 【方言约定】
--   PostgreSQL 使用 TIMESTAMP 与 SMALLINT 逻辑删除字段，JSON 使用 JSONB。
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
    deleted           SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_file_record_user ON file_record (user_id);
CREATE INDEX IF NOT EXISTS idx_file_record_business_type ON file_record (business_type);
CREATE INDEX IF NOT EXISTS idx_file_record_client_request_id ON file_record (client_request_id);
CREATE INDEX IF NOT EXISTS idx_file_record_create_time ON file_record (create_time);

COMMENT ON TABLE  file_record IS '文件元数据表';
COMMENT ON COLUMN file_record.storage_status IS '存储状态（STORED/QUARANTINED/ORPHANED/DELETED）';
COMMENT ON COLUMN file_record.business_type IS '业务类型（GENERIC/FACE_PROFILE/LIFESTYLE_PHOTO）';
COMMENT ON COLUMN file_record.client_request_id IS '客户端上传幂等键';

-- ------------------------------------------------------------
-- 文档表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS file_document (
    id              BIGSERIAL    NOT NULL,
    title           VARCHAR(255),
    doc_type        VARCHAR(50),
    category        VARCHAR(50),
    content         TEXT,
    file_url        VARCHAR(500),
    file_size       BIGINT,
    author_id       BIGINT,
    author_name     VARCHAR(100),
    status          INT          NOT NULL DEFAULT 1,
    tags            JSONB,
    view_count      INT          NOT NULL DEFAULT 0,
    download_count  INT          NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(64),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INT          NOT NULL DEFAULT 1,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_file_document_tenant ON file_document (tenant_id);
CREATE INDEX IF NOT EXISTS idx_file_document_title ON file_document (title);
CREATE INDEX IF NOT EXISTS idx_file_document_author ON file_document (author_id);
CREATE INDEX IF NOT EXISTS idx_file_document_status ON file_document (status);
CREATE INDEX IF NOT EXISTS idx_file_document_create_time ON file_document (create_time);

COMMENT ON TABLE  file_document IS '文档表';
COMMENT ON COLUMN file_document.doc_type IS '文档类型（contract/report/proposal/manual/other）';
COMMENT ON COLUMN file_document.category IS '文档分类（tech/business/finance/hr）';
COMMENT ON COLUMN file_document.tags IS '标签（JSON数组）';

-- ------------------------------------------------------------
-- 图表表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS file_chart (
    id              BIGSERIAL    NOT NULL,
    chart_name      VARCHAR(255),
    chart_type      VARCHAR(50),
    category        VARCHAR(50),
    description     VARCHAR(500),
    data_source     VARCHAR(255),
    config          JSONB,
    creator_id      BIGINT,
    creator_name    VARCHAR(100),
    status          INT          NOT NULL DEFAULT 1,
    view_count      INT          NOT NULL DEFAULT 0,
    tenant_id       VARCHAR(64),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INT          NOT NULL DEFAULT 1,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_file_chart_tenant ON file_chart (tenant_id);
CREATE INDEX IF NOT EXISTS idx_file_chart_name ON file_chart (chart_name);
CREATE INDEX IF NOT EXISTS idx_file_chart_creator ON file_chart (creator_id);
CREATE INDEX IF NOT EXISTS idx_file_chart_status ON file_chart (status);
CREATE INDEX IF NOT EXISTS idx_file_chart_create_time ON file_chart (create_time);

COMMENT ON TABLE  file_chart IS '图表表';
COMMENT ON COLUMN file_chart.chart_type IS '图表类型（line/bar/pie/scatter/radar）';
COMMENT ON COLUMN file_chart.category IS '图表分类（business/tech/operation/finance）';
COMMENT ON COLUMN file_chart.config IS '图表配置（ECharts配置JSON）';

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
    storage_status  VARCHAR(20)  NOT NULL DEFAULT 'STORED',
    user_id         BIGINT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INT          NOT NULL DEFAULT 1,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_file_chunk_file ON file_chunk (file_id);
CREATE INDEX IF NOT EXISTS idx_file_chunk_file_index ON file_chunk (file_id, chunk_index);
CREATE INDEX IF NOT EXISTS idx_file_chunk_status ON file_chunk (storage_status);
CREATE INDEX IF NOT EXISTS idx_file_chunk_create_time ON file_chunk (create_time);

COMMENT ON TABLE  file_chunk IS '分片上传表';
COMMENT ON COLUMN file_chunk.chunk_index IS '分片序号（从0开始）';
COMMENT ON COLUMN file_chunk.storage_status IS '存储状态（STORED/ORPHANED/DELETED）';
