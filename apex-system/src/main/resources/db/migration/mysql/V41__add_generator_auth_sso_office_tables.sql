-- ============================================================
-- V41: 添加代码生成器、认证授权配置、SSO 和办公平台增强表及菜单权限
-- ============================================================
-- 执行日期: 2026-09-19
-- 目标: 补全后端管理端 API 所需的数据库表和菜单权限种子
-- 范围: 代码生成器(高优)、认证授权配置(高优)、SSO(中优)、办公平台增强(低优)
-- 幂等性: 使用 CREATE TABLE IF NOT EXISTS 和 INSERT ... ON DUPLICATE KEY UPDATE
-- ============================================================

-- ============================================================
-- 1. 代码生成器模块表(4 张表)
-- ============================================================

-- 1.1 数据源配置表
CREATE TABLE IF NOT EXISTS `gen_datasource` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `name`        VARCHAR(100) NOT NULL COMMENT '数据源名称',
  `db_type`     VARCHAR(20)  NOT NULL COMMENT '数据库类型(MYSQL/POSTGRESQL/ORACLE/SQLSERVER)',
  `host`        VARCHAR(100) NOT NULL COMMENT '数据库主机地址',
  `port`        INT          NOT NULL COMMENT '数据库端口',
  `database_name` VARCHAR(100) NOT NULL COMMENT '数据库名',
  `username`    VARCHAR(100) NOT NULL COMMENT '用户名',
  `password`    VARCHAR(500) NOT NULL COMMENT '密码(AES加密存储)',
  `schema_name` VARCHAR(100) DEFAULT NULL COMMENT '数据库schema(PostgreSQL/Oracle)',
  `params`      TEXT         DEFAULT NULL COMMENT '额外连接参数(JSON格式)',
  `status`      INT(1)       NOT NULL DEFAULT 1 COMMENT '状态(0-禁用,1-正常)',
  `tenant_id`   VARCHAR(64)  NOT NULL COMMENT '租户ID',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
  `deleted`     INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_datasource_name_tenant` (`name`, `tenant_id`, `deleted`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码生成器-数据源配置表';

-- 1.2 生成配置表
CREATE TABLE IF NOT EXISTS `gen_config` (
  `id`               BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `config_name`      VARCHAR(100) NOT NULL COMMENT '配置名称',
  `datasource_id`    BIGINT       NOT NULL COMMENT '数据源ID',
  `table_names`      TEXT         NOT NULL COMMENT '表名列表(JSON数组)',
  `package_name`     VARCHAR(200) NOT NULL COMMENT '包名(如com.apex.scaffold)',
  `module_name`      VARCHAR(100) NOT NULL COMMENT '模块名(如user/order)',
  `author`           VARCHAR(50)  DEFAULT 'Apex Team' COMMENT '作者名',
  `table_prefix`     VARCHAR(50)  DEFAULT NULL COMMENT '表前缀(移除前缀生成类名)',
  `output_path`      VARCHAR(500) NOT NULL COMMENT '输出路径(绝对路径)',
  `template_ids`     TEXT         DEFAULT NULL COMMENT '使用的模板ID列表(JSON数组)',
  `extra_config`     TEXT         DEFAULT NULL COMMENT '扩展配置(JSON格式)',
  `tenant_id`        VARCHAR(64)  NOT NULL COMMENT '租户ID',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`          INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
  `deleted`          INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_datasource_id` (`datasource_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码生成器-生成配置表';

