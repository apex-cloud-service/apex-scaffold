-- Apex Scaffold - 消息发件箱表(Outbox模式)
-- 版本: V45
-- 功能: 实现本地消息表,保证业务事务与消息发送的原子性
-- 作者: Apex Team
-- 日期: 2026-09-18

-- 创建消息发件箱表
CREATE TABLE IF NOT EXISTS `sys_message_outbox` (
  -- 主键与标识
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` VARCHAR(64) NOT NULL COMMENT '消息唯一标识(全局唯一,雪花算法生成)',
  
  -- 消息路由信息
  `topic` VARCHAR(255) NOT NULL COMMENT 'RocketMQ Topic主题',
  `tag` VARCHAR(64) NOT NULL COMMENT 'RocketMQ Tag标签,用于消息过滤',
  `payload` LONGTEXT NOT NULL COMMENT '消息体JSON载荷,业务数据',
  `message_type` VARCHAR(32) NOT NULL DEFAULT 'BUSINESS_MESSAGE' 
    COMMENT '消息类型:DOMAIN_EVENT(领域事件)/BUSINESS_MESSAGE(业务消息)',
  
  -- 状态管理
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' 
    COMMENT '消息状态:PENDING(待投递)/PROCESSING(投递中)/DELIVERED(已投递)/DEAD(死信)',
  
  -- 重试控制
  `attempts` INT NOT NULL DEFAULT 0 COMMENT '已尝试投递次数',
  `max_attempts` INT NOT NULL DEFAULT 5 COMMENT '最大尝试次数,超过后进入死信',
  `next_attempt_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) 
    COMMENT '下次尝试时间,支持延迟投递',
  `retry_delay_seconds` INT NOT NULL DEFAULT 10 
    COMMENT '重试延迟基数(秒),实际延迟=基数*2^(attempts-1),指数退避',
  
  -- 分布式锁
  `locked_by` VARCHAR(64) DEFAULT NULL COMMENT '锁定实例ID(UUID),防止多实例并发投递',
  `locked_at` DATETIME(6) DEFAULT NULL COMMENT '锁定时间,用于检测过期锁',
  `lock_timeout_seconds` INT NOT NULL DEFAULT 300 COMMENT '锁超时时间(秒),默认5分钟',
  
  -- 错误追踪
  `last_error` VARCHAR(1000) DEFAULT NULL COMMENT '最近一次投递失败的错误信息',
  
  -- 租户隔离
  `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID,实现多租户数据隔离',
  
  -- 审计字段
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '消息创建时间',
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) 
    ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
  `delivered_at` DATETIME(6) DEFAULT NULL COMMENT '投递成功时间',
  
  -- 主键与唯一约束
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`),
  
  -- 性能索引
  KEY `idx_dispatch` (`status`, `next_attempt_at`, `tenant_id`) 
    COMMENT '调度查询索引:查询待投递消息',
  KEY `idx_locked` (`status`, `locked_at`, `locked_by`) 
    COMMENT '锁管理索引:查询和释放过期锁',
  KEY `idx_tenant` (`tenant_id`, `created_at`) 
    COMMENT '租户查询索引:按租户查询消息历史'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
COMMENT='消息发件箱表-Outbox模式实现本地消息表';

