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

CREATE TABLE IF NOT EXISTS sys_tenant (
    id              BIGINT       NOT NULL,
    tenant_code     VARCHAR(64)  NOT NULL,
    tenant_name     VARCHAR(128) NOT NULL,
    admin_user_id   VARCHAR(64)  DEFAULT NULL,
    package_code    VARCHAR(64)  DEFAULT NULL,
    expire_time     TIMESTAMP     DEFAULT NULL,
    status          VARCHAR(32)  NOT NULL,
    max_users       INT          DEFAULT NULL,
    max_storage_mb  INT          DEFAULT NULL,
    remark          VARCHAR(500) DEFAULT NULL,
    create_time     TIMESTAMP     NOT NULL,
    update_time     TIMESTAMP     NOT NULL,
    version         INT          NOT NULL DEFAULT 0,
    deleted         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_tenant_code UNIQUE (tenant_code)
);
CREATE INDEX IF NOT EXISTS idx_sys_tenant_status ON sys_tenant (status);
