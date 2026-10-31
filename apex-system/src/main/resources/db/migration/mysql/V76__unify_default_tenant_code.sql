-- V76: 统一默认租户编码（统一认证收口 A6 / 方案批次 B1）
-- 背景：主库 V44 初始化的默认租户编码为 apex-default，而认证中心代码与种子使用 default，
-- 两套租户 ID 空间脱节。本迁移将 apex-default 全库统一改名为 default。
-- 幂等性：改名前置断言 + UPDATE 天然幂等，可安全重放；动态遍历保证不遗漏任何 tenant_id 表。

DROP PROCEDURE IF EXISTS `unify_default_tenant_code`;
DELIMITER //
CREATE PROCEDURE `unify_default_tenant_code`()
BEGIN
    DECLARE v_source_count INT DEFAULT 0;
    DECLARE v_target_count INT DEFAULT 0;
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_table_name VARCHAR(64);
    DECLARE v_total_renamed BIGINT DEFAULT 0;
    DECLARE v_rows INT DEFAULT 0;

    -- 游标：遍历当前库所有含 tenant_id 列的业务表（排除租户注册表自身，单独改名）
    DECLARE table_cursor CURSOR FOR
        SELECT c.TABLE_NAME
        FROM information_schema.COLUMNS c
        WHERE c.TABLE_SCHEMA = DATABASE()
          AND c.COLUMN_NAME = 'tenant_id'
          AND c.TABLE_NAME <> 'sys_tenant'
          AND c.DATA_TYPE IN ('varchar', 'char', 'text');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    -- 前置断言 1：源租户行数不得超过 1
    SELECT COUNT(*) INTO v_source_count
    FROM `sys_tenant`
    WHERE `tenant_code` = 'apex-default' AND `deleted` = 0;

    -- 前置断言 2：目标编码已被占用且源仍存在 → 冲突拒绝
    SELECT COUNT(*) INTO v_target_count
    FROM `sys_tenant`
    WHERE `tenant_code` = 'default' AND `deleted` = 0;

    IF v_source_count > 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V76 found duplicate active apex-default tenants';
    END IF;

    IF v_source_count = 1 AND v_target_count >= 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V76 cannot rename apex-default to default: target code already exists';
    END IF;

    IF v_source_count = 0 AND v_target_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V76 found neither apex-default nor default tenant';
    END IF;

    -- 租户注册表改名（唯一键随值更新）
    UPDATE `sys_tenant`
    SET `tenant_code` = 'default', `update_time` = NOW()
    WHERE `tenant_code` = 'apex-default';

    -- 动态遍历：所有含 tenant_id 的表执行改名，累计受影响行数
    OPEN table_cursor;
    rename_loop: LOOP
        FETCH table_cursor INTO v_table_name;
        IF v_done = 1 THEN
            LEAVE rename_loop;
        END IF;
        SET @stmt = CONCAT(
            'UPDATE `', v_table_name, '` SET `tenant_id` = ''default'' WHERE `tenant_id` = ''apex-default''');
        PREPARE stmt FROM @stmt;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET v_rows = ROW_COUNT();
        SET v_total_renamed = v_total_renamed + GREATEST(v_rows, 0);
    END LOOP;
    CLOSE table_cursor;

    -- 后置断言：租户注册表不得残留源编码
    SELECT COUNT(*) INTO v_source_count
    FROM `sys_tenant`
    WHERE `tenant_code` = 'apex-default';

    IF v_source_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V76 failed: apex-default still present after rename';
    END IF;
END//
DELIMITER ;

CALL `unify_default_tenant_code`();
DROP PROCEDURE IF EXISTS `unify_default_tenant_code`;
