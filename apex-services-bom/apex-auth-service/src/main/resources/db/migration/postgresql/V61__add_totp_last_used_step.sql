-- V61: TOTP 时间步防重放（统一认证收口 A4 安全加固）
-- 记录最近一次成功验证的 TOTP 时间步，验证时以条件更新原子消费，
-- 拒绝同一时间步验证码的重复使用（30s 窗口 ±1 容差内最多 90 秒重放风险收口）。

ALTER TABLE auth_totp
    ADD COLUMN last_used_step BIGINT DEFAULT NULL;

COMMENT ON COLUMN auth_totp.last_used_step IS '最近一次成功验证的TOTP时间步（防重放）';
