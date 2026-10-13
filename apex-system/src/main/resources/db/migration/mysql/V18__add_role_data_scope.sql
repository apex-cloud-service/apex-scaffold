-- V18: 显式角色数据权限与角色部门授权关系
-- data_scope 允许为空，未配置的角色必须由授权解析器拒绝。

ALTER TABLE `sys_role`
    ADD COLUMN `data_scope` INT DEFAULT NULL COMMENT '数据权限类型（1-全部，2-本部门，3-本部门及以下，4-本人，5-自定义）' AFTER `status`;
ALTER TABLE `sys_role`
    ADD CONSTRAINT `chk_sys_role_data_scope`
    CHECK (`data_scope` IS NULL OR `data_scope` BETWEEN 1 AND 5);
CREATE INDEX `idx_sys_role_data_scope` ON `sys_role` (`data_scope`);

ALTER TABLE `sys_dept`
    ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT '租户ID（多租户隔离）' AFTER `deleted`;
CREATE INDEX `idx_sys_dept_tenant_id` ON `sys_dept` (`tenant_id`);

CREATE TABLE IF NOT EXISTS `sys_role_dept` (
    `id`          BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `role_id`     BIGINT       NOT NULL COMMENT '角色ID',
    `dept_id`     BIGINT       NOT NULL COMMENT '部门ID',
    `tenant_id`   VARCHAR(64)  NOT NULL COMMENT '租户ID（多租户隔离）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    `deleted`     INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0-未删除，1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_role_dept_scope` (`tenant_id`, `role_id`, `dept_id`),
    KEY `idx_sys_role_dept_tenant_id` (`tenant_id`),
    KEY `idx_sys_role_dept_role_id` (`role_id`),
    KEY `idx_sys_role_dept_dept_id` (`dept_id`),
    CONSTRAINT `chk_sys_role_dept_deleted` CHECK (`deleted` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色自定义部门数据权限关系表';
