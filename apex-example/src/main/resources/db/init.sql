-- Apex Scaffold 示例模块数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS apex_example DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE apex_example;

-- 创建示例用户表
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

-- 插入测试数据
INSERT INTO `example_user` (`id`, `username`, `email`, `age`, `phone`, `status`, `deleted`) VALUES
(1, 'zhangsan', 'zhangsan@example.com', 25, '13800138001', 1, 0),
(2, 'lisi', 'lisi@example.com', 30, '13800138002', 1, 0),
(3, 'wangwu', 'wangwu@example.com', 28, '13800138003', 1, 0);