-- 1.3 代码模板表
CREATE TABLE IF NOT EXISTS `gen_template` (
  `id`            BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `template_name` VARCHAR(100) NOT NULL COMMENT '模板名称',
  `template_type` VARCHAR(50)  NOT NULL COMMENT '模板类型(entity/mapper/service/controller等)',
  `file_suffix`   VARCHAR(20)  NOT NULL COMMENT '文件后缀(如.java/.xml)',
  `template_path` VARCHAR(200) NOT NULL COMMENT '模板文件路径(相对路径)',
  `template_content` LONGTEXT   NOT NULL COMMENT '模板内容(FreeMarker语法)',
  `description`   VARCHAR(500) DEFAULT NULL COMMENT '模板描述',
  `is_builtin`    INT(1)       NOT NULL DEFAULT 0 COMMENT '是否内置模板(0-自定义,1-内置)',
  `tenant_id`     VARCHAR(64)  NOT NULL COMMENT '租户ID',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`       INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
  `deleted`       INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_template_type` (`template_type`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码生成器-代码模板表';

-- 1.4 生成历史表
CREATE TABLE IF NOT EXISTS `gen_history` (
  `id`            BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `config_id`     BIGINT       NOT NULL COMMENT '生成配置ID',
  `generated_files` TEXT       NOT NULL COMMENT '生成的文件列表(JSON数组)',
  `output_path`   VARCHAR(500) NOT NULL COMMENT '输出路径',
  `status`        VARCHAR(20)  NOT NULL COMMENT '生成状态(SUCCESS/FAILED)',
  `error_message` TEXT         DEFAULT NULL COMMENT '错误信息',
  `operator_id`   BIGINT       NOT NULL COMMENT '操作人ID',
  `tenant_id`     VARCHAR(64)  NOT NULL COMMENT '租户ID',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`       INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
  `deleted`       INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_config_id` (`config_id`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码生成器-生成历史表';

-- ============================================================
-- 2. 认证授权配置模块表(3 张表)
-- ============================================================

-- 2.1 JWT 配置表
CREATE TABLE IF NOT EXISTS `sys_jwt_config` (
  `id`                BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `issuer`            VARCHAR(200) NOT NULL COMMENT 'JWT签发者',
  `secret_key`        VARCHAR(500) NOT NULL COMMENT '签名密钥(AES加密存储)',
  `access_token_ttl`  INT          NOT NULL DEFAULT 3600 COMMENT '访问令牌有效期(秒)',
  `refresh_token_ttl` INT          NOT NULL DEFAULT 86400 COMMENT '刷新令牌有效期(秒)',
  `algorithm`         VARCHAR(20)  NOT NULL DEFAULT 'HS256' COMMENT '签名算法(HS256/RS256)',
  `public_key`        TEXT         DEFAULT NULL COMMENT 'RS256公钥(PEM格式)',
  `private_key`       TEXT         DEFAULT NULL COMMENT 'RS256私钥(PEM格式,AES加密)',
  `enabled`           INT(1)       NOT NULL DEFAULT 1 COMMENT '是否启用(0-禁用,1-启用)',
  `tenant_id`         VARCHAR(64)  NOT NULL COMMENT '租户ID',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`           INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
  `deleted`           INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统-JWT配置表';

-- 2.2 会话配置表
CREATE TABLE IF NOT EXISTS `sys_session_config` (
  `id`                   BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `max_sessions`         INT          NOT NULL DEFAULT 1 COMMENT '单用户最大会话数',
  `session_timeout`      INT          NOT NULL DEFAULT 1800 COMMENT '会话超时时间(秒)',
  `concurrent_control`   INT(1)       NOT NULL DEFAULT 1 COMMENT '并发控制(0-允许多会话,1-踢出旧会话)',
  `remember_me_enabled`  INT(1)       NOT NULL DEFAULT 1 COMMENT '记住我功能(0-禁用,1-启用)',
  `remember_me_ttl`      INT          NOT NULL DEFAULT 604800 COMMENT '记住我有效期(秒,默认7天)',
  `enabled`              INT(1)       NOT NULL DEFAULT 1 COMMENT '是否启用(0-禁用,1-启用)',
  `tenant_id`            VARCHAR(64)  NOT NULL COMMENT '租户ID',
  `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`              INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
  `deleted`              INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统-会话配置表';

-- 2.3 安全策略表
CREATE TABLE IF NOT EXISTS `sys_security_policy` (
  `id`                      BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `password_min_length`     INT          NOT NULL DEFAULT 8 COMMENT '密码最小长度',
  `password_require_upper`  INT(1)       NOT NULL DEFAULT 1 COMMENT '密码需包含大写字母(0-否,1-是)',
  `password_require_lower`  INT(1)       NOT NULL DEFAULT 1 COMMENT '密码需包含小写字母(0-否,1-是)',
  `password_require_digit`  INT(1)       NOT NULL DEFAULT 1 COMMENT '密码需包含数字(0-否,1-是)',
  `password_require_special` INT(1)      NOT NULL DEFAULT 1 COMMENT '密码需包含特殊字符(0-否,1-是)',
  `password_expiry_days`    INT          DEFAULT NULL COMMENT '密码过期天数(NULL表示不过期)',
  `max_login_attempts`      INT          NOT NULL DEFAULT 5 COMMENT '最大登录尝试次数',
  `lockout_duration`        INT          NOT NULL DEFAULT 1800 COMMENT '锁定时长(秒)',
  `ip_whitelist`            TEXT         DEFAULT NULL COMMENT 'IP白名单(JSON数组)',
  `ip_blacklist`            TEXT         DEFAULT NULL COMMENT 'IP黑名单(JSON数组)',
  `enabled`                 INT(1)       NOT NULL DEFAULT 1 COMMENT '是否启用(0-禁用,1-启用)',
  `tenant_id`               VARCHAR(64)  NOT NULL COMMENT '租户ID',
  `create_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`                 INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
  `deleted`                 INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统-安全策略表';

-- ============================================================
-- 3. SSO 单点登录模块表(3 张表)
-- ============================================================

-- 3.1 SSO 提供商配置表
CREATE TABLE IF NOT EXISTS `sys_sso_provider` (
  `id`              BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `provider_name`   VARCHAR(100) NOT NULL COMMENT '提供商名称',
  `provider_type`   VARCHAR(20)  NOT NULL COMMENT '协议类型(SAML/CAS/LDAP/OIDC)',
  `config`          TEXT         NOT NULL COMMENT '提供商配置(JSON格式)',
  `entity_id`       VARCHAR(200) DEFAULT NULL COMMENT 'SAML EntityID',
  `metadata_url`    VARCHAR(500) DEFAULT NULL COMMENT 'SAML Metadata URL',
  `login_url`       VARCHAR(500) NOT NULL COMMENT '登录地址',
  `logout_url`      VARCHAR(500) DEFAULT NULL COMMENT '登出地址',
  `enabled`         INT(1)       NOT NULL DEFAULT 1 COMMENT '是否启用(0-禁用,1-启用)',
  `priority`        INT          NOT NULL DEFAULT 0 COMMENT '优先级(数字越小优先级越高)',
  `tenant_id`       VARCHAR(64)  NOT NULL COMMENT '租户ID',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`         INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
  `deleted`         INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_name_tenant` (`provider_name`, `tenant_id`, `deleted`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统-SSO提供商配置表';

-- 3.2 SSO 会话表
CREATE TABLE IF NOT EXISTS `sys_sso_session` (
  `id`            BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `provider_id`   BIGINT       NOT NULL COMMENT 'SSO提供商ID',
  `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
  `external_id`   VARCHAR(200) NOT NULL COMMENT '外部用户ID',
  `session_index` VARCHAR(200) NOT NULL COMMENT 'SSO会话索引',
  `assertion`     TEXT         DEFAULT NULL COMMENT 'SAML断言或其他认证数据',
  `login_time`    DATETIME     NOT NULL COMMENT '登录时间',
  `expire_time`   DATETIME     NOT NULL COMMENT '过期时间',
  `status`        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态(ACTIVE/EXPIRED/LOGOUT)',
  `tenant_id`     VARCHAR(64)  NOT NULL COMMENT '租户ID',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`       INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
  `deleted`       INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_index` (`session_index`, `deleted`),
  KEY `idx_provider_id` (`provider_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统-SSO会话表';

-- 3.3 SSO 审计日志表
CREATE TABLE IF NOT EXISTS `sys_sso_audit_log` (
  `id`            BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `provider_id`   BIGINT       NOT NULL COMMENT 'SSO提供商ID',
  `user_id`       BIGINT       DEFAULT NULL COMMENT '用户ID(登录失败时可为空)',
  `external_id`   VARCHAR(200) DEFAULT NULL COMMENT '外部用户ID',
  `operation`     VARCHAR(50)  NOT NULL COMMENT '操作类型(LOGIN/LOGOUT/REFRESH/ERROR)',
  `status`        VARCHAR(20)  NOT NULL COMMENT '操作状态(SUCCESS/FAILED)',
  `ip_address`    VARCHAR(50)  DEFAULT NULL COMMENT '客户端IP',
  `user_agent`    VARCHAR(500) DEFAULT NULL COMMENT '用户代理',
  `error_message` TEXT         DEFAULT NULL COMMENT '错误信息',
  `request_data`  TEXT         DEFAULT NULL COMMENT '请求数据(JSON格式)',
  `tenant_id`     VARCHAR(64)  NOT NULL COMMENT '租户ID',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`       INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号(乐观锁)',
  `deleted`       INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_provider_id` (`provider_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_tenant_id` (`tenant_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统-SSO审计日志表';

-- ============================================================
-- 4. 菜单和权限种子数据
-- ============================================================

-- 4.1 代码生成器菜单(ID段: 4000-4099)
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, deleted)
VALUES (4000, 1000, '代码生成器', 1, 'generator', 'system/generator/index', 'system:generator:view', 'Code', 20, 0, 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    menu_name = '代码生成器',
    parent_id = 1000,
    menu_type = 1,
    path = 'generator',
    component = 'system/generator/index',
    perms = 'system:generator:view',
    icon = 'Code',
    sort = 20,
    status = 0,
    visible = 0,
    update_time = NOW();

-- 4.2 代码生成器权限
INSERT INTO sys_permission (id, name, code, description, type, status, create_time, update_time, deleted) VALUES
    (3000, '代码生成器页面', 'system:generator:view', '查看代码生成器页面', 1, 1, NOW(), NOW(), 0),
    (3001, '数据源列表', 'system:generator:datasource:list', '查询数据源列表', 1, 1, NOW(), NOW(), 0),
    (3002, '新增数据源', 'system:generator:datasource:add', '新增数据源', 1, 1, NOW(), NOW(), 0),
    (3003, '编辑数据源', 'system:generator:datasource:edit', '编辑数据源', 1, 1, NOW(), NOW(), 0),
    (3004, '删除数据源', 'system:generator:datasource:remove', '删除数据源', 1, 1, NOW(), NOW(), 0),
    (3005, '测试连接', 'system:generator:datasource:test', '测试数据源连接', 1, 1, NOW(), NOW(), 0),
    (3006, '查询表结构', 'system:generator:table:list', '查询数据库表结构', 1, 1, NOW(), NOW(), 0),
    (3007, '生成配置列表', 'system:generator:config:list', '查询生成配置列表', 1, 1, NOW(), NOW(), 0),
    (3008, '新增生成配置', 'system:generator:config:add', '新增生成配置', 1, 1, NOW(), NOW(), 0),
    (3009, '编辑生成配置', 'system:generator:config:edit', '编辑生成配置', 1, 1, NOW(), NOW(), 0),
    (3010, '删除生成配置', 'system:generator:config:remove', '删除生成配置', 1, 1, NOW(), NOW(), 0),
    (3011, '执行代码生成', 'system:generator:generate', '执行代码生成', 1, 1, NOW(), NOW(), 0),
    (3012, '生成历史列表', 'system:generator:history:list', '查询生成历史', 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    status = 1,
    update_time = NOW();

-- 4.3 认证授权配置菜单(ID段: 5000-5099)
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, deleted)
VALUES (5000, 1000, '认证授权配置', 1, 'auth-config', 'system/auth-config/index', 'system:authconfig:view', 'Lock', 21, 0, 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    menu_name = '认证授权配置',
    parent_id = 1000,
    menu_type = 1,
    path = 'auth-config',
    component = 'system/auth-config/index',
    perms = 'system:authconfig:view',
    icon = 'Lock',
    sort = 21,
    status = 0,
    visible = 0,
    update_time = NOW();

-- 4.4 认证授权配置权限
INSERT INTO sys_permission (id, name, code, description, type, status, create_time, update_time, deleted) VALUES
    (3100, '认证授权配置页面', 'system:authconfig:view', '查看认证授权配置页面', 1, 1, NOW(), NOW(), 0),
    (3101, '查询JWT配置', 'system:authconfig:jwt:query', '查询JWT配置', 1, 1, NOW(), NOW(), 0),
    (3102, '更新JWT配置', 'system:authconfig:jwt:update', '更新JWT配置', 1, 1, NOW(), NOW(), 0),
    (3103, '查询会话配置', 'system:authconfig:session:query', '查询会话配置', 1, 1, NOW(), NOW(), 0),
    (3104, '更新会话配置', 'system:authconfig:session:update', '更新会话配置', 1, 1, NOW(), NOW(), 0),
    (3105, '查询安全策略', 'system:authconfig:security:query', '查询安全策略', 1, 1, NOW(), NOW(), 0),
    (3106, '更新安全策略', 'system:authconfig:security:update', '更新安全策略', 1, 1, NOW(), NOW(), 0),
    (3107, 'OAuth2客户端列表', 'system:authconfig:oauth2:list', '查询OAuth2客户端列表(复用)', 1, 1, NOW(), NOW(), 0),
    (3108, '在线会话列表', 'system:authconfig:session:list', '查询在线会话列表(复用)', 1, 1, NOW(), NOW(), 0),
    (3109, '强制登出', 'system:authconfig:session:kickout', '强制用户登出', 1, 1, NOW(), NOW(), 0),
    (3110, '登录日志', 'system:authconfig:loginlog:list', '查询登录日志(复用)', 1, 1, NOW(), NOW(), 0),
    (3111, '权限列表', 'system:authconfig:permission:list', '查询权限列表(复用)', 1, 1, NOW(), NOW(), 0),
    (3112, '同步权限码', 'system:authconfig:permission:sync', '扫描并同步权限码', 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    status = 1,
    update_time = NOW();

-- 4.5 SSO 单点登录菜单(ID段: 6000-6099)
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, deleted)
VALUES (6000, 1000, 'SSO管理', 1, 'sso', 'system/sso/index', 'system:sso:view', 'Connection', 22, 0, 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    menu_name = 'SSO管理',
    parent_id = 1000,
    menu_type = 1,
    path = 'sso',
    component = 'system/sso/index',
    perms = 'system:sso:view',
    icon = 'Connection',
    sort = 22,
    status = 0,
    visible = 0,
    update_time = NOW();

-- 4.6 SSO 权限
INSERT INTO sys_permission (id, name, code, description, type, status, create_time, update_time, deleted) VALUES
    (3200, 'SSO管理页面', 'system:sso:view', '查看SSO管理页面', 1, 1, NOW(), NOW(), 0),
    (3201, 'SSO提供商列表', 'system:sso:provider:list', '查询SSO提供商列表', 1, 1, NOW(), NOW(), 0),
    (3202, '新增SSO提供商', 'system:sso:provider:add', '新增SSO提供商', 1, 1, NOW(), NOW(), 0),
    (3203, '编辑SSO提供商', 'system:sso:provider:edit', '编辑SSO提供商', 1, 1, NOW(), NOW(), 0),
    (3204, '删除SSO提供商', 'system:sso:provider:remove', '删除SSO提供商', 1, 1, NOW(), NOW(), 0),
    (3205, '测试SSO连接', 'system:sso:provider:test', '测试SSO提供商连接', 1, 1, NOW(), NOW(), 0),
    (3206, '获取SSO元数据', 'system:sso:provider:metadata', '获取SSO提供商元数据', 1, 1, NOW(), NOW(), 0),
    (3207, 'SSO会话列表', 'system:sso:session:list', '查询SSO会话列表', 1, 1, NOW(), NOW(), 0),
    (3208, '撤销SSO会话', 'system:sso:session:revoke', '撤销SSO会话', 1, 1, NOW(), NOW(), 0),
    (3209, 'SSO审计日志', 'system:sso:auditlog:list', '查询SSO审计日志', 1, 1, NOW(), NOW(), 0),
    (3210, 'SSO统计数据', 'system:sso:statistics', '查询SSO统计数据', 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    status = 1,
    update_time = NOW();

-- 4.7 为超级管理员角色授予所有新增权限
-- 注意:仅授予新增的权限,避免重复插入
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, id
FROM sys_permission
WHERE code IN (
    -- 代码生成器
    'system:generator:view', 'system:generator:datasource:list', 'system:generator:datasource:add',
    'system:generator:datasource:edit', 'system:generator:datasource:remove', 'system:generator:datasource:test',
    'system:generator:table:list', 'system:generator:config:list', 'system:generator:config:add',
    'system:generator:config:edit', 'system:generator:config:remove', 'system:generator:generate',
    'system:generator:history:list',
    -- 认证授权配置
    'system:authconfig:view', 'system:authconfig:jwt:query', 'system:authconfig:jwt:update',
    'system:authconfig:session:query', 'system:authconfig:session:update', 'system:authconfig:security:query',
    'system:authconfig:security:update', 'system:authconfig:oauth2:list', 'system:authconfig:session:list',
    'system:authconfig:session:kickout', 'system:authconfig:loginlog:list', 'system:authconfig:permission:list',
    'system:authconfig:permission:sync',
    -- SSO
    'system:sso:view', 'system:sso:provider:list', 'system:sso:provider:add',
    'system:sso:provider:edit', 'system:sso:provider:remove', 'system:sso:provider:test',
    'system:sso:provider:metadata', 'system:sso:session:list', 'system:sso:session:revoke',
    'system:sso:auditlog:list', 'system:sso:statistics'
)
  AND status = 1
  AND deleted = 0;

-- ============================================================
-- 说明
-- ============================================================
-- 1. 表命名约定:
--    - gen_* : 代码生成器表
--    - sys_jwt_config/sys_session_config/sys_security_policy : 认证授权配置表
--    - sys_sso_* : SSO 相关表
--
-- 2. 菜单 ID 分配:
--    - 4000-4099 : 代码生成器
--    - 5000-5099 : 认证授权配置
--    - 6000-6099 : SSO 管理
--
-- 3. 权限 ID 分配:
--    - 3000-3099 : 代码生成器
--    - 3100-3199 : 认证授权配置
--    - 3200-3299 : SSO
--
-- 4. 前端 component 字段需与 VIEW_REGISTRY 一致
--
-- 5. 密码和密钥字段使用 AES 加密存储,密钥来自环境变量
--
-- 6. 注意:sys_login_attempt 表功能已被 LoginLogHandler 现有逻辑覆盖,不重复建表
--
-- 7. 办公平台增强(office/messages,organization/sync,approval/*,calendar/*)
--    暂不新增表,复用现有 office_* 表,待 Phase 4 补充路由层
