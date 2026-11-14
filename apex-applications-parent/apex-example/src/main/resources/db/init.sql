-- 脚本用途：创建示例数据库、重建 example_user 表并装载固定演示数据。
-- 业务背景：为本地开发、功能演示和示例模块联调提供开箱即用的数据环境。
-- 前置依赖：MySQL 服务可用，执行账号具备建库、切库、删表、建表和写数据权限。
-- 影响范围：创建 apex_example 数据库，并删除重建其中的 example_user 表及数据。
-- 幂等性：可重复执行并得到固定结果，但每次都会 DROP TABLE，现有示例用户数据将被清空。
-- MySQL/Flyway 兼容性：面向 MySQL 8.x 客户端直接执行；含 CREATE DATABASE/USE，不作为 Flyway 迁移使用。
-- 数据风险：DROP TABLE 为破坏性操作，误连非演示实例会永久删除同名表数据。
-- 运维注意：仅用于本地或隔离演示环境，执行前确认连接实例和数据库名称并按需备份。
-- 版本与阶段：无 Flyway 版本；执行阶段为示例模块本地环境初始化。
-- Checksum 提示：不受正式 Flyway checksum 管理，不应加入生产迁移历史或替代增量迁移。
-- ================================================================
-- Apex Scaffold 示例模块数据库初始化脚本
-- ================================================================
-- 用途：创建 example 示例数据库、example_user 表及固定测试数据。
-- 适用：本地开发与手工演示；生产环境请通过正式迁移管理业务表结构。
-- 幂等性：建表前删除同名表，重复执行会重建表并重置示例数据。
-- ================================================================

-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS apex_example DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE apex_example;

-- 2. 创建示例用户表
DROP TABLE IF EXISTS `example_user`;
CREATE TABLE `example_user` (
  `id` BIGINT(20) NOT NULL COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `email` VARCHAR(100) NOT NULL COMMENT '邮箱',
  `age` INT(3) DEFAULT NULL COMMENT '年龄',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态(0-禁用, 1-启用)',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删除, 1-已删除)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_email` (`email`),
  KEY `idx_phone` (`phone`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='示例用户表';

-- 3. 插入固定示例数据
INSERT INTO `example_user` (`id`, `username`, `email`, `age`, `phone`, `status`, `deleted`) VALUES
(1, 'zhangsan', 'zhangsan@example.com', 25, '13800138001', 1, 0),
(2, 'lisi', 'lisi@example.com', 30, '13800138002', 1, 0),
(3, 'wangwu', 'wangwu@example.com', 28, '13800138003', 1, 0);
