-- V76: 统一默认租户编码（统一认证收口 A6 / 方案批次 B1）
-- 语义与 MySQL 版本一致：apex-default 全库改名 default，动态遍历防遗漏。

DO $$
DECLARE
    v_source_count INT;
    v_target_count INT;
    v_table_name TEXT;
    v_total_renamed BIGINT := 0;
    v_rows BIGINT;
BEGIN
    SELECT COUNT(*) INTO v_source_count
    FROM sys_tenant
    WHERE tenant_code = 'apex-default' AND deleted = 0;

    SELECT COUNT(*) INTO v_target_count
    FROM sys_tenant
    WHERE tenant_code = 'default' AND deleted = 0;

    IF v_source_count > 1 THEN
        RAISE EXCEPTION 'V76 found duplicate active apex-default tenants';
    END IF;

    IF v_source_count = 1 AND v_target_count >= 1 THEN
        RAISE EXCEPTION 'V76 cannot rename apex-default to default: target code already exists';
    END IF;

    IF v_source_count = 0 AND v_target_count = 0 THEN
        RAISE EXCEPTION 'V76 found neither apex-default nor default tenant';
    END IF;

    UPDATE sys_tenant
    SET tenant_code = 'default', update_time = NOW()
    WHERE tenant_code = 'apex-default';

    FOR v_table_name IN
        SELECT c.TABLE_NAME::text
        FROM information_schema.COLUMNS c
        WHERE c.TABLE_SCHEMA = current_schema()
          AND c.COLUMN_NAME = 'tenant_id'
          AND c.TABLE_NAME <> 'sys_tenant'
          AND c.DATA_TYPE IN ('character varying', 'character', 'text')
    LOOP
        EXECUTE format(
            'UPDATE %I SET tenant_id = $1 WHERE tenant_id = $2',
            v_table_name)
        USING 'default', 'apex-default';
        GET DIAGNOSTICS v_rows = ROW_COUNT;
        v_total_renamed := v_total_renamed + GREATEST(v_rows, 0);
    END LOOP;

    SELECT COUNT(*) INTO v_source_count
    FROM sys_tenant
    WHERE tenant_code = 'apex-default';

    IF v_source_count > 0 THEN
        RAISE EXCEPTION 'V76 failed: apex-default still present after rename';
    END IF;
END
$$;
