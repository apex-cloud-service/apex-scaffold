-- ============================================================
-- V71: 代码生成异步任务表与异步生成权限码（PostgreSQL）
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
--          授权 ON CONFLICT DO NOTHING，可重复执行。
-- PostgreSQL/Flyway 兼容性：PostgreSQL 9.5+ 语法，由 Flyway V71 顺序执行。
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
CREATE TABLE IF NOT EXISTS gen_task (
  id              BIGINT        NOT NULL,
  config_id       BIGINT        NOT NULL,
  history_id      BIGINT        DEFAULT NULL,
  status          VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
  total_count     INTEGER       NOT NULL DEFAULT 0,
  processed_count INTEGER       NOT NULL DEFAULT 0,
  failed_count    INTEGER       NOT NULL DEFAULT 0,
  preview_token   VARCHAR(128)  NOT NULL,
  overwrite_flag  INTEGER       NOT NULL DEFAULT 0,
  operator_id     BIGINT        DEFAULT NULL,
  error_message   VARCHAR(1000) DEFAULT NULL,
  attempts        INTEGER       NOT NULL DEFAULT 0,
  locked_at       TIMESTAMP     DEFAULT NULL,
  locked_by       VARCHAR(128)  DEFAULT NULL,
  completed_at    TIMESTAMP     DEFAULT NULL,
  canceled_at     TIMESTAMP     DEFAULT NULL,
  tenant_id       VARCHAR(64)   NOT NULL,
  create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version         INTEGER       NOT NULL DEFAULT 0,
  deleted         INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_gen_task_status ON gen_task(status, locked_at, id);
CREATE INDEX IF NOT EXISTS idx_gen_task_tenant ON gen_task(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_gen_task_create_time ON gen_task(create_time);

COMMENT ON TABLE gen_task IS '代码生成器-异步生成任务表';

-- ============================================================
-- 2. 异步生成权限码（3017-3019）
-- ============================================================
-- 权限码受 sys_permission.code VARCHAR(50) 约束，此处使用 29/27/29 字符短码。
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 3017, '异步生成提交', 'system:generator:task:create', '提交代码生成异步任务', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:generator:task:create');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 3018, '异步生成查询', 'system:generator:task:read', '查询代码生成异步任务进度与产物', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:generator:task:read');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 3019, '异步生成取消', 'system:generator:task:cancel', '取消未完成的代码生成异步任务', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:generator:task:cancel');

-- ============================================================
-- 3. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 500000 + p.id, 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'system:generator:task:create',
    'system:generator:task:read',
    'system:generator:task:cancel')
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 执行结果验证建议：
-- 1. SELECT id, name, code FROM sys_permission WHERE id BETWEEN 3017 AND 3019;
-- 2. SELECT COUNT(*) FROM gen_task;
-- ============================================================
