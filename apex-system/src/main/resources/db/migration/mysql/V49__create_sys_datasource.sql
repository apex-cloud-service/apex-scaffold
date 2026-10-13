-- ============================================================
-- V49: 创建系统数据源配置表（MySQL）
-- ============================================================
-- 脚本用途：为管理后台"数据库配置"模块创建动态数据源配置表 sys_datasource，
--          替代原内存存储实现，支持多租户数据源的持久化管理与连接池监控。
-- 业务背景：数据库配置模块此前将数据源配置保存在进程内存（ConcurrentHashMap），
--          重启即丢失；本表提供持久化基础，配合
--          DataSourceManagementService 完成完整生命周期管理。
-- 前置依赖：V1-V48 已完成。
-- 影响范围：新增表与索引，不影响现有数据。
-- 幂等性：使用 CREATE TABLE IF NOT EXISTS / CREATE INDEX 判断，可重复执行。
-- MySQL 兼容性：时间字段统一使用 DATETIME（严禁 TIMESTAMP，规避 Y2038 问题），
--              由 Flyway V49 顺序执行。
-- 数据风险：仅新增对象，无数据变更风险。
-- 迁移元信息：版本 V49；执行阶段为数据库配置模块持久化上线阶段。
-- Checksum 提示：修改已执行迁移的注释会改变 Flyway checksum；存量环境需评估后 repair。
-- ============================================================

-- 1. 创建系统数据源配置表
CREATE TABLE IF NOT EXISTS sys_datasource (
    id                 BIGINT       NOT NULL COMMENT '主键ID（雪花算法生成）',
    name               VARCHAR(100) NOT NULL COMMENT '数据源名称（租户内唯一）',
    type               VARCHAR(20)  NOT NULL COMMENT '数据源类型：master/slave/backup',
    driver             VARCHAR(30)  NOT NULL COMMENT '驱动类型：mysql/postgresql/oracle/sqlserver',
    url                VARCHAR(500) NOT NULL COMMENT 'JDBC 连接 URL',
    username           VARCHAR(100) NOT NULL COMMENT '数据库用户名',
    password           VARCHAR(500) NOT NULL COMMENT '数据库密码（应用层 AES 加密存储，禁止明文）',
    min_idle           INT          NOT NULL DEFAULT 5 COMMENT '最小空闲连接数',
    max_pool_size      INT          NOT NULL DEFAULT 20 COMMENT '最大连接池大小',
    connection_timeout BIGINT       NOT NULL DEFAULT 30000 COMMENT '获取连接超时时间（毫秒）',
    idle_timeout       BIGINT       NOT NULL DEFAULT 600000 COMMENT '空闲连接超时时间（毫秒）',
    status             INT          NOT NULL DEFAULT 1 COMMENT '启用状态：1-启用，0-禁用',
    tenant_id          VARCHAR(64)  NOT NULL COMMENT '租户 ID（多租户隔离）',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version            INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted            INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-正常，1-已删除',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_datasource_name_tenant UNIQUE (name, tenant_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统数据源配置表（数据库配置模块）';

-- 2. 租户隔离查询索引
CREATE INDEX idx_sys_datasource_tenant ON sys_datasource (tenant_id);

-- ============================================================
-- 执行结果验证建议：
-- 1. 查询表结构：SHOW CREATE TABLE sys_datasource;
-- 2. 验证唯一索引：SHOW INDEX FROM sys_datasource WHERE Key_name = 'uk_sys_datasource_name_tenant';
-- 3. 验证租户索引：SHOW INDEX FROM sys_datasource WHERE Key_name = 'idx_sys_datasource_tenant';
-- ============================================================
