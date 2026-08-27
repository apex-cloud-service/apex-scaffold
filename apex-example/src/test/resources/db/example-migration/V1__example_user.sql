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
