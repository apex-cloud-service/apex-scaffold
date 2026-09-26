-- V43: 基于现有表结构补充幂等性能索引

CREATE INDEX IF NOT EXISTS idx_off_message_sender_time_v43
    ON off_message (sender_id, send_time);
CREATE INDEX IF NOT EXISTS idx_off_message_template_code_enabled
    ON off_message_template (template_code, enabled, tenant_id);
CREATE INDEX IF NOT EXISTS idx_off_department_parent_status_sort
    ON off_department (parent_id, status, sort_order);
CREATE INDEX IF NOT EXISTS idx_off_department_sync_external
    ON off_department (sync_source, external_id);
CREATE INDEX IF NOT EXISTS idx_off_employee_dept_status
    ON off_employee (dept_id, status);
CREATE INDEX IF NOT EXISTS idx_off_employee_code_tenant_v43
    ON off_employee (employee_code, tenant_id);
CREATE INDEX IF NOT EXISTS idx_off_employee_sync_external
    ON off_employee (sync_source, external_id);
CREATE INDEX IF NOT EXISTS idx_off_sync_source_time_v43
    ON off_sync_history (sync_source, start_time);
CREATE INDEX IF NOT EXISTS idx_off_sync_status_time_v43
    ON off_sync_history (sync_status, start_time);

CREATE INDEX IF NOT EXISTS idx_office_approval_initiator_status_time
    ON office_approval_process (initiator_id, status, initiate_time);
CREATE INDEX IF NOT EXISTS idx_office_approval_platform_instance_v43
    ON office_approval_process (platform, platform_instance_id);
CREATE INDEX IF NOT EXISTS idx_office_calendar_time_status
    ON office_calendar_event (start_time, end_time, status);
CREATE INDEX IF NOT EXISTS idx_office_calendar_organizer_time
    ON office_calendar_event (organizer_id, start_time);
CREATE INDEX IF NOT EXISTS idx_office_calendar_room_time
    ON office_calendar_event (meeting_room_id, start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_office_calendar_platform_event_v43
    ON office_calendar_event (platform, platform_event_id);

CREATE INDEX IF NOT EXISTS idx_sys_user_keycloak_sub_v43
    ON sys_user (keycloak_sub);
CREATE INDEX IF NOT EXISTS idx_sys_user_email_v43
    ON sys_user (email);
CREATE INDEX IF NOT EXISTS idx_sys_user_tenant_status_v43
    ON sys_user (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_sys_menu_parent_sort_v43
    ON sys_menu (parent_id, sort);
CREATE INDEX IF NOT EXISTS idx_sys_menu_type_status_v43
    ON sys_menu (menu_type, status);
