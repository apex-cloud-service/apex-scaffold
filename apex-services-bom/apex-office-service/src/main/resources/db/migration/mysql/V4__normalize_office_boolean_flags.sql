-- 将 office 布尔标记的历史 NULL 归为 false，并约束后续写入使用非空默认值。
UPDATE office_calendar_event SET is_all_day = 0 WHERE is_all_day IS NULL;
UPDATE office_calendar_event SET is_recurring = 0 WHERE is_recurring IS NULL;
UPDATE office_document_info SET is_folder = 0 WHERE is_folder IS NULL;
UPDATE office_document_info SET is_archived = 0 WHERE is_archived IS NULL;

ALTER TABLE office_calendar_event
    MODIFY COLUMN is_all_day TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否全天事件',
    MODIFY COLUMN is_recurring TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否重复事件';
ALTER TABLE office_document_info
    MODIFY COLUMN is_folder TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为文件夹',
    MODIFY COLUMN is_archived TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已归档';
