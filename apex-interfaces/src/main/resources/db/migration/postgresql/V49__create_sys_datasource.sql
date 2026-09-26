-- ============================================================
-- V49: 创建系统数据源配置表（PostgreSQL）
-- ============================================================
-- 脚本用途：为管理后台"数据库配置"模块创建动态数据源配置表 sys_datasource，
--          替代原内存存储实现，支持多租户数据源的持久化管理与连接池监控。
-- 业务背景：数据库配置模块此前将数据源配置保存在进程内存（ConcurrentHashMap），
--          重启即丢失；本表提供持久化基础，配合
--          DataSourceManagementService 完成完整生命周期管理。
-- 前置依赖：V1-V48 已完成。
-- 影响范围：新增表与索引，不影响现有数据。
-- 幂等性：使用 CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS，可重复执行。
-- PostgreSQL/Flyway 兼容性：使用 PostgreSQL 9.5+ 语法，时间字段使用 TIMESTAMP
--              （64 位，无 Y2038 限制），由 Flyway V49 顺序执行。
-- 数据风险：仅新增对象，无数据变更风险。
-- 迁移元信息：版本 V49；执行阶段为数据库配置模块持久化上线阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- 1. 创建系统数据源配置表
CREATE TABLE IF NOT EXISTS sys_datasource (
    id                 BIGINT       NOT NULL,
    name               VARCHAR(100) NOT NULL,
    type               VARCHAR(20)  NOT NULL,
    driver             VARCHAR(30)  NOT NULL,
    url                VARCHAR(500) NOT NULL,
    username           VARCHAR(100) NOT NULL,
    password           VARCHAR(500) NOT NULL,
    min_idle           INT          NOT NULL DEFAULT 5,
    max_pool_size      INT          NOT NULL DEFAULT 20,
    connection_timeout BIGINT       NOT NULL DEFAULT 30000,
    idle_timeout       BIGINT       NOT NULL DEFAULT 600000,
    status             INT          NOT NULL DEFAULT 1,
    tenant_id          VARCHAR(64)  NOT NULL,
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version            INT          NOT NULL DEFAULT 0,
    deleted            INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_datasource_name_tenant UNIQUE (name, tenant_id, deleted)
);

COMMENT ON TABLE sys_datasource IS '系统数据源配置表（数据库配置模块）';
COMMENT ON COLUMN sys_datasource.id IS '主键ID（雪花算法生成）';
COMMENT ON COLUMN sys_datasource.name IS '数据源名称（租户内唯一）';
COMMENT ON COLUMN sys_datasource.type IS '数据源类型：master/slave/backup';
COMMENT ON COLUMN sys_datasource.driver IS '驱动类型：mysql/postgresql/oracle/sqlserver';
COMMENT ON COLUMN sys_datasource.url IS 'JDBC 连接 URL';
COMMENT ON COLUMN sys_datasource.username IS '数据库用户名';
COMMENT ON COLUMN sys_datasource.password IS '数据库密码（应用层 AES 加密存储，禁止明文）';
COMMENT ON COLUMN sys_datasource.min_idle IS '最小空闲连接数';
COMMENT ON COLUMN sys_datasource.max_pool_size IS '最大连接池大小';
COMMENT ON COLUMN sys_datasource.connection_timeout IS '获取连接超时时间（毫秒）';
COMMENT ON COLUMN sys_datasource.idle_timeout IS '空闲连接超时时间（毫秒）';
COMMENT ON COLUMN sys_datasource.status IS '启用状态：1-启用，0-禁用';
COMMENT ON COLUMN sys_datasource.tenant_id IS '租户 ID（多租户隔离）';
COMMENT ON COLUMN sys_datasource.version IS '乐观锁版本号';
COMMENT ON COLUMN sys_datasource.deleted IS '逻辑删除标记：0-正常，1-已删除';

-- 2. 租户隔离查询索引
CREATE INDEX IF NOT EXISTS idx_sys_datasource_tenant ON sys_datasource (tenant_id);

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询表结构：\d sys_datasource
-- 2. 验证唯一索引：SELECT indexname FROM pg_indexes WHERE tablename = 'sys_datasource';
-- ============================================================
