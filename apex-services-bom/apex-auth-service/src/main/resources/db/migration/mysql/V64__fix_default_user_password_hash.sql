-- ============================================================
-- V64: 修正默认测试账号种子密码哈希
-- ============================================================
-- 背景：V47 插入的 admin/user 密码哈希与其注释声明的 admin123 不匹配，
--       实测 BCryptPasswordEncoder.matches("admin123", <V47 hash>) = false，
--       导致按文档使用 admin/admin123 无法登录（真实 MySQL 联调实测）。
-- 处置：只更新仍在使用 V47 原始哈希的行，不覆盖已被运维改过的密码。
-- 说明：种子里 admin 仍为默认口令，仅适用于本地/演示环境；生产必须在首次登录后改密。
-- ============================================================

UPDATE auth_user
SET password_hash = '$2a$10$A58gAhjpUlO9vwOv4n7zO.dUNrezzw0S92v96BS5qDFoep7dnFyby',
    failed_login_attempts = 0,
    locked_until = NULL,
    updated_at = NOW()
WHERE deleted = 0
  AND password_hash = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH';
