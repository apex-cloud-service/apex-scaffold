-- ============================================================
-- V41: 添加代码生成器、认证授权配置、SSO 和办公平台增强表及菜单权限
-- ============================================================
-- 执行日期: 2026-09-19
-- 目标: 补全后端管理端 API 所需的数据库表和菜单权限种子
-- 范围: 代码生成器(高优)、认证授权配置(高优)、SSO(中优)、办公平台增强(低优)
-- 幂等性: 使用 CREATE TABLE IF NOT EXISTS 和 DO $$ 块处理重复插入
-- ============================================================

-- ============================================================
-- 1. 代码生成器模块表(4 张表)
-- ============================================================

CREATE TABLE IF NOT EXISTS gen_datasource (
  id            BIGINT       NOT NULL,
  name          VARCHAR(100) NOT NULL,
  db_type       VARCHAR(20)  NOT NULL,
  host          VARCHAR(100) NOT NULL,
  port          INTEGER      NOT NULL,
  database_name VARCHAR(100) NOT NULL,
  username      VARCHAR(100) NOT NULL,
  password      VARCHAR(500) NOT NULL,
  schema_name   VARCHAR(100) DEFAULT NULL,
  params        TEXT         DEFAULT NULL,
  status        INTEGER      NOT NULL DEFAULT 1,
  tenant_id     VARCHAR(64)  NOT NULL,
  create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version       INTEGER      NOT NULL DEFAULT 0,
  deleted       INTEGER      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_datasource_name_tenant UNIQUE (name, tenant_id, deleted)
);
CREATE INDEX IF NOT EXISTS idx_gen_datasource_tenant ON gen_datasource(tenant_id);

