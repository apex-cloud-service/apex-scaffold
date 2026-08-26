-- ============================================================================
-- Apex Service - 集成测试 Schema 定义
-- ============================================================================
-- 说明: 本脚本用于 Testcontainers (MySQL 8) 的集成测试环境建表。
--       只包含集成测试需要的最小表集合，字段覆盖对应 PO 类的核心属性。
--       与生产 V1__init_schema.sql 的区别：
--         1. 使用 TIMESTAMP 替代 DATETIME（Testcontainers MySQL 8 兼容性）
--         2. 使用 CLOB 替代 LONGTEXT（H2/测试环境兼容性）
--         3. 使用 BOOLEAN 替代 TINYINT(1)（简化 Java 映射）
--         4. 仅包含集成测试实际依赖的 27 张通用表
--         5. 外键约束使用 ON DELETE CASCADE（测试环境数据清理）
--
-- 适用范围: 所有 *IntegrationTest 类，通过 Flyway 在测试 Database 上执行
-- 维护说明: 新增迁移建表或给现有表 ADD COLUMN 时，同步更新此文件；
--           LONGTEXT->CLOB、DATETIME->TIMESTAMP，约束/索引名需加表名前缀保证 H2 内唯一
-- ============================================================================

-- ============================================================================
-- 第一部分: 系统表与通用日志表
-- ============================================================================

-- 1.1 系统用户表 (sys_user) → UserPO
CREATE TABLE IF NOT EXISTS sys_user (
  id                    BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
  open_id               VARCHAR(100) COMMENT '微信OpenID（手机号注册用户可为NULL）',
  union_id              VARCHAR(100) COMMENT '微信UnionID',
  access_token          VARCHAR(512) COMMENT '微信AccessToken',
  refresh_token         VARCHAR(512) COMMENT '微信RefreshToken',
  token_expire_time     TIMESTAMP COMMENT 'Token过期时间',
  nick_name             VARCHAR(50) COMMENT '昵称',
  avatar_url            VARCHAR(512) COMMENT '头像URL',
  gender                INT COMMENT '性别（0-未知，1-男，2-女）',
  birthday              DATE COMMENT '生日',
  country               VARCHAR(64) COMMENT '国家',
  province              VARCHAR(64) COMMENT '省份',
  city                  VARCHAR(64) COMMENT '城市',
  language              VARCHAR(64) COMMENT '语言',
  phone                 VARCHAR(32) COMMENT '手机号',
  status                INT DEFAULT 1 COMMENT '状态（0-禁用，1-正常）',
  onboarding_status     VARCHAR(32) DEFAULT 'ACTIVE' NOT NULL COMMENT '引导状态',
  last_login_time       TIMESTAMP COMMENT '最后登录时间',
  last_login_ip         VARCHAR(64) COMMENT '最后登录IP',
  last_heartbeat_time   TIMESTAMP COMMENT '最后心跳时间（在线判定）',
  create_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  version               INT DEFAULT 0 COMMENT '版本号（乐观锁）',
  deleted               INT DEFAULT 0 COMMENT '逻辑删除（0-未删，1-已删）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_phone (phone)
);

-- 1.2 系统角色表 (sys_role) → RolePO
CREATE TABLE IF NOT EXISTS sys_role (
  id          BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
  name        VARCHAR(50) NOT NULL COMMENT '角色名称',
  code        VARCHAR(50) NOT NULL COMMENT '角色编码',
  status      INT DEFAULT 1 COMMENT '状态（0-禁用，1-正常）',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  version     INT DEFAULT 0 COMMENT '版本号（乐观锁）',
  deleted     INT DEFAULT 0 COMMENT '逻辑删除（0-未删，1-已删）',
  PRIMARY KEY (id)
);

-- 1.3 系统权限表 (sys_permission) → PermissionPO
CREATE TABLE IF NOT EXISTS sys_permission (
  id          BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
  name        VARCHAR(50) NOT NULL COMMENT '权限名称',
  code        VARCHAR(50) NOT NULL COMMENT '权限编码',
  status      INT DEFAULT 1 COMMENT '状态（0-禁用，1-正常）',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  version     INT DEFAULT 0 COMMENT '版本号（乐观锁）',
  deleted     INT DEFAULT 0 COMMENT '逻辑删除（0-未删，1-已删）',
  PRIMARY KEY (id)
);

