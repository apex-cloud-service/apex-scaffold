-- Seata AT 分支回滚日志，不属于租户业务表。
CREATE TABLE undo_log (
    branch_id BIGINT NOT NULL,
    xid VARCHAR(128) NOT NULL,
    context VARCHAR(128) NOT NULL,
    rollback_info BYTEA NOT NULL,
    log_status INTEGER NOT NULL,
    log_created TIMESTAMP NOT NULL,
    log_modified TIMESTAMP NOT NULL,
    CONSTRAINT ux_undo_log UNIQUE (xid, branch_id)
);
