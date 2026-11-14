-- V78: 租户套餐与功能裁剪（统一认证收口 / 方案批次 B3）
-- sys_tenant_package 平台级定义表（默认租户不裁剪；非默认租户按套餐裁剪菜单与权限码）。
-- 幂等性：CREATE TABLE IF NOT EXISTS + INSERT IGNORE 种子，可安全重放。
-- platform-full 种子为数据驱动（聚合 sys_menu/sys_permission 全量 ID），菜单演进后重放亦不漂移。

CREATE TABLE IF NOT EXISTS `sys_tenant_package` (
    `id`              BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `package_code`    VARCHAR(64)  NOT NULL COMMENT '套餐编码（全局唯一）',
    `package_name`    VARCHAR(128) NOT NULL COMMENT '套餐名称',
    `menu_ids`        JSON         DEFAULT NULL COMMENT '可用菜单ID集合（JSON 数组；NULL 表示全量）',
    `permission_codes` JSON        DEFAULT NULL COMMENT '可用权限码集合（JSON 数组；NULL 表示全量）',
    `features`        JSON         DEFAULT NULL COMMENT '能力订阅集合（JSON 数组，能力运营 D4 预留）',
    `status`          INT          NOT NULL DEFAULT 1 COMMENT '状态（0-停用，1-正常）',
    `remark`          VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`         INT          NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    `deleted`         INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0-未删除，1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_package_code` (`package_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户套餐定义表（平台级，菜单/权限/能力裁剪）';

-- platform-full 种子：全量菜单与权限码（数据驱动聚合）
INSERT IGNORE INTO `sys_tenant_package`
    (`id`, `package_code`, `package_name`, `menu_ids`, `permission_codes`, `features`, `status`, `remark`, `create_time`, `update_time`)
SELECT 1, 'platform-full', '平台全量套餐',
       (SELECT COALESCE(CAST(JSON_ARRAYAGG(`id`) AS JSON), JSON_ARRAY()) FROM `sys_menu` WHERE `deleted` = 0),
       (SELECT COALESCE(CAST(JSON_ARRAYAGG(`code`) AS JSON), JSON_ARRAY()) FROM `sys_permission` WHERE `deleted` = 0),
       JSON_ARRAY(),
       1, '内置全量套餐：不裁剪任何菜单与权限码', NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_tenant_package` WHERE `package_code` = 'platform-full' AND `deleted` = 0);
