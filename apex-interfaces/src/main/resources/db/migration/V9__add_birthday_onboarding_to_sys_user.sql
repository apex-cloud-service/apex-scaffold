-- 脚本用途：为系统用户补充生日与引导流程状态字段。
-- 业务背景：修复 UserMapper 查询列与数据库结构不一致引发的登录鉴权异常。
-- 前置依赖：V1-V8 已完成，sys_user 存在且目标字段尚未创建。
-- 影响范围：变更 sys_user 表结构；新增字段允许 NULL，不主动回填存量用户数据。
-- 幂等性：ALTER TABLE 非幂等，重复执行会因列已存在而失败。
-- MySQL/Flyway 兼容性：面向 MySQL 8.x DDL，由 Flyway V9 顺序执行。
-- 数据风险：大表 DDL 可能锁表或增加复制延迟；空值语义需由应用层兼容。
-- 运维注意：执行前评估用户表规模和备份，执行后验证映射查询及登录鉴权链路。
-- 迁移元信息：版本 V9；执行阶段为用户模型与数据库结构对齐阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ============================================================
-- V9: sys_user 表补齐 UserMapper 查询所需的缺失列
-- ============================================================
-- 背景：UserMapper 查询列包含 birthday / onboarding_status，
--   但 V1 建表脚本未创建这两列，导致按 username/openId 查询用户时
--   抛出 SQLSyntaxErrorException: Unknown column 'birthday'。
-- 本脚本补齐列定义（与 UserEntity 字段类型对齐），修复登录/鉴权链路。
-- ============================================================

ALTER TABLE sys_user
    ADD COLUMN birthday DATE NULL COMMENT '生日' AFTER gender,
    ADD COLUMN onboarding_status VARCHAR(50) NULL COMMENT '引导流程状态' AFTER status;