-- 1.4 用户角色关联表 (sys_user_role) → UserRolePO
CREATE TABLE IF NOT EXISTS sys_user_role (
  id          BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
  user_id     BIGINT NOT NULL COMMENT '用户ID',
  role_id     BIGINT NOT NULL COMMENT '角色ID',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  version     INT DEFAULT 0 COMMENT '版本号（乐观锁）',
  deleted     INT DEFAULT 0 COMMENT '逻辑删除（0-未删，1-已删）',
  PRIMARY KEY (id)
);

-- 1.5 角色权限关联表 (sys_role_permission) → RolePermissionPO
CREATE TABLE IF NOT EXISTS sys_role_permission (
  id            BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
  role_id       BIGINT NOT NULL COMMENT '角色ID',
  permission_id BIGINT NOT NULL COMMENT '权限ID',
  create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  version       INT DEFAULT 0 COMMENT '版本号（乐观锁）',
  deleted       INT DEFAULT 0 COMMENT '逻辑删除（0-未删，1-已删）',
  PRIMARY KEY (id)
);

-- 记录对外部 HTTP 服务的每次调用详情，支撑问题排查和性能监控
CREATE TABLE IF NOT EXISTS sys_api_call_log (
    id               BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    service_name     VARCHAR(100) NOT NULL COMMENT '目标服务名称（wechat/chuanglan/baidu-image/face-analysis 等）',
    method           VARCHAR(10)  NOT NULL COMMENT 'HTTP请求方法（GET/POST/PUT/DELETE）',
    path             VARCHAR(500) COMMENT '请求路径（不含host）',
    url              VARCHAR(2000) COMMENT '完整请求URL（含query参数）',
    request_headers  TEXT COMMENT '请求头（JSON格式，敏感字段已脱敏）',
    request_body     TEXT COMMENT '请求体（超过限制时截断）',
    response_status  VARCHAR(10) COMMENT 'HTTP响应状态码',
    response_headers TEXT COMMENT '响应头（JSON格式）',
    response_body    TEXT COMMENT '响应体（超过限制时截断）',
    execution_time   BIGINT COMMENT '执行耗时（毫秒）',
    status           INT          NOT NULL DEFAULT 0 COMMENT '调用状态（0-成功，1-客户端错误，2-服务端错误，3-网络异常）',
    error_msg        TEXT COMMENT '错误信息',
    trace_id         VARCHAR(100) COMMENT '链路追踪ID',
    request_id       VARCHAR(64) COMMENT '请求唯一标识UUID（V42）',
    call_time        TIMESTAMP COMMENT '调用发起时间（V33）',
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    version          INT          NOT NULL DEFAULT 0 COMMENT '版本号（保留字段）',
    deleted          INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除（0-未删，1-已删）',
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_acl_service_name ON sys_api_call_log (service_name);
CREATE INDEX IF NOT EXISTS idx_acl_request_id ON sys_api_call_log (request_id);
CREATE INDEX IF NOT EXISTS idx_acl_trace_id ON sys_api_call_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_acl_create_time ON sys_api_call_log (create_time);

-- sys_domain_event_outbox
CREATE TABLE IF NOT EXISTS sys_domain_event_outbox (
  attempts INT NOT NULL DEFAULT 0 COMMENT '已失败次数',
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  delivered_at TIMESTAMP(6) DEFAULT NULL COMMENT '完成时间',
  event_class VARCHAR(255) NOT NULL COMMENT '领域事件 Java 类型',
  event_id VARCHAR(64) NOT NULL COMMENT '领域事件唯一标识',
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  last_error VARCHAR(1000) DEFAULT NULL COMMENT '最近一次错误摘要',
  locked_at TIMESTAMP(6) DEFAULT NULL COMMENT '领取时间',
  locked_by VARCHAR(64) DEFAULT NULL COMMENT '投递实例令牌',
  next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次投递时间',
  payload CLOB NOT NULL COMMENT '事件 CLOB 负载',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DELIVERED/DEAD',
  PRIMARY KEY (id),
  UNIQUE KEY sys_domain_event_outbox_uk_outbox_event_id (event_id)
);
CREATE INDEX IF NOT EXISTS sys_domain_event_outbox_idx_outbox_dispatch ON sys_domain_event_outbox (status, next_attempt_at, id);
CREATE INDEX IF NOT EXISTS sys_domain_event_outbox_idx_outbox_locked ON sys_domain_event_outbox (status, locked_at);

-- sys_file
CREATE TABLE IF NOT EXISTS sys_file (
  bucket_name VARCHAR(100) DEFAULT NULL COMMENT '存储桶名称',
  content_type VARCHAR(100) DEFAULT NULL COMMENT '内容类型(MIME)',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  file_name VARCHAR(255) DEFAULT NULL COMMENT '文件名称',
  file_path VARCHAR(500) DEFAULT NULL COMMENT '文件路径',
  file_size BIGINT DEFAULT NULL COMMENT '文件大小(字节)',
  file_type VARCHAR(100) DEFAULT NULL COMMENT '文件类型',
  file_url VARCHAR(500) DEFAULT NULL COMMENT '文件访问URL',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  md5 VARCHAR(32) DEFAULT NULL COMMENT '文件MD5',
  original_name VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  user_id BIGINT DEFAULT NULL COMMENT '上传用户ID',
  version INT(10) NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS sys_file_idx_user_id ON sys_file (user_id);
CREATE INDEX IF NOT EXISTS sys_file_idx_md5 ON sys_file (md5);

-- sys_operation_log
CREATE TABLE IF NOT EXISTS sys_operation_log (
  browser VARCHAR(100) DEFAULT NULL COMMENT '浏览器类型',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  description VARCHAR(255) DEFAULT NULL COMMENT '操作描述',
  device_type VARCHAR(50) DEFAULT NULL COMMENT '设备类型',
  error_msg TEXT DEFAULT NULL COMMENT '错误信息',
  execution_time BIGINT DEFAULT NULL COMMENT '执行时间(毫秒)',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  ip VARCHAR(50) DEFAULT NULL COMMENT '操作IP',
  location VARCHAR(100) DEFAULT NULL COMMENT '操作地点',
  module VARCHAR(100) NOT NULL COMMENT '操作模块',
  operation_time TIMESTAMP DEFAULT NULL COMMENT '操作时间',
  operation_type VARCHAR(50) DEFAULT NULL COMMENT '操作类型',
  os VARCHAR(100) DEFAULT NULL COMMENT '操作系统',
  request_method VARCHAR(10) DEFAULT NULL COMMENT '请求方法',
  request_params TEXT DEFAULT NULL COMMENT '请求参数',
  request_uri VARCHAR(500) DEFAULT NULL COMMENT '请求URI',
  response_result TEXT DEFAULT NULL COMMENT '响应结果',
  status INT(1) DEFAULT NULL COMMENT '操作状态(0-成功,1-失败)',
  trace_id VARCHAR(100) DEFAULT NULL COMMENT '追踪ID',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  user_id BIGINT DEFAULT NULL COMMENT '操作用户ID',
  user_name VARCHAR(50) DEFAULT NULL COMMENT '操作用户名',
  version INT(10) NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS sys_operation_log_idx_module ON sys_operation_log (module);
CREATE INDEX IF NOT EXISTS sys_operation_log_idx_user_id ON sys_operation_log (user_id);
CREATE INDEX IF NOT EXISTS sys_operation_log_idx_operation_type ON sys_operation_log (operation_type);
CREATE INDEX IF NOT EXISTS sys_operation_log_idx_status ON sys_operation_log (status);
CREATE INDEX IF NOT EXISTS sys_operation_log_idx_trace_id ON sys_operation_log (trace_id);

-- 1.6 账号注销异步清理任务表 (V70) → AccountDeletionTaskPO
CREATE TABLE IF NOT EXISTS sys_account_deletion_task (
  id           BIGINT NOT NULL COMMENT '清理任务主键',
  user_id      BIGINT NOT NULL COMMENT '已注销账号用户ID',
  request_id   VARCHAR(128) NOT NULL COMMENT '注销请求标识',
  status       VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLEANING/COMPLETED/FAILED',
  attempts     INT NOT NULL DEFAULT 0 COMMENT '清理尝试次数',
  last_error   VARCHAR(500) DEFAULT NULL COMMENT '最近一次脱敏错误摘要',
  locked_at    TIMESTAMP DEFAULT NULL COMMENT '任务领取时间',
  locked_by    VARCHAR(128) DEFAULT NULL COMMENT '任务领取实例',
  completed_at TIMESTAMP DEFAULT NULL COMMENT '清理完成时间',
  create_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version      INT NOT NULL DEFAULT 0,
  deleted      INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY sys_account_deletion_task_uk_request (request_id)
);
CREATE INDEX IF NOT EXISTS sys_account_deletion_task_idx_status
  ON sys_account_deletion_task (status, locked_at, id);
CREATE INDEX IF NOT EXISTS sys_account_deletion_task_idx_user
  ON sys_account_deletion_task (user_id, status);

-- 1.11 操作审计日志表 (audit_log) → AuditLogPO
CREATE TABLE IF NOT EXISTS audit_log (
  id             BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
  operator_id    VARCHAR(64) COMMENT '操作人ID',
  operator_name  VARCHAR(100) COMMENT '操作人姓名',
  operator_ip    VARCHAR(50) COMMENT '操作人IP地址',
  module         VARCHAR(100) COMMENT '操作模块',
  operation_type VARCHAR(20) COMMENT '操作类型(CREATE/UPDATE/DELETE/QUERY等)',
  description    VARCHAR(500) COMMENT '操作描述',
  method         VARCHAR(200) COMMENT '请求方法(全限定名)',
  request_url    VARCHAR(500) COMMENT '请求URL',
  request_params CLOB COMMENT '请求参数(JSON格式)',
  response_result CLOB COMMENT '响应结果(JSON格式)',
  duration       BIGINT COMMENT '执行时长(毫秒)',
  status         VARCHAR(20) COMMENT '操作状态(SUCCESS/FAIL)',
  error_msg      VARCHAR(1000) COMMENT '错误信息',
  business_id    VARCHAR(100) COMMENT '业务ID(关联具体业务数据)',
  tenant_id      VARCHAR(64) COMMENT '租户ID(多租户场景)',
  create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted        INT DEFAULT 0 COMMENT '逻辑删除（0-未删，1-已删）',
  PRIMARY KEY (id),
  KEY idx_audit_log_operator_id (operator_id),
  KEY idx_audit_log_module (module),
  KEY idx_audit_log_operation_type (operation_type),
  KEY idx_audit_log_create_time (create_time),
  KEY idx_audit_log_business_id (business_id)
);
-- ============================================================================
-- 第二部分: 通用业务表 (biz_*)
-- ============================================================================

-- biz_blacklist
CREATE TABLE IF NOT EXISTS biz_blacklist (
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  enabled INT(1) NOT NULL COMMENT '是否生效(0-取消拉黑,1-拉黑)',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  target_user_id BIGINT NOT NULL COMMENT '被拉黑用户ID',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  user_id BIGINT NOT NULL COMMENT '拉黑者用户ID',
  version INT(10) NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (id),
  UNIQUE KEY biz_blacklist_uk_user_target (user_id, target_user_id)
);

-- biz_credit_account
CREATE TABLE IF NOT EXISTS biz_credit_account (
  balance BIGINT NOT NULL COMMENT '余额(最小单位)',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  daily_favorite_date DATE DEFAULT NULL COMMENT '收藏积分统计日期',
  daily_favorite_total BIGINT DEFAULT 0 COMMENT '当日收藏累计积分(V2: 日上限200)',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  today_login_bonus TINYINT(1) DEFAULT 0 COMMENT '今日登录奖励已领取',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  version INT(10) NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (id),
  UNIQUE KEY biz_credit_account_uk_user_id (user_id)
);

-- biz_credit_tx
CREATE TABLE IF NOT EXISTS biz_credit_tx (
  amount BIGINT NOT NULL COMMENT '变动金额(最小单位)',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  direction VARCHAR(8) NOT NULL COMMENT '方向(IN-收入/OUT-支出)',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  reason VARCHAR(32) NOT NULL COMMENT '变动原因',
  ref_id BIGINT DEFAULT NULL COMMENT '关联ID',
  ref_type VARCHAR(32) DEFAULT NULL COMMENT '关联类型',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  version INT(10) NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS biz_credit_tx_idx_user_id ON biz_credit_tx (user_id);
CREATE INDEX IF NOT EXISTS biz_credit_tx_idx_direction ON biz_credit_tx (direction);
CREATE INDEX IF NOT EXISTS biz_credit_tx_idx_ref_type_id ON biz_credit_tx (ref_type, ref_id);

-- biz_policy_version
CREATE TABLE IF NOT EXISTS biz_policy_version (
  content_url VARCHAR(512) NOT NULL COMMENT '内容URL',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  effective_at VARCHAR(32) NOT NULL COMMENT '生效日期(字符串)',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  policy_type VARCHAR(32) NOT NULL COMMENT '策略类型(USER_AGREEMENT/PRIVACY_POLICY)',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  version VARCHAR(64) NOT NULL COMMENT '版本号(业务字段)',
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS biz_policy_version_idx_policy_type ON biz_policy_version (policy_type);

-- biz_quiz_question
CREATE TABLE IF NOT EXISTS biz_quiz_question (
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  dimension VARCHAR(32) NOT NULL COMMENT '评分维度',
  enabled INT(1) DEFAULT NULL COMMENT '是否启用(0-禁用,1-启用)',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  k_factor INT DEFAULT 32 COMMENT 'ELO K系数',
  options_json CLOB NOT NULL COMMENT '选项(CLOB)',
  question_id VARCHAR(32) NOT NULL COMMENT '题目标识',
  question_text VARCHAR(512) NOT NULL COMMENT '题目内容',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  version INT(10) NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (id),
  UNIQUE KEY biz_quiz_question_uk_question_id (question_id)
);
CREATE INDEX IF NOT EXISTS biz_quiz_question_idx_dimension ON biz_quiz_question (dimension);

-- biz_report
CREATE TABLE IF NOT EXISTS biz_report (
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  created_at TIMESTAMP NOT NULL COMMENT '报告生成时间',
  data_json CLOB DEFAULT NULL COMMENT '报告数据(CLOB)',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  owner_user_id BIGINT NOT NULL COMMENT '归属用户ID',
  photo_id BIGINT DEFAULT NULL COMMENT '关联照片ID',
  progress INT NOT NULL COMMENT '进度(0-100)',
  report_type VARCHAR(32) NOT NULL COMMENT '报告类型(FIRST_IMPRESSION/AESTHETIC_DNA/ANIMAL)',
  status VARCHAR(16) NOT NULL COMMENT '状态(PENDING/RUNNING/DONE/FAILED)',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  version INT(10) NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS biz_report_idx_owner_user_id ON biz_report (owner_user_id);
CREATE INDEX IF NOT EXISTS biz_report_idx_report_type ON biz_report (report_type);
CREATE INDEX IF NOT EXISTS biz_report_idx_status ON biz_report (status);

-- biz_report_ticket
CREATE TABLE IF NOT EXISTS biz_report_ticket (
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  evidence CLOB DEFAULT NULL COMMENT '证据(CLOB)',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  reason VARCHAR(64) NOT NULL COMMENT '举报原因',
  reporter_user_id BIGINT NOT NULL COMMENT '举报者用户ID',
  result VARCHAR(64) DEFAULT NULL COMMENT '处理结果',
  status VARCHAR(16) NOT NULL COMMENT '状态(PENDING/PROCESSING/RESOLVED/REJECTED)',
  target_id BIGINT NOT NULL COMMENT '举报目标ID',
  target_type VARCHAR(16) NOT NULL COMMENT '举报目标类型(USER/PHOTO/MESSAGE)',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  version INT(10) NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS biz_report_ticket_idx_reporter_user_id ON biz_report_ticket (reporter_user_id);
CREATE INDEX IF NOT EXISTS biz_report_ticket_idx_target_type_id ON biz_report_ticket (target_type, target_id);
CREATE INDEX IF NOT EXISTS biz_report_ticket_idx_status ON biz_report_ticket (status);

-- biz_sample
CREATE TABLE IF NOT EXISTS biz_sample (
  bucket_name VARCHAR(64) NOT NULL COMMENT '存储桶名称',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  crop_h INT DEFAULT NULL COMMENT '裁切高度',
  crop_w INT DEFAULT NULL COMMENT '裁切宽度',
  crop_x INT DEFAULT NULL COMMENT '裁切坐标X',
  crop_y INT DEFAULT NULL COMMENT '裁切坐标Y',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  face_ratio DOUBLE DEFAULT NULL COMMENT '人脸占画面比例,用于计算PORTRAIT_CLOSE/HALF/FULL(P0-3修复)',
  face_type VARCHAR(16) DEFAULT NULL COMMENT '脸型分类(CLOSE_UP/ATMOSPHERE)',
  file_name VARCHAR(256) NOT NULL COMMENT '对象名(文件名)',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  mode VARCHAR(16) NOT NULL COMMENT '模式(TWO_CHOICE/FOUR_PICK_TWO)',
  photo_id BIGINT DEFAULT NULL COMMENT '关联照片ID',
  pitch DOUBLE DEFAULT NULL COMMENT '俯仰角,用于计算FRONTAL/NON_FRONTAL(P0-3修复)',
  roll DOUBLE DEFAULT NULL COMMENT '翻滚角,用于计算FRONTAL/NON_FRONTAL(P0-3修复)',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  user_id BIGINT NOT NULL COMMENT '采样用户ID',
  version INT(10) NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  yaw DOUBLE DEFAULT NULL COMMENT '偏航角,用于计算FRONTAL/NON_FRONTAL(P0-3修复)',
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS biz_sample_idx_user_id ON biz_sample (user_id);
CREATE INDEX IF NOT EXISTS biz_sample_idx_photo_id ON biz_sample (photo_id);
CREATE INDEX IF NOT EXISTS biz_sample_idx_mode ON biz_sample (mode);

-- biz_subscription
CREATE TABLE IF NOT EXISTS biz_subscription (
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  end_at TIMESTAMP NOT NULL COMMENT '结束时间',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  level VARCHAR(16) NOT NULL COMMENT '会员等级(VIP/SVIP)',
  start_at TIMESTAMP NOT NULL COMMENT '开始时间',
  status VARCHAR(16) NOT NULL COMMENT '状态(ACTIVE/EXPIRED/CANCELED)',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  version INT(10) NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS biz_subscription_idx_user_id ON biz_subscription (user_id);
CREATE INDEX IF NOT EXISTS biz_subscription_idx_status ON biz_subscription (status);

-- biz_system_message
CREATE TABLE IF NOT EXISTS biz_system_message (
  content VARCHAR(512) NOT NULL COMMENT '文案内容',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  msg_type VARCHAR(32) NOT NULL COMMENT '消息类型',
  read_at TIMESTAMP DEFAULT NULL COMMENT '阅读时间',
  ref_id BIGINT DEFAULT NULL COMMENT '关联ID',
  ref_type VARCHAR(32) DEFAULT NULL COMMENT '关联类型',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  user_id BIGINT NOT NULL COMMENT '接收用户ID',
  version INT(10) NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS biz_system_message_idx_user_id ON biz_system_message (user_id);
CREATE INDEX IF NOT EXISTS biz_system_message_idx_msg_type ON biz_system_message (msg_type);
CREATE INDEX IF NOT EXISTS biz_system_message_idx_user_read ON biz_system_message (user_id, read_at);

-- biz_user_compliance
CREATE TABLE IF NOT EXISTS biz_user_compliance (
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  teen_mode INT(1) NOT NULL COMMENT '青少年模式(0-关闭,1-开启)',
  teen_mode_set_at TIMESTAMP DEFAULT NULL COMMENT '青少年模式设置时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  version INT(10) NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (id),
  UNIQUE KEY biz_user_compliance_uk_user_id (user_id)
);

-- biz_user_consent
CREATE TABLE IF NOT EXISTS biz_user_consent (
  agreed INT(1) NOT NULL COMMENT '是否同意(0-否,1-是)',
  agreed_at TIMESTAMP NOT NULL COMMENT '同意时间',
  channel VARCHAR(32) DEFAULT NULL COMMENT '渠道',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  device_id VARCHAR(128) DEFAULT NULL COMMENT '设备ID摘要',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  policy_type VARCHAR(32) NOT NULL COMMENT '策略类型(USER_AGREEMENT/PRIVACY_POLICY)',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  user_id BIGINT DEFAULT NULL COMMENT '用户ID',
  version VARCHAR(64) NOT NULL COMMENT '同意版本号(业务字段)',
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS biz_user_consent_idx_user_id ON biz_user_consent (user_id);

-- biz_user_profile
CREATE TABLE IF NOT EXISTS biz_user_profile (
  bio VARCHAR(512) DEFAULT NULL COMMENT '个人简介',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  dating_goal VARCHAR(64) DEFAULT NULL COMMENT '交友目的',
  deleted INT(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  height_cm INT DEFAULT NULL COMMENT '身高(厘米)',
  id BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  job VARCHAR(128) DEFAULT NULL COMMENT '职业',
  mbti VARCHAR(8) DEFAULT NULL COMMENT 'MBTI人格类型',
  school VARCHAR(128) DEFAULT NULL COMMENT '学校',
  social_user INT(1) NOT NULL COMMENT '是否社交用户(0-否,1-是)',
  social_user_at TIMESTAMP DEFAULT NULL COMMENT '成为社交用户时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  version INT(10) NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  PRIMARY KEY (id),
  UNIQUE KEY biz_user_profile_uk_user_id (user_id)
);

-- 第一期后台审核不可变业务审计 (V71)
CREATE TABLE IF NOT EXISTS biz_admin_review_audit (
  id BIGINT NOT NULL,
  aggregate_type VARCHAR(32) NOT NULL,
  aggregate_id VARCHAR(128) NOT NULL,
  action VARCHAR(32) NOT NULL,
  actor_id VARCHAR(128) NOT NULL,
  before_state VARCHAR(32) DEFAULT NULL,
  after_state VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) DEFAULT NULL,
  comment VARCHAR(2048) DEFAULT NULL,
  trace_id VARCHAR(100) DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_admin_review_audit_aggregate
  ON biz_admin_review_audit (aggregate_type, aggregate_id, created_at);
CREATE INDEX IF NOT EXISTS idx_admin_review_audit_actor
  ON biz_admin_review_audit (actor_id, created_at);

-- biz_content_moderation_review
CREATE TABLE IF NOT EXISTS biz_content_moderation_review (
  id BIGINT NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  user_id BIGINT NOT NULL,
  content_text CLOB NOT NULL,
  media_json CLOB DEFAULT NULL,
  provider VARCHAR(32) NOT NULL DEFAULT '',
  provider_request_id VARCHAR(64) DEFAULT NULL,
  findings_json CLOB DEFAULT NULL,
  content_hash CHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  reviewer_open_id VARCHAR(64) DEFAULT NULL,
  review_comment VARCHAR(512) DEFAULT NULL,
  claimed_at TIMESTAMP DEFAULT NULL,
  decided_at TIMESTAMP DEFAULT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version INT NOT NULL DEFAULT 0,
  deleted INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_content_moderation_hash (user_id, source_type, content_hash),
  KEY idx_content_moderation_status (status, id),
  KEY idx_content_moderation_reviewer (reviewer_open_id, status)
);

-- 2.16 后台审核原因配置表 (biz_admin_review_reason) → ReviewReason
CREATE TABLE IF NOT EXISTS biz_admin_review_reason (
  id              BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
  review_type     VARCHAR(32) NOT NULL COMMENT '审核类型(PHOTO/INTEREST)',
  code            VARCHAR(64) NOT NULL COMMENT '原因编码',
  label           VARCHAR(128) NOT NULL COMMENT '原因名称',
  description     VARCHAR(512) COMMENT '原因说明',
  enabled         INT NOT NULL DEFAULT 1 COMMENT '是否启用(0-否,1-是)',
  require_comment INT NOT NULL DEFAULT 0 COMMENT '是否强制填写备注(0-否,1-是)',
  sort_order      INT NOT NULL DEFAULT 0 COMMENT '排序值(越小越靠前)',
  create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  version         INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  deleted         INT NOT NULL DEFAULT 0 COMMENT '逻辑删除（0-未删，1-已删）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_review_reason (review_type, code),
  KEY idx_admin_review_reason_enabled (review_type, enabled, sort_order)
);
