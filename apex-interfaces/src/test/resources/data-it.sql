-- 脚本用途：为接口模块集成测试重置并装载最小化认证授权种子数据。
-- 业务背景：通过固定用户、角色、权限及关联关系保证数据库集成测试断言稳定可复现。
-- 前置依赖：测试数据库已完成全部适用 Flyway 建表迁移，目标表结构与列定义可用。
-- 影响范围：清空五张系统授权相关表并写入固定测试记录，仅限隔离的测试数据库。
-- 幂等性：先 DELETE 后 INSERT，可在无外部并发写入的独立测试库中重复执行。
-- MySQL/Flyway 兼容性：采用 MySQL 兼容 DML；本文件是测试数据脚本，不是正式版本迁移。
-- 数据风险：DELETE 会删除目标表全部数据，误连开发、预发或生产数据库将造成严重数据丢失。
-- 运维注意：执行前强校验数据源为一次性测试库，禁止用于共享环境或生产初始化。
-- 版本与阶段：无 Flyway 版本；执行阶段为测试 schema 迁移完成后的数据准备阶段。
-- Checksum 提示：不进入正式 Flyway 版本 checksum；若测试框架计算资源校验值，注释变更仍可能触发差异。
-- ============================================================================
-- Apex Service - 集成测试种子数据
-- ============================================================================
-- 说明: 为集成测试（*IntegrationTest）提供最小化的数据库种子数据。
--       包含 1 个测试用户、1 个角色、1 个权限及其关联关系。
--       ID 分配策略：ID 1-10 保留给种子数据，增量测试数据使用 11+。
--
-- 执行顺序: 由 Flyway 完成 db/migration 迁移建表后执行（幂等重置种子数据）
-- 维护说明: 新增测试实体时使用 ID >= 11，避免与已有种子数据冲突
-- @author Apex Team
-- ============================================================================

-- ============================================================================
-- 步骤 1: 清理已有数据（幂等）
-- ============================================================================
DELETE FROM sys_role_permission;
DELETE FROM sys_user_role;
DELETE FROM sys_permission;
DELETE FROM sys_role;
DELETE FROM sys_user;

-- ============================================================================
-- 步骤 2: 插入测试实体
-- ============================================================================

-- 2.1 测试用户（id=1，用于认证和授权测试）
INSERT INTO sys_user (id, open_id, nick_name, status, create_time, update_time, version, deleted)
VALUES (1, 'test_open_id_001', '测试用户', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

-- 2.2 测试角色（id=1，普通用户角色）
INSERT INTO sys_role (id, name, code, status, create_time, update_time, version, deleted)
VALUES (1, '普通用户', 'ROLE_USER', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

-- 2.3 测试权限（id=5，用户查看权限）
INSERT INTO sys_permission (id, name, code, status, create_time, update_time, version, deleted)
VALUES (5, '用户查看', 'user:view', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

-- 2.4 用户-角色关联（id=1，用户1关联角色1）
INSERT INTO sys_user_role (id, user_id, role_id, create_time, update_time, version, deleted)
VALUES (1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

-- 2.5 角色-权限关联（id=6，角色1关联权限5）
INSERT INTO sys_role_permission (id, role_id, permission_id, create_time, update_time, version, deleted)
VALUES (6, 1, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);