CREATE TABLE IF NOT EXISTS gen_config (
  id               BIGINT       NOT NULL,
  config_name      VARCHAR(100) NOT NULL,
  datasource_id    BIGINT       NOT NULL,
  table_names      TEXT         NOT NULL,
  package_name     VARCHAR(200) NOT NULL,
  module_name      VARCHAR(100) NOT NULL,
  author           VARCHAR(50)  DEFAULT 'Apex Team',
  table_prefix     VARCHAR(50)  DEFAULT NULL,
  output_path      VARCHAR(500) NOT NULL,
  template_ids     TEXT         DEFAULT NULL,
  extra_config     TEXT         DEFAULT NULL,
  tenant_id        VARCHAR(64)  NOT NULL,
  create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version          INTEGER      NOT NULL DEFAULT 0,
  deleted          INTEGER      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_gen_config_datasource ON gen_config(datasource_id);
CREATE INDEX IF NOT EXISTS idx_gen_config_tenant ON gen_config(tenant_id);

CREATE TABLE IF NOT EXISTS gen_template (
  id               BIGINT       NOT NULL,
  template_name    VARCHAR(100) NOT NULL,
  template_type    VARCHAR(50)  NOT NULL,
  file_suffix      VARCHAR(20)  NOT NULL,
  template_path    VARCHAR(200) NOT NULL,
  template_content TEXT         NOT NULL,
  description      VARCHAR(500) DEFAULT NULL,
  is_builtin       INTEGER      NOT NULL DEFAULT 0,
  tenant_id        VARCHAR(64)  NOT NULL,
  create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version          INTEGER      NOT NULL DEFAULT 0,
  deleted          INTEGER      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_gen_template_type ON gen_template(template_type);
CREATE INDEX IF NOT EXISTS idx_gen_template_tenant ON gen_template(tenant_id);

CREATE TABLE IF NOT EXISTS gen_history (
  id              BIGINT       NOT NULL,
  config_id       BIGINT       NOT NULL,
  generated_files TEXT         NOT NULL,
  output_path     VARCHAR(500) NOT NULL,
  status          VARCHAR(20)  NOT NULL,
  error_message   TEXT         DEFAULT NULL,
  operator_id     BIGINT       NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL,
  create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version         INTEGER      NOT NULL DEFAULT 0,
  deleted         INTEGER      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_gen_history_config ON gen_history(config_id);
CREATE INDEX IF NOT EXISTS idx_gen_history_operator ON gen_history(operator_id);
CREATE INDEX IF NOT EXISTS idx_gen_history_tenant ON gen_history(tenant_id);

COMMENT ON TABLE gen_datasource IS '代码生成器-数据源配置表';
COMMENT ON TABLE gen_config IS '代码生成器-生成配置表';
COMMENT ON TABLE gen_template IS '代码生成器-代码模板表';
COMMENT ON TABLE gen_history IS '代码生成器-生成历史表';

-- ============================================================
-- 2. 认证授权配置模块表(3 张表)
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_jwt_config (
  id                BIGINT       NOT NULL,
  issuer            VARCHAR(200) NOT NULL,
  secret_key        VARCHAR(500) NOT NULL,
  access_token_ttl  INTEGER      NOT NULL DEFAULT 3600,
  refresh_token_ttl INTEGER      NOT NULL DEFAULT 86400,
  algorithm         VARCHAR(20)  NOT NULL DEFAULT 'HS256',
  public_key        TEXT         DEFAULT NULL,
  private_key       TEXT         DEFAULT NULL,
  enabled           INTEGER      NOT NULL DEFAULT 1,
  tenant_id         VARCHAR(64)  NOT NULL,
  create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version           INTEGER      NOT NULL DEFAULT 0,
  deleted           INTEGER      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sys_jwt_config_tenant ON sys_jwt_config(tenant_id);

CREATE TABLE IF NOT EXISTS sys_session_config (
  id                   BIGINT      NOT NULL,
  max_sessions         INTEGER     NOT NULL DEFAULT 1,
  session_timeout      INTEGER     NOT NULL DEFAULT 1800,
  concurrent_control   INTEGER     NOT NULL DEFAULT 1,
  remember_me_enabled  INTEGER     NOT NULL DEFAULT 1,
  remember_me_ttl      INTEGER     NOT NULL DEFAULT 604800,
  enabled              INTEGER     NOT NULL DEFAULT 1,
  tenant_id            VARCHAR(64) NOT NULL,
  create_time          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version              INTEGER     NOT NULL DEFAULT 0,
  deleted              INTEGER     NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sys_session_config_tenant ON sys_session_config(tenant_id);

CREATE TABLE IF NOT EXISTS sys_security_policy (
  id                      BIGINT      NOT NULL,
  password_min_length     INTEGER     NOT NULL DEFAULT 8,
  password_require_upper  INTEGER     NOT NULL DEFAULT 1,
  password_require_lower  INTEGER     NOT NULL DEFAULT 1,
  password_require_digit  INTEGER     NOT NULL DEFAULT 1,
  password_require_special INTEGER    NOT NULL DEFAULT 1,
  password_expiry_days    INTEGER     DEFAULT NULL,
  max_login_attempts      INTEGER     NOT NULL DEFAULT 5,
  lockout_duration        INTEGER     NOT NULL DEFAULT 1800,
  ip_whitelist            TEXT        DEFAULT NULL,
  ip_blacklist            TEXT        DEFAULT NULL,
  enabled                 INTEGER     NOT NULL DEFAULT 1,
  tenant_id               VARCHAR(64) NOT NULL,
  create_time             TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time             TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version                 INTEGER     NOT NULL DEFAULT 0,
  deleted                 INTEGER     NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sys_security_policy_tenant ON sys_security_policy(tenant_id);

COMMENT ON TABLE sys_jwt_config IS '系统-JWT配置表';
COMMENT ON TABLE sys_session_config IS '系统-会话配置表';
COMMENT ON TABLE sys_security_policy IS '系统-安全策略表';

-- ============================================================
-- 3. SSO 单点登录模块表(3 张表)
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_sso_provider (
  id             BIGINT       NOT NULL,
  provider_name  VARCHAR(100) NOT NULL,
  provider_type  VARCHAR(20)  NOT NULL,
  config         TEXT         NOT NULL,
  entity_id      VARCHAR(200) DEFAULT NULL,
  metadata_url   VARCHAR(500) DEFAULT NULL,
  login_url      VARCHAR(500) NOT NULL,
  logout_url     VARCHAR(500) DEFAULT NULL,
  enabled        INTEGER      NOT NULL DEFAULT 1,
  priority       INTEGER      NOT NULL DEFAULT 0,
  tenant_id      VARCHAR(64)  NOT NULL,
  create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version        INTEGER      NOT NULL DEFAULT 0,
  deleted        INTEGER      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_sso_provider_name_tenant UNIQUE (provider_name, tenant_id, deleted)
);
CREATE INDEX IF NOT EXISTS idx_sys_sso_provider_tenant ON sys_sso_provider(tenant_id);

CREATE TABLE IF NOT EXISTS sys_sso_session (
  id            BIGINT       NOT NULL,
  provider_id   BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL,
  external_id   VARCHAR(200) NOT NULL,
  session_index VARCHAR(200) NOT NULL,
  assertion     TEXT         DEFAULT NULL,
  login_time    TIMESTAMP    NOT NULL,
  expire_time   TIMESTAMP    NOT NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  tenant_id     VARCHAR(64)  NOT NULL,
  create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version       INTEGER      NOT NULL DEFAULT 0,
  deleted       INTEGER      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_sso_session_index UNIQUE (session_index, deleted)
);
CREATE INDEX IF NOT EXISTS idx_sys_sso_session_provider ON sys_sso_session(provider_id);
CREATE INDEX IF NOT EXISTS idx_sys_sso_session_user ON sys_sso_session(user_id);
CREATE INDEX IF NOT EXISTS idx_sys_sso_session_tenant ON sys_sso_session(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sys_sso_session_expire ON sys_sso_session(expire_time);

CREATE TABLE IF NOT EXISTS sys_sso_audit_log (
  id            BIGINT       NOT NULL,
  provider_id   BIGINT       NOT NULL,
  user_id       BIGINT       DEFAULT NULL,
  external_id   VARCHAR(200) DEFAULT NULL,
  operation     VARCHAR(50)  NOT NULL,
  status        VARCHAR(20)  NOT NULL,
  ip_address    VARCHAR(50)  DEFAULT NULL,
  user_agent    VARCHAR(500) DEFAULT NULL,
  error_message TEXT         DEFAULT NULL,
  request_data  TEXT         DEFAULT NULL,
  tenant_id     VARCHAR(64)  NOT NULL,
  create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version       INTEGER      NOT NULL DEFAULT 0,
  deleted       INTEGER      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sys_sso_audit_provider ON sys_sso_audit_log(provider_id);
CREATE INDEX IF NOT EXISTS idx_sys_sso_audit_user ON sys_sso_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_sys_sso_audit_tenant ON sys_sso_audit_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sys_sso_audit_time ON sys_sso_audit_log(create_time);

COMMENT ON TABLE sys_sso_provider IS '系统-SSO提供商配置表';
COMMENT ON TABLE sys_sso_session IS '系统-SSO会话表';
COMMENT ON TABLE sys_sso_audit_log IS '系统-SSO审计日志表';

-- ============================================================
-- 4. 菜单和权限种子数据
-- ============================================================

DO $$
DECLARE
    v_admin_role_id BIGINT;
BEGIN
    SELECT id INTO v_admin_role_id FROM sys_role WHERE code = 'SUPER_ADMIN' OR id = 1 LIMIT 1;

    INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, deleted)
    VALUES (4000, 1000, '代码生成器', 1, 'generator', 'system/generator/index', 'system:generator:view', 'Code', 20, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
    ON CONFLICT (id) DO UPDATE SET
        menu_name = EXCLUDED.menu_name,
        parent_id = EXCLUDED.parent_id,
        path = EXCLUDED.path,
        component = EXCLUDED.component,
        perms = EXCLUDED.perms,
        icon = EXCLUDED.icon,
        sort = EXCLUDED.sort,
        update_time = CURRENT_TIMESTAMP;

    INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, deleted)
    VALUES (5000, 1000, '认证授权配置', 1, 'auth-config', 'system/auth-config/index', 'system:authconfig:view', 'Lock', 21, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
    ON CONFLICT (id) DO UPDATE SET
        menu_name = EXCLUDED.menu_name,
        parent_id = EXCLUDED.parent_id,
        path = EXCLUDED.path,
        component = EXCLUDED.component,
        perms = EXCLUDED.perms,
        icon = EXCLUDED.icon,
        sort = EXCLUDED.sort,
        update_time = CURRENT_TIMESTAMP;

    INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, deleted)
    VALUES (6000, 1000, 'SSO管理', 1, 'sso', 'system/sso/index', 'system:sso:view', 'Connection', 22, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
    ON CONFLICT (id) DO UPDATE SET
        menu_name = EXCLUDED.menu_name,
        parent_id = EXCLUDED.parent_id,
        path = EXCLUDED.path,
        component = EXCLUDED.component,
        perms = EXCLUDED.perms,
        icon = EXCLUDED.icon,
        sort = EXCLUDED.sort,
        update_time = CURRENT_TIMESTAMP;

    INSERT INTO sys_permission (id, name, code, description, type, status, create_time, update_time, deleted)
    VALUES
        (3000, '代码生成器页面', 'system:generator:view', '查看代码生成器页面', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3001, '数据源列表', 'system:generator:datasource:list', '查询数据源列表', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3002, '新增数据源', 'system:generator:datasource:add', '新增数据源', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3003, '编辑数据源', 'system:generator:datasource:edit', '编辑数据源', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3004, '删除数据源', 'system:generator:datasource:remove', '删除数据源', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3005, '测试连接', 'system:generator:datasource:test', '测试数据源连接', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3006, '查询表结构', 'system:generator:table:list', '查询数据库表结构', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3007, '生成配置列表', 'system:generator:config:list', '查询生成配置列表', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3008, '新增生成配置', 'system:generator:config:add', '新增生成配置', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3009, '编辑生成配置', 'system:generator:config:edit', '编辑生成配置', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3010, '删除生成配置', 'system:generator:config:remove', '删除生成配置', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3011, '执行代码生成', 'system:generator:generate', '执行代码生成', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3012, '生成历史列表', 'system:generator:history:list', '查询生成历史', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3100, '认证授权配置页面', 'system:authconfig:view', '查看认证授权配置页面', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3101, '查询JWT配置', 'system:authconfig:jwt:query', '查询JWT配置', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3102, '更新JWT配置', 'system:authconfig:jwt:update', '更新JWT配置', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3103, '查询会话配置', 'system:authconfig:session:query', '查询会话配置', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3104, '更新会话配置', 'system:authconfig:session:update', '更新会话配置', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3105, '查询安全策略', 'system:authconfig:security:query', '查询安全策略', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3106, '更新安全策略', 'system:authconfig:security:update', '更新安全策略', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3107, 'OAuth2客户端列表', 'system:authconfig:oauth2:list', '查询OAuth2客户端列表(复用)', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3108, '在线会话列表', 'system:authconfig:session:list', '查询在线会话列表(复用)', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3109, '强制登出', 'system:authconfig:session:kickout', '强制用户登出', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3110, '登录日志', 'system:authconfig:loginlog:list', '查询登录日志(复用)', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3111, '权限列表', 'system:authconfig:permission:list', '查询权限列表(复用)', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3112, '同步权限码', 'system:authconfig:permission:sync', '扫描并同步权限码', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3200, 'SSO管理页面', 'system:sso:view', '查看SSO管理页面', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3201, 'SSO提供商列表', 'system:sso:provider:list', '查询SSO提供商列表', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3202, '新增SSO提供商', 'system:sso:provider:add', '新增SSO提供商', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3203, '编辑SSO提供商', 'system:sso:provider:edit', '编辑SSO提供商', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3204, '删除SSO提供商', 'system:sso:provider:remove', '删除SSO提供商', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3205, '测试SSO连接', 'system:sso:provider:test', '测试SSO提供商连接', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3206, '获取SSO元数据', 'system:sso:provider:metadata', '获取SSO提供商元数据', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3207, 'SSO会话列表', 'system:sso:session:list', '查询SSO会话列表', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3208, '撤销SSO会话', 'system:sso:session:revoke', '撤销SSO会话', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3209, 'SSO审计日志', 'system:sso:auditlog:list', '查询SSO审计日志', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
        (3210, 'SSO统计数据', 'system:sso:statistics', '查询SSO统计数据', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
    ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name,
        description = EXCLUDED.description,
        status = 1,
        update_time = CURRENT_TIMESTAMP;

    IF v_admin_role_id IS NOT NULL THEN
        INSERT INTO sys_role_permission (id, role_id, permission_id)
        SELECT 410000 + p.id, v_admin_role_id, p.id
        FROM sys_permission p
        WHERE p.code IN (
            'system:generator:view', 'system:generator:datasource:list', 'system:generator:datasource:add',
            'system:generator:datasource:edit', 'system:generator:datasource:remove', 'system:generator:datasource:test',
            'system:generator:table:list', 'system:generator:config:list', 'system:generator:config:add',
            'system:generator:config:edit', 'system:generator:config:remove', 'system:generator:generate',
            'system:generator:history:list',
            'system:authconfig:view', 'system:authconfig:jwt:query', 'system:authconfig:jwt:update',
            'system:authconfig:session:query', 'system:authconfig:session:update', 'system:authconfig:security:query',
            'system:authconfig:security:update', 'system:authconfig:oauth2:list', 'system:authconfig:session:list',
            'system:authconfig:session:kickout', 'system:authconfig:loginlog:list', 'system:authconfig:permission:list',
            'system:authconfig:permission:sync',
            'system:sso:view', 'system:sso:provider:list', 'system:sso:provider:add',
            'system:sso:provider:edit', 'system:sso:provider:remove', 'system:sso:provider:test',
            'system:sso:provider:metadata', 'system:sso:session:list', 'system:sso:session:revoke',
            'system:sso:auditlog:list', 'system:sso:statistics'
        )
        AND p.status = 1
        AND p.deleted = 0
        ON CONFLICT (role_id, permission_id) DO NOTHING;
    END IF;
END $$;
