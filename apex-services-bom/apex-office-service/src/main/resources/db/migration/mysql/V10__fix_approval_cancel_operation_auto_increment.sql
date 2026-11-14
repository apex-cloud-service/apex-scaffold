-- ============================================================
-- Apex Scaffold - 办公服务独立库 V10：审批撤销编排主键自增修正（MySQL）
-- ============================================================
-- 【用途】
--   V9 建表时 `off_approval_cancel_operation.id` 仅声明 NOT NULL，未声明自增；
--   而实体 PO（ApprovalCancelOperationPO）主键为 {@code IdType.AUTO}，插入语句
--   不携带 id，依赖数据库自增生成主键。在 MySQL 上插入将因 id 无默认值而失败
--   （PG 方言 V9 已使用 BIGSERIAL，不受影响）。本迁移补齐 MySQL 自增声明。
--
-- 【幂等性】
--   ALTER TABLE 对已具备自增的表重复执行幂等。
--
-- 【方言约定】
--   与 PG 的 BIGSERIAL 语义对齐；V10 的 PG 配对迁移仅补充列注释（PG 无需变更）。
--
-- @author Apex Team
-- @version 1.3.2
-- @since 1.3.2
-- ============================================================

ALTER TABLE `off_approval_cancel_operation`
    MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID（自增，与 ApprovalCancelOperationPO 的 IdType.AUTO 对齐）';
