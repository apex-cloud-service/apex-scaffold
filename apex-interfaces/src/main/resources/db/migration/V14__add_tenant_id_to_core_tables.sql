-- ============================================================
-- V14: 多租户 tenant_id 字段补齐
-- 版本: 1.2.0
-- 说明: 为核心业务表增加 tenant_id 列，配合 MyBatis-Plus
--       TenantLineInnerInterceptor 实现 SaaS 级租户数据隔离。
--       系统配置类表（sys_menu/sys_permission/sys_dict* 等）不加租户列，
--       在 TenantLineHandler 中通过 ignoreTable 显式排除。
-- ============================================================

-- sys_user：用户表（核心租户隔离维度）
ALTER TABLE `sys_user`
    ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT '租户ID（多租户隔离）' AFTER `deleted`;
CREATE INDEX `idx_sys_user_tenant_id` ON `sys_user` (`tenant_id`);

-- sys_user_role：用户角色关联表
ALTER TABLE `sys_user_role`
    ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT '租户ID（多租户隔离）' AFTER `deleted`;
CREATE INDEX `idx_user_role_tenant_id` ON `sys_user_role` (`tenant_id`);

-- operation_log：操作审计日志（已有表根据实际情况执行）
-- 注意: 若 V12 已删除 audit_log，则以下操作对 operation_log 生效
-- operation_log 在 V1__init_schema 中未出现（审计日志独立于审计操作日志），
-- 此处仅针对 operation_log 表存在的实例执行；使用存储过程兜底，避免 Flyway 在
-- 不同分支（如已经过 V12 drop audit_log）上失败。

DELIMITER ;;
CREATE PROCEDURE IF NOT EXISTS add_tenant_id_if_not_exists(IN p_table VARCHAR(128), IN p_after_col VARCHAR(128))
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = 'tenant_id'
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `', p_after_col, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET @idx_sql = CONCAT('CREATE INDEX `idx_', p_table, '_tenant_id` ON `', p_table, '` (`tenant_id`)');
        PREPARE idx_stmt FROM @idx_sql;
        EXECUTE idx_stmt;
        DEALLOCATE PREPARE idx_stmt;
    END IF;
END ;;
DELIMITER ;

CALL add_tenant_id_if_not_exists('operation_log', 'deleted');
CALL add_tenant_id_if_not_exists('system_message', 'deleted');

DROP PROCEDURE IF EXISTS add_tenant_id_if_not_exists;
