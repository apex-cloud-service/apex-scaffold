-- ============================================================
-- Flyway迁移脚本：V44__create_auth_center_core_tables.sql
-- 描述：创建统一认证授权中心核心数据表
-- 作者：Apex Team
-- 日期：2026-09-20
-- 版本：1.0.0
-- ============================================================

-- ============================================================
-- 用户认证表
-- 存储用户基本信息和认证凭证，支持多租户隔离
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_user (
    -- 主键ID，雪花算法生成，全局唯一
    id BIGINT PRIMARY KEY COMMENT '用户ID',
    
    -- 租户标识，用于多租户数据隔离，必填
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    
    -- 用户名，租户内唯一，用于登录认证
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    
    -- 邮箱地址，可选，用于邮箱登录和通知
    email VARCHAR(100) COMMENT '邮箱',
    
    -- 手机号码，可选，用于短信登录和通知
    phone VARCHAR(20) COMMENT '手机号',
    
    -- 密码哈希值，使用BCrypt或Argon2加密存储
    password_hash VARCHAR(255) COMMENT '密码哈希',
    
    -- 第三方平台OpenID，用于微信、支付宝等第三方登录
    open_id VARCHAR(100) COMMENT 'OpenID',
    
    -- 账户状态：1-正常 0-禁用 2-锁定 3-待激活
    status TINYINT DEFAULT 1 COMMENT '账户状态',
    
    -- 最后登录时间，记录用户最近一次成功登录的时间
    last_login_at TIMESTAMP COMMENT '最后登录时间',
    
    -- 最后登录IP地址，用于安全审计和异常检测
    last_login_ip VARCHAR(50) COMMENT '最后登录IP',
    
    -- 连续登录失败次数，用于账户锁定策略
    failed_login_attempts INT DEFAULT 0 COMMENT '失败登录次数',
    
    -- 账户锁定截止时间，超过此时间自动解锁
    locked_until TIMESTAMP COMMENT '锁定截止时间',
    
    -- 创建时间，记录创建时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    -- 更新时间，记录最后更新时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 逻辑删除标记：0-未删除 1-已删除
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    
    -- 索引：租户+用户名组合查询
    INDEX idx_tenant_username (tenant_id, username),
    
    -- 索引：租户+手机号组合查询
    INDEX idx_tenant_phone (tenant_id, phone),
    
    -- 索引：OpenID查询
    INDEX idx_open_id (open_id),
    
    -- 索引：最后登录时间查询
    INDEX idx_last_login_at (last_login_at),
    
    -- 唯一约束：租户内用户名唯一
    UNIQUE KEY uk_tenant_username (tenant_id, username, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户认证表';

-- ============================================================
-- Token表
-- 存储访问令牌和刷新令牌，支持Token撤销和黑名单
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_token (
    -- 主键ID，雪花算法生成
    id BIGINT PRIMARY KEY COMMENT 'Token ID',
    
    -- 租户标识，用于多租户数据隔离
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    
    -- 用户ID，关联auth_user表
    user_id BIGINT NOT NULL COMMENT '用户ID',
    
    -- Token类型：ACCESS-访问令牌 REFRESH-刷新令牌
    token_type VARCHAR(20) NOT NULL COMMENT 'Token类型',
    
    -- Token值，实际的JWT字符串或Opaque Token
    token_value VARCHAR(500) NOT NULL COMMENT 'Token值',
    
    -- JWT ID，JWT的唯一标识符，用于撤销和黑名单
    jti VARCHAR(100) UNIQUE COMMENT 'JWT ID',
    
    -- Token过期时间，超过此时间Token失效
    expires_at TIMESTAMP NOT NULL COMMENT '过期时间',
    
    -- 撤销标记：0-正常 1-已撤销
    revoked TINYINT DEFAULT 0 COMMENT '撤销标记',
    
    -- 撤销时间，记录Token被撤销的时间
    revoked_at TIMESTAMP COMMENT '撤销时间',
    
    -- 设备ID，标识Token所属设备
    device_id VARCHAR(100) COMMENT '设备ID',
    
    -- 客户端IP地址，记录Token签发时的客户端IP
    client_ip VARCHAR(50) COMMENT '客户端IP',
    
    -- User-Agent，记录客户端类型和版本
    user_agent VARCHAR(500) COMMENT 'User-Agent',
    
    -- 创建时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    -- 索引：租户+用户查询
    INDEX idx_tenant_user (tenant_id, user_id),
    
    -- 唯一索引：JWT ID查询
    UNIQUE INDEX idx_jti (jti),
    
    -- 索引：过期时间查询，用于清理过期Token
    INDEX idx_expires_at (expires_at),
    
    -- 索引：设备ID查询，用于设备管理
    INDEX idx_device_id (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token表';

-- ============================================================
-- 会话表
-- 存储用户会话信息，支持分布式会话管理和并发控制
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_session (
    -- 主键ID，雪花算法生成
    id BIGINT PRIMARY KEY COMMENT '会话ID',
    
    -- 租户标识
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    
    -- 用户ID
    user_id BIGINT NOT NULL COMMENT '用户ID',
    
    -- 会话ID，全局唯一标识符
    session_id VARCHAR(100) UNIQUE NOT NULL COMMENT '会话ID',
    
    -- 设备ID，标识会话所属设备
    device_id VARCHAR(100) COMMENT '设备ID',
    
    -- 设备类型：WEB-网页 MOBILE-移动APP DESKTOP-桌面应用
    device_type VARCHAR(50) COMMENT '设备类型',
    
    -- 客户端IP地址
    client_ip VARCHAR(50) COMMENT '客户端IP',
    
    -- User-Agent
    user_agent VARCHAR(500) COMMENT 'User-Agent',
    
    -- 最后活跃时间，用于会话超时检测
    last_active_at TIMESTAMP COMMENT '最后活跃时间',
    
    -- 会话过期时间
    expires_at TIMESTAMP NOT NULL COMMENT '过期时间',
    
    -- 创建时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    -- 索引：租户+用户查询
    INDEX idx_tenant_user (tenant_id, user_id),
    
    -- 唯一索引：会话ID查询
    UNIQUE INDEX idx_session_id (session_id),
    
    -- 索引：最后活跃时间查询
    INDEX idx_last_active (last_active_at),
    
    -- 索引：过期时间查询
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- ============================================================
-- 权限表
-- 存储系统权限定义，支持RBAC权限模型
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_permission (
    -- 主键ID，雪花算法生成
    id BIGINT PRIMARY KEY COMMENT '权限ID',
    
    -- 租户标识
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    
    -- 权限编码，租户内唯一，格式：resource:action
    code VARCHAR(100) NOT NULL COMMENT '权限编码',
    
    -- 权限名称
    name VARCHAR(100) NOT NULL COMMENT '权限名称',
    
    -- 资源标识，如：/api/v1/users
    resource VARCHAR(200) COMMENT '资源标识',
    
    -- 操作类型：read-读取 create-创建 update-更新 delete-删除
    action VARCHAR(50) COMMENT '操作类型',
    
    -- 权限描述
    description TEXT COMMENT '权限描述',
    
    -- 启用状态：1-启用 0-禁用
    enabled TINYINT DEFAULT 1 COMMENT '启用状态',
    
    -- 创建时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    -- 更新时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 唯一约束：租户内权限编码唯一
    UNIQUE KEY uk_tenant_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- ============================================================
-- 角色表
-- 存储角色定义，支持RBAC权限模型
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_role (
    -- 主键ID，雪花算法生成
    id BIGINT PRIMARY KEY COMMENT '角色ID',
    
    -- 租户标识
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    
    -- 角色编码，租户内唯一
    code VARCHAR(100) NOT NULL COMMENT '角色编码',
    
    -- 角色名称
    name VARCHAR(100) NOT NULL COMMENT '角色名称',
    
    -- 角色描述
    description TEXT COMMENT '角色描述',
    
    -- 数据权限范围：0-全部数据 1-本部门数据 2-本人数据 3-自定义
    data_scope TINYINT DEFAULT 0 COMMENT '数据权限范围',
    
    -- 启用状态：1-启用 0-禁用
    enabled TINYINT DEFAULT 1 COMMENT '启用状态',
    
    -- 创建时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    -- 更新时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 唯一约束：租户内角色编码唯一
    UNIQUE KEY uk_tenant_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ============================================================
-- 用户角色关联表
-- 多对多关系：一个用户可以拥有多个角色，一个角色可以分配给多个用户
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_user_role (
    -- 主键ID，雪花算法生成
    id BIGINT PRIMARY KEY COMMENT '关联ID',
    
    -- 租户标识
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    
    -- 用户ID
    user_id BIGINT NOT NULL COMMENT '用户ID',
    
    -- 角色ID
    role_id BIGINT NOT NULL COMMENT '角色ID',
    
    -- 创建时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    -- 唯一约束：用户+角色唯一
    UNIQUE KEY uk_user_role (user_id, role_id),
    
    -- 索引：租户+用户查询
    INDEX idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ============================================================
-- 角色权限关联表
-- 多对多关系：一个角色可以拥有多个权限，一个权限可以分配给多个角色
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_role_permission (
    -- 主键ID，雪花算法生成
    id BIGINT PRIMARY KEY COMMENT '关联ID',
    
    -- 租户标识
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    
    -- 角色ID
    role_id BIGINT NOT NULL COMMENT '角色ID',
    
    -- 权限ID
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    
    -- 创建时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    -- 唯一约束：角色+权限唯一
    UNIQUE KEY uk_role_permission (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- ============================================================
-- 审计日志表
-- 记录所有认证授权相关的操作日志，支持安全审计和合规要求
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_audit_log (
    -- 主键ID，雪花算法生成
    id BIGINT PRIMARY KEY COMMENT '日志ID',
    
    -- 租户标识
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    
    -- 用户ID，可为空（如登录失败时用户不存在）
    user_id BIGINT COMMENT '用户ID',
    
    -- 事件类型：LOGIN-登录 LOGOUT-登出 TOKEN_REFRESH-Token刷新 PERMISSION_CHANGE-权限变更
    event_type VARCHAR(50) NOT NULL COMMENT '事件类型',
    
    -- 事件状态：SUCCESS-成功 FAILURE-失败
    event_status VARCHAR(20) NOT NULL COMMENT '事件状态',
    
    -- 认证协议类型：JWT/OAUTH2/SSO/LDAP
    protocol_type VARCHAR(50) COMMENT '协议类型',
    
    -- 客户端IP地址
    client_ip VARCHAR(50) COMMENT '客户端IP',
    
    -- User-Agent
    user_agent VARCHAR(500) COMMENT 'User-Agent',
    
    -- 设备ID
    device_id VARCHAR(100) COMMENT '设备ID',
    
    -- 请求路径
    request_path VARCHAR(500) COMMENT '请求路径',
    
    -- 错误消息，仅在事件失败时记录
    error_message TEXT COMMENT '错误消息',
    
    -- 元数据，JSON格式，存储额外信息
    metadata JSON COMMENT '元数据',
    
    -- 创建时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    -- 索引：租户+用户查询
    INDEX idx_tenant_user (tenant_id, user_id),
    
    -- 索引：事件类型查询
    INDEX idx_event_type (event_type),
    
    -- 索引：创建时间查询
    INDEX idx_created_at (created_at),
    
    -- 索引：事件状态查询
    INDEX idx_event_status (event_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';

-- ============================================================
-- 认证协议配置表
-- 存储各种认证协议的配置信息，支持动态配置和热更新
-- ============================================================
CREATE TABLE IF NOT EXISTS auth_protocol_config (
    -- 主键ID，雪花算法生成
    id BIGINT PRIMARY KEY COMMENT '配置ID',
    
    -- 租户标识
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    
    -- 协议类型：JWT/OAUTH2/SSO/LDAP/KEYCLOAK
    protocol_type VARCHAR(50) NOT NULL COMMENT '协议类型',
    
    -- 协议名称
    protocol_name VARCHAR(100) NOT NULL COMMENT '协议名称',
    
    -- 配置JSON，存储协议特定配置
    config_json TEXT NOT NULL COMMENT '配置JSON',
    
    -- 启用状态：1-启用 0-禁用
    enabled TINYINT DEFAULT 1 COMMENT '启用状态',
    
    -- 优先级，数字越小优先级越高
    priority INT DEFAULT 0 COMMENT '优先级',
    
    -- 创建时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    -- 更新时间
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 乐观锁版本号
    version INT DEFAULT 1 COMMENT '版本号',
    
    -- 索引：租户+协议类型查询
    INDEX idx_tenant_type (tenant_id, protocol_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证协议配置表';
