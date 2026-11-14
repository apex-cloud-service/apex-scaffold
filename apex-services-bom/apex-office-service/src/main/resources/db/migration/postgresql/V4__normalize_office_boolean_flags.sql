-- 将 office 布尔标记的历史 NULL 归为 false，并约束后续写入使用非空默认值。
UPDATE office_calendar_event SET is_all_day = 0 WHERE is_all_day IS NULL;
UPDATE office_calendar_event SET is_recurring = 0 WHERE is_recurring IS NULL;
UPDATE office_document_info SET is_folder = 0 WHERE is_folder IS NULL;
UPDATE office_document_info SET is_archived = 0 WHERE is_archived IS NULL;

ALTER TABLE office_calendar_event
    ALTER COLUMN is_all_day SET DEFAULT 0,
    ALTER COLUMN is_all_day SET NOT NULL,
    ALTER COLUMN is_recurring SET DEFAULT 0,
    ALTER COLUMN is_recurring SET NOT NULL;
ALTER TABLE office_document_info
    ALTER COLUMN is_folder SET DEFAULT 0,
    ALTER COLUMN is_folder SET NOT NULL,
    ALTER COLUMN is_archived SET DEFAULT 0,
    ALTER COLUMN is_archived SET NOT NULL;
