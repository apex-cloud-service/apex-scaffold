-- V43: 基于实际表列的幂等性能索引

DROP PROCEDURE IF EXISTS add_index_if_columns_exist;
DELIMITER $$
CREATE PROCEDURE add_index_if_columns_exist(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_columns VARCHAR(255)
)
BEGIN
    DECLARE v_table_exists INT DEFAULT 0;
    DECLARE v_column_count INT DEFAULT 0;
    DECLARE v_expected_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_table_exists
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name;

    SELECT COUNT(*) INTO v_expected_count
    FROM (
        SELECT TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(p_columns, ',', numbers.n), ',', -1)) AS column_name
        FROM (
            SELECT 1 n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6
        ) numbers
        WHERE numbers.n <= 1 + LENGTH(p_columns) - LENGTH(REPLACE(p_columns, ',', ''))
    ) requested_columns;

    SELECT COUNT(*) INTO v_column_count
    FROM information_schema.COLUMNS c
    WHERE c.TABLE_SCHEMA = DATABASE()
      AND c.TABLE_NAME = p_table_name
      AND FIND_IN_SET(c.COLUMN_NAME, p_columns) > 0;

    IF v_table_exists = 1 AND v_column_count = v_expected_count
       AND NOT EXISTS (
           SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = p_table_name
             AND INDEX_NAME = p_index_name
       ) THEN
        SET @create_index_sql = CONCAT(
            'CREATE INDEX `', p_index_name, '` ON `', p_table_name, '` (`',
            REPLACE(p_columns, ',', '`,`'), '`)');
        PREPARE create_index_stmt FROM @create_index_sql;
        EXECUTE create_index_stmt;
        DEALLOCATE PREPARE create_index_stmt;
    END IF;
END$$
DELIMITER ;

CALL add_index_if_columns_exist('off_message', 'idx_off_message_recipient_status_time', 'recipient_id,status,send_time');
CALL add_index_if_columns_exist('off_message', 'idx_off_message_sender_time_v43', 'sender_id,send_time');
CALL add_index_if_columns_exist('off_message_template', 'idx_off_template_code_enabled', 'template_code,enabled,tenant_id');
CALL add_index_if_columns_exist('off_department', 'idx_off_department_parent_status_sort', 'parent_id,status,sort_order');
CALL add_index_if_columns_exist('off_department', 'idx_off_department_sync_external', 'sync_source,external_id');
CALL add_index_if_columns_exist('off_employee', 'idx_off_employee_dept_status', 'dept_id,status');
CALL add_index_if_columns_exist('off_employee', 'idx_off_employee_code_tenant_v43', 'employee_code,tenant_id');
CALL add_index_if_columns_exist('off_employee', 'idx_off_employee_sync_external', 'sync_source,external_id');
CALL add_index_if_columns_exist('office_approval_process', 'idx_office_approval_initiator_status_time', 'initiator_id,status,initiate_time');
CALL add_index_if_columns_exist('office_approval_process', 'idx_office_approval_platform_instance', 'platform,platform_instance_id');
CALL add_index_if_columns_exist('office_calendar_event', 'idx_office_calendar_time_status', 'start_time,end_time,status');
CALL add_index_if_columns_exist('office_calendar_event', 'idx_office_calendar_organizer_time', 'organizer_id,start_time');
CALL add_index_if_columns_exist('office_calendar_event', 'idx_office_calendar_room_time', 'meeting_room_id,start_time,end_time');
CALL add_index_if_columns_exist('office_calendar_event', 'idx_office_calendar_platform_event', 'platform,platform_event_id');
CALL add_index_if_columns_exist('off_sync_history', 'idx_off_sync_source_time_v43', 'sync_source,start_time');
CALL add_index_if_columns_exist('off_sync_history', 'idx_off_sync_status_time_v43', 'sync_status,start_time');
CALL add_index_if_columns_exist('sys_user', 'idx_sys_user_keycloak_sub_v43', 'keycloak_sub');
CALL add_index_if_columns_exist('sys_user', 'idx_sys_user_email_v43', 'email');
CALL add_index_if_columns_exist('sys_user', 'idx_sys_user_tenant_status_v43', 'tenant_id,status');
CALL add_index_if_columns_exist('sys_menu', 'idx_sys_menu_parent_sort_v43', 'parent_id,sort');
CALL add_index_if_columns_exist('sys_menu', 'idx_sys_menu_type_status_v43', 'menu_type,status');

DROP PROCEDURE IF EXISTS add_index_if_columns_exist;
