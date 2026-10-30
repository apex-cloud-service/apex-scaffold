-- ============================================================
-- V70: 体检快照签名列、保留期清理索引与异步导出任务表（MySQL）
-- ============================================================
-- 脚本用途：补齐 V69 遗留的三处缺口：
--          1) ops_health_snapshot 增加 signature 列，固化采集时的
--             HMAC-SHA256 摘要，供读取侧校验防篡改；
--          2) 增加 (tenant_id, generated_at) 复合索引，支撑按租户
--             保留期批量清理，避免全表扫描；
--          3) 新增 ops_health_export_task 异步导出任务表，让超过
--             同步上限（5000 行）的导出走后台任务；
--          4) 登记异步导出提交/查询 2 个权限码。
-- 业务背景：O07b 体检历史快照。V69 只提供手工落库与同步一次性导出，
--          无法自动留存、无法回收容量、无法证明快照未被改写。
-- 前置依赖：V1-V69 已完成；ops_health_snapshot 表存在。
-- 影响范围：新增 1 列、1 个索引、1 张表与 2 个权限码；
--          既不修改既有列，也不删除既有索引和数据。
-- 幂等性：ADD COLUMN 前检查 information_schema；
--          CREATE TABLE IF NOT EXISTS；权限 INSERT ... WHERE NOT EXISTS；
--          授权 INSERT IGNORE，可重复执行。
-- MySQL 兼容性：MySQL 8.0 语法，由 Flyway V70 顺序执行。
-- 数据风险：signature 为可空列，V69 存量快照无签名，读取侧标记为
--          UNVERIFIABLE 而不是判为篡改，避免误伤历史数据。
--          权限 ID 使用 2619-2620 保留段（V69 注释已预留 2619 起）。
-- 运维注意：保留期清理按 generated_at 早于保留截止时间物理删除，
--          清理批次与执行周期由 apex.operations.health.retention.* 控制。
-- 迁移元信息：版本 V70；执行阶段为管理员体检历史阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 快照签名列（防篡改摘要）
-- ============================================================
SET @signature_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ops_health_snapshot'
      AND COLUMN_NAME = 'signature'
);
SET @signature_ddl := IF(
    @signature_column_exists = 0,
    'ALTER TABLE `ops_health_snapshot` ADD COLUMN `signature` VARCHAR(128) DEFAULT NULL COMMENT ''快照签名(HMAC-SHA256 十六进制)'' AFTER `summary`',
    'SELECT 1'
);
PREPARE signature_stmt FROM @signature_ddl;
EXECUTE signature_stmt;
DEALLOCATE PREPARE signature_stmt;

-- ============================================================
-- 2. 保留期清理索引（租户 + 生成时间）
-- ============================================================
SET @retention_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ops_health_snapshot'
      AND INDEX_NAME = 'idx_ops_health_snapshot_tenant_generated'
);
SET @retention_ddl := IF(
    @retention_index_exists = 0,
    'ALTER TABLE `ops_health_snapshot` ADD KEY `idx_ops_health_snapshot_tenant_generated` (`tenant_id`, `generated_at`)',
    'SELECT 1'
);
PREPARE retention_stmt FROM @retention_ddl;
EXECUTE retention_stmt;
DEALLOCATE PREPARE retention_stmt;

-- ============================================================
-- 3. 体检快照异步导出任务表
-- ============================================================
-- 状态机：PENDING -> RUNNING -> COMPLETED / FAILED；
-- RUNNING 超时（locked_at 过旧）可被其他实例重新认领。
CREATE TABLE IF NOT EXISTS `ops_health_export_task` (
  `id`            BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `query_from`    DATETIME     DEFAULT NULL COMMENT '导出条件-起始生成时间(含)',
  `query_to`      DATETIME     DEFAULT NULL COMMENT '导出条件-结束生成时间(含)',
  `query_status`  VARCHAR(20)  DEFAULT NULL COMMENT '导出条件-总体结论',
  `status`        VARCHAR(24)  NOT NULL DEFAULT 'PENDING' COMMENT '任务状态(PENDING/RUNNING/COMPLETED/FAILED)',
  `attempts`      INT(10)      NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  `last_error`    VARCHAR(500) DEFAULT NULL COMMENT '最近一次失败原因(截断)',
  `file_name`     VARCHAR(200) DEFAULT NULL COMMENT '生成文件名',
  `row_count`     INT(10)      DEFAULT NULL COMMENT '导出记录数',
  `content`       LONGBLOB     DEFAULT NULL COMMENT '导出文件内容(CSV UTF-8)',
  `locked_at`     DATETIME     DEFAULT NULL COMMENT '认领时间',
  `locked_by`     VARCHAR(128) DEFAULT NULL COMMENT '认领实例标识',
  `completed_at`  DATETIME     DEFAULT NULL COMMENT '完成时间',
  `tenant_id`     VARCHAR(64)  NOT NULL COMMENT '租户ID',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`       INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
  `deleted`       INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_ops_health_export_status` (`status`, `locked_at`, `id`),
  KEY `idx_ops_health_export_tenant` (`tenant_id`, `status`),
  KEY `idx_ops_health_export_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运维-体检快照异步导出任务表';

-- ============================================================
-- 4. 异步导出权限码（2619-2620）
-- ============================================================
-- 权限码受 sys_permission.code VARCHAR(50) 约束，此处使用 44/42 字符短码。
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2619, '体检快照异步导出提交', 'system:operations:health:export:task:create', '提交超过同步上限的体检快照异步导出任务', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:operations:health:export:task:create');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2620, '体检快照异步导出查询', 'system:operations:health:export:task:read', '查询体检快照异步导出任务状态并下载结果', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:operations:health:export:task:read');

-- ============================================================
-- 5. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'system:operations:health:export:task:create',
    'system:operations:health:export:task:read')
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

-- ============================================================
-- 执行结果验证建议：
-- 1. SHOW COLUMNS FROM ops_health_snapshot LIKE 'signature';
-- 2. SHOW INDEX FROM ops_health_snapshot WHERE Key_name = 'idx_ops_health_snapshot_tenant_generated';
-- 3. SELECT id, name, code FROM sys_permission WHERE id BETWEEN 2619 AND 2620;
-- 4. SELECT COUNT(*) FROM ops_health_export_task;
-- ============================================================
