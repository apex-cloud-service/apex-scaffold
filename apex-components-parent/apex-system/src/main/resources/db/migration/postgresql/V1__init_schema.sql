-- 脚本用途：建立 Apex Scaffold 通用数据库的首版完整结构基线。
-- 业务背景：为认证授权、系统管理、审计监控及通用业务能力提供统一持久化基础。
-- 前置依赖：需预先创建并选中目标数据库，执行账号须具备建表、建索引权限。
-- 影响范围：创建通用业务表、系统表、关联表、索引及约束，不负责创建数据库实例。
-- 幂等性：建表语句以 IF NOT EXISTS 降低重复执行风险，但不会校正已存在表的结构差异。
-- PostgreSQL/Flyway 兼容性：面向 PostgreSQL 14+，由 Flyway 作为 V1 基线顺序执行。
-- 数据风险：既有同名但结构不一致的表会被保留，可能使后续迁移或应用映射失败。
-- 运维注意：首次部署前确认字符集、排序规则、时区和账号权限，并在迁移后核对表及索引。
-- 迁移元信息：版本 V1；执行阶段为全新数据库首次初始化，必须先于 V2-V15。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- V1: 通用脚手架初始化基线
-- ================================================================
-- 用途: Apex Scaffold 通用数据库初始化脚本，仅包含通用能力表。
-- 背景: v2.0 通用化重构后重建干净迁移基线，业务表已全部移除。
-- 影响: 新数据库首次部署时创建全部通用表结构、索引和约束。
-- ================================================================


-- biz_credit_tx

CREATE TABLE IF NOT EXISTS biz_credit_tx (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  direction   VARCHAR(8)   NOT NULL,
  amount      BIGINT       NOT NULL,
  reason      VARCHAR(32)  NOT NULL,
  ref_type    VARCHAR(32)  DEFAULT NULL,
  ref_id      BIGINT       DEFAULT NULL,
  create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version     INTEGER      NOT NULL DEFAULT 0,
  deleted     INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
-- 用户ID索引，用于查询某用户的所有交易流水
  -- 交易方向索引，用于按收入/支出分类统计流水
  -- 关联业务类型+ID联合索引，用于追溯具体业务场景（如某次投票的奖励流水）
);

-- sys_operation_log

CREATE TABLE IF NOT EXISTS sys_operation_log (
  id              BIGINT       NOT NULL,
  module          VARCHAR(100) NOT NULL,
  operation_type  VARCHAR(50)  DEFAULT NULL,
  description     VARCHAR(255) DEFAULT NULL,
  request_method  VARCHAR(10)  DEFAULT NULL,
  request_uri     VARCHAR(500) DEFAULT NULL,
  request_params  TEXT         DEFAULT NULL,
  response_result TEXT         DEFAULT NULL,
  status          INTEGER       DEFAULT NULL,
  error_msg       TEXT         DEFAULT NULL,
  execution_time  BIGINT       DEFAULT NULL,
  ip              VARCHAR(50)  DEFAULT NULL,
  location        VARCHAR(100) DEFAULT NULL,
  user_id         BIGINT       DEFAULT NULL,
  user_name       VARCHAR(50)  DEFAULT NULL,
  operation_time  TIMESTAMP     DEFAULT NULL,
  device_type     VARCHAR(50)  DEFAULT NULL,
  browser         VARCHAR(100) DEFAULT NULL,
  os              VARCHAR(100) DEFAULT NULL,
  trace_id        VARCHAR(100) DEFAULT NULL,
  create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version         INTEGER      NOT NULL DEFAULT 0,
  deleted         INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
-- 模块索引，用于按模块查询操作日志，支持后台审计按功能模块筛选
  -- 用户ID索引，用于查询指定用户的所有操作记录
  -- 操作类型索引，用于按操作类型（如登录、修改、删除等）筛选日志
  -- 状态索引，用于筛选成功或失败的操作日志
  -- 追踪ID索引，用于分布式链路追踪，将同一请求链路的日志关联
);

-- biz_sample

