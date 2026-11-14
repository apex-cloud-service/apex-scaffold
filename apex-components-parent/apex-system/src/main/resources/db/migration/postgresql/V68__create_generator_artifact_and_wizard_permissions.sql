-- ============================================================
-- V68: 代码生成产物表与向导权限码（PostgreSQL）
-- ============================================================
-- 脚本用途：新增 gen_artifact 表，记录向导受控执行写入独立任务目录的产物，
--          并登记模板目录/预检/预览/产物下载 4 个权限码。
-- 业务背景：O08 生成器可预览向导。预览只做纯渲染不写盘，执行必须引用已校验的
--          配置/模板内容版本与输入摘要，默认拒绝覆盖；产物以任务目录隔离
--          tenant/task，浏览器只按产物 ID 下载，不提交任意路径。
-- 前置依赖：V1-V67 已完成；gen_config/gen_history 表存在。
-- 影响范围：新增 1 张表与 4 个权限码，不修改既有结构。
-- 幂等性：CREATE TABLE IF NOT EXISTS；权限 INSERT ... WHERE NOT EXISTS；
--          授权 ON CONFLICT DO NOTHING，可重复执行。
-- PostgreSQL/Flyway 兼容性：PostgreSQL 9.5+ 语法，由 Flyway V68 顺序执行。
-- 数据风险：权限 ID 使用 3013-3016 保留段（3000-3012 已被 V41 使用）。
-- 运维注意：产物依赖磁盘保留期清理，需按部署配置定期归档；删除记录不删除文件。
-- 迁移元信息：版本 V68；执行阶段为代码生成向导阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- ============================================================
-- 1. 代码生成产物表
-- ============================================================
CREATE TABLE IF NOT EXISTS gen_artifact (
  id             BIGINT        NOT NULL,
  task_id        BIGINT        NOT NULL,
  config_id      BIGINT        NOT NULL,
  history_id     BIGINT        DEFAULT NULL,
  table_name     VARCHAR(200)  NOT NULL,
  template_id    BIGINT        NOT NULL,
  template_type  VARCHAR(50)   DEFAULT NULL,
  relative_path  VARCHAR(500)  NOT NULL,
  file_name      VARCHAR(200)  NOT NULL,
  file_size      BIGINT        NOT NULL DEFAULT 0,
  content_digest VARCHAR(64)   DEFAULT NULL,
  status         VARCHAR(20)   NOT NULL,
  error_message  VARCHAR(1000) DEFAULT NULL,
  tenant_id      VARCHAR(64)   NOT NULL,
  create_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version        INTEGER       NOT NULL DEFAULT 0,
  deleted        INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_gen_artifact_task ON gen_artifact(task_id);
CREATE INDEX IF NOT EXISTS idx_gen_artifact_config ON gen_artifact(config_id);
CREATE INDEX IF NOT EXISTS idx_gen_artifact_tenant ON gen_artifact(tenant_id);

COMMENT ON TABLE gen_artifact IS '代码生成器-生成产物表';

-- ============================================================
-- 2. 向导权限码（3013-3016）
-- ============================================================
INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 3013, '模板目录', 'system:generator:template:list', '查询代码生成模板目录与引擎能力', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:generator:template:list');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 3014, '生成预检', 'system:generator:preflight', '执行代码生成结构化预检', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:generator:preflight');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 3015, '生成预览', 'system:generator:preview', '纯渲染预览代码生成结果（不写盘）', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:generator:preview');

INSERT INTO sys_permission (id, name, code, description, type, status)
SELECT 3016, '产物下载', 'system:generator:artifact:download', '按产物ID下载代码生成产物', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'system:generator:artifact:download');

-- ============================================================
-- 3. 为超级管理员角色（ADMIN，role_id=1）授权
-- ============================================================
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 500000 + p.id, 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'system:generator:template:list',
    'system:generator:preflight',
    'system:generator:preview',
    'system:generator:artifact:download')
AND EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ADMIN')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 执行结果验证建议：
-- 1. SELECT id, name, code FROM sys_permission WHERE id BETWEEN 3013 AND 3016;
-- 2. SELECT COUNT(*) FROM gen_artifact;
-- ============================================================
