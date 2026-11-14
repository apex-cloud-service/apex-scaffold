-- ============================================================
-- Apex Scaffold - 知识库服务 H2 初始化脚本（MODE=MySQL）
-- ============================================================
-- 【用途】
--   单元测试与本地开发使用 H2 内存库（MODE=MySQL）时，通过 spring.sql.init
--   加载本脚本初始化表结构，替代 Flyway；表结构与 db/migration MySQL V1 对齐。
--
-- 【表清单】
--   kb_space / kb_document / kb_chunk / kb_conversation / kb_message
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.3.2
-- ============================================================

CREATE TABLE IF NOT EXISTS kb_space (
    id          BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(512),
    status      INT          NOT NULL DEFAULT 1,
    tenant_id   VARCHAR(64),
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     INT          NOT NULL DEFAULT 1,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_kb_space_tenant ON kb_space (tenant_id);
CREATE INDEX IF NOT EXISTS idx_kb_space_name ON kb_space (name);

CREATE TABLE IF NOT EXISTS kb_document (
    id           BIGINT        NOT NULL,
    space_id     BIGINT        NOT NULL,
    title        VARCHAR(255)  NOT NULL,
    source_type  VARCHAR(20)   NOT NULL DEFAULT 'UPLOAD',
    content_type VARCHAR(255),
    object_key   VARCHAR(500),
    file_size    BIGINT,
    content_text TEXT,
    parse_status VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    parse_error  VARCHAR(500),
    chunk_count  INT           NOT NULL DEFAULT 0,
    index_status VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    tenant_id    VARCHAR(64),
    create_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version      INT           NOT NULL DEFAULT 1,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_kb_document_space ON kb_document (space_id);
CREATE INDEX IF NOT EXISTS idx_kb_document_tenant ON kb_document (tenant_id);
CREATE INDEX IF NOT EXISTS idx_kb_document_parse_status ON kb_document (parse_status);
CREATE INDEX IF NOT EXISTS idx_kb_document_index_status ON kb_document (index_status);
CREATE INDEX IF NOT EXISTS idx_kb_document_update_time ON kb_document (update_time);

CREATE TABLE IF NOT EXISTS kb_chunk (
    id             BIGINT       NOT NULL,
    document_id    BIGINT       NOT NULL,
    seq            INT          NOT NULL DEFAULT 0,
    content        TEXT         NOT NULL,
    content_hash   VARCHAR(64)  NOT NULL,
    token_estimate INT,
    tenant_id      VARCHAR(64),
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        INT          NOT NULL DEFAULT 1,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_document ON kb_chunk (document_id, seq);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_tenant ON kb_chunk (tenant_id);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_content_hash ON kb_chunk (content_hash);

CREATE TABLE IF NOT EXISTS kb_conversation (
    id          BIGINT       NOT NULL,
    title       VARCHAR(200),
    user_id     BIGINT       NOT NULL,
    tenant_id   VARCHAR(64),
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     INT          NOT NULL DEFAULT 1,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_kb_conversation_user ON kb_conversation (user_id);
CREATE INDEX IF NOT EXISTS idx_kb_conversation_tenant ON kb_conversation (tenant_id);

CREATE TABLE IF NOT EXISTS kb_message (
    id              BIGINT       NOT NULL,
    conversation_id BIGINT       NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    content         TEXT         NOT NULL,
    citations       TEXT,
    tenant_id       VARCHAR(64),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INT          NOT NULL DEFAULT 1,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_kb_message_conversation ON kb_message (conversation_id, id);
CREATE INDEX IF NOT EXISTS idx_kb_message_tenant ON kb_message (tenant_id);
