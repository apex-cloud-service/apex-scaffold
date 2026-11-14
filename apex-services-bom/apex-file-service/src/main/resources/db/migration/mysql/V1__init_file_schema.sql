-- ============================================================
-- Apex Scaffold - 文件服务独立库初始化脚本（MySQL）
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
--   file_document / file_chart：数据库自增（应用侧 IdType.AUTO）。
--
-- 【方言约定】
--   MySQL 统一使用 DATETIME，禁止 TIMESTAMP；字符集 utf8mb4。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

-- ------------------------------------------------------------
-- 文件元数据表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `file_record` (
    `id`                BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `file_name`         VARCHAR(255)          DEFAULT NULL COMMENT '文件名称（存储名）',
    `original_name`     VARCHAR(255)          DEFAULT NULL COMMENT '原始文件名',
    `file_path`         VARCHAR(500)          DEFAULT NULL COMMENT '文件路径（对象存储 key）',
    `file_url`          VARCHAR(500)          DEFAULT NULL COMMENT '文件访问URL',
    `file_size`         BIGINT                DEFAULT NULL COMMENT '文件大小（字节）',
    `file_type`         VARCHAR(100)          DEFAULT NULL COMMENT '文件类型',
    `content_type`      VARCHAR(255)          DEFAULT NULL COMMENT '内容类型（MIME）',
    `bucket_name`       VARCHAR(100)          DEFAULT NULL COMMENT '存储桶名称',
    `md5`               VARCHAR(64)           DEFAULT NULL COMMENT '文件MD5',
    `user_id`           BIGINT                DEFAULT NULL COMMENT '上传用户ID',
    `business_type`     VARCHAR(50)           DEFAULT NULL COMMENT '业务类型（GENERIC/FACE_PROFILE/LIFESTYLE_PHOTO）',
    `client_request_id` VARCHAR(128)          DEFAULT NULL COMMENT '客户端上传幂等键',
    `storage_status`    VARCHAR(20)  NOT NULL DEFAULT 'STORED' COMMENT '存储状态（STORED/QUARANTINED/ORPHANED/DELETED）',
    `sha256`            VARCHAR(64)           DEFAULT NULL COMMENT '文件SHA-256哈希值',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`           INT          NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_file_record_user` (`user_id`),
    KEY `idx_file_record_business_type` (`business_type`),
    KEY `idx_file_record_client_request_id` (`client_request_id`),
    KEY `idx_file_record_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件元数据表';

-- ------------------------------------------------------------
-- 文档表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `file_document` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID（自增）',
    `title`           VARCHAR(255)          DEFAULT NULL COMMENT '文档标题',
    `doc_type`        VARCHAR(50)           DEFAULT NULL COMMENT '文档类型（contract/report/proposal/manual/other）',
    `category`        VARCHAR(50)           DEFAULT NULL COMMENT '文档分类（tech/business/finance/hr）',
    `content`         TEXT                  DEFAULT NULL COMMENT '文档内容（纯文本或Markdown）',
    `file_url`        VARCHAR(500)          DEFAULT NULL COMMENT '附件文件URL',
    `file_size`       BIGINT                DEFAULT NULL COMMENT '附件文件大小（字节）',
    `author_id`       BIGINT                DEFAULT NULL COMMENT '作者ID（关联 sys_user.id）',
    `author_name`     VARCHAR(100)          DEFAULT NULL COMMENT '作者姓名（冗余字段）',
    `status`          INT          NOT NULL DEFAULT 1 COMMENT '状态（0=草稿 1=正常 2=归档）',
    `tags`            JSON                  DEFAULT NULL COMMENT '标签（JSON数组）',
    `view_count`      INT          NOT NULL DEFAULT 0 COMMENT '查看次数',
    `download_count`  INT          NOT NULL DEFAULT 0 COMMENT '下载次数',
    `tenant_id`       VARCHAR(64)           DEFAULT NULL COMMENT '租户ID（多租户隔离）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`         INT          NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_file_document_tenant` (`tenant_id`),
    KEY `idx_file_document_title` (`title`),
    KEY `idx_file_document_author` (`author_id`),
    KEY `idx_file_document_status` (`status`),
    KEY `idx_file_document_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档表';

-- ------------------------------------------------------------
-- 图表表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `file_chart` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID（自增）',
    `chart_name`      VARCHAR(255)          DEFAULT NULL COMMENT '图表名称',
    `chart_type`      VARCHAR(50)           DEFAULT NULL COMMENT '图表类型（line/bar/pie/scatter/radar）',
    `category`        VARCHAR(50)           DEFAULT NULL COMMENT '图表分类（business/tech/operation/finance）',
    `description`     VARCHAR(500)          DEFAULT NULL COMMENT '图表描述',
    `data_source`     VARCHAR(255)          DEFAULT NULL COMMENT '数据源（API地址或数据查询配置）',
    `config`          JSON                  DEFAULT NULL COMMENT '图表配置（ECharts配置JSON）',
    `creator_id`      BIGINT                DEFAULT NULL COMMENT '创建人ID（关联 sys_user.id）',
    `creator_name`    VARCHAR(100)          DEFAULT NULL COMMENT '创建人姓名（冗余字段）',
    `status`          INT          NOT NULL DEFAULT 1 COMMENT '状态（0=草稿 1=正常）',
    `view_count`      INT          NOT NULL DEFAULT 0 COMMENT '查看次数',
    `tenant_id`       VARCHAR(64)           DEFAULT NULL COMMENT '租户ID（多租户隔离）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`         INT          NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_file_chart_tenant` (`tenant_id`),
    KEY `idx_file_chart_name` (`chart_name`),
    KEY `idx_file_chart_creator` (`creator_id`),
    KEY `idx_file_chart_status` (`status`),
    KEY `idx_file_chart_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图表表';

-- ------------------------------------------------------------
-- 分片上传表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `file_chunk` (
    `id`              BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `file_id`         BIGINT       NOT NULL COMMENT '文件ID（关联 file_record.id）',
    `chunk_index`     INT          NOT NULL COMMENT '分片序号（从0开始）',
    `chunk_size`      BIGINT       NOT NULL COMMENT '分片大小（字节）',
    `total_chunks`    INT          NOT NULL COMMENT '分片总数',
    `md5`             VARCHAR(64)           DEFAULT NULL COMMENT '分片MD5',
    `storage_status`  VARCHAR(20)  NOT NULL DEFAULT 'STORED' COMMENT '存储状态（STORED/ORPHANED/DELETED）',
    `user_id`         BIGINT                DEFAULT NULL COMMENT '上传用户ID',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`         INT          NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_file_chunk_file` (`file_id`),
    KEY `idx_file_chunk_file_index` (`file_id`, `chunk_index`),
    KEY `idx_file_chunk_status` (`storage_status`),
    KEY `idx_file_chunk_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分片上传表';
