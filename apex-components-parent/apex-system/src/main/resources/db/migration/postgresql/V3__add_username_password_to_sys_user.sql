-- 脚本用途：为系统用户增加账号密码登录所需字段与用户名唯一索引。
-- 业务背景：在既有第三方身份登录之外补充传统账号密码认证能力。
-- 前置依赖：V1 已创建 sys_user，且表中不存在 username、password 列及 uk_username 索引。
-- 影响范围：变更 sys_user 表结构并建立唯一索引，不写入或改写现有密码数据。
-- 幂等性：ALTER TABLE 非幂等，重复执行会因列或索引已存在而失败。
-- MySQL/Flyway 兼容性：面向 MySQL 8.x DDL，由 Flyway V3 在 V1、V2 后执行。
-- 数据风险：建唯一索引可能受既有数据冲突影响；DDL 可能锁表并增加复制延迟。
-- 运维注意：生产执行前评估表规模、在线 DDL 能力和回滚方案，执行后验证索引状态。
-- 迁移元信息：版本 V3；执行阶段为认证能力扩展的结构迁移阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
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

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS username VARCHAR(50) DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS password VARCHAR(100) DEFAULT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_username ON sys_user (username);