CREATE TABLE IF NOT EXISTS biz_sample (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  photo_id    BIGINT       DEFAULT NULL,
  bucket_name VARCHAR(64)  NOT NULL,
  file_name   VARCHAR(256) NOT NULL,
  mode        VARCHAR(16)  NOT NULL,
  face_type   VARCHAR(16)  DEFAULT NULL,
  crop_x      INT          DEFAULT NULL,
  crop_y      INT          DEFAULT NULL,
  crop_w      INT          DEFAULT NULL,
  crop_h      INT          DEFAULT NULL,
  pitch       DOUBLE PRECISION       DEFAULT NULL,
  yaw         DOUBLE PRECISION       DEFAULT NULL,
  roll        DOUBLE PRECISION       DEFAULT NULL,
  face_ratio  DOUBLE PRECISION       DEFAULT NULL,
  create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version     INTEGER      NOT NULL DEFAULT 0,
  deleted     INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
-- 用户ID索引，用于查询某用户的所有采样记录
  -- 照片ID索引，用于关联查询照片对应的采样数据
  -- 对局模式索引，用于按TWO_CHOICE/FOUR_PICK_TWO等模式筛选采样数据
);

-- biz_system_message

CREATE TABLE IF NOT EXISTS biz_system_message (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  msg_type    VARCHAR(32)  NOT NULL,
  content     VARCHAR(512) NOT NULL,
  ref_type    VARCHAR(32)  DEFAULT NULL,
  ref_id      BIGINT       DEFAULT NULL,
  read_at     TIMESTAMP     DEFAULT NULL,
  create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version     INTEGER      NOT NULL DEFAULT 0,
  deleted     INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
-- 接收用户ID索引，用于查询某用户的所有系统消息
  -- 消息类型索引，用于按类型筛选系统通知
  -- 用户+已读时间联合索引，用于查询未读消息列表
);

-- sys_user_role

CREATE TABLE IF NOT EXISTS sys_user_role (
  id          BIGINT   NOT NULL,
  user_id     BIGINT   NOT NULL,
  role_id     BIGINT   NOT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version     INTEGER  NOT NULL DEFAULT 0,
  deleted     INTEGER   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 用户角色联合唯一索引，防止同一用户重复授权同一角色
  CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
-- 角色ID索引，用于根据角色查询其下所有用户列表
);

-- audit_log

