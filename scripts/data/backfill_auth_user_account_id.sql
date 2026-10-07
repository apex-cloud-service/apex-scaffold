-- ============================================================
-- auth_user.account_id 统一账户映射回填模板（人工执行，非 Flyway）
-- ============================================================
-- 背景：
--   认证中心库（apex_auth）与主应用库（apex）为两个独立数据库，
--   Flyway 迁移无法跨库 JOIN，因此 auth_user ↔ sys_user 的存量账户
--   映射回填以本模板脚本由 DBA/运维人工执行。
--
-- 使用步骤：
--   1) 【步骤一】在主应用库导出候选账户清单（username/phone → sys_user.id）
--   2) 【步骤二】在认证中心库执行回填（按部署形态二选一）：
--        - 形态 A：两库同实例（MySQL 可用 db.table 跨库语法）
--        - 形态 B：两库独立实例（先生成临时映射表再回填）
--   3) 【步骤三】执行核对查询，输出未匹配清单人工处理
--
-- 安全须知：
--   - 回填仅填充 account_id 列，不改任何凭证/状态数据，可重复执行（幂等）
--   - username 匹配为最高置信度；phone 匹配请人工核对结果清单后再执行
--   - 匹配不到的 auth_user 保持 account_id 为空：登录仍成功，但令牌不带
--     accountId 声明，主应用将拒绝该令牌访问业务接口（需先在主应用库
--     建立对应 sys_user 或人工补充映射）
--
-- 关联方案：docs/02-architecture/multi-client-integration-plan.md（4.5 节）
-- ============================================================

-- ------------------------------------------------------------
-- 步骤一（主应用库）：导出候选账户清单
-- ------------------------------------------------------------
-- 输出 CSV：username, phone, sys_user_id
SELECT username, phone, id AS sys_user_id
FROM sys_user
WHERE deleted = 0
  AND username IS NOT NULL AND username <> ''
ORDER BY id;

-- ------------------------------------------------------------
-- 步骤二（认证中心库）：回填 auth_user.account_id
-- ------------------------------------------------------------

-- 形态 A：两库同实例（apex 为主应用库名，按实际部署调整）
-- 仅 username 精确匹配（最高置信度），且目标 sys_user 未删除
UPDATE apex_auth.auth_user au
JOIN apex.sys_user su
  ON su.username = au.username
 AND su.deleted = 0
SET au.account_id = su.id
WHERE au.deleted = 0
  AND au.account_id IS NULL
  AND au.username IS NOT NULL AND au.username <> '';

-- 形态 B：两库独立实例
--   B-1. 在认证中心库建临时映射表（步骤一导出的 CSV 导入或 INSERT）
-- CREATE TABLE IF NOT EXISTS tmp_account_mapping (
--     username     VARCHAR(64)  NOT NULL PRIMARY KEY,
--     sys_user_id  BIGINT       NOT NULL
-- );
--   B-2. 回填（幂等：仅填空值）
-- UPDATE auth_user au
-- JOIN tmp_account_mapping m ON m.username = au.username
-- SET au.account_id = m.sys_user_id
-- WHERE au.deleted = 0
--   AND au.account_id IS NULL;
--   B-3. 核对完成后清理临时表
-- DROP TABLE tmp_account_mapping;

-- ------------------------------------------------------------
-- 步骤三（认证中心库）：核对与未匹配清单
-- ------------------------------------------------------------

-- 3-1. 已映射数量
SELECT COUNT(*) AS mapped_count
FROM auth_user
WHERE deleted = 0 AND account_id IS NOT NULL;

-- 3-2. 未匹配清单（人工处理：在主应用库补建 sys_user 或修正 username 后重跑步骤二）
SELECT id, tenant_id, username, phone, status, last_login_at
FROM auth_user
WHERE deleted = 0
  AND account_id IS NULL
  AND username IS NOT NULL AND username <> '';

-- 3-3. 冲突核查：同一 sys_user_id 被多个 auth_user 引用（正常应为 0 行）
SELECT account_id, COUNT(*) AS ref_count
FROM auth_user
WHERE deleted = 0 AND account_id IS NOT NULL
GROUP BY account_id
HAVING COUNT(*) > 1;
