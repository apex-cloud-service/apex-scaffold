CREATE TABLE IF NOT EXISTS off_message_template (
    id BIGSERIAL PRIMARY KEY,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    template_type VARCHAR(32) NOT NULL,
    template_content TEXT NOT NULL,
    variables JSON,
    enabled SMALLINT NOT NULL DEFAULT 1,
    tenant_id VARCHAR(64) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_off_message_template_code_tenant UNIQUE (template_code, tenant_id, deleted)
);
CREATE INDEX IF NOT EXISTS idx_off_message_template_tenant_enabled
    ON off_message_template (tenant_id, enabled, deleted);

CREATE TABLE IF NOT EXISTS off_message (
    id BIGSERIAL PRIMARY KEY,
    message_code VARCHAR(64) NOT NULL,
    template_id BIGINT,
    subject VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    sender_id BIGINT,
    sender_name VARCHAR(64),
    send_time TIMESTAMP NOT NULL,
    expire_time TIMESTAMP,
    status VARCHAR(16) NOT NULL DEFAULT 'SENT',
    tenant_id VARCHAR(64) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_off_message_code_tenant UNIQUE (message_code, tenant_id, deleted)
);
CREATE INDEX IF NOT EXISTS idx_off_message_tenant_status
    ON off_message (tenant_id, status, deleted);
CREATE INDEX IF NOT EXISTS idx_off_message_sender_time
    ON off_message (sender_id, send_time);
CREATE INDEX IF NOT EXISTS idx_off_message_template
    ON off_message (template_id);

CREATE TABLE IF NOT EXISTS off_message_recipient (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    recipient_name VARCHAR(64),
    recipient_type VARCHAR(16) NOT NULL DEFAULT 'USER',
    read_status SMALLINT NOT NULL DEFAULT 0,
    read_time TIMESTAMP,
    tenant_id VARCHAR(64) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_off_message_recipient UNIQUE (message_id, recipient_id, deleted)
);
CREATE INDEX IF NOT EXISTS idx_off_recipient_status
    ON off_message_recipient (recipient_id, read_status, deleted);
CREATE INDEX IF NOT EXISTS idx_off_recipient_tenant
    ON off_message_recipient (tenant_id, deleted);

CREATE TABLE IF NOT EXISTS off_department (
    id BIGSERIAL PRIMARY KEY,
    dept_code VARCHAR(64) NOT NULL,
    dept_name VARCHAR(128) NOT NULL,
    parent_id BIGINT,
    dept_path VARCHAR(512),
    dept_level INT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    leader_id BIGINT,
    leader_name VARCHAR(64),
    phone VARCHAR(32),
    email VARCHAR(128),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sync_source VARCHAR(32),
    external_id VARCHAR(128),
    tenant_id VARCHAR(64) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_off_department_code_tenant UNIQUE (dept_code, tenant_id, deleted)
);
CREATE INDEX IF NOT EXISTS idx_off_department_parent_tenant
    ON off_department (parent_id, tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_department_sync
    ON off_department (sync_source, external_id);

CREATE TABLE IF NOT EXISTS off_employee (
    id BIGSERIAL PRIMARY KEY,
    employee_code VARCHAR(64) NOT NULL,
    employee_name VARCHAR(64) NOT NULL,
    dept_id BIGINT NOT NULL,
    dept_name VARCHAR(128),
    position VARCHAR(64),
    job_title VARCHAR(64),
    mobile VARCHAR(32),
    email VARCHAR(128),
    gender VARCHAR(8),
    birthday DATE,
    hire_date DATE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    user_id BIGINT,
    sync_source VARCHAR(32),
    external_id VARCHAR(128),
    tenant_id VARCHAR(64) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_off_employee_code_tenant UNIQUE (employee_code, tenant_id, deleted)
);
CREATE INDEX IF NOT EXISTS idx_off_employee_dept
    ON off_employee (dept_id, tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_off_employee_user
    ON off_employee (user_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_off_employee_sync
    ON off_employee (sync_source, external_id);

CREATE TABLE IF NOT EXISTS off_sync_history (
    id BIGSERIAL PRIMARY KEY,
    sync_type VARCHAR(32) NOT NULL,
    sync_source VARCHAR(32) NOT NULL,
    sync_status VARCHAR(16) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    duration INT,
    dept_count INT DEFAULT 0,
    employee_count INT DEFAULT 0,
    error_message TEXT,
    tenant_id VARCHAR(64) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_off_sync_tenant_time
    ON off_sync_history (tenant_id, start_time);
CREATE INDEX IF NOT EXISTS idx_off_sync_status_time
    ON off_sync_history (sync_status, start_time);

INSERT INTO sys_menu (
    id, parent_id, menu_name, menu_type, path, component, perms, icon,
    sort, visible, status, create_time, update_time, deleted
)
VALUES (
    2100, 2000, '办公消息', 1, 'message', 'office/message/index',
    'office:message:query', 'message', 1, 1, 1,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
)
ON CONFLICT (id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    component = EXCLUDED.component,
    perms = EXCLUDED.perms,
    status = 1,
    deleted = 0,
    update_time = CURRENT_TIMESTAMP;

INSERT INTO sys_permission (
    id, name, code, description, type, status, create_time, update_time, deleted
)
VALUES
    (3300, '查询办公消息', 'office:message:query', '查询办公消息', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (3301, '发送办公消息', 'office:message:create', '创建办公消息', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (3302, '删除办公消息', 'office:message:delete', '删除办公消息', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = 1,
    deleted = 0,
    update_time = CURRENT_TIMESTAMP;
