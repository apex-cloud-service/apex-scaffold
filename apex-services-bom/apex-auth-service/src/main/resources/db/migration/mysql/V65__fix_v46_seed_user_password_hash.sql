-- ============================================================
-- V65: 修正 V46 种子用户密码哈希（V64 的补充）
-- ============================================================
-- 背景：V64 已修正 V47 写入的哈希变体；但 V46 先行插入同主键用户
--       （admin/user/test，id 1001-1003），V47 的插入因主键冲突未生效，
--       存量环境实际持有的是 V46 哈希，同样与注释声明的口令不匹配，
--       按文档口令登录仍失败（本地联调实测）。
-- 处置：只更新仍在使用 V46 原始哈希的行，不覆盖已被运维改过的密码；
--       目标哈希与 V64 一致（实测 BCryptPasswordEncoder.matches("admin123", <hash>) = true）。
-- 说明：种子里 admin 仍为默认口令，仅适用于本地/演示环境；生产必须在首次登录后改密。
-- ============================================================

UPDATE auth_user
SET password_hash = '$2a$10$A58gAhjpUlO9vwOv4n7zO.dUNrezzw0S92v96BS5qDFoep7dnFyby',
    failed_login_attempts = 0,
    locked_until = NULL,
    updated_at = NOW()
WHERE deleted = 0
  AND password_hash = '$2a$10$X5wFuNVXz.wFvzMWXl9VcO4KZkm8E.WKLWqJbx6LqD6YPnFDJJ1Ya';
