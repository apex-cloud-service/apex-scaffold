-- 脚本用途：建立 Apex Scaffold 通用数据库的首版完整结构基线。
-- 业务背景：为认证授权、系统管理、审计监控及通用业务能力提供统一持久化基础。
-- 前置依赖：需预先创建并选中目标数据库，执行账号须具备建表、建索引权限。
-- 影响范围：创建通用业务表、系统表、关联表、索引及约束，不负责创建数据库实例。
-- 幂等性：建表语句以 IF NOT EXISTS 降低重复执行风险，但不会校正已存在表的结构差异。
-- MySQL/Flyway 兼容性：面向 MySQL 8.x、InnoDB 与 utf8mb4，由 Flyway 作为 V1 基线顺序执行。
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

CREATE TABLE IF NOT EXISTS `biz_credit_tx` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
  `direction`   VARCHAR(8)   NOT NULL COMMENT '方向(IN-收入/OUT-支出)',
  `amount`      BIGINT       NOT NULL COMMENT '变动金额(最小单位)',
  `reason`      VARCHAR(32)  NOT NULL COMMENT '变动原因',
  `ref_type`    VARCHAR(32)  DEFAULT NULL COMMENT '关联类型',
  `ref_id`      BIGINT       DEFAULT NULL COMMENT '关联ID',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`     INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 用户ID索引，用于查询某用户的所有交易流水
  KEY `idx_user_id` (`user_id`),
  -- 交易方向索引，用于按收入/支出分类统计流水
  KEY `idx_direction` (`direction`),
  -- 关联业务类型+ID联合索引，用于追溯具体业务场景（如某次投票的奖励流水）
  KEY `idx_ref_type_id` (`ref_type`, `ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='算力交易流水表';;

-- sys_operation_log

CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `id`              BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `module`          VARCHAR(100) NOT NULL COMMENT '操作模块',
  `operation_type`  VARCHAR(50)  DEFAULT NULL COMMENT '操作类型',
  `description`     VARCHAR(255) DEFAULT NULL COMMENT '操作描述',
  `request_method`  VARCHAR(10)  DEFAULT NULL COMMENT '请求方法',
  `request_uri`     VARCHAR(500) DEFAULT NULL COMMENT '请求URI',
  `request_params`  TEXT         DEFAULT NULL COMMENT '请求参数',
  `response_result` TEXT         DEFAULT NULL COMMENT '响应结果',
  `status`          INT(1)       DEFAULT NULL COMMENT '操作状态(0-成功,1-失败)',
  `error_msg`       TEXT         DEFAULT NULL COMMENT '错误信息',
  `execution_time`  BIGINT       DEFAULT NULL COMMENT '执行时间(毫秒)',
  `ip`              VARCHAR(50)  DEFAULT NULL COMMENT '操作IP',
  `location`        VARCHAR(100) DEFAULT NULL COMMENT '操作地点',
  `user_id`         BIGINT       DEFAULT NULL COMMENT '操作用户ID',
  `user_name`       VARCHAR(50)  DEFAULT NULL COMMENT '操作用户名',
  `operation_time`  DATETIME     DEFAULT NULL COMMENT '操作时间',
  `device_type`     VARCHAR(50)  DEFAULT NULL COMMENT '设备类型',
  `browser`         VARCHAR(100) DEFAULT NULL COMMENT '浏览器类型',
  `os`              VARCHAR(100) DEFAULT NULL COMMENT '操作系统',
  `trace_id`        VARCHAR(100) DEFAULT NULL COMMENT '追踪ID',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`         INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`         INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 模块索引，用于按模块查询操作日志，支持后台审计按功能模块筛选
  KEY `idx_module` (`module`),
  -- 用户ID索引，用于查询指定用户的所有操作记录
  KEY `idx_user_id` (`user_id`),
  -- 操作类型索引，用于按操作类型（如登录、修改、删除等）筛选日志
  KEY `idx_operation_type` (`operation_type`),
  -- 状态索引，用于筛选成功或失败的操作日志
  KEY `idx_status` (`status`),
  -- 追踪ID索引，用于分布式链路追踪，将同一请求链路的日志关联
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';;

-- biz_sample

