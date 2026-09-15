-- 文档信息表
CREATE TABLE IF NOT EXISTS office_document_info (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    platform VARCHAR(50) NOT NULL,
    platform_document_id VARCHAR(200) NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    path VARCHAR(1000),
    url VARCHAR(1000),
    owner_id VARCHAR(100) NOT NULL,
    owner_name VARCHAR(100) NOT NULL,
    parent_folder_id VARCHAR(200),
    file_size BIGINT,
    file_extension VARCHAR(50),
    mime_type VARCHAR(100),
    is_folder BOOLEAN DEFAULT FALSE,
    permission VARCHAR(50) DEFAULT 'PRIVATE',
    collaborator_ids TEXT,
    version VARCHAR(50),
    last_modified_by_id VARCHAR(100),
    last_modified_by_name VARCHAR(100),
    last_modified_time TIMESTAMP,
    is_archived BOOLEAN DEFAULT FALSE,
    version_number INT DEFAULT 0,
    deleted BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_document_info_tenant_id ON office_document_info(tenant_id);
CREATE INDEX IF NOT EXISTS idx_document_info_platform_document ON office_document_info(platform, platform_document_id);
CREATE INDEX IF NOT EXISTS idx_document_info_owner ON office_document_info(owner_id);
CREATE INDEX IF NOT EXISTS idx_document_info_parent_folder ON office_document_info(parent_folder_id);
CREATE INDEX IF NOT EXISTS idx_document_info_title ON office_document_info(title);
CREATE INDEX IF NOT EXISTS idx_document_info_archived ON office_document_info(is_archived);
CREATE INDEX IF NOT EXISTS idx_document_info_create_time ON office_document_info(create_time);

-- 添加表注释
COMMENT ON TABLE office_document_info IS '办公平台文档信息表';
COMMENT ON COLUMN office_document_info.id IS '主键ID';
COMMENT ON COLUMN office_document_info.tenant_id IS '租户ID';
COMMENT ON COLUMN office_document_info.platform IS '办公平台类型（feishu/dingtalk/wecom）';
COMMENT ON COLUMN office_document_info.platform_document_id IS '平台中的文档ID';
COMMENT ON COLUMN office_document_info.document_type IS '文档类型（DOC/SHEET/SLIDE/MINDMAP/FOLDER/FILE）';
COMMENT ON COLUMN office_document_info.title IS '文档标题';
COMMENT ON COLUMN office_document_info.path IS '文档路径';
COMMENT ON COLUMN office_document_info.url IS '文档URL';
COMMENT ON COLUMN office_document_info.owner_id IS '文档所有者ID';
COMMENT ON COLUMN office_document_info.owner_name IS '文档所有者姓名';
COMMENT ON COLUMN office_document_info.parent_folder_id IS '父文件夹ID';
COMMENT ON COLUMN office_document_info.file_size IS '文档大小（字节）';
COMMENT ON COLUMN office_document_info.file_extension IS '文档扩展名';
COMMENT ON COLUMN office_document_info.mime_type IS '文档MIME类型';
COMMENT ON COLUMN office_document_info.is_folder IS '是否为文件夹';
COMMENT ON COLUMN office_document_info.permission IS '权限类型（PRIVATE/PUBLIC/SHARED）';
COMMENT ON COLUMN office_document_info.collaborator_ids IS '协作者ID列表（JSON格式）';
COMMENT ON COLUMN office_document_info.version IS '文档版本号';
COMMENT ON COLUMN office_document_info.last_modified_by_id IS '最后修改人ID';
COMMENT ON COLUMN office_document_info.last_modified_by_name IS '最后修改人姓名';
COMMENT ON COLUMN office_document_info.last_modified_time IS '最后修改时间';
COMMENT ON COLUMN office_document_info.is_archived IS '是否已归档';
COMMENT ON COLUMN office_document_info.version_number IS '乐观锁版本号';
COMMENT ON COLUMN office_document_info.deleted IS '逻辑删除标记';
COMMENT ON COLUMN office_document_info.create_time IS '创建时间';
COMMENT ON COLUMN office_document_info.update_time IS '更新时间';

-- 创建更新触发器
CREATE OR REPLACE FUNCTION update_office_document_info_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_office_document_info_update_time
    BEFORE UPDATE ON office_document_info
    FOR EACH ROW
    EXECUTE FUNCTION update_office_document_info_update_time();
