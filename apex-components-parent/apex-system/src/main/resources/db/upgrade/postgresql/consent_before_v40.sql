-- 人工执行，非 Flyway。停写、备份后使用 psql -v ON_ERROR_STOP=1 执行。
-- PostgreSQL V40 无存在性守卫：本修复完成后不能直接运行原 V40 的 consent 段。
-- DBA 需审核历史迁移协调；本文件不自动 repair、不标记 V40 已执行。
BEGIN;
LOCK TABLE oauth2_authorization_consent IN ACCESS EXCLUSIVE MODE;
ALTER TABLE oauth2_authorization_consent ADD COLUMN IF NOT EXISTS id VARCHAR(100);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_attribute
        WHERE attrelid='oauth2_authorization_consent'::regclass AND attname='id' AND NOT attisdropped
        AND atttypid='character varying'::regtype AND atttypmod=104) THEN
        RAISE EXCEPTION 'unexpected consent id type; VARCHAR(100) required; manual review required';
    END IF;
END $$;
ALTER TABLE oauth2_authorization_consent ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE oauth2_authorization_consent ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
UPDATE oauth2_authorization_consent
SET id='legacy:' || encode(sha256(convert_to(octet_length(registered_client_id)::text || ':' ||
    upper(encode(convert_to(registered_client_id,'UTF8'),'hex')) || ':' ||
    upper(encode(convert_to(principal_name,'UTF8'),'hex')), 'UTF8')), 'hex')
WHERE id IS NULL OR id='';
DO $$
DECLARE pk_name TEXT; pk_columns TEXT;
BEGIN
    IF EXISTS (SELECT 1 FROM oauth2_authorization_consent GROUP BY registered_client_id,principal_name HAVING COUNT(*)>1)
       OR EXISTS (SELECT 1 FROM oauth2_authorization_consent WHERE registered_client_id IS NULL OR principal_name IS NULL) THEN
        RAISE EXCEPTION 'consent business keys invalid; manual review required';
    END IF;
    SELECT c.conname,string_agg(a.attname,',' ORDER BY k.ordinality) INTO pk_name,pk_columns
    FROM pg_constraint c CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY k(num,ordinality)
    JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.num
    WHERE c.conrelid='oauth2_authorization_consent'::regclass AND c.contype='p' GROUP BY c.conname;
    IF pk_columns IS NOT NULL AND pk_columns NOT IN ('id','registered_client_id,principal_name') THEN
        RAISE EXCEPTION 'unexpected consent primary key';
    END IF;
    -- 先建立唯一性，任何错误均回滚，不丢失原复合主键。
    IF to_regclass('uk_client_principal') IS NULL THEN
        CREATE UNIQUE INDEX uk_client_principal ON oauth2_authorization_consent(registered_client_id,principal_name);
    ELSIF NOT EXISTS (SELECT 1 FROM pg_index WHERE indexrelid='uk_client_principal'::regclass
        AND indrelid='oauth2_authorization_consent'::regclass AND indisunique AND indisvalid
        AND indpred IS NULL AND indexprs IS NULL AND indnatts=2
        AND indkey::text=(SELECT attnum::text FROM pg_attribute WHERE attrelid='oauth2_authorization_consent'::regclass AND attname='registered_client_id') || ' ' ||
        (SELECT attnum::text FROM pg_attribute WHERE attrelid='oauth2_authorization_consent'::regclass AND attname='principal_name')) THEN
        RAISE EXCEPTION 'unexpected uk_client_principal definition';
    END IF;
    ALTER TABLE oauth2_authorization_consent ALTER COLUMN id SET NOT NULL;
    IF pk_columns IS DISTINCT FROM 'id' THEN
        IF pk_name IS NOT NULL THEN
            EXECUTE format('ALTER TABLE oauth2_authorization_consent DROP CONSTRAINT %I',pk_name);
        END IF;
        ALTER TABLE oauth2_authorization_consent ADD PRIMARY KEY(id);
    END IF;
END $$;
COMMIT;
