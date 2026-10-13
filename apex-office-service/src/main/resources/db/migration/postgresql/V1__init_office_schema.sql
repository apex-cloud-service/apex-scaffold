-- ============================================================
-- Apex Scaffold - 办公服务独立库初始化脚本（PostgreSQL）
-- ============================================================
-- 【用途】
--   办公服务（apex-office-service）独立数据库 apex_office 的 Flyway 基线脚本，
--   与旧单体的迁移版本链解耦，新库从 V1 重新开始（迁移方案 §9.3 规则）。
--
-- 【表清单】（迁移方案 Phase 5）
--   office_platform_app          办公平台应用配置
--   office_identity_binding      外部身份绑定
--   office_organization_mapping  组织映射
--   office_sync_task             同步任务游标
--   office_message               办公消息发送记录
--   office_approval_process      审批流程
--   office_calendar_event        日历事件
--   office_document_info         文档信息
--
-- 【字段来源原则】
--   以代码实现（PO 类）为唯一事实来源（对齐 apex-file-service V1 /
--   apex-interfaces V40 迁移原则）。列名与以下类字段严格一一对应
--   （MyBatis-Plus map-underscore-to-camel-case）：
--     com.apex.scaffold.office.infrastructure.persistence.OfficeAppPO
--     com.apex.scaffold.office.infrastructure.persistence.OfficeBindingPO
--     com.apex.scaffold.office.infrastructure.persistence.OfficeOrganizationMappingPO
--     com.apex.scaffold.office.infrastructure.persistence.OfficeSyncTaskPO
--     com.apex.scaffold.office.infrastructure.persistence.po.OfficeMessagePO
--     com.apex.scaffold.office.infrastructure.po.ApprovalProcessPO
--     com.apex.scaffold.office.infrastructure.po.CalendarEventPO
--     com.apex.scaffold.office.infrastructure.po.DocumentInfoPO
--   注意：office_message 采用 PO 显式列名（target_type/target_user_ids/
--   send_status/failure_reason），与旧单体 V31 的 recipient_type/status/
--   result_message 命名不一致，一律以 PO 为准。
--
-- 【不迁移的表】
--   旧单体 off_department / off_employee / off_sync_history / off_message /
--   off_message_recipient / off_message_template 等组织与消息域表归属单体的
--   org/message 模块，不在本服务承载范围内，故不建入本库。
--
-- 【主键策略】
--   全部表使用数据库自增（应用侧 IdType.AUTO）：
--     MySQL 使用 BIGINT AUTO_INCREMENT，PostgreSQL 使用 BIGSERIAL。
--
-- 【方言约定】
--   PostgreSQL 使用 TIMESTAMP 与 SMALLINT 逻辑删除字段。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

