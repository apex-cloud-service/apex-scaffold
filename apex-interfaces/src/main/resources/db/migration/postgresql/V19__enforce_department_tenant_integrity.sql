-- PostgreSQL tenant integrity enforcement.
DO $$
DECLARE
    seed_dept_count INTEGER;
    seed_admin_count INTEGER;
    other_user_reference_count INTEGER;
    role_dept_reference_count INTEGER;
    unassigned_dept_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO seed_dept_count
    FROM sys_dept
    WHERE id = 1 AND parent_id = 0 AND ancestors = '0'
      AND name = 'Apex 科技' AND tenant_id IS NULL;

    IF seed_dept_count = 1 THEN
        SELECT COUNT(*) INTO seed_admin_count
        FROM sys_user
        WHERE id = 1 AND open_id = 'admin-openid'
          AND username = 'admin' AND dept_id = 1;
        SELECT COUNT(*) INTO other_user_reference_count
        FROM sys_user
        WHERE dept_id = 1
          AND NOT (id = 1 AND open_id = 'admin-openid' AND username = 'admin');
        SELECT COUNT(*) INTO role_dept_reference_count
        FROM sys_role_dept WHERE dept_id = 1;

        IF seed_admin_count = 1 AND other_user_reference_count = 0
           AND role_dept_reference_count = 0 THEN
            UPDATE sys_user SET dept_id = NULL
            WHERE id = 1 AND open_id = 'admin-openid'
              AND username = 'admin' AND dept_id = 1;
            DELETE FROM sys_dept
            WHERE id = 1 AND parent_id = 0 AND ancestors = '0'
              AND name = 'Apex 科技' AND tenant_id IS NULL;
        ELSE
            RAISE EXCEPTION 'V19 cannot safely remove the legacy seed department because its references are ambiguous';
        END IF;
    END IF;

    SELECT COUNT(*) INTO unassigned_dept_count
    FROM sys_dept WHERE tenant_id IS NULL;
    IF unassigned_dept_count > 0 THEN
        RAISE EXCEPTION 'V19 requires every department to have an explicit tenant_id before migration';
    END IF;
END $$;

ALTER TABLE sys_dept
    ALTER COLUMN tenant_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_dept_tenant_id_id
    ON sys_dept (tenant_id, id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_sys_role_dept_tenant_dept'
    ) THEN
        ALTER TABLE sys_role_dept
            ADD CONSTRAINT fk_sys_role_dept_tenant_dept
            FOREIGN KEY (tenant_id, dept_id)
            REFERENCES sys_dept (tenant_id, id);
    END IF;
END $$;
