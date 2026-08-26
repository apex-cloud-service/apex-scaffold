-- ============================================================================
-- Apex Service - 集成测试种子数据
-- ============================================================================
-- 说明: 为集成测试（*IntegrationTest）提供最小化的数据库种子数据。
--       包含 1 个测试用户、1 个角色、1 个权限及其关联关系。
--       ID 分配策略：ID 1-10 保留给种子数据，增量测试数据使用 11+。
--
-- 执行顺序: 在 schema-it.sql 之后由 Flyway 执行
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
