-- V18: 显式角色数据权限与角色部门授权关系
-- data_scope 允许为空，未配置的角色必须由授权解析器拒绝。

ALTER TABLE sys_role
    ADD COLUMN IF NOT EXISTS data_scope INT DEFAULT NULL;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_sys_role_data_scope'
          AND conrelid = 'sys_role'::regclass
    ) THEN
        ALTER TABLE sys_role
            ADD CONSTRAINT chk_sys_role_data_scope
            CHECK (data_scope IS NULL OR data_scope BETWEEN 1 AND 5);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_sys_role_data_scope ON sys_role (data_scope);

ALTER TABLE sys_dept
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) DEFAULT NULL;
CREATE INDEX IF NOT EXISTS idx_sys_dept_tenant_id ON sys_dept (tenant_id);

CREATE TABLE IF NOT EXISTS sys_role_dept (
    id          BIGINT       NOT NULL,
    role_id     BIGINT       NOT NULL,
    dept_id     BIGINT       NOT NULL,
    tenant_id   VARCHAR(64)  NOT NULL,
    create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version     INT          NOT NULL DEFAULT 0,
    deleted     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_dept_scope UNIQUE (tenant_id, role_id, dept_id),
    CONSTRAINT chk_sys_role_dept_deleted CHECK (deleted IN (0, 1))
);
CREATE INDEX IF NOT EXISTS idx_sys_role_dept_tenant_id ON sys_role_dept (tenant_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_dept_role_id ON sys_role_dept (role_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_dept_dept_id ON sys_role_dept (dept_id);
