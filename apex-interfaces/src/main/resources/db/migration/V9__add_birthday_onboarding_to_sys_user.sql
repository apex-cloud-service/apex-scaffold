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
