-- V44: 初始化默认租户及管理员租户归属

DROP PROCEDURE IF EXISTS `initialize_default_tenant`;
DELIMITER //
CREATE PROCEDURE `initialize_default_tenant`()
BEGIN
    DECLARE v_tenant_count INT DEFAULT 0;
    DECLARE v_other_tenant_count INT DEFAULT 0;
    DECLARE v_admin_count INT DEFAULT 0;
    DECLARE v_unassigned_user_count INT DEFAULT 0;
    DECLARE v_unassigned_dept_count INT DEFAULT 0;
    DECLARE v_root_conflict_count INT DEFAULT 0;
    DECLARE v_unassigned_role_count INT DEFAULT 0;
    DECLARE v_unassigned_role_dept_count INT DEFAULT 0;
    DECLARE v_tenant_id BIGINT DEFAULT 1;

    SELECT COUNT(*) INTO v_tenant_count
    FROM `sys_tenant`
    WHERE `tenant_code` = 'apex-default' AND `deleted` = 0;

    SELECT COUNT(*) INTO v_other_tenant_count
    FROM `sys_tenant`
    WHERE `tenant_code` <> 'apex-default' AND `deleted` = 0;

    IF v_other_tenant_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V44 cannot initialize apex-default while another active tenant exists';
    END IF;

    IF v_tenant_count > 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V44 found duplicate active apex-default tenants';
    END IF;

    SELECT COUNT(*) INTO v_admin_count
    FROM `sys_user`
    WHERE `username` = 'admin' AND `deleted` = 0 AND `status` = 1;

    IF v_admin_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V44 requires exactly one active admin user';
    END IF;

    SELECT COUNT(*) INTO v_unassigned_user_count
    FROM `sys_user`
    WHERE `deleted` = 0
      AND (`tenant_id` IS NULL OR `tenant_id` = '')
      AND NOT (`username` = 'admin' AND `status` = 1);

    IF v_unassigned_user_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V44 found unassigned non-admin users and cannot infer their tenant';
    END IF;

    SELECT COUNT(*) INTO v_root_conflict_count
    FROM `sys_dept`
    WHERE `id` = 1
      AND NOT (`parent_id` = 0 AND `ancestors` = '0' AND `name` = 'Apex 科技');

    IF v_root_conflict_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V44 cannot safely use department id 1 as the default root';
    END IF;

    SELECT COUNT(*) INTO v_unassigned_dept_count
    FROM `sys_dept`
    WHERE `tenant_id` IS NULL OR `tenant_id` = '';

    IF v_unassigned_dept_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V44 found unassigned departments and cannot infer their tenant';
    END IF;

    SELECT COUNT(*) INTO v_unassigned_role_count
    FROM `sys_user_role` ur
    INNER JOIN `sys_user` u ON u.`id` = ur.`user_id`
    WHERE ur.`deleted` = 0
      AND (ur.`tenant_id` IS NULL OR ur.`tenant_id` = '')
      AND NOT (u.`username` = 'admin' AND u.`status` = 1);

    IF v_unassigned_role_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V44 found unassigned roles for non-admin users';
    END IF;

    SELECT COUNT(*) INTO v_unassigned_role_dept_count
    FROM `sys_role_dept` rd
    LEFT JOIN `sys_dept` d ON d.`id` = rd.`dept_id`
    WHERE rd.`deleted` = 0
      AND (rd.`tenant_id` IS NULL OR rd.`tenant_id` = '')
      AND (d.`tenant_id` IS NULL OR d.`tenant_id` <> 'apex-default');

    IF v_unassigned_role_dept_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V44 found ambiguous role department tenant assignments';
    END IF;

    IF v_tenant_count = 0 THEN
        INSERT INTO `sys_tenant`
            (`id`, `tenant_code`, `tenant_name`, `status`, `create_time`, `update_time`, `version`, `deleted`)
        VALUES
            (v_tenant_id, 'apex-default', 'Apex 默认租户', 'ACTIVE', NOW(), NOW(), 0, 0);
    ELSE
        SELECT `id` INTO v_tenant_id
        FROM `sys_tenant`
        WHERE `tenant_code` = 'apex-default' AND `deleted` = 0
        LIMIT 1;
        UPDATE `sys_tenant`
        SET `tenant_name` = 'Apex 默认租户', `status` = 'ACTIVE', `update_time` = NOW()
        WHERE `id` = v_tenant_id;
    END IF;

    INSERT INTO `sys_dept`
        (`id`, `parent_id`, `ancestors`, `name`, `order_num`, `status`, `tenant_id`, `create_time`, `update_time`, `version`, `deleted`)
    SELECT 1, 0, '0', 'Apex 科技', 1, 1, 'apex-default', NOW(), NOW(), 0, 0
    WHERE NOT EXISTS (SELECT 1 FROM `sys_dept` WHERE `id` = 1);

    UPDATE `sys_user`
    SET `tenant_id` = 'apex-default', `dept_id` = 1, `update_time` = NOW()
    WHERE `username` = 'admin' AND `deleted` = 0 AND `status` = 1;

    UPDATE `sys_user_role` ur
    INNER JOIN `sys_user` u ON u.`id` = ur.`user_id`
    SET ur.`tenant_id` = 'apex-default', ur.`update_time` = NOW()
    WHERE u.`username` = 'admin' AND u.`deleted` = 0 AND u.`status` = 1;

    UPDATE `sys_role_dept`
    SET `tenant_id` = 'apex-default', `update_time` = NOW()
    WHERE (`tenant_id` IS NULL OR `tenant_id` = '') AND `dept_id` = 1;

    INSERT INTO `sys_role_dept`
        (`id`, `role_id`, `dept_id`, `tenant_id`, `create_time`, `update_time`, `version`, `deleted`)
    SELECT base.max_id + ROW_NUMBER() OVER (ORDER BY ur.`role_id`), ur.`role_id`, 1, 'apex-default', NOW(), NOW(), 0, 0
    FROM `sys_user_role` ur
    CROSS JOIN (SELECT COALESCE(MAX(`id`), 0) AS max_id FROM `sys_role_dept`) base
    INNER JOIN `sys_user` u ON u.`id` = ur.`user_id`
    WHERE u.`username` = 'admin'
      AND ur.`tenant_id` = 'apex-default'
      AND ur.`deleted` = 0
      AND NOT EXISTS (
          SELECT 1 FROM `sys_role_dept` rd
          WHERE rd.`role_id` = ur.`role_id`
            AND rd.`dept_id` = 1
            AND rd.`tenant_id` = 'apex-default'
            AND rd.`deleted` = 0
      );

    UPDATE `sys_tenant` t
    INNER JOIN `sys_user` u ON u.`username` = 'admin' AND u.`tenant_id` = 'apex-default'
    SET t.`admin_user_id` = CAST(u.`id` AS CHAR), t.`update_time` = NOW()
    WHERE t.`tenant_code` = 'apex-default' AND t.`deleted` = 0;
END//
DELIMITER ;

CALL `initialize_default_tenant`();
DROP PROCEDURE IF EXISTS `initialize_default_tenant`;
