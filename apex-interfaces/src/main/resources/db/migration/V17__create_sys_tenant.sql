-- ============================================================
-- V17: 租户模型与生命周期表
-- 版本: 1.3.0
-- 说明: 创建 sys_tenant 表，承载 TENANT-001 租户基础信息、
--       状态生命周期与配额约束。sys_tenant 为平台级元数据表，
--       已在 DefaultTenantLineHandler.IGNORE_TABLES 中显式排除，
--       不参与租户行级隔离，由平台管理员统一维护。
-- 前置依赖: V1-V16 已完成。
-- 幂等性: CREATE TABLE IF NOT EXISTS 幂等。
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_tenant` (
    `id`              BIGINT       NOT NULL                 COMMENT '主键ID（雪花算法）',
    `tenant_code`     VARCHAR(64)  NOT NULL                 COMMENT '租户编码（全局唯一，作为租户上下文ID）',
    `tenant_name`     VARCHAR(128) NOT NULL                 COMMENT '租户名称',
    `admin_user_id`   VARCHAR(64)  DEFAULT NULL             COMMENT '租户管理员用户ID',
    `package_code`    VARCHAR(64)  DEFAULT NULL             COMMENT '套餐标识',
    `expire_time`     DATETIME     DEFAULT NULL             COMMENT '有效期至（NULL表示长期有效）',
    `status`          VARCHAR(32)  NOT NULL                 COMMENT '租户状态（ACTIVE/DISABLED/FROZEN/DEACTIVATED/ARCHIVED）',
    `max_users`       INT          DEFAULT NULL             COMMENT '最大用户数（NULL表示不限制）',
    `max_storage_mb`  INT          DEFAULT NULL             COMMENT '最大存储容量MB（NULL表示不限制）',
    `remark`          VARCHAR(500) DEFAULT NULL             COMMENT '备注',
    `create_time`     DATETIME     NOT NULL                 COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL                 COMMENT '更新时间',
    `version`         INT          NOT NULL DEFAULT 0       COMMENT '乐观锁版本号',
    `deleted`         INT          NOT NULL DEFAULT 0       COMMENT '逻辑删除标记（0-未删除，1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_tenant_code` (`tenant_code`),
    KEY `idx_sys_tenant_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户基础信息与生命周期表（平台级元数据）';
