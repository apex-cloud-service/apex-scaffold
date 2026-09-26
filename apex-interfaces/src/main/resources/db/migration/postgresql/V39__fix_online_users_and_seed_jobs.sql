-- V39: 修复在线用户表和定时任务种子数据

-- 创建在线用户表
CREATE TABLE IF NOT EXISTS sys_user_online (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    nick_name VARCHAR(100),
    session_id VARCHAR(100) NOT NULL,
    ip_address VARCHAR(50),
    location VARCHAR(200),
    browser VARCHAR(100),
    os VARCHAR(100),
    login_time TIMESTAMP NOT NULL,
    last_access_time TIMESTAMP NOT NULL,
    tenant_id VARCHAR(50) NOT NULL,
    CONSTRAINT uk_session_id UNIQUE (session_id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_user_id ON sys_user_online(user_id);
CREATE INDEX IF NOT EXISTS idx_tenant_id ON sys_user_online(tenant_id);
CREATE INDEX IF NOT EXISTS idx_last_access_time ON sys_user_online(last_access_time);

-- 添加表注释
COMMENT ON TABLE sys_user_online IS '在线用户表';
COMMENT ON COLUMN sys_user_online.id IS '主键ID';
COMMENT ON COLUMN sys_user_online.user_id IS '用户ID';
COMMENT ON COLUMN sys_user_online.username IS '用户名';
COMMENT ON COLUMN sys_user_online.nick_name IS '昵称';
COMMENT ON COLUMN sys_user_online.session_id IS '会话ID';
COMMENT ON COLUMN sys_user_online.ip_address IS 'IP地址';
COMMENT ON COLUMN sys_user_online.location IS '登录地点';
COMMENT ON COLUMN sys_user_online.browser IS '浏览器';
COMMENT ON COLUMN sys_user_online.os IS '操作系统';
COMMENT ON COLUMN sys_user_online.login_time IS '登录时间';
COMMENT ON COLUMN sys_user_online.last_access_time IS '最后访问时间';
COMMENT ON COLUMN sys_user_online.tenant_id IS '租户ID';

-- 插入定时任务种子数据
INSERT INTO sys_job (id, job_name, job_group, cron_expression, invoke_target, misfire_policy, concurrent, status, remark, tenant_id)
VALUES
(1, '系统监控数据采集', 'SYSTEM', '0 */5 * * * ?', 'systemMonitorHandler', 1, 0, 1, '每5分钟采集一次系统监控数据', 'default'),
(2, '清理过期日志', 'SYSTEM', '0 0 2 * * ?', 'logCleanupHandler', 1, 0, 1, '每天凌晨2点清理30天前的日志', 'default'),
(3, '用户会话检查', 'SYSTEM', '0 */10 * * * ?', 'sessionCheckHandler', 1, 0, 1, '每10分钟检查并清理超时用户会话', 'default'),
(4, '缓存预热', 'SYSTEM', '0 0 6 * * ?', 'cacheWarmupHandler', 1, 0, 0, '每天早上6点进行缓存预热', 'default')
ON CONFLICT (id) DO UPDATE SET
    job_name = EXCLUDED.job_name,
    job_group = EXCLUDED.job_group,
    cron_expression = EXCLUDED.cron_expression,
    invoke_target = EXCLUDED.invoke_target,
    update_time = NOW();
