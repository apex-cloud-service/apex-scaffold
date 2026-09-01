-- 脚本用途：清理历史空租户部门并强制部门与授权关系的同租户完整性。
-- 风险控制：无法唯一确认历史种子部门归属时终止迁移，禁止静默分配租户。
DROP PROCEDURE IF EXISTS `enforce_department_tenant_integrity`;

DELIMITER //
CREATE PROCEDURE `enforce_department_tenant_integrity`()
BEGIN
    DECLARE seed_dept_count INT DEFAULT 0;
    DECLARE seed_admin_count INT DEFAULT 0;
    DECLARE other_user_reference_count INT DEFAULT 0;
    DECLARE role_dept_reference_count INT DEFAULT 0;
    DECLARE unassigned_dept_count INT DEFAULT 0;

    SELECT COUNT(*) INTO seed_dept_count
    FROM `sys_dept`
    WHERE `id` = 1
      AND `parent_id` = 0
      AND `ancestors` = '0'
      AND `name` = 'Apex 科技'
      AND `tenant_id` IS NULL;

    IF seed_dept_count = 1 THEN
        SELECT COUNT(*) INTO seed_admin_count
        FROM `sys_user`
        WHERE `id` = 1
          AND `open_id` = 'admin-openid'
          AND `username` = 'admin'
          AND `dept_id` = 1;

        SELECT COUNT(*) INTO other_user_reference_count
        FROM `sys_user`
        WHERE `dept_id` = 1
          AND NOT (`id` = 1 AND `open_id` = 'admin-openid' AND `username` = 'admin');

        SELECT COUNT(*) INTO role_dept_reference_count
        FROM `sys_role_dept`
        WHERE `dept_id` = 1;

        IF seed_admin_count = 1
                AND other_user_reference_count = 0
                AND role_dept_reference_count = 0 THEN
            UPDATE `sys_user`
            SET `dept_id` = NULL
            WHERE `id` = 1
              AND `open_id` = 'admin-openid'
              AND `username` = 'admin'
              AND `dept_id` = 1;

            DELETE FROM `sys_dept`
            WHERE `id` = 1
              AND `parent_id` = 0
              AND `ancestors` = '0'
              AND `name` = 'Apex 科技'
              AND `tenant_id` IS NULL;
        ELSE
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'V19 cannot safely remove the legacy seed department because its references are ambiguous';
        END IF;
    END IF;

    SELECT COUNT(*) INTO unassigned_dept_count
    FROM `sys_dept`
    WHERE `tenant_id` IS NULL;

    IF unassigned_dept_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V19 requires every department to have an explicit tenant_id before migration';
    END IF;
END//
DELIMITER ;

CALL `enforce_department_tenant_integrity`();
DROP PROCEDURE IF EXISTS `enforce_department_tenant_integrity`;

ALTER TABLE `sys_dept`
    MODIFY COLUMN `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID（多租户隔离）',
    ADD UNIQUE KEY `uk_sys_dept_tenant_id_id` (`tenant_id`, `id`);

ALTER TABLE `sys_role_dept`
    ADD CONSTRAINT `fk_sys_role_dept_tenant_dept`
    FOREIGN KEY (`tenant_id`, `dept_id`)
    REFERENCES `sys_dept` (`tenant_id`, `id`);
