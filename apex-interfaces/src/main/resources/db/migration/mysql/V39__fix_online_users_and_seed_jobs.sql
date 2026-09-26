-- V39: 修复在线用户表和定时任务种子数据

-- 创建在线用户表
CREATE TABLE IF NOT EXISTS `sys_user_online` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `nick_name` VARCHAR(100) COMMENT '昵称',
    `session_id` VARCHAR(100) NOT NULL COMMENT '会话ID',
    `ip_address` VARCHAR(50) COMMENT 'IP地址',
    `location` VARCHAR(200) COMMENT '登录地点',
    `browser` VARCHAR(100) COMMENT '浏览器',
    `os` VARCHAR(100) COMMENT '操作系统',
    `login_time` DATETIME NOT NULL COMMENT '登录时间',
    `last_access_time` DATETIME NOT NULL COMMENT '最后访问时间',
    `tenant_id` VARCHAR(50) NOT NULL COMMENT '租户ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_last_access_time` (`last_access_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='在线用户表';

-- 插入定时任务种子数据
INSERT INTO `sys_job` (`id`, `job_name`, `job_group`, `cron_expression`, `invoke_target`, `misfire_policy`, `concurrent`, `status`, `remark`, `tenant_id`)
VALUES
(1, '系统监控数据采集', 'SYSTEM', '0 */5 * * * ?', 'systemMonitorHandler', 1, 0, 1, '每5分钟采集一次系统监控数据', 'default'),
(2, '清理过期日志', 'SYSTEM', '0 0 2 * * ?', 'logCleanupHandler', 1, 0, 1, '每天凌晨2点清理30天前的日志', 'default'),
(3, '用户会话检查', 'SYSTEM', '0 */10 * * * ?', 'sessionCheckHandler', 1, 0, 1, '每10分钟检查并清理超时用户会话', 'default'),
(4, '缓存预热', 'SYSTEM', '0 0 6 * * ?', 'cacheWarmupHandler', 1, 0, 0, '每天早上6点进行缓存预热', 'default')
ON DUPLICATE KEY UPDATE
    `job_name` = VALUES(`job_name`),
    `job_group` = VALUES(`job_group`),
    `cron_expression` = VALUES(`cron_expression`),
    `invoke_target` = VALUES(`invoke_target`),
    `update_time` = NOW();
