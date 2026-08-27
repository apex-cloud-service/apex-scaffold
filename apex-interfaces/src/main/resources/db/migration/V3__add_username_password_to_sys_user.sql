-- ================================================================
-- Apex Scaffold V3 迁移：账号密码登录支持
-- ================================================================
-- 说明：为 sys_user 补充 username/password 字段，支持传统账号密码登录
--      （与微信/手机号登录并行，登录类型由 AuthService 统一分发）。
-- 设计：
--   - username 唯一索引（uk_username），账号不可重复
--   - password 存 BCrypt 哈希（spring-security-crypto BCryptPasswordEncoder）
--   - 存量微信/手机号用户 username 为 NULL，不影响既有登录链路
-- ================================================================

ALTER TABLE `sys_user`
    ADD COLUMN `username` VARCHAR(50) DEFAULT NULL COMMENT '登录账号（账号密码登录，微信/手机号用户可为NULL）' AFTER `open_id`,
    ADD COLUMN `password` VARCHAR(100) DEFAULT NULL COMMENT '登录密码（BCrypt哈希，仅账号密码登录使用）' AFTER `username`,
    ADD UNIQUE KEY `uk_username` (`username`);