CREATE TABLE IF NOT EXISTS `biz_sample` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id`     BIGINT       NOT NULL COMMENT '采样用户ID',
  `photo_id`    BIGINT       DEFAULT NULL COMMENT '关联照片ID',
  `bucket_name` VARCHAR(64)  NOT NULL COMMENT '存储桶名称',
  `file_name`   VARCHAR(256) NOT NULL COMMENT '对象名(文件名)',
  `mode`        VARCHAR(16)  NOT NULL COMMENT '模式(TWO_CHOICE/FOUR_PICK_TWO)',
  `face_type`   VARCHAR(16)  DEFAULT NULL COMMENT '脸型分类(CLOSE_UP/ATMOSPHERE)',
  `crop_x`      INT          DEFAULT NULL COMMENT '裁切坐标X',
  `crop_y`      INT          DEFAULT NULL COMMENT '裁切坐标Y',
  `crop_w`      INT          DEFAULT NULL COMMENT '裁切宽度',
  `crop_h`      INT          DEFAULT NULL COMMENT '裁切高度',
  `pitch`       DOUBLE       DEFAULT NULL COMMENT '俯仰角,用于计算FRONTAL/NON_FRONTAL(P0-3修复)',
  `yaw`         DOUBLE       DEFAULT NULL COMMENT '偏航角,用于计算FRONTAL/NON_FRONTAL(P0-3修复)',
  `roll`        DOUBLE       DEFAULT NULL COMMENT '翻滚角,用于计算FRONTAL/NON_FRONTAL(P0-3修复)',
  `face_ratio`  DOUBLE       DEFAULT NULL COMMENT '人脸占画面比例,用于计算PORTRAIT_CLOSE/HALF/FULL(P0-3修复)',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`     INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 用户ID索引，用于查询某用户的所有采样记录
  KEY `idx_user_id` (`user_id`),
  -- 照片ID索引，用于关联查询照片对应的采样数据
  KEY `idx_photo_id` (`photo_id`),
  -- 对局模式索引，用于按TWO_CHOICE/FOUR_PICK_TWO等模式筛选采样数据
  KEY `idx_mode` (`mode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采样记录表';;

-- biz_system_message

CREATE TABLE IF NOT EXISTS `biz_system_message` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id`     BIGINT       NOT NULL COMMENT '接收用户ID',
  `msg_type`    VARCHAR(32)  NOT NULL COMMENT '消息类型',
  `content`     VARCHAR(512) NOT NULL COMMENT '文案内容',
  `ref_type`    VARCHAR(32)  DEFAULT NULL COMMENT '关联类型',
  `ref_id`      BIGINT       DEFAULT NULL COMMENT '关联ID',
  `read_at`     DATETIME     DEFAULT NULL COMMENT '阅读时间',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`     INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 接收用户ID索引，用于查询某用户的所有系统消息
  KEY `idx_user_id` (`user_id`),
  -- 消息类型索引，用于按类型筛选系统通知
  KEY `idx_msg_type` (`msg_type`),
  -- 用户+已读时间联合索引，用于查询未读消息列表
  KEY `idx_user_read` (`user_id`, `read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统消息表';;

-- sys_user_role

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `id`          BIGINT   NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
  `role_id`     BIGINT   NOT NULL COMMENT '角色ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)  NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`     INT(1)   NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 用户角色联合唯一索引，防止同一用户重复授权同一角色
  UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
  -- 角色ID索引，用于根据角色查询其下所有用户列表
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';;

-- audit_log

CREATE TABLE IF NOT EXISTS `audit_log` (
  `id`            BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `operator_id`   VARCHAR(64)  DEFAULT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(50)  DEFAULT NULL COMMENT '操作人姓名',
  `operator_ip`   VARCHAR(50)  DEFAULT NULL COMMENT '操作人IP',
  `module`        VARCHAR(50)  DEFAULT NULL COMMENT '操作模块',
  `operation_type` VARCHAR(50) DEFAULT NULL COMMENT '操作类型',
  `description`   VARCHAR(255) DEFAULT NULL COMMENT '操作描述',
  `method`        VARCHAR(100) DEFAULT NULL COMMENT '请求方法',
  `request_url`   VARCHAR(255) DEFAULT NULL COMMENT '请求URL',
  `request_params` LONGTEXT    DEFAULT NULL COMMENT '请求参数(JSON)',
  `response_result` LONGTEXT   DEFAULT NULL COMMENT '响应结果(JSON)',
  `duration`      BIGINT       DEFAULT NULL COMMENT '耗时(毫秒)',
  `status`        VARCHAR(20)  DEFAULT NULL COMMENT '状态(SUCCESS/FAILED)',
  `error_msg`     TEXT         DEFAULT NULL COMMENT '错误信息',
  `business_id`   VARCHAR(64)  DEFAULT NULL COMMENT '业务ID',
  `tenant_id`     VARCHAR(64)  DEFAULT NULL COMMENT '租户ID',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted`       INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_module` (`module`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志表';

-- sys_account_deletion_task

CREATE TABLE IF NOT EXISTS `sys_account_deletion_task` (
  `id`           BIGINT       NOT NULL COMMENT '清理任务主键（雪花算法）',
  `user_id`      BIGINT       NOT NULL COMMENT '已注销账号用户ID',
  `request_id`   VARCHAR(128) NOT NULL COMMENT '注销请求标识',
  `status`       VARCHAR(24)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLEANING/COMPLETED/FAILED',
  `attempts`     INT          NOT NULL DEFAULT 0 COMMENT '清理尝试次数',
  `last_error`   VARCHAR(500) DEFAULT NULL COMMENT '最近一次脱敏错误摘要',
  `locked_at`    DATETIME     DEFAULT NULL COMMENT '任务领取时间',
  `locked_by`    VARCHAR(128) DEFAULT NULL COMMENT '任务领取实例',
  `completed_at` DATETIME     DEFAULT NULL COMMENT '清理完成时间',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version`      INT          NOT NULL DEFAULT 0,
  `deleted`      INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_deletion_request` (`request_id`),
  KEY `idx_account_deletion_status` (`status`, `locked_at`, `id`),
  KEY `idx_account_deletion_user` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号注销异步清理任务';;

-- biz_credit_account

CREATE TABLE IF NOT EXISTS `biz_credit_account` (
  `id`                  BIGINT   NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id`             BIGINT   NOT NULL COMMENT '用户ID',
  `balance`             BIGINT   NOT NULL COMMENT '余额(最小单位)',
  `today_login_bonus`   TINYINT(1) DEFAULT 0 COMMENT '今日登录奖励已领取',
  `daily_favorite_total` BIGINT   DEFAULT 0 COMMENT '当日收藏累计积分(V2: 日上限200)',
  `daily_favorite_date` DATE      DEFAULT NULL COMMENT '收藏积分统计日期',
  `create_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`             INT(10)  NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`             INT(1)   NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 用户ID唯一索引，每个用户只有一个算力账户，用于快速查询和更新余额
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='算力账户表';;

-- biz_user_consent

CREATE TABLE IF NOT EXISTS `biz_user_consent` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id`     BIGINT       DEFAULT NULL COMMENT '用户ID',
  `policy_type` VARCHAR(32)  NOT NULL COMMENT '策略类型(USER_AGREEMENT/PRIVACY_POLICY)',
  `version`     VARCHAR(64)  NOT NULL COMMENT '同意版本号(业务字段)',
  `agreed`      INT(1)       NOT NULL COMMENT '是否同意(0-否,1-是)',
  `agreed_at`   DATETIME     NOT NULL COMMENT '同意时间',
  `channel`     VARCHAR(32)  DEFAULT NULL COMMENT '渠道',
  `device_id`   VARCHAR(128) DEFAULT NULL COMMENT '设备ID摘要',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 用户ID索引，用于查询用户的所有协议同意历史记录
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户协议同意记录表';;

-- sys_permission

CREATE TABLE IF NOT EXISTS `sys_permission` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `name`        VARCHAR(50)  NOT NULL COMMENT '权限名称',
  `code`        VARCHAR(50)  NOT NULL COMMENT '权限编码',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '权限描述',
  `type`        INT(1)       DEFAULT NULL COMMENT '权限类型',
  `status`      INT(1)       NOT NULL DEFAULT '1' COMMENT '状态(0-禁用,1-正常)',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`     INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 权限编码唯一索引，用于Spring Security权限校验时按code匹配
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';;

-- sys_api_call_log

CREATE TABLE IF NOT EXISTS `sys_api_call_log`
(
    `id`               BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `service_name`     VARCHAR(100) NOT NULL COMMENT '目标服务名称，如 wechat / chuanglan / baidu-image / face-analysis',
    `method`           VARCHAR(10)  NOT NULL COMMENT 'HTTP请求方法，如 GET / POST / PUT / DELETE',
    `path`             VARCHAR(500)          COMMENT '请求路径（不含host），如 /api/v1/login',
    `url`              VARCHAR(2000)         COMMENT '完整请求URL（包含query参数）',
    `request_headers`  TEXT                  COMMENT '请求头（JSON格式），敏感字段如Authorization/ApiKey等会被脱敏',
    `request_body`     MEDIUMTEXT            COMMENT '请求体（超过默认限制时截断），二进制用base64编码',
    `response_status`  VARCHAR(10)           COMMENT 'HTTP响应状态码，如 200 / 404 / 500。无响应时为 null',
    `response_headers` TEXT                  COMMENT '响应头（JSON格式）',
    `response_body`    MEDIUMTEXT            COMMENT '响应体（超过默认限制时截断）',
    `execution_time`   BIGINT                COMMENT '执行耗时（毫秒），从发起请求到收到响应的时间',
    `status`           INT(1)       NOT NULL DEFAULT 0 COMMENT '调用状态：0-成功（HTTP 2xx） 1-客户端错误（4xx） 2-服务端错误（5xx） 3-网络异常',
    `error_msg`        TEXT                  COMMENT '错误信息，异常时记录异常的堆栈前3行',
    `trace_id`         VARCHAR(100)          COMMENT '链路追踪ID，关联前端请求与后端API调用',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`          INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（保留字段）',
    `deleted`          INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    INDEX `idx_service_name` (`service_name`),
    INDEX `idx_path` (`path`(100)),
    INDEX `idx_status` (`status`),
    INDEX `idx_trace_id` (`trace_id`),
    INDEX `idx_create_time` (`create_time`),
    INDEX `idx_service_status_create` (`service_name`, `status`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'API调用日志表（记录对外部HTTP服务的每次调用详情）';

-- sys_domain_event_outbox

CREATE TABLE IF NOT EXISTS `sys_domain_event_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_id` VARCHAR(64) NOT NULL COMMENT '领域事件唯一标识',
  `event_class` VARCHAR(255) NOT NULL COMMENT '领域事件 Java 类型',

-- payload: 事件 JSON 负载，包含事件携带的全部业务数据。
  `payload` LONGTEXT NOT NULL COMMENT '事件 JSON 负载',

-- PENDING: 待投递 / PROCESSING: 投递中 / DELIVERED: 已投递 / DEAD: 死信。
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DELIVERED/DEAD',

-- attempts: 已失败重试次数，达到上限后移入 DEAD。
  `attempts` INT NOT NULL DEFAULT 0 COMMENT '已失败次数',

-- next_attempt_at: 指数退避后的下次重试时间点。
  `next_attempt_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下次投递时间',

-- locked_by: 投递实例的唯一标识（如实例 ID），用于多实例互斥领取。
  `locked_by` VARCHAR(64) DEFAULT NULL COMMENT '投递实例令牌',

  `locked_at` DATETIME(6) DEFAULT NULL COMMENT '领取时间',

-- last_error: 最近一次投递失败的错误摘要（脱敏后），用于故障排查。
  `last_error` VARCHAR(1000) DEFAULT NULL COMMENT '最近一次错误摘要',

  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',

-- delivered_at: 投递成功完成后记录，用于判断事件投递延迟。
  `delivered_at` DATETIME(6) DEFAULT NULL COMMENT '完成时间',

  PRIMARY KEY (`id`),

-- event_id 全局唯一，防止同一事件重复写入。
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),

-- 调度器核心查询：按状态和下次尝试时间领取待投递事件。
-- id 作为第三列确保相同优先级事件按 FIFO 顺序处理。
  KEY `idx_outbox_dispatch` (`status`, `next_attempt_at`, `id`),

-- 定位超时未完成的事件（locked 状态过久，需重新调度）。
  KEY `idx_outbox_locked` (`status`, `locked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='领域事件可靠投递表';;

-- biz_content_moderation_review

CREATE TABLE IF NOT EXISTS `biz_content_moderation_review` (
  `id`                   BIGINT       NOT NULL COMMENT '主键ID',
  `source_type`          VARCHAR(32)  NOT NULL COMMENT 'ANONYMOUS_MESSAGE/TOPIC/TOPIC_RESPONSE/OPENING_TEXT',
  `user_id`              BIGINT       NOT NULL COMMENT '内容作者用户ID',
  `content_text`         LONGTEXT     NOT NULL COMMENT '文本内容快照',
  `media_json`           LONGTEXT     DEFAULT NULL COMMENT '媒体引用快照',
  `provider`             VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '审核供应商',
  `provider_request_id`  VARCHAR(64)  DEFAULT NULL COMMENT '供应商请求ID(百度log_id)',
  `findings_json`        LONGTEXT     DEFAULT NULL COMMENT '供应商命中详情JSON',
  `content_hash`         CHAR(32)     NOT NULL COMMENT '内容指纹(作者+来源+文本MD5)',
  `status`               VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/REVIEWING/APPROVED/REJECTED',
  `reviewer_open_id`     VARCHAR(64)  DEFAULT NULL COMMENT '认领复核人openId',
  `review_comment`       VARCHAR(512) DEFAULT NULL COMMENT '复核意见',
  `claimed_at`           DATETIME     DEFAULT NULL COMMENT '认领时间',
  `decided_at`           DATETIME     DEFAULT NULL COMMENT '裁决时间',
  `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`              INT(10)      NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `deleted`              INT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_content_moderation_hash` (`user_id`, `source_type`, `content_hash`),
  KEY `idx_content_moderation_status` (`status`, `id`),
  KEY `idx_content_moderation_reviewer` (`reviewer_open_id`, `status`),
  CONSTRAINT `ck_content_moderation_status` CHECK (`status` IN ('PENDING', 'REVIEWING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容人工复核任务';;

-- biz_quiz_question

CREATE TABLE IF NOT EXISTS `biz_quiz_question` (
  `id`            BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `question_id`   VARCHAR(32)  NOT NULL COMMENT '题目标识',
  `question_text` VARCHAR(512) NOT NULL COMMENT '题目内容',
  `options_json`  LONGTEXT     NOT NULL COMMENT '选项(JSON)',
  `dimension`     VARCHAR(32)  NOT NULL COMMENT '评分维度',
  `k_factor`      INT          DEFAULT 32 COMMENT 'ELO K系数',
  `enabled`       INT(1)       DEFAULT NULL COMMENT '是否启用(0-禁用,1-启用)',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`       INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`       INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 题目标识唯一索引，用于按question_id快速查找题目
  UNIQUE KEY `uk_question_id` (`question_id`),
  -- 评分维度索引，用于按维度筛选题目进行对局匹配
  KEY `idx_dimension` (`dimension`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='答题题目表';;

-- biz_report_ticket

CREATE TABLE IF NOT EXISTS `biz_report_ticket` (
  `id`               BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `reporter_user_id` BIGINT       NOT NULL COMMENT '举报者用户ID',
  `target_type`      VARCHAR(16)  NOT NULL COMMENT '举报目标类型(USER/PHOTO/MESSAGE)',
  `target_id`        BIGINT       NOT NULL COMMENT '举报目标ID',
  `reason`           VARCHAR(64)  NOT NULL COMMENT '举报原因',
  `evidence`         LONGTEXT     DEFAULT NULL COMMENT '证据(JSON)',
  `status`           VARCHAR(16)  NOT NULL COMMENT '状态(PENDING/PROCESSING/RESOLVED/REJECTED)',
  `result`           VARCHAR(64)  DEFAULT NULL COMMENT '处理结果',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`          INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`          INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 举报者用户ID索引，用于查询某用户提交的所有举报工单
  KEY `idx_reporter_user_id` (`reporter_user_id`),
  -- 举报目标类型+目标ID联合索引，用于快速定位同一目标的重复举报
  KEY `idx_target_type_id` (`target_type`, `target_id`),
  -- 工单状态索引，用于按处理状态（待处理/处理中/已解决/已驳回）筛选工单列表
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报工单表';;

-- sys_role_permission

CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id`            BIGINT   NOT NULL COMMENT '主键ID(雪花算法)',
  `role_id`       BIGINT   NOT NULL COMMENT '角色ID',
  `permission_id` BIGINT   NOT NULL COMMENT '权限ID',
  `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`       INT(10)  NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`       INT(1)   NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 角色权限联合唯一索引，防止同一角色重复授权同一权限
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  -- 权限ID索引，用于根据权限查询拥有该权限的所有角色
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';;

-- biz_user_compliance

CREATE TABLE IF NOT EXISTS `biz_user_compliance` (
  `id`               BIGINT   NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id`          BIGINT   NOT NULL COMMENT '用户ID',
  `teen_mode`        INT(1)   NOT NULL COMMENT '青少年模式(0-关闭,1-开启)',
  `teen_mode_set_at` DATETIME DEFAULT NULL COMMENT '青少年模式设置时间',
  `create_time`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`          INT(10)  NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`          INT(1)   NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 用户ID唯一索引，每个用户仅一条合规记录，用于快速获取用户安全设置
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户合规表(青少年模式)';;

-- biz_policy_version

CREATE TABLE IF NOT EXISTS `biz_policy_version` (
  `id`            BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `policy_type`   VARCHAR(32)  NOT NULL COMMENT '策略类型(USER_AGREEMENT/PRIVACY_POLICY)',
  `version`       VARCHAR(64)  NOT NULL COMMENT '版本号(业务字段)',
  `effective_at`  VARCHAR(32)  NOT NULL COMMENT '生效日期(字符串)',
  `content_url`   VARCHAR(512) NOT NULL COMMENT '内容URL',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 策略类型索引，用于按类型（USER_AGREEMENT/PRIVACY_POLICY）查询最新版本
  KEY `idx_policy_type` (`policy_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='策略版本表';;

-- biz_subscription

CREATE TABLE IF NOT EXISTS `biz_subscription` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
  `level`       VARCHAR(16)  NOT NULL COMMENT '会员等级(VIP/SVIP)',
  `start_at`    DATETIME     NOT NULL COMMENT '开始时间',
  `end_at`      DATETIME     NOT NULL COMMENT '结束时间',
  `status`      VARCHAR(16)  NOT NULL COMMENT '状态(ACTIVE/EXPIRED/CANCELED)',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`     INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 用户ID索引，用于查询某用户的订阅记录
  KEY `idx_user_id` (`user_id`),
  -- 订阅状态索引，用于筛选活跃/过期/已取消的会员
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员订阅表';;

-- biz_blacklist

CREATE TABLE IF NOT EXISTS `biz_blacklist` (
  `id`             BIGINT   NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id`        BIGINT   NOT NULL COMMENT '拉黑者用户ID',
  `target_user_id` BIGINT   NOT NULL COMMENT '被拉黑用户ID',
  `enabled`        INT(1)   NOT NULL COMMENT '是否生效(0-取消拉黑,1-拉黑)',
  `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`        INT(10)  NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`        INT(1)   NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 拉黑者+被拉黑者联合唯一索引，防止同一用户重复拉黑同一目标
  UNIQUE KEY `uk_user_target` (`user_id`, `target_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='黑名单表';;

-- sys_role

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `name`        VARCHAR(50)  NOT NULL COMMENT '角色名称',
  `code`        VARCHAR(50)  NOT NULL COMMENT '角色编码',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
  `status`      INT(1)       NOT NULL DEFAULT '1' COMMENT '状态(0-禁用,1-正常)',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`     INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 角色编码唯一索引，保证code全局唯一，用于权限校验时按编码查找角色
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';;

-- biz_user_profile

CREATE TABLE IF NOT EXISTS `biz_user_profile` (
  `id`            BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
  `dating_goal`   VARCHAR(64)  DEFAULT NULL COMMENT '交友目的',
  `mbti`          VARCHAR(8)   DEFAULT NULL COMMENT 'MBTI人格类型',
  `height_cm`     INT          DEFAULT NULL COMMENT '身高(厘米)',
  `school`        VARCHAR(128) DEFAULT NULL COMMENT '学校',
  `job`           VARCHAR(128) DEFAULT NULL COMMENT '职业',
  `bio`           VARCHAR(512) DEFAULT NULL COMMENT '个人简介',
  `social_user`   INT(1)       NOT NULL COMMENT '是否社交用户(0-否,1-是)',
  `social_user_at` DATETIME    DEFAULT NULL COMMENT '成为社交用户时间',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`       INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`       INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 用户ID唯一索引，保证每个用户只有一条资料记录，用于按用户快速查找/更新资料
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户资料表';;

-- biz_admin_review_reason

CREATE TABLE IF NOT EXISTS biz_admin_review_reason (
  id BIGINT NOT NULL,
  review_type VARCHAR(32) NOT NULL COMMENT 'PHOTO/INTEREST',
  code VARCHAR(64) NOT NULL,
  label VARCHAR(128) NOT NULL,
  description VARCHAR(512) DEFAULT NULL,
  enabled INT(1) NOT NULL DEFAULT 1,
  require_comment INT(1) NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  version INT NOT NULL DEFAULT 0,
  deleted INT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_review_reason (review_type, code),
  KEY idx_admin_review_reason_enabled (review_type, enabled, sort_order),
  CONSTRAINT ck_admin_review_reason_type CHECK (review_type IN ('PHOTO','INTEREST')),
  CONSTRAINT ck_admin_review_reason_enabled CHECK (enabled IN (0,1)),
  CONSTRAINT ck_admin_review_reason_comment CHECK (require_comment IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台审核原因配置';;

-- biz_report

CREATE TABLE IF NOT EXISTS `biz_report` (
  `id`            BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `report_type`   VARCHAR(32)  NOT NULL COMMENT '报告类型(FIRST_IMPRESSION/AESTHETIC_DNA/ANIMAL)',
  `owner_user_id` BIGINT       NOT NULL COMMENT '归属用户ID',
  `photo_id`      BIGINT       DEFAULT NULL COMMENT '关联照片ID',
  `status`        VARCHAR(16)  NOT NULL COMMENT '状态(PENDING/RUNNING/DONE/FAILED)',
  `progress`      INT          NOT NULL COMMENT '进度(0-100)',
  `data_json`     LONGTEXT     DEFAULT NULL COMMENT '报告数据(JSON)',
  `created_at`    DATETIME     NOT NULL COMMENT '报告生成时间',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`       INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`       INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 归属用户ID索引，用于查询某用户的所有报告
  KEY `idx_owner_user_id` (`owner_user_id`),
  -- 报告类型索引，用于按类型（FIRST_IMPRESSION/AESTHETIC_DNA/ANIMAL）筛选报告
  KEY `idx_report_type` (`report_type`),
  -- 状态索引，用于查询生成中/已完成/失败的报告
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告表';;

-- sys_file

CREATE TABLE IF NOT EXISTS `sys_file` (
  `id`            BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `file_name`     VARCHAR(255) DEFAULT NULL COMMENT '文件名称',
  `original_name` VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
  `file_path`     VARCHAR(500) DEFAULT NULL COMMENT '文件路径',
  `file_url`      VARCHAR(500) DEFAULT NULL COMMENT '文件访问URL',
  `file_size`     BIGINT       DEFAULT NULL COMMENT '文件大小(字节)',
  `file_type`     VARCHAR(100) DEFAULT NULL COMMENT '文件类型',
  `content_type`  VARCHAR(100) DEFAULT NULL COMMENT '内容类型(MIME)',
  `bucket_name`   VARCHAR(100) DEFAULT NULL COMMENT '存储桶名称',
  `md5`           VARCHAR(32)  DEFAULT NULL COMMENT '文件MD5',
  `user_id`       BIGINT       DEFAULT NULL COMMENT '上传用户ID',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`       INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`       INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 用户ID索引，用于查询某个用户上传的所有文件列表
  KEY `idx_user_id` (`user_id`),
  -- MD5索引，用于文件去重检测，避免相同文件重复上传
  KEY `idx_md5` (`md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件信息表';;

-- sys_user

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`                BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `open_id`           VARCHAR(100) DEFAULT NULL COMMENT '微信OpenID(手机号注册用户可为NULL)',
  `union_id`          VARCHAR(100) DEFAULT NULL COMMENT '微信UnionID',
  `access_token`      VARCHAR(255) DEFAULT NULL COMMENT '微信AccessToken',
  `refresh_token`     VARCHAR(255) DEFAULT NULL COMMENT '微信RefreshToken',
  `token_expire_time` DATETIME     DEFAULT NULL COMMENT 'Token过期时间',
  `nick_name`         VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  `avatar_url`        VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `gender`            INT(1)       DEFAULT NULL COMMENT '性别(0-未知,1-男,2-女)',
  `country`           VARCHAR(50)  DEFAULT NULL COMMENT '国家',
  `province`          VARCHAR(50)  DEFAULT NULL COMMENT '省份',
  `city`              VARCHAR(50)  DEFAULT NULL COMMENT '城市',
  `language`          VARCHAR(20)  DEFAULT NULL COMMENT '语言',
  `phone`             VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  `status`            INT(1)       NOT NULL DEFAULT '1' COMMENT '状态(0-禁用,1-正常)',
  `last_login_time`   DATETIME     DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip`     VARCHAR(50)  DEFAULT NULL COMMENT '最后登录IP',
  `last_heartbeat_time` DATETIME   DEFAULT NULL COMMENT '最后心跳时间(V2: 在线判定)',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`           INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`           INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  -- 微信OpenID唯一索引，用于微信登录时根据open_id快速查找用户
  UNIQUE KEY `uk_open_id` (`open_id`),
  -- UnionID索引，用于微信开放平台跨应用用户关联查询
  KEY `idx_union_id` (`union_id`),
  -- 手机号索引，用于手机号注册登录和手机号查找用户
  KEY `idx_phone` (`phone`),
  -- 状态索引，用于筛选正常/禁用用户列表
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表(微信认证+手机号注册)';;

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
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_admin_review_audit_aggregate (aggregate_type, aggregate_id, created_at),
  KEY idx_admin_review_audit_actor (actor_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台审核不可变业务审计';

-- ------------------------------------------------------------------
-- sys_menu
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，0表示顶级菜单',
  `menu_name`   VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '菜单名称',
  `menu_type`   INT(1)       NOT NULL DEFAULT 1 COMMENT '菜单类型(0-目录,1-菜单,2-按钮)',
  `path`        VARCHAR(200) DEFAULT NULL COMMENT '路由地址',
  `component`   VARCHAR(255) DEFAULT NULL COMMENT '组件路径',
  `perms`       VARCHAR(100) DEFAULT NULL COMMENT '权限标识',
  `icon`        VARCHAR(100) DEFAULT NULL COMMENT '菜单图标',
  `sort`        INT(4)       NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `visible`     INT(1)       NOT NULL DEFAULT 1 COMMENT '是否显示(0-隐藏,1-显示)',
  `status`      INT(1)       NOT NULL DEFAULT 1 COMMENT '状态(0-停用,1-正常)',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`     INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_menu_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单表';

-- ------------------------------------------------------------------
-- sys_dict_type
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_dict_type` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `dict_name`   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '字典名称',
  `dict_type`   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '字典类型编码',
  `status`      INT(1)       NOT NULL DEFAULT 1 COMMENT '状态(0-停用,1-正常)',
  `remark`      VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`     INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_code` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典类型表';

-- ------------------------------------------------------------------
-- sys_dict_data
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_dict_data` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `dict_sort`   INT(4)       NOT NULL DEFAULT 0 COMMENT '字典排序',
  `dict_label`  VARCHAR(100) NOT NULL DEFAULT '' COMMENT '字典标签',
  `dict_value`  VARCHAR(100) NOT NULL DEFAULT '' COMMENT '字典键值',
  `dict_type`   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '所属字典类型编码',
  `is_default`  INT(1)       NOT NULL DEFAULT 0 COMMENT '是否默认(0-否,1-是)',
  `status`      INT(1)       NOT NULL DEFAULT 1 COMMENT '状态(0-停用,1-正常)',
  `remark`      VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`     INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_dict_data_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典数据表';

-- ------------------------------------------------------------------
-- sys_config
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id`           BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `config_name`  VARCHAR(100) NOT NULL DEFAULT '' COMMENT '参数名称',
  `config_key`   VARCHAR(100) NOT NULL DEFAULT '' COMMENT '参数键名',
  `config_value` VARCHAR(500) DEFAULT NULL COMMENT '参数键值',
  `config_type`  INT(1)       NOT NULL DEFAULT 1 COMMENT '参数类型(0-内置,1-用户)',
  `status`       INT(1)       NOT NULL DEFAULT 1 COMMENT '状态(0-停用,1-正常)',
  `remark`       VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`      INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`      INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数配置表';

-- ------------------------------------------------------------------
-- sys_job
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_job` (
  `id`              BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `job_name`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group`       VARCHAR(64)  NOT NULL DEFAULT 'DEFAULT' COMMENT '任务分组',
  `invoke_target`   VARCHAR(500) NOT NULL DEFAULT '' COMMENT '调用目标(beanName.methodName或带参数)',
  `cron_expression` VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Cron表达式',
  `misfire_policy`  INT(1)       NOT NULL DEFAULT 0 COMMENT '错失执行策略(0-默认,1-立即补执行,2-放弃)',
  `concurrent`      INT(1)       NOT NULL DEFAULT 1 COMMENT '是否允许并发(0-禁止,1-允许)',
  `status`          INT(1)       NOT NULL DEFAULT 1 COMMENT '状态(0-停用,1-正常)',
  `remark`          VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`         INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`         INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_job_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务表';

-- ------------------------------------------------------------------
-- sys_job_log
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_job_log` (
  `id`             BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `job_id`         BIGINT       NOT NULL COMMENT '任务ID',
  `job_name`       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '任务名称(执行快照)',
  `job_group`      VARCHAR(64)  NOT NULL DEFAULT 'DEFAULT' COMMENT '任务分组(执行快照)',
  `invoke_target`  VARCHAR(500) NOT NULL DEFAULT '' COMMENT '调用目标(执行快照)',
  `job_message`    VARCHAR(500) DEFAULT NULL COMMENT '执行消息',
  `status`         INT(1)       NOT NULL DEFAULT 0 COMMENT '执行状态(0-成功,1-失败,2-执行中)',
  `exception_info` TEXT         DEFAULT NULL COMMENT '异常信息',
  `start_time`     DATETIME     DEFAULT NULL COMMENT '开始时间',
  `end_time`       DATETIME     DEFAULT NULL COMMENT '结束时间',
  `duration`       BIGINT       DEFAULT NULL COMMENT '执行耗时(毫秒)',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`        INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`        INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_job_log_job_id` (`job_id`),
  KEY `idx_job_log_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务执行日志表';

-- ------------------------------------------------------------------
-- sys_login_log
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_login_log` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `username`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '登录账号',
  `ip`          VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
  `browser`     VARCHAR(100) DEFAULT NULL COMMENT '浏览器信息',
  `os`          VARCHAR(100) DEFAULT NULL COMMENT '操作系统信息',
  `status`      INT(1)       NOT NULL DEFAULT 1 COMMENT '登录状态(0-成功,1-失败)',
  `message`     VARCHAR(255) DEFAULT NULL COMMENT '登录消息',
  `login_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`     INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_login_log_username` (`username`),
  KEY `idx_login_log_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';
