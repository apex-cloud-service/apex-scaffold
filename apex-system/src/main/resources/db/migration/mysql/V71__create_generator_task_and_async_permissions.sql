-- ============================================================
-- V71: 代码生成异步任务表与异步生成权限码（MySQL）
-- ============================================================
-- 脚本用途：新增 gen_task 表，记录向导受控执行的异步任务与进度，
--          并登记异步生成提交/查询/取消 3 个权限码。
-- 业务背景：O08b 异步生成进度。V68 的向导执行是同步请求内串行完成
--          渲染、写盘、落历史与落产物，大配置下请求耗时不可控，
--          浏览器也无法获知中间进度。改为后台任务推进后，请求只负责
--          提交，进度以 total/processed/failed 计数回读。
-- 前置依赖：V1-V70 已完成；gen_artifact/gen_history 表存在。
-- 影响范围：新增 1 张表与 3 个权限码，不修改既有结构。
-- 幂等性：CREATE TABLE IF NOT EXISTS；权限 INSERT ... WHERE NOT EXISTS；
--          授权 INSERT IGNORE，可重复执行。
-- MySQL 兼容性：MySQL 8.0 语法，由 Flyway V71 顺序执行。
-- 数据风险：权限 ID 使用 3017-3019 保留段（3000-3016 已被 V41/V68 使用）。
-- 运维注意：任务表含预览令牌，属于敏感数据，保留期清理按完成时间物理删除；
--          清理批次与周期由 apex.generator.task.retention.* 控制。
-- 迁移元信息：版本 V71；执行阶段为代码生成异步进度阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 代码生成异步任务表
-- ============================================================
-- 状态机：PENDING -> RUNNING -> COMPLETED / FAILED，运行中可主动 CANCELED；
-- RUNNING 超时（locked_at 过旧）可被其他实例重新认领。
CREATE TABLE IF NOT EXISTS `gen_task` (
  `id`             BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `config_id`      BIGINT       NOT NULL COMMENT '生成配置ID',
  `history_id`     BIGINT       DEFAULT NULL COMMENT '生成历史ID(完成时回填)',
  `status`         VARCHAR(24)  NOT NULL DEFAULT 'PENDING' COMMENT '任务状态(PENDING/RUNNING/COMPLETED/FAILED/CANCELED)',
  `total_count`    INT(10)      NOT NULL DEFAULT 0 COMMENT '待生成文件总数',
  `processed_count` INT(10)     NOT NULL DEFAULT 0 COMMENT '已处理文件数',
  `failed_count`   INT(10)      NOT NULL DEFAULT 0 COMMENT '失败文件数',
  `preview_token`  VARCHAR(128) NOT NULL COMMENT '提交时引用的预览令牌',
  `overwrite_flag` INT(1)       NOT NULL DEFAULT 0 COMMENT '是否允许覆盖(0-否,1-是)',
  `operator_id`    BIGINT       DEFAULT NULL COMMENT '提交人用户ID',
  `error_message`  VARCHAR(1000) DEFAULT NULL COMMENT '最近一次失败原因(截断)',
  `attempts`       INT(10)      NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  `locked_at`      DATETIME     DEFAULT NULL COMMENT '认领时间',
  `locked_by`      VARCHAR(128) DEFAULT NULL COMMENT '认领实例标识',
  `completed_at`   DATETIME     DEFAULT NULL COMMENT '完成时间',
  `canceled_at`    DATETIME     DEFAULT NULL COMMENT '取消时间',
  `tenant_id`      VARCHAR(64)  NOT NULL COMMENT '租户ID',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`        INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
  `deleted`        INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_gen_task_status` (`status`, `locked_at`, `id`),
  KEY `idx_gen_task_tenant` (`tenant_id`, `status`),
  KEY `idx_gen_task_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码生成器-异步生成任务表';

-- ============================================================
-- 2. 异步生成权限码（3017-3019）
-- ============================================================
-- 权限码受 sys_permission.code VARCHAR(50) 约束，此处使用 29/27/29 字符短码。
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 3017, '异步生成提交', 'system:generator:task:create', '提交代码生成异步任务', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:generator:task:create');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 3018, '异步生成查询', 'system:generator:task:read', '查询代码生成异步任务进度与产物', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:generator:task:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 3019, '异步生成取消', 'system:generator:task:cancel', '取消未完成的代码生成异步任务', 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:generator:task:cancel');

-- ============================================================
-- 3. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'system:generator:task:create',
    'system:generator:task:read',
    'system:generator:task:cancel')
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN');

-- ============================================================
-- 执行结果验证建议：
-- 1. SELECT id, name, code FROM sys_permission WHERE id BETWEEN 3017 AND 3019;
-- 2. SELECT COUNT(*) FROM gen_task;
-- ============================================================