CREATE TABLE IF NOT EXISTS audit_log (
  id            BIGINT       NOT NULL,
  operator_id   VARCHAR(64)  DEFAULT NULL,
  operator_name VARCHAR(50)  DEFAULT NULL,
  operator_ip   VARCHAR(50)  DEFAULT NULL,
  module        VARCHAR(50)  DEFAULT NULL,
  operation_type VARCHAR(50) DEFAULT NULL,
  description   VARCHAR(255) DEFAULT NULL,
  method        VARCHAR(100) DEFAULT NULL,
  request_url   VARCHAR(255) DEFAULT NULL,
  request_params TEXT    DEFAULT NULL,
  response_result TEXT   DEFAULT NULL,
  duration      BIGINT       DEFAULT NULL,
  status        VARCHAR(20)  DEFAULT NULL,
  error_msg     TEXT         DEFAULT NULL,
  business_id   VARCHAR(64)  DEFAULT NULL,
  tenant_id     VARCHAR(64)  DEFAULT NULL,
  create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted       INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_biz_credit_tx_idx_user_id ON biz_credit_tx (user_id);
CREATE INDEX IF NOT EXISTS idx_biz_credit_tx_idx_direction ON biz_credit_tx (direction);
CREATE INDEX IF NOT EXISTS idx_biz_credit_tx_idx_ref_type_id ON biz_credit_tx (ref_type, ref_id);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_idx_module ON sys_operation_log (module);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_idx_user_id ON sys_operation_log (user_id);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_idx_operation_type ON sys_operation_log (operation_type);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_idx_status ON sys_operation_log (status);
CREATE INDEX IF NOT EXISTS idx_sys_operation_log_idx_trace_id ON sys_operation_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_biz_sample_idx_user_id ON biz_sample (user_id);
CREATE INDEX IF NOT EXISTS idx_biz_sample_idx_photo_id ON biz_sample (photo_id);
CREATE INDEX IF NOT EXISTS idx_biz_sample_idx_mode ON biz_sample (mode);
CREATE INDEX IF NOT EXISTS idx_biz_system_message_idx_user_id ON biz_system_message (user_id);
CREATE INDEX IF NOT EXISTS idx_biz_system_message_idx_msg_type ON biz_system_message (msg_type);
CREATE INDEX IF NOT EXISTS idx_biz_system_message_idx_user_read ON biz_system_message (user_id, read_at);
CREATE INDEX IF NOT EXISTS idx_sys_user_role_idx_role_id ON sys_user_role (role_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_idx_operator_id ON audit_log (operator_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_idx_module ON audit_log (module);
CREATE INDEX IF NOT EXISTS idx_audit_log_idx_create_time ON audit_log (create_time);

-- sys_account_deletion_task

CREATE TABLE IF NOT EXISTS sys_account_deletion_task (
  id           BIGINT       NOT NULL,
  user_id      BIGINT       NOT NULL,
  request_id   VARCHAR(128) NOT NULL,
  status       VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
  attempts     INT          NOT NULL DEFAULT 0,
  last_error   VARCHAR(500) DEFAULT NULL,
  locked_at    TIMESTAMP     DEFAULT NULL,
  locked_by    VARCHAR(128) DEFAULT NULL,
  completed_at TIMESTAMP     DEFAULT NULL,
  create_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version      INT          NOT NULL DEFAULT 0,
  deleted      INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_account_deletion_request UNIQUE (request_id)
);

-- biz_credit_account

CREATE TABLE IF NOT EXISTS biz_credit_account (
  id                  BIGINT   NOT NULL,
  user_id             BIGINT   NOT NULL,
  balance             BIGINT   NOT NULL,
  today_login_bonus   SMALLINT DEFAULT 0,
  daily_favorite_total BIGINT   DEFAULT 0,
  daily_favorite_date DATE      DEFAULT NULL,
  create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version             INTEGER  NOT NULL DEFAULT 0,
  deleted             INTEGER   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 用户ID唯一索引，每个用户只有一个算力账户，用于快速查询和更新余额
  CONSTRAINT uk_user_id UNIQUE (user_id)
);

-- biz_user_consent

CREATE TABLE IF NOT EXISTS biz_user_consent (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       DEFAULT NULL,
  policy_type VARCHAR(32)  NOT NULL,
  version     VARCHAR(64)  NOT NULL,
  agreed      INTEGER       NOT NULL,
  agreed_at   TIMESTAMP     NOT NULL,
  channel     VARCHAR(32)  DEFAULT NULL,
  device_id   VARCHAR(128) DEFAULT NULL,
  create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted     INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
-- 用户ID索引，用于查询用户的所有协议同意历史记录
);

-- sys_permission

CREATE TABLE IF NOT EXISTS sys_permission (
  id          BIGINT       NOT NULL,
  name        VARCHAR(50)  NOT NULL,
  code        VARCHAR(50)  NOT NULL,
  description VARCHAR(255) DEFAULT NULL,
  type        INTEGER       DEFAULT NULL,
  status      INTEGER       NOT NULL DEFAULT 1,
  create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version     INTEGER      NOT NULL DEFAULT 0,
  deleted     INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 权限编码唯一索引，用于Spring Security权限校验时按code匹配
  CONSTRAINT uk_code UNIQUE (code)
);

-- sys_api_call_log

CREATE TABLE IF NOT EXISTS sys_api_call_log
(
    id               BIGINT       NOT NULL,
    service_name     VARCHAR(100) NOT NULL,
    method           VARCHAR(10)  NOT NULL,
    path             VARCHAR(500),
    url              VARCHAR(2000),
    request_headers  TEXT,
    request_body     TEXT,
    response_status  VARCHAR(10),
    response_headers TEXT,
    response_body    TEXT,
    execution_time   BIGINT,
    status           INTEGER       NOT NULL DEFAULT 0,
    error_msg        TEXT,
    trace_id         VARCHAR(100),
    create_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version          INTEGER      NOT NULL DEFAULT 0,
    deleted          INTEGER       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sys_account_deletion_task_idx_account_deletion_status ON sys_account_deletion_task (status, locked_at, id);
CREATE INDEX IF NOT EXISTS idx_sys_account_deletion_task_idx_account_deletion_user ON sys_account_deletion_task (user_id, status);
CREATE INDEX IF NOT EXISTS idx_biz_user_consent_idx_user_id ON biz_user_consent (user_id);
CREATE INDEX IF NOT EXISTS idx_sys_api_call_log_idx_service_status_create ON sys_api_call_log (service_name, status, create_time);
CREATE INDEX IF NOT EXISTS idx_sys_api_call_log_idx_path ON sys_api_call_log (path);
CREATE INDEX IF NOT EXISTS idx_sys_api_call_log_idx_create_time ON sys_api_call_log (create_time);
CREATE INDEX IF NOT EXISTS idx_sys_api_call_log_idx_trace_id ON sys_api_call_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_sys_api_call_log_idx_status ON sys_api_call_log (status);
CREATE INDEX IF NOT EXISTS idx_sys_api_call_log_idx_service_name ON sys_api_call_log (service_name);

-- sys_domain_event_outbox

CREATE TABLE IF NOT EXISTS sys_domain_event_outbox (
  id BIGINT NOT NULL GENERATED BY DEFAULT AS IDENTITY,
  event_id VARCHAR(64) NOT NULL,
  event_class VARCHAR(255) NOT NULL,

-- payload: 事件 JSON 负载，包含事件携带的全部业务数据。
  payload TEXT NOT NULL,

-- PENDING: 待投递 / PROCESSING: 投递中 / DELIVERED: 已投递 / DEAD: 死信。
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',

-- attempts: 已失败重试次数，达到上限后移入 DEAD。
  attempts INT NOT NULL DEFAULT 0,

-- next_attempt_at: 指数退避后的下次重试时间点。
  next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

-- locked_by: 投递实例的唯一标识（如实例 ID），用于多实例互斥领取。
  locked_by VARCHAR(64) DEFAULT NULL,

  locked_at TIMESTAMP(6) DEFAULT NULL,

-- last_error: 最近一次投递失败的错误摘要（脱敏后），用于故障排查。
  last_error VARCHAR(1000) DEFAULT NULL,

  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

-- delivered_at: 投递成功完成后记录，用于判断事件投递延迟。
  delivered_at TIMESTAMP(6) DEFAULT NULL,

  PRIMARY KEY (id),

-- event_id 全局唯一，防止同一事件重复写入。
  CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
-- 调度器核心查询：按状态和下次尝试时间领取待投递事件。
-- id 作为第三列确保相同优先级事件按 FIFO 顺序处理。

-- 定位超时未完成的事件（locked 状态过久，需重新调度）。
);

-- biz_content_moderation_review

CREATE TABLE IF NOT EXISTS biz_content_moderation_review (
  id                   BIGINT       NOT NULL,
  source_type          VARCHAR(32)  NOT NULL,
  user_id              BIGINT       NOT NULL,
  content_text         TEXT     NOT NULL,
  media_json           TEXT     DEFAULT NULL,
  provider             VARCHAR(32)  NOT NULL DEFAULT '',
  provider_request_id  VARCHAR(64)  DEFAULT NULL,
  findings_json        TEXT     DEFAULT NULL,
  content_hash         CHAR(32)     NOT NULL,
  status               VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  reviewer_open_id     VARCHAR(64)  DEFAULT NULL,
  review_comment       VARCHAR(512) DEFAULT NULL,
  claimed_at           TIMESTAMP     DEFAULT NULL,
  decided_at           TIMESTAMP     DEFAULT NULL,
  create_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version              INTEGER      NOT NULL DEFAULT 0,
  deleted              INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_content_moderation_hash UNIQUE (user_id, source_type, content_hash),
  CONSTRAINT ck_content_moderation_status CHECK (status IN ('PENDING', 'REVIEWING', 'APPROVED', 'REJECTED'))
);

-- biz_quiz_question

CREATE TABLE IF NOT EXISTS biz_quiz_question (
  id            BIGINT       NOT NULL,
  question_id   VARCHAR(32)  NOT NULL,
  question_text VARCHAR(512) NOT NULL,
  options_json  TEXT     NOT NULL,
  dimension     VARCHAR(32)  NOT NULL,
  k_factor      INT          DEFAULT 32,
  enabled       INTEGER       DEFAULT NULL,
  create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version       INTEGER      NOT NULL DEFAULT 0,
  deleted       INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 题目标识唯一索引，用于按question_id快速查找题目
  CONSTRAINT uk_question_id UNIQUE (question_id)
-- 评分维度索引，用于按维度筛选题目进行对局匹配
);

-- biz_report_ticket

CREATE TABLE IF NOT EXISTS biz_report_ticket (
  id               BIGINT       NOT NULL,
  reporter_user_id BIGINT       NOT NULL,
  target_type      VARCHAR(16)  NOT NULL,
  target_id        BIGINT       NOT NULL,
  reason           VARCHAR(64)  NOT NULL,
  evidence         TEXT     DEFAULT NULL,
  status           VARCHAR(16)  NOT NULL,
  result           VARCHAR(64)  DEFAULT NULL,
  create_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version          INTEGER      NOT NULL DEFAULT 0,
  deleted          INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
-- 举报者用户ID索引，用于查询某用户提交的所有举报工单
  -- 举报目标类型+目标ID联合索引，用于快速定位同一目标的重复举报
  -- 工单状态索引，用于按处理状态（待处理/处理中/已解决/已驳回）筛选工单列表
);

-- sys_role_permission

CREATE TABLE IF NOT EXISTS sys_role_permission (
  id            BIGINT   NOT NULL,
  role_id       BIGINT   NOT NULL,
  permission_id BIGINT   NOT NULL,
  create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version       INTEGER  NOT NULL DEFAULT 0,
  deleted       INTEGER   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 角色权限联合唯一索引，防止同一角色重复授权同一权限
  CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
-- 权限ID索引，用于根据权限查询拥有该权限的所有角色
);

-- biz_user_compliance

CREATE TABLE IF NOT EXISTS biz_user_compliance (
  id               BIGINT   NOT NULL,
  user_id          BIGINT   NOT NULL,
  teen_mode        INTEGER   NOT NULL,
  teen_mode_set_at TIMESTAMP DEFAULT NULL,
  create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version          INTEGER  NOT NULL DEFAULT 0,
  deleted          INTEGER   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 用户ID唯一索引，每个用户仅一条合规记录，用于快速获取用户安全设置
  CONSTRAINT uk_user_compliance_user_id UNIQUE (user_id)
);

-- biz_policy_version

CREATE TABLE IF NOT EXISTS biz_policy_version (
  id            BIGINT       NOT NULL,
  policy_type   VARCHAR(32)  NOT NULL,
  version       VARCHAR(64)  NOT NULL,
  effective_at  VARCHAR(32)  NOT NULL,
  content_url   VARCHAR(512) NOT NULL,
  create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted       INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
-- 策略类型索引，用于按类型（USER_AGREEMENT/PRIVACY_POLICY）查询最新版本
);

-- biz_subscription

CREATE TABLE IF NOT EXISTS biz_subscription (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  level       VARCHAR(16)  NOT NULL,
  start_at    TIMESTAMP     NOT NULL,
  end_at      TIMESTAMP     NOT NULL,
  status      VARCHAR(16)  NOT NULL,
  create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version     INTEGER      NOT NULL DEFAULT 0,
  deleted     INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
-- 用户ID索引，用于查询某用户的订阅记录
  -- 订阅状态索引，用于筛选活跃/过期/已取消的会员
);

-- biz_blacklist

CREATE TABLE IF NOT EXISTS biz_blacklist (
  id             BIGINT   NOT NULL,
  user_id        BIGINT   NOT NULL,
  target_user_id BIGINT   NOT NULL,
  enabled        INTEGER   NOT NULL,
  create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version        INTEGER  NOT NULL DEFAULT 0,
  deleted        INTEGER   NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 拉黑者+被拉黑者联合唯一索引，防止同一用户重复拉黑同一目标
  CONSTRAINT uk_user_target UNIQUE (user_id, target_user_id)
);

-- sys_role

CREATE TABLE IF NOT EXISTS sys_role (
  id          BIGINT       NOT NULL,
  name        VARCHAR(50)  NOT NULL,
  code        VARCHAR(50)  NOT NULL,
  description VARCHAR(255) DEFAULT NULL,
  status      INTEGER       NOT NULL DEFAULT 1,
  create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version     INTEGER      NOT NULL DEFAULT 0,
  deleted     INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 角色编码唯一索引，保证code全局唯一，用于权限校验时按编码查找角色
  CONSTRAINT uk_role_code UNIQUE (code)
);

-- biz_user_profile

CREATE TABLE IF NOT EXISTS biz_user_profile (
  id            BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL,
  dating_goal   VARCHAR(64)  DEFAULT NULL,
  mbti          VARCHAR(8)   DEFAULT NULL,
  height_cm     INT          DEFAULT NULL,
  school        VARCHAR(128) DEFAULT NULL,
  job           VARCHAR(128) DEFAULT NULL,
  bio           VARCHAR(512) DEFAULT NULL,
  social_user   INTEGER       NOT NULL,
  social_user_at TIMESTAMP    DEFAULT NULL,
  create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version       INTEGER      NOT NULL DEFAULT 0,
  deleted       INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 用户ID唯一索引，保证每个用户只有一条资料记录，用于按用户快速查找/更新资料
  CONSTRAINT uk_user_profile_user_id UNIQUE (user_id)
);

-- biz_admin_review_reason

CREATE TABLE IF NOT EXISTS biz_admin_review_reason (
  id BIGINT NOT NULL,
  review_type VARCHAR(32) NOT NULL,
  code VARCHAR(64) NOT NULL,
  label VARCHAR(128) NOT NULL,
  description VARCHAR(512) DEFAULT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  require_comment INTEGER NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version INT NOT NULL DEFAULT 0,
  deleted INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_admin_review_reason UNIQUE (review_type, code),
  CONSTRAINT ck_admin_review_reason_type CHECK (review_type IN ('PHOTO','INTEREST')),
  CONSTRAINT ck_admin_review_reason_enabled CHECK (enabled IN (0,1)),
  CONSTRAINT ck_admin_review_reason_comment CHECK (require_comment IN (0,1))
);

-- biz_report

CREATE TABLE IF NOT EXISTS biz_report (
  id            BIGINT       NOT NULL,
  report_type   VARCHAR(32)  NOT NULL,
  owner_user_id BIGINT       NOT NULL,
  photo_id      BIGINT       DEFAULT NULL,
  status        VARCHAR(16)  NOT NULL,
  progress      INT          NOT NULL,
  data_json     TEXT     DEFAULT NULL,
  created_at    TIMESTAMP     NOT NULL,
  create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version       INTEGER      NOT NULL DEFAULT 0,
  deleted       INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
-- 归属用户ID索引，用于查询某用户的所有报告
  -- 报告类型索引，用于按类型（FIRST_IMPRESSION/AESTHETIC_DNA/ANIMAL）筛选报告
  -- 状态索引，用于查询生成中/已完成/失败的报告
);

-- sys_file

CREATE TABLE IF NOT EXISTS sys_file (
  id            BIGINT       NOT NULL,
  file_name     VARCHAR(255) DEFAULT NULL,
  original_name VARCHAR(255) DEFAULT NULL,
  file_path     VARCHAR(500) DEFAULT NULL,
  file_url      VARCHAR(500) DEFAULT NULL,
  file_size     BIGINT       DEFAULT NULL,
  file_type     VARCHAR(100) DEFAULT NULL,
  content_type  VARCHAR(100) DEFAULT NULL,
  bucket_name   VARCHAR(100) DEFAULT NULL,
  md5           VARCHAR(32)  DEFAULT NULL,
  user_id       BIGINT       DEFAULT NULL,
  create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version       INTEGER      NOT NULL DEFAULT 0,
  deleted       INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
-- 用户ID索引，用于查询某个用户上传的所有文件列表
  -- MD5索引，用于文件去重检测，避免相同文件重复上传
);

-- sys_user

CREATE TABLE IF NOT EXISTS sys_user (
  id                BIGINT       NOT NULL,
  open_id           VARCHAR(100) DEFAULT NULL,
  union_id          VARCHAR(100) DEFAULT NULL,
  access_token      VARCHAR(255) DEFAULT NULL,
  refresh_token     VARCHAR(255) DEFAULT NULL,
  token_expire_time TIMESTAMP     DEFAULT NULL,
  nick_name         VARCHAR(50)  DEFAULT NULL,
  avatar_url        VARCHAR(255) DEFAULT NULL,
  gender            INTEGER       DEFAULT NULL,
  country           VARCHAR(50)  DEFAULT NULL,
  province          VARCHAR(50)  DEFAULT NULL,
  city              VARCHAR(50)  DEFAULT NULL,
  language          VARCHAR(20)  DEFAULT NULL,
  phone             VARCHAR(20)  DEFAULT NULL,
  status            INTEGER       NOT NULL DEFAULT 1,
  last_login_time   TIMESTAMP     DEFAULT NULL,
  last_login_ip     VARCHAR(50)  DEFAULT NULL,
  last_heartbeat_time TIMESTAMP   DEFAULT NULL,
  create_time       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version           INTEGER      NOT NULL DEFAULT 0,
  deleted           INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 微信OpenID唯一索引，用于微信登录时根据open_id快速查找用户
  CONSTRAINT uk_open_id UNIQUE (open_id)
-- UnionID索引，用于微信开放平台跨应用用户关联查询
  -- 手机号索引，用于手机号注册登录和手机号查找用户
  -- 状态索引，用于筛选正常/禁用用户列表
);

-- biz_admin_review_audit

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
CREATE INDEX IF NOT EXISTS idx_sys_domain_event_outbox_idx_outbox_dispatch ON sys_domain_event_outbox (status, next_attempt_at, id);
CREATE INDEX IF NOT EXISTS idx_sys_domain_event_outbox_idx_outbox_locked ON sys_domain_event_outbox (status, locked_at);
CREATE INDEX IF NOT EXISTS idx_biz_content_moderation_review_idx_content_moderation_status ON biz_content_moderation_review (status, id);
CREATE INDEX IF NOT EXISTS idx_biz_content_moderation_review_idx_content_moderation_reviewer ON biz_content_moderation_review (reviewer_open_id, status);
CREATE INDEX IF NOT EXISTS idx_biz_quiz_question_idx_dimension ON biz_quiz_question (dimension);
CREATE INDEX IF NOT EXISTS idx_biz_report_ticket_idx_reporter_user_id ON biz_report_ticket (reporter_user_id);
CREATE INDEX IF NOT EXISTS idx_biz_report_ticket_idx_target_type_id ON biz_report_ticket (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_biz_report_ticket_idx_status ON biz_report_ticket (status);
CREATE INDEX IF NOT EXISTS idx_sys_role_permission_idx_permission_id ON sys_role_permission (permission_id);
CREATE INDEX IF NOT EXISTS idx_biz_policy_version_idx_policy_type ON biz_policy_version (policy_type);
CREATE INDEX IF NOT EXISTS idx_biz_subscription_idx_user_id ON biz_subscription (user_id);
CREATE INDEX IF NOT EXISTS idx_biz_subscription_idx_status ON biz_subscription (status);
CREATE INDEX IF NOT EXISTS idx_biz_admin_review_reason_idx_admin_review_reason_enabled ON biz_admin_review_reason (review_type, enabled, sort_order);
CREATE INDEX IF NOT EXISTS idx_biz_report_idx_owner_user_id ON biz_report (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_biz_report_idx_report_type ON biz_report (report_type);
CREATE INDEX IF NOT EXISTS idx_biz_report_idx_status ON biz_report (status);
CREATE INDEX IF NOT EXISTS idx_sys_file_idx_user_id ON sys_file (user_id);
CREATE INDEX IF NOT EXISTS idx_sys_file_idx_md5 ON sys_file (md5);
CREATE INDEX IF NOT EXISTS idx_sys_user_idx_union_id ON sys_user (union_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_idx_phone ON sys_user (phone);
CREATE INDEX IF NOT EXISTS idx_sys_user_idx_status ON sys_user (status);
CREATE INDEX IF NOT EXISTS idx_biz_admin_review_audit_idx_admin_review_audit_aggregate ON biz_admin_review_audit (aggregate_type, aggregate_id, created_at);
CREATE INDEX IF NOT EXISTS idx_biz_admin_review_audit_idx_admin_review_audit_actor ON biz_admin_review_audit (actor_id, created_at);

-- ------------------------------------------------------------------
-- sys_menu
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_menu (
  id          BIGINT       NOT NULL,
  parent_id   BIGINT       NOT NULL DEFAULT 0,
  menu_name   VARCHAR(50)  NOT NULL DEFAULT '',
  menu_type   INTEGER       NOT NULL DEFAULT 1,
  path        VARCHAR(200) DEFAULT NULL,
  component   VARCHAR(255) DEFAULT NULL,
  perms       VARCHAR(100) DEFAULT NULL,
  icon        VARCHAR(100) DEFAULT NULL,
  sort        INTEGER       NOT NULL DEFAULT 0,
  visible     INTEGER       NOT NULL DEFAULT 1,
  status      INTEGER       NOT NULL DEFAULT 1,
  create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version     INTEGER      NOT NULL DEFAULT 0,
  deleted     INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sys_menu_idx_menu_parent_id ON sys_menu (parent_id);

-- ------------------------------------------------------------------
-- sys_dict_type
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_dict_type (
  id          BIGINT       NOT NULL,
  dict_name   VARCHAR(100) NOT NULL DEFAULT '',
  dict_type   VARCHAR(100) NOT NULL DEFAULT '',
  status      INTEGER       NOT NULL DEFAULT 1,
  remark      VARCHAR(500) DEFAULT NULL,
  create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version     INTEGER      NOT NULL DEFAULT 0,
  deleted     INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_dict_type_code UNIQUE (dict_type)
);

-- ------------------------------------------------------------------
-- sys_dict_data
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_dict_data (
  id          BIGINT       NOT NULL,
  dict_sort   INTEGER       NOT NULL DEFAULT 0,
  dict_label  VARCHAR(100) NOT NULL DEFAULT '',
  dict_value  VARCHAR(100) NOT NULL DEFAULT '',
  dict_type   VARCHAR(100) NOT NULL DEFAULT '',
  is_default  INTEGER       NOT NULL DEFAULT 0,
  status      INTEGER       NOT NULL DEFAULT 1,
  remark      VARCHAR(500) DEFAULT NULL,
  create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version     INTEGER      NOT NULL DEFAULT 0,
  deleted     INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sys_dict_data_idx_dict_data_type ON sys_dict_data (dict_type);

-- ------------------------------------------------------------------
-- sys_config
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_config (
  id           BIGINT       NOT NULL,
  config_name  VARCHAR(100) NOT NULL DEFAULT '',
  config_key   VARCHAR(100) NOT NULL DEFAULT '',
  config_value VARCHAR(500) DEFAULT NULL,
  config_type  INTEGER       NOT NULL DEFAULT 1,
  status       INTEGER       NOT NULL DEFAULT 1,
  remark       VARCHAR(500) DEFAULT NULL,
  create_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version      INTEGER      NOT NULL DEFAULT 0,
  deleted      INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_config_key UNIQUE (config_key)
);

-- ------------------------------------------------------------------
-- sys_job
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_job (
  id              BIGINT       NOT NULL,
  job_name        VARCHAR(64)  NOT NULL DEFAULT '',
  job_group       VARCHAR(64)  NOT NULL DEFAULT 'DEFAULT',
  invoke_target   VARCHAR(500) NOT NULL DEFAULT '',
  cron_expression VARCHAR(255) NOT NULL DEFAULT '',
  misfire_policy  INTEGER       NOT NULL DEFAULT 0,
  concurrent      INTEGER       NOT NULL DEFAULT 1,
  status          INTEGER       NOT NULL DEFAULT 1,
  remark          VARCHAR(500) DEFAULT NULL,
  create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version         INTEGER      NOT NULL DEFAULT 0,
  deleted         INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sys_job_idx_job_status ON sys_job (status);

-- ------------------------------------------------------------------
-- sys_job_log
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_job_log (
  id             BIGINT       NOT NULL,
  job_id         BIGINT       NOT NULL,
  job_name       VARCHAR(64)  NOT NULL DEFAULT '',
  job_group      VARCHAR(64)  NOT NULL DEFAULT 'DEFAULT',
  invoke_target  VARCHAR(500) NOT NULL DEFAULT '',
  job_message    VARCHAR(500) DEFAULT NULL,
  status         INTEGER       NOT NULL DEFAULT 0,
  exception_info TEXT         DEFAULT NULL,
  start_time     TIMESTAMP     DEFAULT NULL,
  end_time       TIMESTAMP     DEFAULT NULL,
  duration       BIGINT       DEFAULT NULL,
  create_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version        INTEGER      NOT NULL DEFAULT 0,
  deleted        INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sys_job_log_idx_job_log_job_id ON sys_job_log (job_id);
CREATE INDEX IF NOT EXISTS idx_sys_job_log_idx_job_log_start_time ON sys_job_log (start_time);

-- ------------------------------------------------------------------
-- sys_login_log
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_login_log (
  id          BIGINT       NOT NULL,
  username    VARCHAR(64)  NOT NULL DEFAULT '',
  ip          VARCHAR(64)  DEFAULT NULL,
  browser     VARCHAR(100) DEFAULT NULL,
  os          VARCHAR(100) DEFAULT NULL,
  status      INTEGER       NOT NULL DEFAULT 1,
  message     VARCHAR(255) DEFAULT NULL,
  login_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version     INTEGER      NOT NULL DEFAULT 0,
  deleted     INTEGER       NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_sys_login_log_idx_login_log_username ON sys_login_log (username);
CREATE INDEX IF NOT EXISTS idx_sys_login_log_idx_login_log_login_time ON sys_login_log (login_time);
