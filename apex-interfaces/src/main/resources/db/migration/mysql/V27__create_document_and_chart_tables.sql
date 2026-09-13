-- 脚本用途：创建文档管理和图表管理的核心表
-- 版本：v1.4.0 新增数据管理模块
-- 依赖：无特殊依赖

-- 文档表
CREATE TABLE `doc_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(200) NOT NULL COMMENT '文档标题',
    `doc_type` VARCHAR(50) NOT NULL COMMENT '文档类型（contract=合同 report=报告 proposal=方案 manual=手册 other=其他）',
    `category` VARCHAR(50) NOT NULL COMMENT '文档分类（tech=技术 business=业务 finance=财务 hr=人力资源）',
    `content` TEXT COMMENT '文档内容（纯文本或Markdown）',
    `file_url` VARCHAR(500) COMMENT '文件URL（关联对象存储）',
    `file_size` BIGINT DEFAULT 0 COMMENT '文件大小（字节）',
    `author_id` BIGINT NOT NULL COMMENT '作者ID（关联 sys_user.id）',
    `author_name` VARCHAR(100) NOT NULL COMMENT '作者姓名（冗余字段，减少JOIN）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态（0=草稿 1=正常 2=归档）',
    `tags` JSON COMMENT '标签（JSON数组，如 ["重要","财务"]）',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '查看次数',
    `download_count` INT NOT NULL DEFAULT 0 COMMENT '下载次数',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID（多租户隔离）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除 1=已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_doc_type` (`tenant_id`, `doc_type`),
    KEY `idx_tenant_category` (`tenant_id`, `category`),
    KEY `idx_tenant_author` (`tenant_id`, `author_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_update_time` (`update_time`),
    FULLTEXT KEY `ft_title_content` (`title`, `content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档表';

-- 图表表
CREATE TABLE `doc_chart` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `chart_name` VARCHAR(200) NOT NULL COMMENT '图表名称',
    `chart_type` VARCHAR(50) NOT NULL COMMENT '图表类型（line=折线图 bar=柱状图 pie=饼图 scatter=散点图 radar=雷达图）',
    `category` VARCHAR(50) NOT NULL COMMENT '图表分类（business=业务 tech=技术 operation=运营 finance=财务）',
    `description` VARCHAR(500) COMMENT '图表描述',
    `data_source` VARCHAR(500) COMMENT '数据源（API地址或数据查询配置）',
    `config` JSON COMMENT '图表配置（ECharts配置JSON）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态（0=草稿 1=正常）',
    `creator_id` BIGINT NOT NULL COMMENT '创建人ID（关联 sys_user.id）',
    `creator_name` VARCHAR(100) NOT NULL COMMENT '创建人姓名（冗余字段，减少JOIN）',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '查看次数',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID（多租户隔离）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0=未删除 1=已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_chart_type` (`tenant_id`, `chart_type`),
    KEY `idx_tenant_category` (`tenant_id`, `category`),
    KEY `idx_tenant_creator` (`tenant_id`, `creator_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图表表';
