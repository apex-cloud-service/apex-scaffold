-- 脚本用途：创建文档管理和图表管理的核心表
-- 版本：v1.4.0 新增数据管理模块
-- 依赖：无特殊依赖

-- 文档表
CREATE TABLE doc_document (
    id BIGSERIAL NOT NULL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    doc_type VARCHAR(50) NOT NULL,
    category VARCHAR(50) NOT NULL,
    content TEXT,
    file_url VARCHAR(500),
    file_size BIGINT DEFAULT 0,
    author_id BIGINT NOT NULL,
    author_name VARCHAR(100) NOT NULL,
    status SMALLINT NOT NULL DEFAULT 0,
    tags JSONB,
    view_count INTEGER NOT NULL DEFAULT 0,
    download_count INTEGER NOT NULL DEFAULT 0,
    tenant_id VARCHAR(64) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE doc_document IS '文档表';
COMMENT ON COLUMN doc_document.id IS '主键ID';
COMMENT ON COLUMN doc_document.title IS '文档标题';
COMMENT ON COLUMN doc_document.doc_type IS '文档类型（contract=合同 report=报告 proposal=方案 manual=手册 other=其他）';
COMMENT ON COLUMN doc_document.category IS '文档分类（tech=技术 business=业务 finance=财务 hr=人力资源）';
COMMENT ON COLUMN doc_document.content IS '文档内容（纯文本或Markdown）';
COMMENT ON COLUMN doc_document.file_url IS '文件URL（关联对象存储）';
COMMENT ON COLUMN doc_document.file_size IS '文件大小（字节）';
COMMENT ON COLUMN doc_document.author_id IS '作者ID（关联 sys_user.id）';
COMMENT ON COLUMN doc_document.author_name IS '作者姓名（冗余字段，减少JOIN）';
COMMENT ON COLUMN doc_document.status IS '状态（0=草稿 1=正常 2=归档）';
COMMENT ON COLUMN doc_document.tags IS '标签（JSON数组，如 ["重要","财务"]）';
COMMENT ON COLUMN doc_document.view_count IS '查看次数';
COMMENT ON COLUMN doc_document.download_count IS '下载次数';
COMMENT ON COLUMN doc_document.tenant_id IS '租户ID（多租户隔离）';
COMMENT ON COLUMN doc_document.create_time IS '创建时间';
COMMENT ON COLUMN doc_document.update_time IS '更新时间';
COMMENT ON COLUMN doc_document.deleted IS '逻辑删除标识（0=未删除 1=已删除）';

CREATE INDEX idx_doc_document_tenant_doc_type ON doc_document (tenant_id, doc_type);
CREATE INDEX idx_doc_document_tenant_category ON doc_document (tenant_id, category);
CREATE INDEX idx_doc_document_tenant_author ON doc_document (tenant_id, author_id);
CREATE INDEX idx_doc_document_status ON doc_document (status);
CREATE INDEX idx_doc_document_create_time ON doc_document (create_time);
CREATE INDEX idx_doc_document_update_time ON doc_document (update_time);
CREATE INDEX idx_doc_document_title_content_fulltext ON doc_document USING gin(to_tsvector('chinese', title || ' ' || COALESCE(content, '')));

-- 图表表
CREATE TABLE doc_chart (
    id BIGSERIAL NOT NULL PRIMARY KEY,
    chart_name VARCHAR(200) NOT NULL,
    chart_type VARCHAR(50) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    data_source VARCHAR(500),
    config JSONB,
    status SMALLINT NOT NULL DEFAULT 0,
    creator_id BIGINT NOT NULL,
    creator_name VARCHAR(100) NOT NULL,
    view_count INTEGER NOT NULL DEFAULT 0,
    tenant_id VARCHAR(64) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE doc_chart IS '图表表';
COMMENT ON COLUMN doc_chart.id IS '主键ID';
COMMENT ON COLUMN doc_chart.chart_name IS '图表名称';
COMMENT ON COLUMN doc_chart.chart_type IS '图表类型（line=折线图 bar=柱状图 pie=饼图 scatter=散点图 radar=雷达图）';
COMMENT ON COLUMN doc_chart.category IS '图表分类（business=业务 tech=技术 operation=运营 finance=财务）';
COMMENT ON COLUMN doc_chart.description IS '图表描述';
COMMENT ON COLUMN doc_chart.data_source IS '数据源（API地址或数据查询配置）';
COMMENT ON COLUMN doc_chart.config IS '图表配置（ECharts配置JSON）';
COMMENT ON COLUMN doc_chart.status IS '状态（0=草稿 1=正常）';
COMMENT ON COLUMN doc_chart.creator_id IS '创建人ID（关联 sys_user.id）';
COMMENT ON COLUMN doc_chart.creator_name IS '创建人姓名（冗余字段，减少JOIN）';
COMMENT ON COLUMN doc_chart.view_count IS '查看次数';
COMMENT ON COLUMN doc_chart.tenant_id IS '租户ID（多租户隔离）';
COMMENT ON COLUMN doc_chart.create_time IS '创建时间';
COMMENT ON COLUMN doc_chart.update_time IS '更新时间';
COMMENT ON COLUMN doc_chart.deleted IS '逻辑删除标识（0=未删除 1=已删除）';

CREATE INDEX idx_doc_chart_tenant_chart_type ON doc_chart (tenant_id, chart_type);
CREATE INDEX idx_doc_chart_tenant_category ON doc_chart (tenant_id, category);
CREATE INDEX idx_doc_chart_tenant_creator ON doc_chart (tenant_id, creator_id);
CREATE INDEX idx_doc_chart_status ON doc_chart (status);
CREATE INDEX idx_doc_chart_create_time ON doc_chart (create_time);
CREATE INDEX idx_doc_chart_update_time ON doc_chart (update_time);

-- 创建更新时间触发器函数
CREATE OR REPLACE FUNCTION update_doc_document_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_doc_chart_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建触发器
CREATE TRIGGER trigger_doc_document_update_time
    BEFORE UPDATE ON doc_document
    FOR EACH ROW
    EXECUTE FUNCTION update_doc_document_updated_at();

CREATE TRIGGER trigger_doc_chart_update_time
    BEFORE UPDATE ON doc_chart
    FOR EACH ROW
    EXECUTE FUNCTION update_doc_chart_updated_at();
