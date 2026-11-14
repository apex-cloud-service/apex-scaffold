-- ============================================================
-- Apex Scaffold - 知识库服务独立库初始化脚本（PostgreSQL）
-- ============================================================
-- 【用途】
--   知识库服务（apex-knowledge-service）独立数据库 apex_knowledge 的 Flyway 基线脚本，
--   与旧单体迁移版本链解耦，新库从 V1 重新开始（迁移方案 §9.3 规则）。
--
-- 【表清单】
--   kb_space        知识空间（租户内知识组织单元）
--   kb_document     知识文档主档（对象引用 + 解析/索引状态机）
--   kb_chunk        知识分块（检索最小单元，向量本体不落库）
--   kb_conversation 问答会话
--   kb_message      问答消息（含引用溯源 JSON）
--
-- 【字段来源原则】
--   以代码实现（PO 类）为唯一事实来源；列名与 MySQL 版本严格一一对应，
--   与以下 PO 类字段一一对应（MyBatis-Plus map-underscore-to-camel-case）。
--
-- 【主键策略】
--   全部雪花算法（应用侧 BasePO IdType.ASSIGN_ID），列定义为 BIGINT 且不自增。
--
-- 【方言约定】
--   PostgreSQL 使用 TIMESTAMP 与 SMALLINT 逻辑删除字段；JSON 使用 JSONB。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.3.2
-- ============================================================

-- ------------------------------------------------------------
-- 知识空间表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kb_space (
    id          BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(512),
    status      INT          NOT NULL DEFAULT 1,
    tenant_id   VARCHAR(64),
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     INT          NOT NULL DEFAULT 1,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_kb_space_tenant ON kb_space (tenant_id);
CREATE INDEX IF NOT EXISTS idx_kb_space_name ON kb_space (name);
CREATE INDEX IF NOT EXISTS idx_kb_space_status ON kb_space (status);

COMMENT ON TABLE kb_space IS '知识空间表';

-- ------------------------------------------------------------
-- 知识文档表
-- ------------------------------------------------------------
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
    deleted      SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_kb_document_space ON kb_document (space_id);
CREATE INDEX IF NOT EXISTS idx_kb_document_tenant ON kb_document (tenant_id);
CREATE INDEX IF NOT EXISTS idx_kb_document_parse_status ON kb_document (parse_status);
CREATE INDEX IF NOT EXISTS idx_kb_document_index_status ON kb_document (index_status);
CREATE INDEX IF NOT EXISTS idx_kb_document_update_time ON kb_document (update_time);

COMMENT ON TABLE kb_document IS '知识文档表';

-- ------------------------------------------------------------
-- 知识分块表
-- ------------------------------------------------------------
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
    deleted        SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_document ON kb_chunk (document_id, seq);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_tenant ON kb_chunk (tenant_id);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_content_hash ON kb_chunk (content_hash);

COMMENT ON TABLE kb_chunk IS '知识分块表';

-- ------------------------------------------------------------
-- 问答会话表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kb_conversation (
    id          BIGINT       NOT NULL,
    title       VARCHAR(200),
    user_id     BIGINT       NOT NULL,
    tenant_id   VARCHAR(64),
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     INT          NOT NULL DEFAULT 1,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_kb_conversation_user ON kb_conversation (user_id);
CREATE INDEX IF NOT EXISTS idx_kb_conversation_tenant ON kb_conversation (tenant_id);

COMMENT ON TABLE kb_conversation IS '知识问答会话表';

-- ------------------------------------------------------------
-- 问答消息表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kb_message (
    id              BIGINT       NOT NULL,
    conversation_id BIGINT       NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    content         TEXT         NOT NULL,
    citations       JSONB,
    tenant_id       VARCHAR(64),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         INT          NOT NULL DEFAULT 1,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_kb_message_conversation ON kb_message (conversation_id, id);
CREATE INDEX IF NOT EXISTS idx_kb_message_tenant ON kb_message (tenant_id);

COMMENT ON TABLE kb_message IS '知识问答消息表';