-- ------------------------------------------------------------
-- 办公平台应用配置
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_platform_app (
    id                       BIGSERIAL    NOT NULL,
    tenant_id                VARCHAR(64)  NOT NULL,
    platform                 VARCHAR(32)  NOT NULL,
    app_id                   VARCHAR(128) NOT NULL,
    secret_reference         VARCHAR(255) NOT NULL,
    redirect_uri             VARCHAR(512),
    signing_secret_reference VARCHAR(255),
    capabilities             TEXT,
    enabled                  SMALLINT     NOT NULL DEFAULT 0,
    version                  INT          NOT NULL DEFAULT 0,
    deleted                  SMALLINT     NOT NULL DEFAULT 0,
    create_time              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_office_app_tenant_platform ON office_platform_app (tenant_id, platform);
COMMENT ON TABLE  office_platform_app IS '办公平台应用配置';
COMMENT ON COLUMN office_platform_app.capabilities IS '平台能力标识（逗号分隔）';
COMMENT ON COLUMN office_platform_app.enabled IS '启用状态（0-禁用 1-启用）';

-- ------------------------------------------------------------
-- 外部身份绑定
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_identity_binding (
    id               BIGSERIAL    NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL,
    user_id          BIGINT       NOT NULL,
    platform         VARCHAR(32)  NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    enabled          SMALLINT     NOT NULL DEFAULT 1,
    version          INT          NOT NULL DEFAULT 0,
    deleted          SMALLINT     NOT NULL DEFAULT 0,
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_office_binding_external ON office_identity_binding (tenant_id, platform, external_user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_office_binding_user ON office_identity_binding (tenant_id, platform, user_id);
COMMENT ON TABLE  office_identity_binding IS '办公平台身份绑定';
COMMENT ON COLUMN office_identity_binding.status IS '绑定状态';

-- ------------------------------------------------------------
-- 组织映射
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_organization_mapping (
    id               BIGSERIAL    NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL,
    platform         VARCHAR(32)  NOT NULL,
    external_org_id  VARCHAR(255) NOT NULL,
    external_user_id VARCHAR(255),
    apex_dept_id     VARCHAR(64),
    apex_user_id     VARCHAR(64),
    version          INT          NOT NULL DEFAULT 0,
    deleted          SMALLINT     NOT NULL DEFAULT 0,
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_office_org ON office_organization_mapping (tenant_id, platform, external_org_id);
COMMENT ON TABLE  office_organization_mapping IS '办公平台组织映射';

-- ------------------------------------------------------------
-- 同步任务游标
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_sync_task (
    id              BIGSERIAL    NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    platform        VARCHAR(32)  NOT NULL,
    cursor_value    VARCHAR(512),
    state           VARCHAR(32)  NOT NULL,
    failure_message VARCHAR(1000),
    version         INT          NOT NULL DEFAULT 0,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_office_sync ON office_sync_task (tenant_id, platform);
COMMENT ON TABLE  office_sync_task IS '办公平台同步任务';
COMMENT ON COLUMN office_sync_task.cursor_value IS '增量同步游标';

-- ------------------------------------------------------------
-- 办公消息发送记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_message (
    id                  BIGSERIAL    NOT NULL,
    tenant_id           BIGINT       NOT NULL,
    platform            VARCHAR(50)  NOT NULL,
    app_id              BIGINT       NOT NULL,
    message_type        VARCHAR(50)  NOT NULL,
    target_type         VARCHAR(50)  NOT NULL,
    target_user_ids     TEXT         NOT NULL,
    title               VARCHAR(500),
    content             TEXT         NOT NULL,
    extra_data          TEXT,
    send_status         VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    platform_message_id VARCHAR(200),
    failure_reason      VARCHAR(1000),
    retry_count         INT          NOT NULL DEFAULT 0,
    max_retry_count     INT          NOT NULL DEFAULT 3,
    sent_at             TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    deleted             SMALLINT     NOT NULL DEFAULT 0,
    version             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_office_message_tenant_platform ON office_message (tenant_id, platform);
CREATE INDEX IF NOT EXISTS idx_office_message_tenant_status ON office_message (tenant_id, send_status);
CREATE INDEX IF NOT EXISTS idx_office_message_platform_message_id ON office_message (platform_message_id);
CREATE INDEX IF NOT EXISTS idx_office_message_sent_at ON office_message (sent_at);
COMMENT ON TABLE  office_message IS '办公平台消息发送记录';
COMMENT ON COLUMN office_message.target_type IS '接收者类型（USER/DEPARTMENT/GROUP/USER_LIST）';
COMMENT ON COLUMN office_message.send_status IS '发送状态（PENDING/SENDING/SUCCESS/FAILED/PARTIAL_SUCCESS）';
COMMENT ON COLUMN office_message.failure_reason IS '发送结果/失败原因';

-- ------------------------------------------------------------
-- 审批流程
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_approval_process (
    id                    BIGSERIAL    NOT NULL,
    tenant_id             BIGINT       NOT NULL,
    platform              VARCHAR(50)  NOT NULL,
    app_id                BIGINT,
    process_definition_id VARCHAR(200),
    process_instance_id   VARCHAR(200),
    platform_instance_id  VARCHAR(200),
    process_name          VARCHAR(500),
    initiator_id          VARCHAR(100),
    initiator_name        VARCHAR(100),
    status                VARCHAR(50),
    current_node          VARCHAR(200),
    current_approver_ids  TEXT,
    form_data             TEXT,
    records               TEXT,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at          TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version               INT          NOT NULL DEFAULT 0,
    deleted               SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_office_approval_tenant ON office_approval_process (tenant_id);
CREATE INDEX IF NOT EXISTS idx_office_approval_platform_instance ON office_approval_process (platform, platform_instance_id);
CREATE INDEX IF NOT EXISTS idx_office_approval_process_instance ON office_approval_process (process_instance_id);
CREATE INDEX IF NOT EXISTS idx_office_approval_initiator ON office_approval_process (initiator_id);
CREATE INDEX IF NOT EXISTS idx_office_approval_status ON office_approval_process (status);
CREATE INDEX IF NOT EXISTS idx_office_approval_created_at ON office_approval_process (created_at);
COMMENT ON TABLE  office_approval_process IS '办公平台审批流程';
COMMENT ON COLUMN office_approval_process.status IS '审批状态（PENDING/IN_PROGRESS/APPROVED/REJECTED/CANCELLED/TRANSFERRED）';

-- ------------------------------------------------------------
-- 日历事件
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_calendar_event (
    id                BIGSERIAL    NOT NULL,
    tenant_id         BIGINT       NOT NULL,
    platform          VARCHAR(50)  NOT NULL,
    platform_event_id VARCHAR(200) NOT NULL,
    title             VARCHAR(500) NOT NULL,
    description       TEXT,
    event_type        VARCHAR(50)  NOT NULL,
    organizer_id      VARCHAR(100) NOT NULL,
    organizer_name    VARCHAR(100) NOT NULL,
    start_time        TIMESTAMP    NOT NULL,
    end_time          TIMESTAMP    NOT NULL,
    is_all_day        SMALLINT     NOT NULL DEFAULT 0,
    location          VARCHAR(500),
    participant_ids   TEXT,
    participant_names TEXT,
    meeting_room_id   VARCHAR(100),
    reminder_minutes  INT          DEFAULT -1,
    is_recurring      SMALLINT     NOT NULL DEFAULT 0,
    recurrence_rule   TEXT,
    status            VARCHAR(50)  DEFAULT 'TENTATIVE',
    version           INT          NOT NULL DEFAULT 0,
    deleted           SMALLINT     NOT NULL DEFAULT 0,
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_office_calendar_tenant ON office_calendar_event (tenant_id);
CREATE INDEX IF NOT EXISTS idx_office_calendar_platform_event ON office_calendar_event (platform, platform_event_id);
CREATE INDEX IF NOT EXISTS idx_office_calendar_organizer ON office_calendar_event (organizer_id);
CREATE INDEX IF NOT EXISTS idx_office_calendar_time_range ON office_calendar_event (start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_office_calendar_status ON office_calendar_event (status);
CREATE INDEX IF NOT EXISTS idx_office_calendar_create_time ON office_calendar_event (create_time);
COMMENT ON TABLE  office_calendar_event IS '办公平台日历事件';
COMMENT ON COLUMN office_calendar_event.event_type IS '事件类型（MEETING/TASK/REMINDER/VACATION/BIRTHDAY/OTHER）';
COMMENT ON COLUMN office_calendar_event.status IS '事件状态（TENTATIVE/CONFIRMED/CANCELLED）';

-- ------------------------------------------------------------
-- 文档信息
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_document_info (
    id                    BIGSERIAL     NOT NULL,
    tenant_id             BIGINT        NOT NULL,
    platform              VARCHAR(50)   NOT NULL,
    platform_document_id  VARCHAR(200)  NOT NULL,
    document_type         VARCHAR(50)   NOT NULL,
    title                 VARCHAR(500)  NOT NULL,
    path                  VARCHAR(1000),
    url                   VARCHAR(1000),
    owner_id              VARCHAR(100)  NOT NULL,
    owner_name            VARCHAR(100)  NOT NULL,
    parent_folder_id      VARCHAR(200),
    file_size             BIGINT,
    file_extension        VARCHAR(50),
    mime_type             VARCHAR(100),
    is_folder             SMALLINT      NOT NULL DEFAULT 0,
    permission            VARCHAR(50)   DEFAULT 'PRIVATE',
    collaborator_ids      TEXT,
    version               VARCHAR(50),
    last_modified_by_id   VARCHAR(100),
    last_modified_by_name VARCHAR(100),
    last_modified_time    TIMESTAMP,
    is_archived           SMALLINT      NOT NULL DEFAULT 0,
    version_number        INT           NOT NULL DEFAULT 0,
    deleted               SMALLINT      NOT NULL DEFAULT 0,
    create_time           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_office_document_tenant ON office_document_info (tenant_id);
CREATE INDEX IF NOT EXISTS idx_office_document_platform_doc ON office_document_info (platform, platform_document_id);
CREATE INDEX IF NOT EXISTS idx_office_document_owner ON office_document_info (owner_id);
CREATE INDEX IF NOT EXISTS idx_office_document_parent ON office_document_info (parent_folder_id);
CREATE INDEX IF NOT EXISTS idx_office_document_title ON office_document_info (title);
CREATE INDEX IF NOT EXISTS idx_office_document_archived ON office_document_info (is_archived);
CREATE INDEX IF NOT EXISTS idx_office_document_create_time ON office_document_info (create_time);
COMMENT ON TABLE  office_document_info IS '办公平台文档信息';
COMMENT ON COLUMN office_document_info.document_type IS '文档类型（DOC/SHEET/SLIDE/MINDMAP/FOLDER/FILE）';
COMMENT ON COLUMN office_document_info.permission IS '权限类型（PRIVATE/PUBLIC/SHARED）';
