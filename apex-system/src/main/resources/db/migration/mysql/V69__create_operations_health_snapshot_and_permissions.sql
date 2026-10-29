-- ============================================================
-- V69: 运维体检历史快照表与历史/趋势/导出/租户摘要权限码（MySQL）
-- ============================================================
-- 脚本用途：新增 ops_health_snapshot 表，保存管理员主动落库的体检结论快照，
--          支撑历史列表、趋势分析与 CSV 导出；并登记 4 个权限码。
-- 业务背景：O07b 体检历史快照。O07 只提供即时、单实例报告，无法回答
--          「昨天是否也这样」与「风险是否在收敛」。快照只固化总体结论、
--          状态计数与摘要文本，不复制逐项证据，避免平台级实例证据随快照扩散。
-- 前置依赖：V1-V68 已完成；sys_permission/sys_role_permission 表存在。
-- 影响范围：新增 1 张表与 4 个权限码，不修改既有结构。
-- 幂等性：CREATE TABLE IF NOT EXISTS；权限 INSERT ... WHERE NOT EXISTS；
--          授权 INSERT IGNORE，可重复执行。
-- MySQL 兼容性：MySQL 8.0 语法，由 Flyway V69 顺序执行。
-- 数据风险：权限 ID 使用 2615-2618 保留段
--          （2614 已被 V67 体检报告查询占用，2619 起预留）。
-- 运维注意：快照不自动清理，需按保留期定期归档；租户侧摘要只读取
--          BUSINESS_RISK 分组的聚合结论，平台实例证据不外泄。
-- 迁移元信息：版本 V69；执行阶段为管理员体检历史阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 体检历史快照表
-- ============================================================
CREATE TABLE IF NOT EXISTS `ops_health_snapshot` (
  `id`                  BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `capture_source`      VARCHAR(20)  NOT NULL COMMENT '采集来源(MANUAL/SCHEDULED)',
  `service_name`        VARCHAR(200) NOT NULL COMMENT '采集时的服务名',
  `instance_id`         VARCHAR(200) NOT NULL COMMENT '采集时的实例标识(PID@主机名)',
  `scope`               VARCHAR(20)  NOT NULL COMMENT '采集范围(固定 INSTANCE)',
  `rule_version`        VARCHAR(50)  NOT NULL COMMENT '判定规则版本',
  `overall_status`      VARCHAR(20)  NOT NULL COMMENT '总体结论(UP/DEGRADED/DOWN/UNKNOWN)',
  `summary`             VARCHAR(1000) DEFAULT NULL COMMENT '总体结论说明',
  `up_count`            INT(10)      NOT NULL DEFAULT 0 COMMENT 'UP 检查项数',
  `degraded_count`      INT(10)      NOT NULL DEFAULT 0 COMMENT 'DEGRADED 检查项数',
  `down_count`          INT(10)      NOT NULL DEFAULT 0 COMMENT 'DOWN 检查项数',
  `unknown_count`       INT(10)      NOT NULL DEFAULT 0 COMMENT 'UNKNOWN 检查项数',
  `not_configured_count` INT(10)     NOT NULL DEFAULT 0 COMMENT 'NOT_CONFIGURED 检查项数',
  `generated_at`        DATETIME     NOT NULL COMMENT '报告生成时间(采集时刻)',
  `tenant_id`           VARCHAR(64)  NOT NULL COMMENT '租户ID',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`             INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
  `deleted`             INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_ops_health_snapshot_generated` (`generated_at`),
  KEY `idx_ops_health_snapshot_status` (`overall_status`),
  KEY `idx_ops_health_snapshot_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='运维-体检历史快照表';

-- ============================================================
-- 2. 体检历史权限码（2615-2618）
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2615, '体检快照查询', 'system:operations:health:snapshot:list', '查询管理员体检历史快照列表与趋势', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:operations:health:snapshot:list');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2616, '体检快照落库', 'system:operations:health:snapshot:create', '将当前体检结论固化保存为历史快照', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:operations:health:snapshot:create');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2617, '体检快照导出', 'system:operations:health:snapshot:export', '导出体检历史快照 CSV', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:operations:health:snapshot:export');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 2618, '租户体检摘要', 'system:operations:health:tenant:read', '查询仅含业务风险聚合的租户体检摘要', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:operations:health:tenant:read');

-- ============================================================
-- 3. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'system:operations:health:snapshot:list',
    'system:operations:health:snapshot:create',
    'system:operations:health:snapshot:export',
    'system:operations:health:tenant:read')
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

-- ============================================================
-- 执行结果验证建议：
-- 1. SELECT id, name, code FROM sys_permission WHERE id BETWEEN 2615 AND 2618;
-- 2. SELECT COUNT(*) FROM ops_health_snapshot;
-- ============================================================
