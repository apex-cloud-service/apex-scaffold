-- ============================================================
-- Apex Scaffold - 办公服务独立库 V10：审批撤销编排主键自增修正（PostgreSQL 配对）
-- ============================================================
-- 【用途】
--   与 MySQL V10 配对。MySQL V10 将 off_approval_cancel_operation.id 修正为
--   自增；PostgreSQL 的 V9 已使用 BIGSERIAL（隐式序列默认值），无需结构变更，
--   本迁移仅补充列注释以固化与 PO（IdType.AUTO）的语义对齐口径。
--
-- 【幂等性】
--   COMMENT ON 语句可重复执行。
--
-- @author Apex Team
-- @version 1.3.2
-- @since 1.3.2
-- ============================================================

COMMENT ON COLUMN off_approval_cancel_operation.id IS '主键ID（自增序列生成，与 ApprovalCancelOperationPO 的 IdType.AUTO 对齐）';
