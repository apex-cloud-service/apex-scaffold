-- ============================================================
-- Apex Scaffold - 知识库服务独立库初始化脚本（MySQL）
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
--   以代码实现（PO 类）为唯一事实来源；列名与以下类字段严格一一对应
--   （MyBatis-Plus map-underscore-to-camel-case）：
--     com.apex.scaffold.knowledge.infrastructure.persistence.po.KnowledgeSpacePO
--     com.apex.scaffold.knowledge.infrastructure.persistence.po.KnowledgeDocumentPO
--     com.apex.scaffold.knowledge.infrastructure.persistence.po.KnowledgeChunkPO
--     com.apex.scaffold.knowledge.infrastructure.persistence.po.KbConversationPO
--     com.apex.scaffold.knowledge.infrastructure.persistence.po.KbMessagePO
--
-- 【主键策略】
--   全部雪花算法（应用侧 BasePO IdType.ASSIGN_ID），列定义为 BIGINT 且不自增。
--
-- 【方言约定】
--   MySQL 统一使用 DATETIME，禁止 TIMESTAMP；字符集 utf8mb4。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.3.2
-- ============================================================

-- ------------------------------------------------------------
-- 知识空间表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kb_space` (
    `id`          BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `name`        VARCHAR(64)  NOT NULL COMMENT '空间名称（租户内唯一）',
    `description` VARCHAR(512)          DEFAULT NULL COMMENT '空间描述',
    `status`      INT          NOT NULL DEFAULT 1 COMMENT '状态（0=停用 1=正常）',
    `tenant_id`   VARCHAR(64)           DEFAULT NULL COMMENT '租户ID（多租户隔离）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`     INT          NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_kb_space_tenant` (`tenant_id`),
    KEY `idx_kb_space_name` (`name`),
    KEY `idx_kb_space_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识空间表';

-- ------------------------------------------------------------
-- 知识文档表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kb_document` (
    `id`           BIGINT        NOT NULL COMMENT '主键ID（雪花算法）',
    `space_id`     BIGINT        NOT NULL COMMENT '所属空间ID（kb_space.id）',
    `title`        VARCHAR(255)  NOT NULL COMMENT '文档标题',
    `source_type`  VARCHAR(20)   NOT NULL DEFAULT 'UPLOAD' COMMENT '来源类型（UPLOAD/MANUAL）',
    `content_type` VARCHAR(255)           DEFAULT NULL COMMENT '对象内容类型（MIME）',
    `object_key`   VARCHAR(500)           DEFAULT NULL COMMENT '对象存储键（MANUAL 来源为空）',
    `file_size`    BIGINT                 DEFAULT NULL COMMENT '对象大小（字节）',
    `content_text` LONGTEXT               COMMENT '解析出的纯文本正文（摄取产物）',
    `parse_status` VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '解析状态（PENDING/PARSING/PARSED/FAILED）',
    `parse_error`  VARCHAR(500)           DEFAULT NULL COMMENT '失败原因摘要',
    `chunk_count`  INT           NOT NULL DEFAULT 0 COMMENT '分块数量',
    `index_status` VARCHAR(20)   NOT NULL DEFAULT 'NONE' COMMENT '索引状态（NONE/PENDING/INDEXED/INDEXED_LEXICAL/FAILED）',
    `tenant_id`    VARCHAR(64)            DEFAULT NULL COMMENT '租户ID（多租户隔离）',
    `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`      INT           NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    `deleted`      TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_kb_document_space` (`space_id`),
    KEY `idx_kb_document_tenant` (`tenant_id`),
    KEY `idx_kb_document_parse_status` (`parse_status`),
    KEY `idx_kb_document_index_status` (`index_status`),
    KEY `idx_kb_document_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档表';

-- ------------------------------------------------------------
-- 知识分块表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kb_chunk` (
    `id`             BIGINT       NOT NULL COMMENT '主键ID（雪花算法，同时作为向量索引文档主键）',
    `document_id`    BIGINT       NOT NULL COMMENT '所属文档ID（kb_document.id）',
    `seq`            INT          NOT NULL DEFAULT 0 COMMENT '文档内序号（从0开始）',
    `content`        MEDIUMTEXT   NOT NULL COMMENT '分块正文',
    `content_hash`   VARCHAR(64)  NOT NULL COMMENT '内容SHA-256摘要（增量去重）',
    `token_estimate` INT                   DEFAULT NULL COMMENT '预估token数（字符数近似）',
    `tenant_id`      VARCHAR(64)           DEFAULT NULL COMMENT '租户ID（多租户隔离）',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`        INT          NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_kb_chunk_document` (`document_id`, `seq`),
    KEY `idx_kb_chunk_tenant` (`tenant_id`),
    KEY `idx_kb_chunk_content_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识分块表';

-- ------------------------------------------------------------
-- 问答会话表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kb_conversation` (
    `id`          BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `title`       VARCHAR(200)          DEFAULT NULL COMMENT '会话标题（默认取首条提问截断）',
    `user_id`     BIGINT       NOT NULL COMMENT '归属用户ID',
    `tenant_id`   VARCHAR(64)           DEFAULT NULL COMMENT '租户ID（多租户隔离）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`     INT          NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_kb_conversation_user` (`user_id`),
    KEY `idx_kb_conversation_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识问答会话表';

-- ------------------------------------------------------------
-- 问答消息表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kb_message` (
    `id`              BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `conversation_id` BIGINT       NOT NULL COMMENT '所属会话ID（kb_conversation.id）',
    `role`            VARCHAR(20)  NOT NULL COMMENT '消息角色（USER/ASSISTANT）',
    `content`         MEDIUMTEXT   NOT NULL COMMENT '消息正文',
    `citations`       JSON                  COMMENT '引用溯源JSON数组（回答消息专用）',
    `tenant_id`       VARCHAR(64)           DEFAULT NULL COMMENT '租户ID（多租户隔离）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`         INT          NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_kb_message_conversation` (`conversation_id`, `id`),
    KEY `idx_kb_message_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识问答消息表';
