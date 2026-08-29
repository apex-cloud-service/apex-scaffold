-- 脚本用途：为 example 模块集成测试创建示例用户表并写入固定种子数据。
-- 业务背景：在 H2 内存数据库中复现示例用户查询与持久化场景，保证测试结果稳定。
-- 前置依赖：测试数据源以 H2 MySQL 兼容模式启动，Flyway 已指向本测试迁移目录。
-- 影响范围：创建 example_user 测试表并插入三条固定记录，不作用于生产数据库。
-- 幂等性：建表使用 IF NOT EXISTS，但固定主键 INSERT 非幂等，完整脚本不应在同一 schema 重跑。
-- MySQL/Flyway 兼容性：以 H2 MODE=MySQL 和测试 Flyway V1 为准，不保证替代生产 MySQL 初始化脚本。
-- 数据风险：若误配置到共享 schema，可能创建同名表或因固定 ID 冲突导致启动失败。
-- 运维注意：保持测试迁移位置与生产迁移隔离，测试结束后由测试框架销毁内存库。
-- 迁移元信息：测试版本 V1；执行阶段为 example 集成测试上下文启动阶段。
-- Checksum 提示：修改注释也会改变测试 Flyway checksum；复用持久化测试库时需重建或审慎 repair。
-- ================================================================
-- Apex Scaffold example 测试迁移 V1：示例用户表
-- ================================================================
-- 用途：为 H2 MODE=MySQL 集成测试创建 example_user 表并写入固定数据。
-- 兼容性：保持 H2 测试方言可执行；生产/开发 MySQL 初始化使用同名主资源脚本。
-- ================================================================
-- example 测试库初始化（H2 兼容，供集成测试 Flyway 加载）
-- ================================================================
-- 背景: example 集成测试使用 H2 内存库（MODE=MySQL），
--       生产/开发库的 example_user 表由 src/main/resources/db/init.sql 提供。
-- 影响: 测试上下文启动时自动建表并插入种子数据，保证集成测试可重复运行。
-- ================================================================

CREATE TABLE IF NOT EXISTS example_user (
  id          BIGINT NOT NULL COMMENT '用户ID（雪花算法）',
  username    VARCHAR(50) NOT NULL COMMENT '用户名',
  email       VARCHAR(100) NOT NULL COMMENT '邮箱',
  age         INT DEFAULT NULL COMMENT '年龄',
  phone       VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  status      INT NOT NULL DEFAULT 1 COMMENT '状态（0-禁用，1-启用）',
  deleted     INT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0-未删除，1-已删除）',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
);

-- 种子数据（固定 ID，便于集成测试断言）
INSERT INTO example_user (id, username, email, age, phone, status, deleted) VALUES
(1, 'zhangsan', 'zhangsan@example.com', 25, '13800138001', 1, 0),
(2, 'lisi', 'lisi@example.com', 30, '13800138002', 1, 0),
(3, 'wangwu', 'wangwu@example.com', 28, '13800138003', 1, 0);
