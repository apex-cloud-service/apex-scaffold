-- ============================================================
-- Apex Scaffold - 办公服务 basic Profile 初始化脚本（H2）
-- ============================================================
-- 【用途】
--   基础版（basic）单机环境使用 H2 内存库（MODE=MySQL）时，
--   通过 spring.sql.init 加载本脚本初始化表结构，替代 Flyway。
--   表结构与应用代码 PO 保持一致（与 db/migration 下 MySQL/PostgreSQL V1 脚本对齐）。
--   注意：office_approval_process / office_message 使用 created_at/updated_at
--   （如 PO 定义），其余表使用 create_time/update_time。
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
--   off_message_template         办公消息模板（V2 迁入）
--   off_message                  办公消息（V2 迁入）
--   off_message_recipient        办公消息接收人（V2 迁入）
--   off_department               办公组织部门（V2 迁入）
--   off_employee                 办公组织员工（V2 迁入）
--   off_sync_history             办公组织同步历史（V2 迁入）
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

-- ------------------------------------------------------------
-- 办公平台应用配置
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_platform_app (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id                VARCHAR(64)  NOT NULL,
    platform                 VARCHAR(32)  NOT NULL,
    app_id                   VARCHAR(128) NOT NULL,
    secret_reference         VARCHAR(255) NOT NULL,
    redirect_uri             VARCHAR(512),
    signing_secret_reference VARCHAR(255),
    capabilities             CLOB,
    enabled                  TINYINT      NOT NULL DEFAULT 0,
    version                  INT          NOT NULL DEFAULT 0,
    deleted                  TINYINT      NOT NULL DEFAULT 0,
    create_time              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time              TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_office_app_tenant_platform ON office_platform_app (tenant_id, platform);

-- ------------------------------------------------------------
-- 外部身份绑定
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_identity_binding (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id        VARCHAR(64)  NOT NULL,
    user_id          BIGINT       NOT NULL,
    platform         VARCHAR(32)  NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    enabled          TINYINT      NOT NULL DEFAULT 1,
    version          INT          NOT NULL DEFAULT 0,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_office_binding_external ON office_identity_binding (tenant_id, platform, external_user_id);
CREATE INDEX IF NOT EXISTS idx_office_binding_user ON office_identity_binding (tenant_id, platform, user_id);

-- ------------------------------------------------------------
-- 组织映射
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_organization_mapping (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id        VARCHAR(64)  NOT NULL,
    platform         VARCHAR(32)  NOT NULL,
    external_org_id  VARCHAR(255) NOT NULL,
    external_user_id VARCHAR(255),
    apex_dept_id     VARCHAR(64),
    apex_user_id     VARCHAR(64),
    version          INT          NOT NULL DEFAULT 0,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_office_org ON office_organization_mapping (tenant_id, platform, external_org_id);

-- ------------------------------------------------------------
-- 同步任务游标
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_sync_task (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id       VARCHAR(64)  NOT NULL,
    platform        VARCHAR(32)  NOT NULL,
    cursor_value    VARCHAR(512),
    state           VARCHAR(32)  NOT NULL,
    failure_message VARCHAR(1000),
    version         INT          NOT NULL DEFAULT 0,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_office_sync ON office_sync_task (tenant_id, platform);

-- ------------------------------------------------------------
-- 办公消息发送记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_message (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT       NOT NULL,
    platform            VARCHAR(50)  NOT NULL,
    app_id              BIGINT       NOT NULL,
    message_type        VARCHAR(50)  NOT NULL,
    target_type         VARCHAR(50)  NOT NULL,
    target_user_ids     CLOB         NOT NULL,
    title               VARCHAR(500),
    content             CLOB         NOT NULL,
    extra_data          CLOB,
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
    deleted             TINYINT      NOT NULL DEFAULT 0,
    version             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_office_message_tenant_platform ON office_message (tenant_id, platform);
CREATE INDEX IF NOT EXISTS idx_office_message_tenant_status ON office_message (tenant_id, send_status);
CREATE INDEX IF NOT EXISTS idx_office_message_platform_message_id ON office_message (platform_message_id);
CREATE INDEX IF NOT EXISTS idx_office_message_sent_at ON office_message (sent_at);

-- ------------------------------------------------------------
-- 审批流程
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_approval_process (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
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
    current_approver_ids  CLOB,
    form_data             CLOB,
    records               CLOB,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at          TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version               INT          NOT NULL DEFAULT 0,
    deleted               TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_office_approval_tenant ON office_approval_process (tenant_id);
CREATE INDEX IF NOT EXISTS idx_office_approval_platform_instance ON office_approval_process (platform, platform_instance_id);
CREATE INDEX IF NOT EXISTS idx_office_approval_process_instance ON office_approval_process (process_instance_id);
CREATE INDEX IF NOT EXISTS idx_office_approval_initiator ON office_approval_process (initiator_id);
CREATE INDEX IF NOT EXISTS idx_office_approval_status ON office_approval_process (status);
CREATE INDEX IF NOT EXISTS idx_office_approval_created_at ON office_approval_process (created_at);

-- ------------------------------------------------------------
-- 日历事件
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_calendar_event (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id         BIGINT       NOT NULL,
    platform          VARCHAR(50)  NOT NULL,
    platform_event_id VARCHAR(200) NOT NULL,
    title             VARCHAR(500) NOT NULL,
    description       CLOB,
    event_type        VARCHAR(50)  NOT NULL,
    organizer_id      VARCHAR(100) NOT NULL,
    organizer_name    VARCHAR(100) NOT NULL,
    start_time        TIMESTAMP    NOT NULL,
    end_time          TIMESTAMP    NOT NULL,
    is_all_day        TINYINT      NOT NULL DEFAULT 0,
    location          VARCHAR(500),
    participant_ids   CLOB,
    participant_names CLOB,
    meeting_room_id   VARCHAR(100),
    reminder_minutes  INT          DEFAULT -1,
    is_recurring      TINYINT      NOT NULL DEFAULT 0,
    recurrence_rule   CLOB,
    status            VARCHAR(50)  DEFAULT 'TENTATIVE',
    version           INT          NOT NULL DEFAULT 0,
    deleted           TINYINT      NOT NULL DEFAULT 0,
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

-- ------------------------------------------------------------
-- 文档信息
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS office_document_info (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id             BIGINT       NOT NULL,
    platform              VARCHAR(50)  NOT NULL,
    platform_document_id  VARCHAR(200) NOT NULL,
    document_type         VARCHAR(50)  NOT NULL,
    title                 VARCHAR(500) NOT NULL,
    path                  VARCHAR(1000),
    url                   VARCHAR(1000),
    owner_id              VARCHAR(100) NOT NULL,
    owner_name            VARCHAR(100) NOT NULL,
    parent_folder_id      VARCHAR(200),
    file_size             BIGINT,
    file_extension        VARCHAR(50),
    mime_type             VARCHAR(100),
    is_folder             TINYINT      NOT NULL DEFAULT 0,
    permission            VARCHAR(50)  DEFAULT 'PRIVATE',
    collaborator_ids      CLOB,
    version               VARCHAR(50),
    last_modified_by_id   VARCHAR(100),
    last_modified_by_name VARCHAR(100),
    last_modified_time    TIMESTAMP,
    is_archived           TINYINT      NOT NULL DEFAULT 0,
    version_number        INT          NOT NULL DEFAULT 0,
    deleted               TINYINT      NOT NULL DEFAULT 0,
    create_time           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_office_document_tenant ON office_document_info (tenant_id);
CREATE INDEX IF NOT EXISTS idx_office_document_platform_doc ON office_document_info (platform, platform_document_id);
CREATE INDEX IF NOT EXISTS idx_office_document_owner ON office_document_info (owner_id);
CREATE INDEX IF NOT EXISTS idx_office_document_parent ON office_document_info (parent_folder_id);
CREATE INDEX IF NOT EXISTS idx_office_document_title ON office_document_info (title);
CREATE INDEX IF NOT EXISTS idx_office_document_archived ON office_document_info (is_archived);
CREATE INDEX IF NOT EXISTS idx_office_document_create_time ON office_document_info (create_time);

-- ------------------------------------------------------------
-- 办公消息模板（V2 迁入）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_message_template (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    template_code    VARCHAR(64)  NOT NULL,
    template_name    VARCHAR(128) NOT NULL,
    template_type    VARCHAR(32)  NOT NULL,
    template_content CLOB         NOT NULL,
    variables        CLOB,
    enabled          TINYINT      NOT NULL DEFAULT 1,
    tenant_id        VARCHAR(64)  NOT NULL,
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    version          INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_off_message_template_code_tenant ON off_message_template (template_code, tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_message_template_tenant_enabled ON off_message_template (tenant_id, enabled, deleted);

-- ------------------------------------------------------------
-- 办公消息（V2 迁入）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_message (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    message_code VARCHAR(64)  NOT NULL,
    template_id  BIGINT,
    subject      VARCHAR(255) NOT NULL,
    content      CLOB         NOT NULL,
    message_type VARCHAR(32)  NOT NULL,
    priority     VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    sender_id    BIGINT,
    sender_name  VARCHAR(64),
    send_time    TIMESTAMP    NOT NULL,
    expire_time  TIMESTAMP,
    status       VARCHAR(16)  NOT NULL DEFAULT 'SENT',
    tenant_id    VARCHAR(64)  NOT NULL,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    version      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_off_message_code_tenant ON off_message (message_code, tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_message_tenant_status ON off_message (tenant_id, status, deleted);
CREATE INDEX IF NOT EXISTS idx_off_message_sender_time ON off_message (sender_id, send_time);
CREATE INDEX IF NOT EXISTS idx_off_message_template ON off_message (template_id);

-- ------------------------------------------------------------
-- 办公消息接收人（V2 迁入）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_message_recipient (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    message_id     BIGINT      NOT NULL,
    recipient_id   BIGINT      NOT NULL,
    recipient_name VARCHAR(64),
    recipient_type VARCHAR(16) NOT NULL DEFAULT 'USER',
    read_status    TINYINT     NOT NULL DEFAULT 0,
    read_time      TIMESTAMP,
    tenant_id      VARCHAR(64) NOT NULL,
    create_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT     NOT NULL DEFAULT 0,
    version        INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_off_message_recipient ON off_message_recipient (message_id, recipient_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_recipient_status ON off_message_recipient (recipient_id, read_status, deleted);
CREATE INDEX IF NOT EXISTS idx_off_recipient_tenant ON off_message_recipient (tenant_id, deleted);

-- ------------------------------------------------------------
-- 办公组织部门（V2 迁入）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_department (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    dept_code    VARCHAR(64)  NOT NULL,
    dept_name    VARCHAR(128) NOT NULL,
    parent_id    BIGINT,
    dept_path    VARCHAR(512),
    dept_level   INT          NOT NULL DEFAULT 1,
    sort_order   INT          NOT NULL DEFAULT 0,
    leader_id    BIGINT,
    leader_name  VARCHAR(64),
    phone        VARCHAR(32),
    email        VARCHAR(128),
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    sync_source  VARCHAR(32),
    external_id  VARCHAR(128),
    tenant_id    VARCHAR(64)  NOT NULL,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    version      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_off_department_code_tenant ON off_department (dept_code, tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_department_parent_tenant ON off_department (parent_id, tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_department_sync ON off_department (sync_source, external_id);

-- ------------------------------------------------------------
-- 办公组织员工（V2 迁入）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_employee (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    employee_code VARCHAR(64)  NOT NULL,
    employee_name VARCHAR(64)  NOT NULL,
    dept_id       BIGINT       NOT NULL,
    dept_name     VARCHAR(128),
    position      VARCHAR(64),
    job_title     VARCHAR(64),
    mobile        VARCHAR(32),
    email         VARCHAR(128),
    gender        VARCHAR(8),
    birthday      DATE,
    hire_date     DATE,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    user_id       BIGINT,
    sync_source   VARCHAR(32),
    external_id   VARCHAR(128),
    tenant_id     VARCHAR(64)  NOT NULL,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_off_employee_code_tenant ON off_employee (employee_code, tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_employee_dept ON off_employee (dept_id, tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_employee_user ON off_employee (user_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_off_employee_sync ON off_employee (sync_source, external_id);

-- ------------------------------------------------------------
-- 办公组织同步历史（V2 迁入）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_sync_history (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    sync_type      VARCHAR(32)  NOT NULL,
    sync_source    VARCHAR(32)  NOT NULL,
    sync_status    VARCHAR(16)  NOT NULL,
    start_time     TIMESTAMP    NOT NULL,
    end_time       TIMESTAMP,
    duration       INT,
    dept_count     INT          DEFAULT 0,
    employee_count INT          DEFAULT 0,
    error_message  CLOB,
    tenant_id      VARCHAR(64)  NOT NULL,
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_off_sync_tenant_time ON off_sync_history (tenant_id, start_time);
CREATE INDEX IF NOT EXISTS idx_off_sync_status_time ON off_sync_history (sync_status, start_time);