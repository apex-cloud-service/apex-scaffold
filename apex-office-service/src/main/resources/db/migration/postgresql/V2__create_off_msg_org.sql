-- ============================================================
-- Apex Scaffold - 办公服务独立库 V2：办公组织与消息域迁入（PostgreSQL）
-- ============================================================
-- 【用途】
--   将旧单体 V42__add_office_platform_tables.sql 中归属办公领域的
--   六张 off_* 表迁入办公服务独立库 apex_office（迁移方案 §9.2/§9.3、
--   office-off-star-extraction-plan.md Phase 1）。新库版本链从 V1 起
--   与单体解耦，本脚本在 V1 之后追加。
--
-- 【表清单】
--   off_message_template     办公消息模板
--   off_message              办公消息
--   off_message_recipient    办公消息接收人
--   off_department           办公组织部门
--   off_employee             办公组织员工
--   off_sync_history         办公组织同步历史
--
-- 【字段来源原则】
--   以旧单体 V42 的 DDL 为唯一事实来源，列名/类型/可空性/默认值/索引/
--   唯一键严格保持一致；仅迁移表结构，不含 sys_menu / sys_permission
--   种子数据（系统域种子仍归属单体，不在本库承载范围内）。
--
-- 【幂等性】
--   全部 CREATE TABLE IF NOT EXISTS、CREATE [UNIQUE] INDEX IF NOT EXISTS，
--   可重复执行。
--
-- 【方言约定】
--   PostgreSQL 使用 TIMESTAMP 与 SMALLINT 逻辑删除字段；自增主键 BIGSERIAL。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

-- ------------------------------------------------------------
-- 办公消息模板
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_message_template (
    id               BIGSERIAL    NOT NULL,
    template_code    VARCHAR(64)  NOT NULL,
    template_name    VARCHAR(128) NOT NULL,
    template_type    VARCHAR(32)  NOT NULL,
    template_content TEXT         NOT NULL,
    variables        JSON,
    enabled          SMALLINT     NOT NULL DEFAULT 1,
    tenant_id        VARCHAR(64)  NOT NULL,
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT     NOT NULL DEFAULT 0,
    version          INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_off_message_template_code_tenant ON off_message_template (template_code, tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_message_template_tenant_enabled ON off_message_template (tenant_id, enabled, deleted);
COMMENT ON TABLE  off_message_template IS '办公消息模板';

-- ------------------------------------------------------------
-- 办公消息
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_message (
    id           BIGSERIAL    NOT NULL,
    message_code VARCHAR(64)  NOT NULL,
    template_id  BIGINT,
    subject      VARCHAR(255) NOT NULL,
    content      TEXT         NOT NULL,
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
    deleted      SMALLINT     NOT NULL DEFAULT 0,
    version      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_off_message_code_tenant ON off_message (message_code, tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_message_tenant_status ON off_message (tenant_id, status, deleted);
CREATE INDEX IF NOT EXISTS idx_off_message_sender_time ON off_message (sender_id, send_time);
CREATE INDEX IF NOT EXISTS idx_off_message_template ON off_message (template_id);
COMMENT ON TABLE  off_message IS '办公消息';
COMMENT ON COLUMN off_message.priority IS '优先级';
COMMENT ON COLUMN off_message.status IS '消息状态（SENT/...）';

-- ------------------------------------------------------------
-- 办公消息接收人
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_message_recipient (
    id             BIGSERIAL   NOT NULL,
    message_id     BIGINT      NOT NULL,
    recipient_id   BIGINT      NOT NULL,
    recipient_name VARCHAR(64),
    recipient_type VARCHAR(16) NOT NULL DEFAULT 'USER',
    read_status    SMALLINT    NOT NULL DEFAULT 0,
    read_time      TIMESTAMP,
    tenant_id      VARCHAR(64) NOT NULL,
    create_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT    NOT NULL DEFAULT 0,
    version        INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_off_message_recipient ON off_message_recipient (message_id, recipient_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_recipient_status ON off_message_recipient (recipient_id, read_status, deleted);
CREATE INDEX IF NOT EXISTS idx_off_recipient_tenant ON off_message_recipient (tenant_id, deleted);
COMMENT ON TABLE  off_message_recipient IS '办公消息接收人';
COMMENT ON COLUMN off_message_recipient.read_status IS '已读状态（0-未读 1-已读）';

-- ------------------------------------------------------------
-- 办公组织部门
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_department (
    id          BIGSERIAL    NOT NULL,
    dept_code   VARCHAR(64)  NOT NULL,
    dept_name   VARCHAR(128) NOT NULL,
    parent_id   BIGINT,
    dept_path   VARCHAR(512),
    dept_level  INT          NOT NULL DEFAULT 1,
    sort_order  INT          NOT NULL DEFAULT 0,
    leader_id   BIGINT,
    leader_name VARCHAR(64),
    phone       VARCHAR(32),
    email       VARCHAR(128),
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    sync_source VARCHAR(32),
    external_id VARCHAR(128),
    tenant_id   VARCHAR(64)  NOT NULL,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_off_department_code_tenant ON off_department (dept_code, tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_department_parent_tenant ON off_department (parent_id, tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_department_sync ON off_department (sync_source, external_id);
COMMENT ON TABLE  off_department IS '办公组织部门';

-- ------------------------------------------------------------
-- 办公组织员工
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_employee (
    id            BIGSERIAL    NOT NULL,
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
    deleted       SMALLINT     NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_off_employee_code_tenant ON off_employee (employee_code, tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_employee_dept ON off_employee (dept_id, tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_employee_user ON off_employee (user_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_off_employee_sync ON off_employee (sync_source, external_id);
COMMENT ON TABLE  off_employee IS '办公组织员工';

-- ------------------------------------------------------------
-- 办公组织同步历史
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS off_sync_history (
    id             BIGSERIAL   NOT NULL,
    sync_type      VARCHAR(32) NOT NULL,
    sync_source    VARCHAR(32) NOT NULL,
    sync_status    VARCHAR(16) NOT NULL,
    start_time     TIMESTAMP   NOT NULL,
    end_time       TIMESTAMP,
    duration       INT,
    dept_count     INT         DEFAULT 0,
    employee_count INT         DEFAULT 0,
    error_message  TEXT,
    tenant_id      VARCHAR(64) NOT NULL,
    create_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT    NOT NULL DEFAULT 0,
    version        INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_off_sync_tenant_time ON off_sync_history (tenant_id, start_time);
CREATE INDEX IF NOT EXISTS idx_off_sync_status_time ON off_sync_history (sync_status, start_time);
COMMENT ON TABLE  off_sync_history IS '办公组织同步历史';
