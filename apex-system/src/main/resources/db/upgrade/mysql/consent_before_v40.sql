-- 人工执行，非 Flyway。停写并备份后，在 system 库用 mysql（禁止 --force）执行。
-- 仅修复 consent；不修改迁移历史。失败记录由 DBA 审核后决定如何处理。
-- MySQL DDL 隐式提交；每一步可重入。保留已有非空 id 和时间值。
DROP PROCEDURE IF EXISTS apex_consent_before_v40;
DELIMITER $$
CREATE PROCEDURE apex_consent_before_v40()
BEGIN
    DECLARE pk_columns TEXT;
    DECLARE unique_columns TEXT;
    IF EXISTS (SELECT 1 FROM oauth2_authorization_consent
               GROUP BY registered_client_id, principal_name HAVING COUNT(*) > 1)
       OR EXISTS (SELECT 1 FROM oauth2_authorization_consent
                  WHERE registered_client_id IS NULL OR principal_name IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'consent business keys invalid; manual review required';
    END IF;
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) INTO pk_columns
      FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='oauth2_authorization_consent' AND index_name='PRIMARY';
    IF pk_columns IS NOT NULL AND pk_columns NOT IN ('id','registered_client_id,principal_name') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'unexpected consent primary key';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='oauth2_authorization_consent' AND column_name='id') THEN
        ALTER TABLE oauth2_authorization_consent ADD COLUMN id VARCHAR(100) NULL FIRST;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='oauth2_authorization_consent' AND column_name='id'
        AND data_type='varchar' AND character_maximum_length=100) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'unexpected consent id type; manual review required';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='oauth2_authorization_consent' AND column_name='created_at') THEN
        ALTER TABLE oauth2_authorization_consent ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='oauth2_authorization_consent' AND column_name='updated_at') THEN
        ALTER TABLE oauth2_authorization_consent ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
    END IF;
    -- 长度前缀与 UTF-8 十六进制编码避免分隔符、中文与拼接歧义。
    UPDATE oauth2_authorization_consent
       SET id=CONCAT('legacy:',SHA2(CONCAT(OCTET_LENGTH(registered_client_id),':',HEX(registered_client_id),':',HEX(principal_name)),256)),
           updated_at=updated_at
     WHERE id IS NULL OR id='';
    IF EXISTS (SELECT 1 FROM oauth2_authorization_consent GROUP BY id HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'duplicate consent ids; existing ids preserved for manual review';
    END IF;
    SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) INTO unique_columns
      FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name='oauth2_authorization_consent' AND index_name='uk_client_principal' AND non_unique=0
      AND sub_part IS NULL;
    IF unique_columns IS NULL THEN
        ALTER TABLE oauth2_authorization_consent ADD UNIQUE INDEX uk_client_principal(registered_client_id,principal_name);
    ELSEIF unique_columns <> 'registered_client_id,principal_name' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'unexpected uk_client_principal definition';
    END IF;
    IF pk_columns IS NULL THEN
        ALTER TABLE oauth2_authorization_consent MODIFY id VARCHAR(100) NOT NULL, ADD PRIMARY KEY(id);
    ELSEIF pk_columns <> 'id' THEN
        ALTER TABLE oauth2_authorization_consent MODIFY id VARCHAR(100) NOT NULL, DROP PRIMARY KEY, ADD PRIMARY KEY(id);
    ELSE
        ALTER TABLE oauth2_authorization_consent MODIFY id VARCHAR(100) NOT NULL;
    END IF;
END$$
DELIMITER ;
CALL apex_consent_before_v40();
DROP PROCEDURE apex_consent_before_v40;
