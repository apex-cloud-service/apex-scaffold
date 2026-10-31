-- V77: 主库 RBAC 租户化（统一认证收口 A6 / 方案批次 B2）
-- 语义与 MySQL 版本一致：sys_role 租户列 + 唯一键重建（uk_role_code → uk_tenant_code）、
-- sys_role_permission 租户列并与角色对齐。存量角色全部归属 default 租户。

ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_role_code' AND conrelid = 'sys_role'::regclass)
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_tenant_code' AND conrelid = 'sys_role'::regclass) THEN
        ALTER TABLE sys_role
            DROP CONSTRAINT uk_role_code,
            ADD CONSTRAINT uk_tenant_code UNIQUE (tenant_id, code);
    END IF;
END
$$;

ALTER TABLE sys_role_permission ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

UPDATE sys_role_permission rp
   SET tenant_id = r.tenant_id
  FROM sys_role r
 WHERE rp.role_id = r.id
   AND rp.tenant_id <> r.tenant_id;

CREATE INDEX IF NOT EXISTS idx_sys_role_permission_tenant ON sys_role_permission (tenant_id);

UPDATE sys_role_dept rd
   SET tenant_id = r.tenant_id
  FROM sys_role r
 WHERE rd.role_id = r.id
   AND rd.tenant_id <> r.tenant_id;
