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
-- 脚本用途：创建超级管理员扩展角色并授予内置管理员账号。
-- 业务背景：满足后台用户和角色管理接口对 SUPER_ADMIN 角色码的强制校验。
-- 前置依赖：V8 已创建种子管理员与基础管理员角色，目标角色及关联表结构可用。
-- 影响范围：写入或更新 sys_role，并向 sys_user_role 增加管理员角色关联。
-- 幂等性：使用 ON DUPLICATE KEY UPDATE 与 INSERT IGNORE，按固定主键可重复执行。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x 幂等 DML，由 Flyway V10 顺序执行。
-- 数据风险：会提升种子管理员权限；主键已被其他业务占用时可能掩盖错误关联。
-- 运维注意：执行后审计超级管理员名单、默认账号状态与最小权限策略。
-- 迁移元信息：版本 V10；执行阶段为后台高权限接口授权补齐阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ============================================================
-- V10: 为种子管理员授予 SUPER_ADMIN 扩展角色
-- ============================================================
-- 背景：后台用户/角色管理等接口（AdminUserManagementService.assertSuperAdmin）
--   硬校验操作者具备 SUPER_ADMIN 角色码；V8 仅授予了 ADMIN 角色，
--   导致 /admin-api/v1/users、/admin-api/v1/staff-roles 返回 403。
-- 本脚本补充 SUPER_ADMIN 角色并关联到种子管理员，幂等可重复执行。
-- ============================================================

-- 1. 扩展授权角色：SUPER_ADMIN
INSERT INTO sys_role (id, name, code, description, status)
VALUES (2, '超级管理员', 'SUPER_ADMIN', '系统内置超级管理员扩展授权角色（后台管理接口硬校验）', 1)
ON DUPLICATE KEY UPDATE name = '超级管理员', status = 1;

-- 2. 关联到种子管理员（sys_user_role 无唯一键，用主键幂等插入）
INSERT IGNORE INTO sys_user_role (id, user_id, role_id) VALUES (2, 1, 2);
-- 脚本用途：统一菜单状态语义并初始化监控中心目录、页面与权限关联。
-- 业务背景：修复历史种子状态与枚举定义相反的问题，并接入监控后台能力。
-- 前置依赖：V1-V10 已完成，相关菜单、权限、角色及关联表存在。
-- 影响范围：批量更新现有菜单状态，新增或更新监控菜单和权限，并调整菜单层级。
-- 幂等性：采用 UPDATE、ON DUPLICATE KEY UPDATE 和条件插入，设计为可重复执行。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x DML，由 Flyway V11 按版本顺序执行。
-- 数据风险：批量更新会改变后台菜单可见性和层级；错误配置可能导致授权或路由异常。
-- 运维注意：执行前留存菜单配置，执行后验证菜单树、监控权限和前端组件映射。
-- 迁移元信息：版本 V11；执行阶段为菜单语义修复与监控中心上线阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ============================================================
-- V11: 修复菜单状态语义 + 种子监控中心菜单
-- ============================================================
-- 背景：
--   1. 后端 MenuState 枚举定义为 status: 0=停用 1=正常；MenuVisible 枚举为
--      visible: 0=隐藏 1=显示。但 V2/V8 种子数据将全部菜单写入 status=0、
--      visible=0，与枚举语义相反，导致「启停菜单」接口与前端状态展示不一致。
--   2. 新增监控大盘聚合接口（/admin-api/v1/monitor/*，权限 system:monitor:view），
--      需在菜单树中补充「监控中心」目录与「监控大盘」页面，
--      并将登录日志/审计日志/在线用户归入监控中心。
-- 本脚本幂等可重复执行。
-- ============================================================

-- 1. 统一既有目录/菜单状态：status=1 正常、visible=1 显示（与枚举语义对齐）
UPDATE sys_menu
SET status  = 1,
    visible = 1
WHERE menu_type IN (0, 1)
  AND deleted = 0;

-- 2. 新增「监控中心」目录（parent=0，目录 menu_type=0）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1100, 0, '监控中心', 0, 'monitor', NULL, NULL, 'Monitor', 11, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = '监控中心', menu_type = 0, path = 'monitor', icon = 'Monitor',
                        sort = 11, visible = 1, status = 1;

-- 3. 新增「监控大盘」菜单（component 对应前端 VIEW_REGISTRY monitor/dashboard/index）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1101, 1100, '监控大盘', 1, 'dashboard', 'monitor/dashboard/index', 'system:monitor:view', 'DataLine', 1, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = '监控大盘', parent_id = 1100, menu_type = 1, path = 'dashboard',
                        component = 'monitor/dashboard/index', perms = 'system:monitor:view',
                        icon = 'DataLine', sort = 1, visible = 1, status = 1;

-- 4. 将登录日志/审计日志/在线用户归入「监控中心」（同时保持启用与显示）
UPDATE sys_menu SET parent_id = 1100, status = 1, visible = 1 WHERE id IN (1006, 1007, 1012);

-- 5. 监控权限码 + 授予 ADMIN 角色（ROLE_ADMIN 已直接放行，此处补齐权限数据）
INSERT INTO sys_permission (id, name, code, description, type, status) VALUES
    (2050, '监控大盘', 'system:monitor:view', '查看系统监控大盘（性能/治理/业务指标）', 1, 1)
ON DUPLICATE KEY UPDATE name = '监控大盘', status = 1;

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10050, 1, 2050
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2050);
-- 脚本用途：删除已废弃且由 sys_operation_log 替代的 audit_log 表。
-- 业务背景：审计读写链路完成收敛后清理 V1 遗留的无引用旧表。
-- 前置依赖：V1-V11 已完成，应用及 Mapper 已停止引用 audit_log，并已确认无需保留其数据。
-- 影响范围：永久删除 audit_log 表结构及其中全部数据，不影响 sys_operation_log。
-- 幂等性：DROP TABLE IF EXISTS 可重复执行，但首次删除产生不可逆的数据与结构变更。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x 标准兼容 DDL，由 Flyway V12 顺序执行。
-- 数据风险：属于破坏性迁移；若环境中存在历史数据或隐式依赖，将造成永久丢失或运行故障。
-- 运维注意：执行前完成备份、行数核验和依赖排查，禁止仅依据注释假定表为空。
-- 迁移元信息：版本 V12；执行阶段为审计链路切换完成后的遗留对象清理阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- Apex Scaffold V12 迁移：删除废弃的 audit_log 空表
-- ================================================================
-- 背景：早期审计写入曾使用 audit_log 表，2026-08-27 重构后操作审计
--       统一收敛为 @OperationLog + OperationLogAspect → sys_operation_log
--       表（写读同源）。经全库扫描确认：已无任何 Java 代码 / Mapper /
--       PO 引用 audit_log 表（仅 V1 基线中遗留空表定义，实际 0 行数据），
--       AuditLog* 命名类均改为查询 sys_operation_log。
-- 处理：以独立迁移 DROP 该空表，不动 V1 基线（避免破坏 Flyway checksum）。
-- 安全：删表前确认无数据（SELECT COUNT(*) = 0），且无外键依赖。
-- ================================================================

DROP TABLE IF EXISTS `audit_log`;
-- 脚本用途：初始化 API 调用日志查询权限、管理员授权及监控菜单入口。
-- 业务背景：为外部 HTTP 调用日志的后台分页查询和详情查看提供完整授权导航。
-- 前置依赖：V11 已创建监控中心，V1-V12 目标表与 ADMIN 角色均可用。
-- 影响范围：写入或更新权限、角色权限关联和菜单配置，不变更日志表结构及数据。
-- 幂等性：使用 ON DUPLICATE KEY UPDATE 与 NOT EXISTS 条件插入，设计为可重复执行。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x DML、VALUES() 与 FROM DUAL，由 Flyway V13 执行。
-- 数据风险：固定 ID 冲突或错误授权会造成迁移失败、越权访问或菜单异常。
-- 运维注意：执行前核对权限 ID 号段，执行后验证 ADMIN 授权与监控菜单访问。
-- 迁移元信息：版本 V13；执行阶段为 API 调用日志管理能力的权限与菜单上线阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- Apex Scaffold V13 迁移：API调用日志查询权限与菜单
-- ================================================================
-- 说明：为对外 HTTP 调用日志（sys_api_call_log 表）补充查询权限码、
--      ADMIN 角色授权与菜单项，支撑管理后台「监控中心-API调用日志」
--      页面（GET /admin-api/v1/api-call-logs，权限 system:api-call-log:list）。
-- 依赖：V11 已创建「监控中心」目录（id=1100）与「监控大盘」菜单（id=1101）。
-- 权限码：V4 用 2025-2026、V5 用 2027、V6 用 2028、V8 用 2033-2041、
--      V11 用 2050，本迁移从 2051 起；角色权限关联沿用 V11 的幂等写法。
-- 本脚本幂等可重复执行。
-- ================================================================

-- 1. 权限码
INSERT INTO sys_permission (id, name, code, description, type, status) VALUES
    (2051, 'API调用日志列表', 'system:api-call-log:list', '查询对外API调用日志（分页）', 1, 1),
    (2052, 'API调用日志详情', 'system:api-call-log:query', '查询API调用日志详情', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = 1;

-- 2. 授予 ADMIN 角色（表无唯一键且 id 非自增，按存在性防重保证幂等）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10051, 1, 2051
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2051);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10052, 1, 2052
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2052);

-- 3. 菜单项（归入「监控中心」目录，component 对应前端视图）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1102, 1100, 'API调用日志', 1, 'api-call-log', 'monitor/api-call-log/index', 'system:api-call-log:list', 'Link', 4, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = 'API调用日志', parent_id = 1100, menu_type = 1, path = 'api-call-log',
                        component = 'monitor/api-call-log/index', perms = 'system:api-call-log:list',
                        icon = 'Link', sort = 4, visible = 1, status = 1;
-- 脚本用途：为用户、用户角色及可选业务表补充租户隔离字段和索引。
-- 业务背景：配合 MyBatis-Plus 租户拦截器建立 SaaS 数据隔离的首批结构基础。
-- 前置依赖：V1-V13 已完成；sys_user、sys_user_role 存在且尚无目标列与索引。
-- 影响范围：直接变更两张核心表，并按表存在性条件处理 operation_log、system_message。
-- 幂等性：核心表 ALTER/CREATE INDEX 非幂等；可选表由存储过程防重复，整体不可重跑。
-- MySQL/Flyway 兼容性：依赖 MySQL 8.x 存储过程、DELIMITER 与 information_schema；需验证 Flyway 解析配置。
-- 数据风险：新增列默认 NULL，不等于完成存量租户归属；DDL 可能锁表并增加复制延迟。
-- 运维注意：执行前备份并评估表规模，执行后必须核对索引、回填策略和隔离规则生效情况。
-- 迁移元信息：版本 V14（发布标识 1.2.0）；执行阶段为多租户结构第一阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ============================================================
-- V14: 多租户 tenant_id 字段补齐
-- 版本: 1.2.0
-- 说明: 为核心业务表增加 tenant_id 列，配合 MyBatis-Plus
--       TenantLineInnerInterceptor 实现 SaaS 级租户数据隔离。
--       系统配置类表（sys_menu/sys_permission/sys_dict* 等）不加租户列，
--       在 TenantLineHandler 中通过 ignoreTable 显式排除。
-- ============================================================

-- sys_user：用户表（核心租户隔离维度）
ALTER TABLE `sys_user`
    ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT '租户ID（多租户隔离）' AFTER `deleted`;
CREATE INDEX `idx_sys_user_tenant_id` ON `sys_user` (`tenant_id`);

-- sys_user_role：用户角色关联表
ALTER TABLE `sys_user_role`
    ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT '租户ID（多租户隔离）' AFTER `deleted`;
CREATE INDEX `idx_user_role_tenant_id` ON `sys_user_role` (`tenant_id`);

-- operation_log：操作审计日志（已有表根据实际情况执行）
-- 注意: 若 V12 已删除 audit_log，则以下操作对 operation_log 生效
-- operation_log 在 V1__init_schema 中未出现（审计日志独立于审计操作日志），
-- 此处仅针对 operation_log 表存在的实例执行；使用存储过程兜底，避免 Flyway 在
-- 不同分支（如已经过 V12 drop audit_log）上失败。

DELIMITER ;;
CREATE PROCEDURE IF NOT EXISTS add_tenant_id_if_not_exists(IN p_table VARCHAR(128), IN p_after_col VARCHAR(128))
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = 'tenant_id'
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `', p_after_col, '`');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET @idx_sql = CONCAT('CREATE INDEX `idx_', p_table, '_tenant_id` ON `', p_table, '` (`tenant_id`)');
        PREPARE idx_stmt FROM @idx_sql;
        EXECUTE idx_stmt;
        DEALLOCATE PREPARE idx_stmt;
    END IF;
END ;;
DELIMITER ;

CALL add_tenant_id_if_not_exists('operation_log', 'deleted');
CALL add_tenant_id_if_not_exists('system_message', 'deleted');

DROP PROCEDURE IF EXISTS add_tenant_id_if_not_exists;
-- 脚本用途：补齐 V1 核心业务表的 tenant_id 字段及配套索引。
-- 业务背景：承接 V14 的首批改造，扩大租户隔离覆盖面并兼容不同分支的实际表结构。
-- 前置依赖：V1-V14 已按序完成，执行账号具备 information_schema 查询及 ALTER、CREATE INDEX 权限。
-- 影响范围：仅对实际存在且缺少目标对象的核心表执行条件 DDL，不处理系统配置类表。
-- 幂等性：列和索引均经元数据判断，设计为可重复执行；并发迁移仍可能发生竞态冲突。
-- MySQL/Flyway 兼容性：面向 MySQL 8.x，使用会话变量及 PREPARE，由 Flyway JDBC 逐句执行。
-- 数据风险：新增列默认 NULL，需另行治理存量租户归属；多表 DDL 可能锁表、耗用空间并延迟复制。
-- 运维注意：在低峰期执行并监控锁等待，完成后逐表核对列、索引、回填及租户隔离效果。
-- 迁移元信息：版本 V15；执行阶段为多租户结构补全与覆盖收口阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- Apex Scaffold V15 迁移：补齐核心业务表租户隔离字段与索引
-- ================================================================
-- 用途：为 V1 基线中的核心业务表补齐 tenant_id 及对应索引。
-- 依赖：V14 已为 sys_user、sys_user_role 创建 tenant_id 和索引；本迁移
--       使用信息_schema 条件判断，兼容已执行 V14 的数据库与不同分支表结构。
-- 兼容性：不使用 DELIMITER、存储过程或客户端脚本扩展；每组 SET/PREPARE/
--       EXECUTE/DEALLOCATE 语句均可由 Flyway JDBC 按单条语句执行。
-- 约束：系统配置类表不在本迁移范围内；biz_admin_review_audit 的 tenant_id
--       位于主键 id 之后，其余表位于 deleted 之后。
-- ================================================================

-- biz_credit_tx
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_tx') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_tx' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_credit_tx` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_tx' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_tx' AND INDEX_NAME = 'idx_biz_credit_tx_tenant_id'), 'CREATE INDEX `idx_biz_credit_tx_tenant_id` ON `biz_credit_tx` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- sys_operation_log
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_operation_log') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_operation_log' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_operation_log` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_operation_log' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_operation_log' AND INDEX_NAME = 'idx_sys_operation_log_tenant_id'), 'CREATE INDEX `idx_sys_operation_log_tenant_id` ON `sys_operation_log` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- biz_sample
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_sample') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_sample' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_sample` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_sample' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_sample' AND INDEX_NAME = 'idx_biz_sample_tenant_id'), 'CREATE INDEX `idx_biz_sample_tenant_id` ON `biz_sample` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- biz_system_message
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_system_message') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_system_message' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_system_message` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_system_message' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_system_message' AND INDEX_NAME = 'idx_biz_system_message_tenant_id'), 'CREATE INDEX `idx_biz_system_message_tenant_id` ON `biz_system_message` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- sys_user_role
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_user_role` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role' AND INDEX_NAME = 'idx_user_role_tenant_id'), 'CREATE INDEX `idx_user_role_tenant_id` ON `sys_user_role` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- sys_account_deletion_task
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_account_deletion_task') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_account_deletion_task' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_account_deletion_task` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_account_deletion_task' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_account_deletion_task' AND INDEX_NAME = 'idx_sys_account_deletion_task_tenant_id'), 'CREATE INDEX `idx_sys_account_deletion_task_tenant_id` ON `sys_account_deletion_task` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 其余 V1 核心业务表：逐表执行条件 DDL，避免重复列或索引导致迁移失败。
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_account') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_account' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_credit_account` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_account' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_credit_account' AND INDEX_NAME = 'idx_biz_credit_account_tenant_id'), 'CREATE INDEX `idx_biz_credit_account_tenant_id` ON `biz_credit_account` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_consent') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_consent' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_user_consent` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_consent' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_consent' AND INDEX_NAME = 'idx_biz_user_consent_tenant_id'), 'CREATE INDEX `idx_biz_user_consent_tenant_id` ON `biz_user_consent` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_api_call_log') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_api_call_log' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_api_call_log` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_api_call_log' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_api_call_log' AND INDEX_NAME = 'idx_sys_api_call_log_tenant_id'), 'CREATE INDEX `idx_sys_api_call_log_tenant_id` ON `sys_api_call_log` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_content_moderation_review') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_content_moderation_review' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_content_moderation_review` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_content_moderation_review' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_content_moderation_review' AND INDEX_NAME = 'idx_biz_content_moderation_review_tenant_id'), 'CREATE INDEX `idx_biz_content_moderation_review_tenant_id` ON `biz_content_moderation_review` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_quiz_question') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_quiz_question' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_quiz_question` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_quiz_question' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_quiz_question' AND INDEX_NAME = 'idx_biz_quiz_question_tenant_id'), 'CREATE INDEX `idx_biz_quiz_question_tenant_id` ON `biz_quiz_question` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report_ticket') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report_ticket' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_report_ticket` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report_ticket' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report_ticket' AND INDEX_NAME = 'idx_biz_report_ticket_tenant_id'), 'CREATE INDEX `idx_biz_report_ticket_tenant_id` ON `biz_report_ticket` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_user` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_sys_user_tenant_id'), 'CREATE INDEX `idx_sys_user_tenant_id` ON `sys_user` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_compliance') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_compliance' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_user_compliance` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_compliance' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_compliance' AND INDEX_NAME = 'idx_biz_user_compliance_tenant_id'), 'CREATE INDEX `idx_biz_user_compliance_tenant_id` ON `biz_user_compliance` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_policy_version') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_policy_version' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_policy_version` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_policy_version' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_policy_version' AND INDEX_NAME = 'idx_biz_policy_version_tenant_id'), 'CREATE INDEX `idx_biz_policy_version_tenant_id` ON `biz_policy_version` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_subscription') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_subscription' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_subscription` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_subscription' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_subscription' AND INDEX_NAME = 'idx_biz_subscription_tenant_id'), 'CREATE INDEX `idx_biz_subscription_tenant_id` ON `biz_subscription` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_blacklist') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_blacklist' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_blacklist` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_blacklist' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_blacklist' AND INDEX_NAME = 'idx_biz_blacklist_tenant_id'), 'CREATE INDEX `idx_biz_blacklist_tenant_id` ON `biz_blacklist` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_profile') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_profile' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_user_profile` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_profile' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_user_profile' AND INDEX_NAME = 'idx_biz_user_profile_tenant_id'), 'CREATE INDEX `idx_biz_user_profile_tenant_id` ON `biz_user_profile` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_reason') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_reason' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_admin_review_reason` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_reason' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_reason' AND INDEX_NAME = 'idx_biz_admin_review_reason_tenant_id'), 'CREATE INDEX `idx_biz_admin_review_reason_tenant_id` ON `biz_admin_review_reason` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_report` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_report' AND INDEX_NAME = 'idx_biz_report_tenant_id'), 'CREATE INDEX `idx_biz_report_tenant_id` ON `biz_report` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_file` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND INDEX_NAME = 'idx_sys_file_tenant_id'), 'CREATE INDEX `idx_sys_file_tenant_id` ON `sys_file` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_log') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_log' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_login_log` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_log' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_login_log' AND INDEX_NAME = 'idx_sys_login_log_tenant_id'), 'CREATE INDEX `idx_sys_login_log_tenant_id` ON `sys_login_log` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- biz_admin_review_audit 的 tenant_id 紧随主键 id，单独处理列顺序。
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_audit') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_audit' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `biz_admin_review_audit` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `id`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_audit' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_admin_review_audit' AND INDEX_NAME = 'idx_biz_admin_review_audit_tenant_id'), 'CREATE INDEX `idx_biz_admin_review_audit_tenant_id` ON `biz_admin_review_audit` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
-- 脚本用途：为定时任务及任务日志补充租户隔离字段与索引。
-- 幂等性：通过 information_schema 检查目标表、列和索引后再执行 DDL。
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_job` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job' AND INDEX_NAME = 'idx_job_tenant_id'), 'CREATE INDEX `idx_job_tenant_id` ON `sys_job` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job' AND INDEX_NAME = 'idx_job_tenant_status'), 'CREATE INDEX `idx_job_tenant_status` ON `sys_job` (`tenant_id`, `status`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job_log') AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job_log' AND COLUMN_NAME = 'tenant_id'), 'ALTER TABLE `sys_job_log` ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT ''租户ID（多租户隔离）'' AFTER `deleted`', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job_log' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job_log' AND INDEX_NAME = 'idx_job_log_tenant_id'), 'CREATE INDEX `idx_job_log_tenant_id` ON `sys_job_log` (`tenant_id`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sql = IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job_log' AND COLUMN_NAME = 'tenant_id') AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_job_log' AND INDEX_NAME = 'idx_job_log_tenant_job_start'), 'CREATE INDEX `idx_job_log_tenant_job_start` ON `sys_job_log` (`tenant_id`, `job_id`, `start_time`)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
-- ============================================================
-- V17: 租户模型与生命周期表
-- 版本: 1.3.0
-- 说明: 创建 sys_tenant 表，承载 TENANT-001 租户基础信息、
--       状态生命周期与配额约束。sys_tenant 为平台级元数据表，
--       已在 DefaultTenantLineHandler.IGNORE_TABLES 中显式排除，
--       不参与租户行级隔离，由平台管理员统一维护。
-- 前置依赖: V1-V16 已完成。
-- 幂等性: CREATE TABLE IF NOT EXISTS 幂等。
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_tenant` (
    `id`              BIGINT       NOT NULL                 COMMENT '主键ID（雪花算法）',
    `tenant_code`     VARCHAR(64)  NOT NULL                 COMMENT '租户编码（全局唯一，作为租户上下文ID）',
    `tenant_name`     VARCHAR(128) NOT NULL                 COMMENT '租户名称',
    `admin_user_id`   VARCHAR(64)  DEFAULT NULL             COMMENT '租户管理员用户ID',
    `package_code`    VARCHAR(64)  DEFAULT NULL             COMMENT '套餐标识',
    `expire_time`     DATETIME     DEFAULT NULL             COMMENT '有效期至（NULL表示长期有效）',
    `status`          VARCHAR(32)  NOT NULL                 COMMENT '租户状态（ACTIVE/DISABLED/FROZEN/DEACTIVATED/ARCHIVED）',
    `max_users`       INT          DEFAULT NULL             COMMENT '最大用户数（NULL表示不限制）',
    `max_storage_mb`  INT          DEFAULT NULL             COMMENT '最大存储容量MB（NULL表示不限制）',
    `remark`          VARCHAR(500) DEFAULT NULL             COMMENT '备注',
    `create_time`     DATETIME     NOT NULL                 COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL                 COMMENT '更新时间',
    `version`         INT          NOT NULL DEFAULT 0       COMMENT '乐观锁版本号',
    `deleted`         INT          NOT NULL DEFAULT 0       COMMENT '逻辑删除标记（0-未删除，1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_tenant_code` (`tenant_code`),
    KEY `idx_sys_tenant_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户基础信息与生命周期表（平台级元数据）';
-- V18: 显式角色数据权限与角色部门授权关系
-- data_scope 允许为空，未配置的角色必须由授权解析器拒绝。

ALTER TABLE `sys_role`
    ADD COLUMN `data_scope` INT DEFAULT NULL COMMENT '数据权限类型（1-全部，2-本部门，3-本部门及以下，4-本人，5-自定义）' AFTER `status`;
ALTER TABLE `sys_role`
    ADD CONSTRAINT `chk_sys_role_data_scope`
    CHECK (`data_scope` IS NULL OR `data_scope` BETWEEN 1 AND 5);
CREATE INDEX `idx_sys_role_data_scope` ON `sys_role` (`data_scope`);

ALTER TABLE `sys_dept`
    ADD COLUMN `tenant_id` VARCHAR(64) DEFAULT NULL COMMENT '租户ID（多租户隔离）' AFTER `deleted`;
CREATE INDEX `idx_sys_dept_tenant_id` ON `sys_dept` (`tenant_id`);

CREATE TABLE IF NOT EXISTS `sys_role_dept` (
    `id`          BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
    `role_id`     BIGINT       NOT NULL COMMENT '角色ID',
    `dept_id`     BIGINT       NOT NULL COMMENT '部门ID',
    `tenant_id`   VARCHAR(64)  NOT NULL COMMENT '租户ID（多租户隔离）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    `deleted`     INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除标记（0-未删除，1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_role_dept_scope` (`tenant_id`, `role_id`, `dept_id`),
    KEY `idx_sys_role_dept_tenant_id` (`tenant_id`),
    KEY `idx_sys_role_dept_role_id` (`role_id`),
    KEY `idx_sys_role_dept_dept_id` (`dept_id`),
    CONSTRAINT `chk_sys_role_dept_deleted` CHECK (`deleted` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色自定义部门数据权限关系表';
-- 脚本用途：清理历史空租户部门并强制部门与授权关系的同租户完整性。
-- 风险控制：无法唯一确认历史种子部门归属时终止迁移，禁止静默分配租户。
DROP PROCEDURE IF EXISTS `enforce_department_tenant_integrity`;

DELIMITER //
CREATE PROCEDURE `enforce_department_tenant_integrity`()
BEGIN
    DECLARE seed_dept_count INT DEFAULT 0;
    DECLARE seed_admin_count INT DEFAULT 0;
    DECLARE other_user_reference_count INT DEFAULT 0;
    DECLARE role_dept_reference_count INT DEFAULT 0;
    DECLARE unassigned_dept_count INT DEFAULT 0;

    SELECT COUNT(*) INTO seed_dept_count
    FROM `sys_dept`
    WHERE `id` = 1
      AND `parent_id` = 0
      AND `ancestors` = '0'
      AND `name` = 'Apex 科技'
      AND `tenant_id` IS NULL;

    IF seed_dept_count = 1 THEN
        SELECT COUNT(*) INTO seed_admin_count
        FROM `sys_user`
        WHERE `id` = 1
          AND `open_id` = 'admin-openid'
          AND `username` = 'admin'
          AND `dept_id` = 1;

        SELECT COUNT(*) INTO other_user_reference_count
        FROM `sys_user`
        WHERE `dept_id` = 1
          AND NOT (`id` = 1 AND `open_id` = 'admin-openid' AND `username` = 'admin');

        SELECT COUNT(*) INTO role_dept_reference_count
        FROM `sys_role_dept`
        WHERE `dept_id` = 1;

        IF seed_admin_count = 1
                AND other_user_reference_count = 0
                AND role_dept_reference_count = 0 THEN
            UPDATE `sys_user`
            SET `dept_id` = NULL
            WHERE `id` = 1
              AND `open_id` = 'admin-openid'
              AND `username` = 'admin'
              AND `dept_id` = 1;

            DELETE FROM `sys_dept`
            WHERE `id` = 1
              AND `parent_id` = 0
              AND `ancestors` = '0'
              AND `name` = 'Apex 科技'
              AND `tenant_id` IS NULL;
        ELSE
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'V19 cannot safely remove the legacy seed department because its references are ambiguous';
        END IF;
    END IF;

    SELECT COUNT(*) INTO unassigned_dept_count
    FROM `sys_dept`
    WHERE `tenant_id` IS NULL;

    IF unassigned_dept_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V19 requires every department to have an explicit tenant_id before migration';
    END IF;
END//
DELIMITER ;

CALL `enforce_department_tenant_integrity`();
DROP PROCEDURE IF EXISTS `enforce_department_tenant_integrity`;

ALTER TABLE `sys_dept`
    MODIFY COLUMN `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID（多租户隔离）',
    ADD UNIQUE KEY `uk_sys_dept_tenant_id_id` (`tenant_id`, `id`);

ALTER TABLE `sys_role_dept`
    ADD CONSTRAINT `fk_sys_role_dept_tenant_dept`
    FOREIGN KEY (`tenant_id`, `dept_id`)
    REFERENCES `sys_dept` (`tenant_id`, `id`);
-- 脚本用途：初始化系统管理权限码与后台菜单种子数据。
-- 业务背景：让角色授权和管理后台导航在基础表结构落地后具备可用的默认配置。
-- 前置依赖：V1 已成功创建 sys_permission、sys_menu 等目标表，且显式主键未被占用。
-- 影响范围：向权限表和菜单表写入固定 ID、权限码及导航配置，不修改表结构。
-- 幂等性：普通 INSERT 不具备重复执行幂等性；应仅由 Flyway 按版本执行一次。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x 常规 DML 与 CURRENT_TIMESTAMP，按 Flyway V2 顺序执行。
-- 数据风险：重复执行或目标 ID/唯一键冲突会失败；错误种子值会直接影响授权与菜单展示。
-- 运维注意：执行前核对 ID 号段和权限码占用，执行后检查 Flyway 历史及种子关联完整性。
-- 迁移元信息：版本 V2；执行阶段为 V1 建表后的基础权限与菜单初始化。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- V2: 系统管理权限码与菜单种子数据
-- ================================================================
-- 用途: 预置系统管理权限码（system:*）与后台菜单树，使管理后台
--       部署后即可在"角色-权限"配置中直接引用，无需手工录入。
-- 背景: P0 阶段新增菜单/字典/参数/定时任务/登录日志能力后，
--       权限点与菜单导航的统一初始化。
-- 说明: 使用显式 ID（1000+ / 2000+），避免与雪花算法主键冲突；
--       由 Flyway 记录版本，仅执行一次。
-- ================================================================

-- ------------------------------------------------------------------
-- 1. 系统管理权限码（sys_permission）
-- ------------------------------------------------------------------
INSERT INTO sys_permission (id, name, code, description, type, status, create_time, update_time, version, deleted) VALUES
(2001, '员工管理', 'staff:manage', '后台员工创建/编辑/启停/角色配置', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2002, '会话撤销', 'staff:session:revoke', '强制撤销指定用户全部会话', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2003, '菜单列表', 'system:menu:list', '查询系统菜单树', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2004, '菜单查询', 'system:menu:query', '查询菜单详情', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2005, '菜单新增', 'system:menu:add', '创建系统菜单', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2006, '菜单编辑', 'system:menu:edit', '更新系统菜单', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2007, '菜单删除', 'system:menu:remove', '删除系统菜单', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2008, '字典列表', 'system:dict:list', '查询字典类型与数据', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2009, '字典查询', 'system:dict:query', '查询字典详情', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2010, '字典新增', 'system:dict:add', '创建字典类型与数据', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2011, '字典编辑', 'system:dict:edit', '更新字典类型与数据', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2012, '字典删除', 'system:dict:remove', '删除字典类型与数据', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2013, '参数列表', 'system:config:list', '查询系统参数配置', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2014, '参数查询', 'system:config:query', '查询参数配置详情', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2015, '参数新增', 'system:config:add', '创建系统参数', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2016, '参数编辑', 'system:config:edit', '更新系统参数', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2017, '参数删除', 'system:config:remove', '删除系统参数', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2018, '任务列表', 'system:job:list', '查询定时任务', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2019, '任务查询', 'system:job:query', '查询任务详情与执行日志', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2020, '任务新增', 'system:job:add', '创建定时任务', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2021, '任务编辑', 'system:job:edit', '更新定时任务', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2022, '任务删除', 'system:job:remove', '删除定时任务', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2023, '任务执行', 'system:job:run', '手动触发定时任务', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2024, '登录日志', 'system:login-log:list', '查询登录日志', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

-- ------------------------------------------------------------------
-- 2. 系统管理菜单树（sys_menu）
-- ------------------------------------------------------------------
-- 2.1 系统管理目录
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted) VALUES
(1000, 0, '系统管理', 0, 'system', 'system/index', NULL, 'Setting', 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

-- 2.2 系统管理子菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, version, deleted) VALUES
(1001, 1000, '用户管理', 1, 'system/user', 'system/user/index', 'staff:manage', 'User', 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(1002, 1000, '菜单管理', 1, 'system/menu', 'system/menu/index', 'system:menu:list', 'Menu', 2, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(1003, 1000, '字典管理', 1, 'system/dict', 'system/dict/index', 'system:dict:list', 'Collection', 3, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(1004, 1000, '参数配置', 1, 'system/config', 'system/config/index', 'system:config:list', 'Tools', 4, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(1005, 1000, '定时任务', 1, 'system/job', 'system/job/index', 'system:job:list', 'Timer', 5, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(1006, 1000, '登录日志', 1, 'system/login-log', 'system/login-log/index', 'system:login-log:list', 'Document', 6, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);
-- 脚本用途：创建轻量工作流引擎的核心表（工作流定义、流程实例、任务、审批记录）
-- 版本：v1.3.0 新增 Enterprise 版商业化卖点
-- 依赖：License 特性门控 workflow（仅 Enterprise 版可用）

-- 工作流定义表
CREATE TABLE `wf_workflow_definition` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `workflow_code` VARCHAR(100) NOT NULL COMMENT '工作流编码（唯一标识）',
    `workflow_name` VARCHAR(200) NOT NULL COMMENT '工作流名称',
    `version` INT NOT NULL DEFAULT 1 COMMENT '版本号',
    `node_config_json` TEXT NOT NULL COMMENT '节点配置JSON（DAG定义）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT/ACTIVE/SUSPENDED/ARCHIVED）',
    `description` VARCHAR(500) COMMENT '描述',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID（多租户隔离）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_workflow_code_version` (`workflow_code`, `version`, `deleted`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定义表';

-- 流程实例表
CREATE TABLE `wf_process_instance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `workflow_definition_id` BIGINT NOT NULL COMMENT '工作流定义ID',
    `business_key` VARCHAR(200) NOT NULL COMMENT '业务主键（外部关联键）',
    `current_node_key` VARCHAR(100) COMMENT '当前节点KEY',
    `status` VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT '状态（RUNNING/COMPLETED/REJECTED/CANCELLED/SUSPENDED）',
    `initiator_id` BIGINT NOT NULL COMMENT '发起人ID',
    `context_json` TEXT COMMENT '流程上下文JSON（变量、条件等）',
    `start_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID（多租户隔离）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_business_key` (`business_key`, `deleted`),
    KEY `idx_workflow_def` (`workflow_definition_id`),
    KEY `idx_tenant_initiator` (`tenant_id`, `initiator_id`),
    KEY `idx_status` (`status`),
    KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程实例表';

-- 任务表
CREATE TABLE `wf_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `process_instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `node_key` VARCHAR(100) NOT NULL COMMENT '节点KEY',
    `node_name` VARCHAR(200) NOT NULL COMMENT '节点名称',
    `assignee_id` BIGINT NOT NULL COMMENT '办理人ID',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING/COMPLETED/REJECTED/CANCELLED/TRANSFERRED）',
    `due_date` DATETIME COMMENT '到期时间',
    `completed_time` DATETIME COMMENT '完成时间',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID（多租户隔离）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_process_instance` (`process_instance_id`),
    KEY `idx_tenant_assignee_status` (`tenant_id`, `assignee_id`, `status`),
    KEY `idx_due_date` (`due_date`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

-- 审批记录表
CREATE TABLE `wf_approval_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `process_instance_id` BIGINT NOT NULL COMMENT '流程实例ID',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `approver_id` BIGINT NOT NULL COMMENT '审批人ID',
    `action` VARCHAR(20) NOT NULL COMMENT '操作（APPROVE/REJECT/TRANSFER/CANCEL）',
    `comment` VARCHAR(1000) COMMENT '审批意见',
    `tenant_id` VARCHAR(64) NOT NULL COMMENT '租户ID（多租户隔离）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_process_instance` (`process_instance_id`),
    KEY `idx_task` (`task_id`),
    KEY `idx_tenant_approver` (`tenant_id`, `approver_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录表';

-- 添加外键约束
ALTER TABLE `wf_process_instance`
    ADD CONSTRAINT `fk_process_workflow_def`
    FOREIGN KEY (`workflow_definition_id`)
    REFERENCES `wf_workflow_definition` (`id`);

ALTER TABLE `wf_task`
    ADD CONSTRAINT `fk_task_process_instance`
    FOREIGN KEY (`process_instance_id`)
    REFERENCES `wf_process_instance` (`id`);

ALTER TABLE `wf_approval_record`
    ADD CONSTRAINT `fk_approval_process_instance`
    FOREIGN KEY (`process_instance_id`)
    REFERENCES `wf_process_instance` (`id`),
    ADD CONSTRAINT `fk_approval_task`
    FOREIGN KEY (`task_id`)
    REFERENCES `wf_task` (`id`);
-- ============================================================
-- Flyway 迁移脚本 V21：增强工作流表
-- ============================================================
-- 执行日期: 2026-09-04
-- 目标: 为工作流表添加高级功能支持（回退、转办、挂起、超时处理）
-- ============================================================

-- 为流程实例表添加挂起相关字段
ALTER TABLE wf_process_instance
    ADD COLUMN suspended TINYINT(1) DEFAULT 0 COMMENT '是否挂起: 0-否 1-是',
    ADD COLUMN suspend_reason VARCHAR(500) COMMENT '挂起原因',
    ADD COLUMN suspend_time DATETIME COMMENT '挂起时间',
    ADD COLUMN resume_time DATETIME COMMENT '恢复时间';

-- 为流程实例表添加状态索引（加速按状态查询）
CREATE INDEX idx_process_instance_status ON wf_process_instance(status);

-- 为流程实例表添加挂起状态索引
CREATE INDEX idx_process_instance_suspended ON wf_process_instance(suspended);

-- 为任务表添加超时相关字段
ALTER TABLE wf_task
    ADD COLUMN timeout_at DATETIME COMMENT '超时时间',
    ADD COLUMN timeout_action VARCHAR(50) COMMENT '超时动作: AUTO_APPROVE/AUTO_REJECT/REMIND',
    ADD COLUMN timeout_handled TINYINT(1) DEFAULT 0 COMMENT '是否已处理超时: 0-否 1-是';

-- 为任务表添加转办/委派相关字段
ALTER TABLE wf_task
    ADD COLUMN transferred_from BIGINT COMMENT '转办来源用户ID',
    ADD COLUMN transferred_time DATETIME COMMENT '转办时间',
    ADD COLUMN delegated_from BIGINT COMMENT '委派来源用户ID',
    ADD COLUMN delegated_time DATETIME COMMENT '委派时间',
    ADD COLUMN delegation_completed TINYINT(1) DEFAULT 0 COMMENT '委派是否已完成: 0-否 1-是';

-- 为任务表添加复合索引（加速待办任务查询）
CREATE INDEX idx_task_status_assignee ON wf_task(status, assignee_id);

-- 为任务表添加超时任务查询索引
CREATE INDEX idx_task_timeout ON wf_task(timeout_at, timeout_handled);

-- 为任务表添加转办来源索引
CREATE INDEX idx_task_transferred_from ON wf_task(transferred_from);

-- 为审批记录表添加索引（加速按实例ID查询）
CREATE INDEX idx_approval_record_instance ON wf_approval_record(process_instance_id);

-- 为审批记录表添加审批人索引
CREATE INDEX idx_approval_record_approver ON wf_approval_record(approver_id);

-- 创建任务历史表（用于记录任务流转历史）
CREATE TABLE IF NOT EXISTS wf_task_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    process_instance_id BIGINT NOT NULL COMMENT '流程实例ID',
    action_type VARCHAR(50) NOT NULL COMMENT '动作类型: CREATE/COMPLETE/REJECT/TRANSFER/DELEGATE/TIMEOUT',
    operator_id BIGINT COMMENT '操作人ID',
    operator_name VARCHAR(100) COMMENT '操作人姓名',
    from_status VARCHAR(50) COMMENT '原状态',
    to_status VARCHAR(50) COMMENT '目标状态',
    comment VARCHAR(500) COMMENT '操作备注',
    action_time DATETIME NOT NULL COMMENT '操作时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    INDEX idx_task_history_task_id (task_id),
    INDEX idx_task_history_instance_id (process_instance_id),
    INDEX idx_task_history_action_time (action_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务历史记录表';

-- 创建流程回退历史表
CREATE TABLE IF NOT EXISTS wf_rollback_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    process_instance_id BIGINT NOT NULL COMMENT '流程实例ID',
    from_node_id VARCHAR(100) NOT NULL COMMENT '回退前节点ID',
    to_node_id VARCHAR(100) NOT NULL COMMENT '回退目标节点ID',
    rollback_reason VARCHAR(500) COMMENT '回退原因',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(100) COMMENT '操作人姓名',
    rollback_time DATETIME NOT NULL COMMENT '回退时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    INDEX idx_rollback_history_instance (process_instance_id),
    INDEX idx_rollback_history_time (rollback_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程回退历史表';

-- 为 wf_definition 表添加版本管理字段（支持未来版本管理功能）
ALTER TABLE wf_definition
    ADD COLUMN version INT DEFAULT 1 COMMENT '版本号',
    ADD COLUMN is_latest TINYINT(1) DEFAULT 1 COMMENT '是否最新版本: 0-否 1-是',
    ADD COLUMN previous_version_id BIGINT COMMENT '前一版本ID';

-- 为 wf_definition 表添加版本索引
CREATE INDEX idx_definition_code_version ON wf_definition(workflow_code, version);
CREATE INDEX idx_definition_is_latest ON wf_definition(is_latest);

-- 插入配置说明数据（可选，用于记录迁移信息）
INSERT INTO sys_config (config_key, config_value, config_type, config_desc, create_time, update_time)
VALUES ('workflow.version', 'v1.4.0', 'system', '工作流引擎版本：支持回退、转办、挂起、超时处理', NOW(), NOW())
ON DUPLICATE KEY UPDATE config_value = 'v1.4.0', config_desc = '工作流引擎版本：支持回退、转办、挂起、超时处理', update_time = NOW();

-- ============================================================
-- 迁移说明
-- ============================================================
-- 1. 本迁移脚本为工作流引擎添加了高级功能支持
-- 2. 所有 ALTER TABLE 操作使用 ADD COLUMN，对现有数据无影响
-- 3. 索引优化可显著提升查询性能（特别是待办任务列表）
-- 4. 新增的历史表用于审计和问题排查
-- 5. 版本管理字段为未来流程定义版本管理预留扩展空间
-- ============================================================
-- 脚本用途：初始化全文搜索与日志查询系统的权限、管理员授权及菜单入口。
-- 业务背景：为全文搜索、运行时日志查询、业务日志统一查询提供完整授权导航。
-- 前置依赖：V11 已创建监控中心，V13 已创建API调用日志权限，目标表与 ADMIN 角色均可用。
-- 影响范围：写入权限、角色权限关联和菜单配置，不变更表结构及数据。
-- 幂等性：使用 ON DUPLICATE KEY UPDATE 与 NOT EXISTS 条件插入，设计为可重复执行。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x DML、VALUES() 与 FROM DUAL，由 Flyway V22 执行。
-- 数据风险：固定 ID 冲突或错误授权会造成迁移失败、越权访问或菜单异常。
-- 运维注意：执行前核对权限 ID 号段，执行后验证 ADMIN 授权与菜单访问。
-- 迁移元信息：版本 V22；执行阶段为全文搜索与日志查询系统的权限与菜单上线阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- Apex Scaffold V22 迁移：全文搜索与日志查询权限与菜单
-- ================================================================
-- 说明：为全文搜索（Meilisearch）、运行时日志查询（Loki）、
--      业务日志统一查询补充权限码、ADMIN 角色授权与菜单项。
-- 依赖：V11 已创建「监控中心」目录（id=1100）。
-- 权限码：V4 用 2025-2026、V5 用 2027、V6 用 2028、V8 用 2033-2041、
--      V11 用 2050、V13 用 2051-2052，本迁移从 2053 起。
-- 菜单ID：V11 用 1101、V13 用 1102，本迁移从 1103 起。
-- 角色权限ID：V13 用 10051-10052，本迁移从 10053 起。
-- 本脚本幂等可重复执行。
-- ================================================================

-- ================================================================
-- 一、全文搜索权限与菜单
-- ================================================================

-- 1.1 权限码
INSERT INTO sys_permission (id, name, code, description, type, status) VALUES
    (2053, '全文搜索', 'system:search:query', '全文搜索功能（用户/角色/菜单/部门/字典/配置）', 1, 1),
    (2054, '索引重建', 'system:search:rebuild', '重建搜索索引', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = 1;

-- 1.2 授予 ADMIN 角色
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10053, 1, 2053
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2053);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10054, 1, 2054
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2054);

-- 1.3 菜单项（全局搜索在导航栏，不需要单独菜单项，此处仅记录权限）

-- ================================================================
-- 二、运行时日志查询权限与菜单
-- ================================================================

-- 2.1 权限码
INSERT INTO sys_permission (id, name, code, description, type, status) VALUES
    (2055, '运行时日志查询', 'system:log:runtime:query', '查询应用运行时日志（Loki）', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = 1;

-- 2.2 授予 ADMIN 角色
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10055, 1, 2055
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2055);

-- 2.3 菜单项（归入「监控中心」目录）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1103, 1100, '运行时日志', 1, 'runtime-log', 'monitor/runtime-log/index', 'system:log:runtime:query', 'DocumentCopy', 5, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = '运行时日志', parent_id = 1100, menu_type = 1, path = 'runtime-log',
                        component = 'monitor/runtime-log/index', perms = 'system:log:runtime:query',
                        icon = 'DocumentCopy', sort = 5, visible = 1, status = 1;

-- ================================================================
-- 三、业务日志统一查询权限与菜单
-- ================================================================

-- 3.1 权限码
INSERT INTO sys_permission (id, name, code, description, type, status) VALUES
    (2056, '业务日志查询', 'system:log:business:query', '统一查询业务日志（登录/操作/API）', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = 1;

-- 3.2 授予 ADMIN 角色
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 10056, 1, 2056
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE role_id = 1 AND permission_id = 2056);

-- 3.3 菜单项（归入「监控中心」目录）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1104, 1100, '业务日志', 1, 'business-log', 'monitor/business-log/index', 'system:log:business:query', 'List', 6, 1, 1)
ON DUPLICATE KEY UPDATE menu_name = '业务日志', parent_id = 1100, menu_type = 1, path = 'business-log',
                        component = 'monitor/business-log/index', perms = 'system:log:business:query',
                        icon = 'List', sort = 6, visible = 1, status = 1;

-- ================================================================
-- 迁移完成说明
-- ================================================================
-- 本迁移添加了：
-- 1. 4个权限码（2053-2056）
-- 2. 4个角色权限关联（10053-10056，授予ADMIN角色）
-- 3. 2个菜单项（1103-1104，归入监控中心）
--
-- API端点映射：
-- - GET /admin-api/v1/search                  → system:search:query
-- - POST /admin-api/v1/search/rebuild         → system:search:rebuild
-- - GET /admin-api/v1/logs/runtime            → system:log:runtime:query
-- - GET /admin-api/v1/logs/business           → system:log:business:query
--
-- 前端页面映射：
-- - 全局搜索：导航栏组件（无独立菜单）
-- - 运行时日志：/monitor/runtime-log
-- - 业务日志：/monitor/business-log
--
-- 验证方法：
-- 1. 查询权限是否插入：SELECT * FROM sys_permission WHERE id BETWEEN 2053 AND 2056;
-- 2. 查询ADMIN角色授权：SELECT * FROM sys_role_permission WHERE id BETWEEN 10053 AND 10056;
-- 3. 查询菜单是否插入：SELECT * FROM sys_menu WHERE id IN (1103, 1104);
-- 4. 登录管理后台，验证菜单可见且可访问
-- ================================================================
-- ============================================================
-- V23: 添加数据库配置菜单和权限
-- ============================================================
-- 背景：支持多数据库部署配置采集功能，需要在系统管理菜单下新增数据库配置页面。
-- 功能：数据库连接测试、部署配置生成（YAML/env/docker-compose）、当前数据库信息查询。
-- 定位：部署配置生成工具，不是运行时数据库管理控制台。
-- 依赖：前端 system/database/index 视图已在 VIEW_REGISTRY 注册。
-- 幂等性：使用 INSERT ... ON DUPLICATE KEY UPDATE 确保可重复执行。
-- ============================================================

-- 1. 添加数据库配置菜单（系统管理目录下）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status, create_time, update_time, deleted)
VALUES (1013, 1000, '数据库配置', 1, 'database', 'system/database/index', 'system:database:view', 'Database', 8, 0, 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    menu_name = '数据库配置',
    parent_id = 1000,
    menu_type = 1,
    path = 'database',
    component = 'system/database/index',
    perms = 'system:database:view',
    icon = 'Database',
    sort = 8,
    status = 0,
    visible = 0,
    update_time = NOW();

-- 2. 添加数据库配置相关权限码
INSERT INTO sys_permission (id, name, code, description, type, status, create_time, update_time, deleted) VALUES
    (2042, '数据库配置页面', 'system:database:view',     '查看数据库配置页面', 1, 1, NOW(), NOW(), 0),
    (2043, '测试数据库连接', 'system:database:test',     '测试数据库连接',     1, 1, NOW(), NOW(), 0),
    (2044, '生成部署配置',   'system:database:generate', '生成部署配置',       1, 1, NOW(), NOW(), 0),
    (2045, '查询数据库信息', 'system:database:info',     '查询当前数据库信息', 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    status = 1,
    update_time = NOW();

-- 3. 为超级管理员角色授予数据库配置权限
-- 注意：仅授予新增的 4 个权限，避免重复插入
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, id
FROM sys_permission
WHERE code IN ('system:database:view', 'system:database:test', 'system:database:generate', 'system:database:info')
  AND status = 1
  AND deleted = 0;
-- 脚本用途：移除所有外键约束，保持功能完整性
-- 版本：v1.4.0
-- 说明：移除外键可以提高写入性能，避免级联删除风险，应用层通过逻辑保证数据完整性

-- 1. 移除部门相关外键约束
ALTER TABLE `sys_role_dept`
    DROP FOREIGN KEY IF EXISTS `fk_sys_role_dept_tenant_dept`;

-- 2. 移除工作流引擎外键约束

-- 2.1 移除流程实例 → 工作流定义外键
ALTER TABLE `wf_process_instance`
    DROP FOREIGN KEY IF EXISTS `fk_process_workflow_def`;

-- 2.2 移除任务 → 流程实例外键
ALTER TABLE `wf_task`
    DROP FOREIGN KEY IF EXISTS `fk_task_process_instance`;

-- 2.3 移除审批记录 → 流程实例外键
ALTER TABLE `wf_approval_record`
    DROP FOREIGN KEY IF EXISTS `fk_approval_process_instance`;

-- 2.4 移除审批记录 → 任务外键
ALTER TABLE `wf_approval_record`
    DROP FOREIGN KEY IF EXISTS `fk_approval_task`;

-- 功能保证说明：
-- 1. sys_role_dept 的租户+部门完整性由应用层代码保证，MyBatis-Plus租户拦截器自动过滤
-- 2. 工作流引擎的关联关系由WorkflowService、WorkflowQueryService等服务层逻辑保证
-- 3. 保留所有索引以确保查询性能不受影响
-- 4. 逻辑删除(deleted字段)防止误删数据
-- ============================================================================
-- Flyway Migration: V25 - OAuth2 Authorization Server Schema
-- Description: 创建OAuth2授权服务器所需的数据表，支持标准OAuth2协议
-- Author: Apex Team
-- Date: 2026-09-08
-- Dependencies: Spring Authorization Server 1.4.0
-- ============================================================================

-- ============================================================================
-- 1. OAuth2 客户端注册表
-- ============================================================================
CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) NOT NULL COMMENT '客户端唯一标识(UUID)',
    client_id VARCHAR(100) NOT NULL COMMENT '客户端ID(对外暴露的标识)',
    client_id_issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '客户端ID签发时间',
    client_secret VARCHAR(200) COMMENT '客户端密钥(BCrypt加密,可为空表示公共客户端)',
    client_secret_expires_at TIMESTAMP NULL COMMENT '客户端密钥过期时间(NULL表示永不过期)',
    client_name VARCHAR(200) NOT NULL COMMENT '客户端名称(用于展示)',
    client_authentication_methods VARCHAR(1000) NOT NULL COMMENT '客户端认证方式(JSON数组): client_secret_basic, client_secret_post, none',
    authorization_grant_types VARCHAR(1000) NOT NULL COMMENT '授权类型(JSON数组): authorization_code, client_credentials, password, refresh_token',
    redirect_uris TEXT COMMENT '授权成功后重定向URI(JSON数组,授权码模式必填)',
    post_logout_redirect_uris TEXT COMMENT '登出后重定向URI(JSON数组)',
    scopes VARCHAR(1000) NOT NULL COMMENT '权限作用域(JSON数组): read, write, user:profile, admin:users等',
    client_settings TEXT NOT NULL COMMENT '客户端配置(JSON对象): require-proof-key, require-authorization-consent等',
    token_settings TEXT NOT NULL COMMENT 'Token配置(JSON对象): access-token-time-to-live, refresh-token-time-to-live等',
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_id (client_id),
    INDEX idx_client_name (client_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2客户端注册表';

-- ============================================================================
-- 2. OAuth2 授权记录表
-- ============================================================================
CREATE TABLE oauth2_authorization (
    id VARCHAR(100) NOT NULL COMMENT '授权记录唯一标识',
    registered_client_id VARCHAR(100) NOT NULL COMMENT '关联的客户端ID(外键)',
    principal_name VARCHAR(200) NOT NULL COMMENT '用户标识(OpenID或用户名)',
    authorization_grant_type VARCHAR(100) NOT NULL COMMENT '授权类型: authorization_code, client_credentials, password, refresh_token',
    authorized_scopes VARCHAR(1000) COMMENT '已授权的作用域(逗号分隔)',
    attributes TEXT COMMENT '授权属性(JSON对象,存储额外元数据)',
    state VARCHAR(500) COMMENT 'OAuth2 state参数(防CSRF)',

    -- 授权码相关字段
    authorization_code_value TEXT COMMENT '授权码值(SHA-256哈希后存储)',
    authorization_code_issued_at TIMESTAMP NULL COMMENT '授权码签发时间',
    authorization_code_expires_at TIMESTAMP NULL COMMENT '授权码过期时间(通常5分钟)',
    authorization_code_metadata TEXT COMMENT '授权码元数据(JSON)',

    -- 访问令牌相关字段
    access_token_value TEXT COMMENT '访问令牌值(JWT或Opaque Token)',
    access_token_issued_at TIMESTAMP NULL COMMENT '访问令牌签发时间',
    access_token_expires_at TIMESTAMP NULL COMMENT '访问令牌过期时间(通常2小时)',
    access_token_metadata TEXT COMMENT '访问令牌元数据(JSON)',
    access_token_type VARCHAR(100) COMMENT '访问令牌类型: Bearer',
    access_token_scopes VARCHAR(1000) COMMENT '访问令牌作用域',

    -- OIDC ID Token相关字段
    oidc_id_token_value TEXT COMMENT 'OIDC ID令牌值(JWT格式)',
    oidc_id_token_issued_at TIMESTAMP NULL COMMENT 'OIDC ID令牌签发时间',
    oidc_id_token_expires_at TIMESTAMP NULL COMMENT 'OIDC ID令牌过期时间',
    oidc_id_token_metadata TEXT COMMENT 'OIDC ID令牌元数据(JSON)',

    -- 刷新令牌相关字段
    refresh_token_value TEXT COMMENT '刷新令牌值',
    refresh_token_issued_at TIMESTAMP NULL COMMENT '刷新令牌签发时间',
    refresh_token_expires_at TIMESTAMP NULL COMMENT '刷新令牌过期时间(通常7天)',
    refresh_token_metadata TEXT COMMENT '刷新令牌元数据(JSON)',

    -- 设备授权流相关字段(Device Authorization Flow)
    user_code_value TEXT COMMENT '用户码值(8位短码,用户输入)',
    user_code_issued_at TIMESTAMP NULL COMMENT '用户码签发时间',
    user_code_expires_at TIMESTAMP NULL COMMENT '用户码过期时间',
    user_code_metadata TEXT COMMENT '用户码元数据(JSON)',

    device_code_value TEXT COMMENT '设备码值(设备轮询使用)',
    device_code_issued_at TIMESTAMP NULL COMMENT '设备码签发时间',
    device_code_expires_at TIMESTAMP NULL COMMENT '设备码过期时间',
    device_code_metadata TEXT COMMENT '设备码元数据(JSON)',

    PRIMARY KEY (id),
    INDEX idx_registered_client_id (registered_client_id),
    INDEX idx_principal_name (principal_name),
    INDEX idx_authorization_code_value (authorization_code_value(255)),
    INDEX idx_access_token_value (access_token_value(255)),
    INDEX idx_refresh_token_value (refresh_token_value(255)),
    INDEX idx_access_token_expires_at (access_token_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2授权记录表(存储授权码、Token等)';

-- ============================================================================
-- 3. OAuth2 授权确认表
-- ============================================================================
CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL COMMENT '客户端ID',
    principal_name VARCHAR(200) NOT NULL COMMENT '用户标识(OpenID)',
    authorities VARCHAR(1000) NOT NULL COMMENT '已授权的权限(逗号分隔的Scope列表)',
    PRIMARY KEY (registered_client_id, principal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2授权确认表(记录用户对客户端的授权同意)';

-- ============================================================================
-- 4. OAuth2 Scope权限映射表
-- ============================================================================
CREATE TABLE oauth2_scope_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    scope VARCHAR(100) NOT NULL COMMENT 'OAuth2作用域(如: read, user:profile)',
    permission_code VARCHAR(100) NOT NULL COMMENT '系统权限标识(如: SCOPE_READ)',
    description VARCHAR(500) COMMENT '权限描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_permission (scope, permission_code),
    INDEX idx_scope (scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2作用域权限映射表';

-- ============================================================================
-- 5. 初始化内置OAuth2客户端
-- ============================================================================

-- 5.1 生成内置客户端密钥的BCrypt哈希
-- apex-admin-web密钥: apex-admin-web-secret-2026 -> $2a$10$...
-- apex-m2m-service密钥: apex-m2m-secret-2026 -> $2a$10$...
-- 注意: 生产环境请使用强密钥并通过环境变量注入

-- 5.2 前端管理后台客户端(apex-admin-web)
INSERT INTO oauth2_registered_client (
    id,
    client_id,
    client_secret,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    post_logout_redirect_uris,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'apex-admin-web-client-001',
    'apex-admin-web',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', -- 密钥: apex-admin-web-secret-2026
    'Apex Admin Web后台管理系统',
    '["client_secret_basic","client_secret_post"]',
    '["authorization_code","refresh_token"]',
    '["http://localhost:5173/oauth2/callback","http://localhost:3000/oauth2/callback","https://admin.apex.example.com/oauth2/callback"]',
    '["http://localhost:5173/","https://admin.apex.example.com/"]',
    '["read","write","user:profile","user:update","admin:users","admin:roles"]',
    '{"@class":"java.util.HashMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.HashMap","settings.token.authorization-code-time-to-live":["java.time.Duration",300],"settings.token.access-token-time-to-live":["java.time.Duration",7200],"settings.token.refresh-token-time-to-live":["java.time.Duration",604800],"settings.token.reuse-refresh-tokens":false}'
);

-- 5.3 服务间调用客户端(M2M: Machine-to-Machine)
INSERT INTO oauth2_registered_client (
    id,
    client_id,
    client_secret,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'apex-m2m-service-001',
    'apex-m2m-service',
    '$2a$10$8S.z5vl7f5xP3qhD6rZYGuEU5q6nPp0VmRFLLGPHN8MKq8VZ8xV3S', -- 密钥: apex-m2m-secret-2026
    'Apex M2M服务客户端(用于服务间调用)',
    '["client_secret_basic","client_secret_post"]',
    '["client_credentials"]',
    '["api:read","api:write"]',
    '{"@class":"java.util.HashMap","settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.HashMap","settings.token.access-token-time-to-live":["java.time.Duration",3600]}'
);

-- 5.4 移动应用客户端(公共客户端,使用PKCE)
INSERT INTO oauth2_registered_client (
    id,
    client_id,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'apex-mobile-app-001',
    'apex-mobile-app',
    'Apex移动应用(公共客户端)',
    '["none"]',
    '["authorization_code","refresh_token"]',
    '["com.apex.app://oauth2/callback"]',
    '["read","write","user:profile"]',
    '{"@class":"java.util.HashMap","settings.client.require-proof-key":true,"settings.client.require-authorization-consent":true}',
    '{"@class":"java.util.HashMap","settings.token.authorization-code-time-to-live":["java.time.Duration",300],"settings.token.access-token-time-to-live":["java.time.Duration",3600],"settings.token.refresh-token-time-to-live":["java.time.Duration",2592000],"settings.token.reuse-refresh-tokens":false}'
);

-- ============================================================================
-- 6. 初始化标准OAuth2 Scope权限映射
-- ============================================================================
INSERT INTO oauth2_scope_permission (scope, permission_code, description) VALUES
-- 基础读写权限
('read', 'SCOPE_READ', '读取基本资源(只读访问)'),
('write', 'SCOPE_WRITE', '写入基本资源(创建、更新)'),

-- 用户相关权限
('user:profile', 'SCOPE_USER_PROFILE', '读取用户个人信息(昵称、头像、邮箱等)'),
('user:update', 'SCOPE_USER_UPDATE', '更新用户个人信息'),
('user:avatar', 'SCOPE_USER_AVATAR', '上传和修改用户头像'),

-- 管理员权限
('admin:users', 'SCOPE_ADMIN_USERS', '管理用户(查看、创建、更新、删除用户)'),
('admin:roles', 'SCOPE_ADMIN_ROLES', '管理角色权限(查看、分配角色)'),
('admin:system', 'SCOPE_ADMIN_SYSTEM', '系统管理(配置、字典、菜单等)'),

-- API访问权限(用于服务间调用)
('api:read', 'SCOPE_API_READ', 'API只读访问(所有GET接口)'),
('api:write', 'SCOPE_API_WRITE', 'API读写访问(所有POST/PUT/DELETE接口)'),

-- 文件操作权限
('file:upload', 'SCOPE_FILE_UPLOAD', '文件上传权限'),
('file:download', 'SCOPE_FILE_DOWNLOAD', '文件下载权限');

-- ============================================================================
-- 7. 为OAuth2客户端管理添加菜单权限
-- ============================================================================

-- 7.1 创建OAuth2客户端管理菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort_order, visible, status, created_by, created_at, updated_at)
VALUES
(300, 1, 'OAuth2客户端', 1, '/system/oauth2-clients', '/system/oauth2-clients/index', 'system:oauth2client:list', 'key', 8, 1, 1, 'system', NOW(), NOW());

-- 7.2 创建OAuth2客户端管理按钮权限
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, sort_order, visible, status, created_by, created_at, updated_at)
VALUES
(3001, 300, 'OAuth2客户端查询', 2, 'system:oauth2client:query', 1, 0, 1, 'system', NOW(), NOW()),
(3002, 300, 'OAuth2客户端新增', 2, 'system:oauth2client:add', 2, 0, 1, 'system', NOW(), NOW()),
(3003, 300, 'OAuth2客户端修改', 2, 'system:oauth2client:edit', 3, 0, 1, 'system', NOW(), NOW()),
(3004, 300, 'OAuth2客户端删除', 2, 'system:oauth2client:remove', 4, 0, 1, 'system', NOW(), NOW()),
(3005, 300, 'OAuth2客户端重置密钥', 2, 'system:oauth2client:reset', 5, 0, 1, 'system', NOW(), NOW());

-- 7.3 为超级管理员角色授予OAuth2客户端管理权限
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_key = 'SUPER_ADMIN'
  AND m.id IN (300, 3001, 3002, 3003, 3004, 3005);

-- ============================================================================
-- 8. 创建OAuth2审计日志表(可选,用于审计)
-- ============================================================================
CREATE TABLE oauth2_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    client_id VARCHAR(100) NOT NULL COMMENT '客户端ID',
    principal_name VARCHAR(200) COMMENT '用户标识',
    event_type VARCHAR(50) NOT NULL COMMENT '事件类型: AUTHORIZATION, TOKEN_ISSUED, TOKEN_REVOKED, CLIENT_CREATED等',
    grant_type VARCHAR(50) COMMENT '授权类型',
    scopes VARCHAR(1000) COMMENT '请求的作用域',
    client_ip VARCHAR(50) COMMENT '客户端IP',
    user_agent VARCHAR(500) COMMENT 'User-Agent',
    success TINYINT(1) DEFAULT 1 COMMENT '是否成功: 1-成功, 0-失败',
    error_code VARCHAR(50) COMMENT '错误码',
    error_message VARCHAR(500) COMMENT '错误信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_client_id (client_id),
    INDEX idx_principal_name (principal_name),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2审计日志表';

-- ============================================================================
-- 9. 添加注释说明
-- ============================================================================

-- 客户端密钥生成命令(BCrypt):
-- echo -n 'your-secret-key' | htpasswd -bnBC 10 "" | tr -d ':\n' | sed 's/$2y/$2a/'

-- 授权码模式流程:
-- 1. 客户端引导用户访问 /oauth2/authorize?response_type=code&client_id=xxx&redirect_uri=xxx&scope=xxx
-- 2. 用户登录并同意授权
-- 3. 重定向到 redirect_uri?code=xxx
-- 4. 客户端使用授权码调用 /oauth2/token 换取access_token

-- 客户端模式流程:
-- 1. 客户端直接调用 /oauth2/token?grant_type=client_credentials&scope=xxx
-- 2. 返回access_token(无refresh_token)

-- Token刷新流程:
-- 1. 客户端调用 /oauth2/token?grant_type=refresh_token&refresh_token=xxx
-- 2. 返回新的access_token和refresh_token

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- ============================================================
-- V26: Add Keycloak integration fields to sys_user
-- ============================================================
-- Description: Add Keycloak SSO integration support fields
-- Author: Apex Team
-- Date: 2026-09-08
-- ============================================================

-- Add keycloak_sub field to sys_user table
ALTER TABLE sys_user
ADD COLUMN keycloak_sub VARCHAR(255) NULL COMMENT 'Keycloak用户唯一标识(sub claim)' AFTER email;

-- Add unique index for keycloak_sub
ALTER TABLE sys_user
ADD UNIQUE INDEX uk_keycloak_sub (keycloak_sub);

-- Add email field if not exists (for Keycloak OIDC user info)
-- Check if email column exists, if not add it
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'sys_user'
     AND COLUMN_NAME = 'email') = 0,
    'ALTER TABLE sys_user ADD COLUMN email VARCHAR(255) NULL COMMENT ''用户邮箱'' AFTER phone',
    'SELECT ''Column email already exists'' AS message'
));

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add index for email if not exists
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'sys_user'
     AND INDEX_NAME = 'idx_email') = 0,
    'ALTER TABLE sys_user ADD INDEX idx_email (email)',
    'SELECT ''Index idx_email already exists'' AS message'
));

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add comment for sys_user table
ALTER TABLE sys_user COMMENT = '系统用户表（支持本地JWT认证和Keycloak SSO认证）';
-- 脚本用途：为系统用户增加账号密码登录所需字段与用户名唯一索引。
-- 业务背景：在既有第三方身份登录之外补充传统账号密码认证能力。
-- 前置依赖：V1 已创建 sys_user，且表中不存在 username、password 列及 uk_username 索引。
-- 影响范围：变更 sys_user 表结构并建立唯一索引，不写入或改写现有密码数据。
-- 幂等性：ALTER TABLE 非幂等，重复执行会因列或索引已存在而失败。
-- MySQL/Flyway 兼容性：面向 MySQL 8.x DDL，由 Flyway V3 在 V1、V2 后执行。
-- 数据风险：建唯一索引可能受既有数据冲突影响；DDL 可能锁表并增加复制延迟。
-- 运维注意：生产执行前评估表规模、在线 DDL 能力和回滚方案，执行后验证索引状态。
-- 迁移元信息：版本 V3；执行阶段为认证能力扩展的结构迁移阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- Apex Scaffold V3 迁移：账号密码登录支持
-- ================================================================
-- 说明：为 sys_user 补充 username/password 字段，支持传统账号密码登录
--      （与微信/手机号登录并行，登录类型由 AuthService 统一分发）。
-- 设计：
--   - username 唯一索引（uk_username），账号不可重复
--   - password 存 BCrypt 哈希（spring-security-crypto BCryptPasswordEncoder）
--   - 存量微信/手机号用户 username 为 NULL，不影响既有登录链路
-- ================================================================

ALTER TABLE `sys_user`
    ADD COLUMN `username` VARCHAR(50) DEFAULT NULL COMMENT '登录账号（账号密码登录，微信/手机号用户可为NULL）' AFTER `open_id`,
    ADD COLUMN `password` VARCHAR(100) DEFAULT NULL COMMENT '登录密码（BCrypt哈希，仅账号密码登录使用）' AFTER `username`,
    ADD UNIQUE KEY `uk_username` (`username`);
-- 脚本用途：补充操作审计日志的查询权限与管理后台菜单入口。
-- 业务背景：打通审计日志落库、授权控制和后台查询页面的使用闭环。
-- 前置依赖：V1 已创建目标表，V2 权限及菜单种子已完成，相关固定 ID 未被占用。
-- 影响范围：向 sys_permission 与 sys_menu 写入固定种子记录，不变更表结构。
-- 幂等性：普通 INSERT 非幂等，重复执行可能因主键或唯一键冲突失败。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x 常规 DML，由 Flyway V4 按版本顺序执行。
-- 数据风险：权限码或菜单配置错误会造成越权、拒绝访问或导航异常。
-- 运维注意：执行前核对 ID 与权限码，执行后验证审计页面授权及菜单可见性。
-- 迁移元信息：版本 V4；执行阶段为审计能力上线后的权限与菜单补种阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- Apex Scaffold V4 迁移：审计日志查询权限与菜单
-- ================================================================
-- 说明：为操作审计日志（sys_operation_log 表）补充查询权限码与菜单项，
--      支撑管理后台「系统监控-审计日志」页面（C2 审计落库闭环）。
-- 依赖：V2 种子迁移（权限码 2001-2024、菜单 1000-1006 已存在）
-- 修复（2026-08-27）：sys_menu 列名对齐 V1 表结构（menu_name/perms），
--      列序对齐 V2 写法，menu_type=1（菜单页），sort=7（接登录日志 6）。
-- ================================================================

INSERT INTO `sys_permission` (`id`, `name`, `code`, `description`, `type`, `status`, `create_time`, `update_time`, `version`, `deleted`) VALUES
(2025, '审计日志列表', 'system:audit-log:list', '查询操作审计日志', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2026, '审计日志详情', 'system:audit-log:query', '查询操作审计日志详情', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`, `version`, `deleted`) VALUES
(1007, 1000, '审计日志', 1, 'system/audit-log', 'system/audit-log/index', 'system:audit-log:list', 'Audit', 7, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);
-- 脚本用途：新增在线用户与会话管理页面所需的查询权限码。
-- 业务背景：基于用户心跳状态提供后台在线用户查询和会话治理能力。
-- 前置依赖：V1 已创建 sys_permission，V2-V4 已完成且权限 ID 2027 未被占用。
-- 影响范围：向 sys_permission 写入一条固定权限记录，不修改用户或会话数据。
-- 幂等性：普通 INSERT 非幂等，重复执行可能因主键或权限码冲突失败。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x 常规 DML，由 Flyway V5 顺序执行。
-- 数据风险：权限定义错误会导致在线用户信息不可访问或被不当授权。
-- 运维注意：执行后核对权限码与角色授权策略，避免向非管理角色扩大可见范围。
-- 迁移元信息：版本 V5；执行阶段为在线会话管理能力的权限初始化阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- Apex Scaffold V5 迁移：在线用户查询权限
-- ================================================================
-- 说明：为「在线用户/会话管理」补充查询权限码（C4），支撑管理后台
--      在线用户列表页面。在线判定基于 sys_user.last_heartbeat_time
--      （登录与活跃请求更新，超过 5 分钟视为离线）。
-- 依赖：V2 种子迁移（权限码 1000-2024、V4 已用 2025-2026）
-- ================================================================

INSERT INTO `sys_permission` (`id`, `name`, `code`, `description`, `type`, `status`, `create_time`, `update_time`, `version`, `deleted`) VALUES
(2027, '在线用户列表', 'system:online-user:list', '查询在线用户（会话管理）', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);
-- 脚本用途：创建部门树结构并为用户增加部门归属，同时初始化部门管理权限。
-- 业务背景：为组织架构管理和基于部门的数据范围过滤提供数据库基础。
-- 前置依赖：V1 已创建 sys_user、权限及菜单相关表，V2-V5 已按序完成。
-- 影响范围：创建 sys_dept，变更 sys_user，并写入部门权限及菜单种子数据。
-- 幂等性：建表使用 IF NOT EXISTS，但 ALTER TABLE 与普通种子 INSERT 非幂等，整体不可重复执行。
-- MySQL/Flyway 兼容性：面向 MySQL 8.x、InnoDB 与 Flyway V6 顺序迁移。
-- 数据风险：用户新增列与索引可能锁表；固定 ID 冲突会中断迁移并留下部分已执行 DDL。
-- 运维注意：执行前备份并评估 sys_user 表规模，执行后核对部门树、字段及权限菜单。
-- 迁移元信息：版本 V6；执行阶段为组织架构与数据权限基础建设阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- Apex Scaffold V6 迁移：部门管理与数据权限
-- ================================================================
-- 说明：新增部门表（sys_dept，树形结构）+ sys_user.dept_id 归属字段 +
--      部门管理权限码（C3 数据权限/部门体系）。
-- 设计：
--   - sys_dept 树形（parent_id 自关联），ancestors 存祖级链便于层级过滤
--   - sys_user.dept_id 关联部门，配合 @DataScope 注解实现数据权限过滤
-- 依赖：V2 种子迁移（权限码 1000-2024、V4 2025-2026、V5 2027 已用）
-- ================================================================

CREATE TABLE IF NOT EXISTS `sys_dept` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门ID(0为根部门)',
  `ancestors`   VARCHAR(255) NOT NULL DEFAULT '' COMMENT '祖级列表(逗号分隔,如 0,100)',
  `name`        VARCHAR(50)  NOT NULL COMMENT '部门名称',
  `order_num`   INT(4)       NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `leader`      VARCHAR(50)  DEFAULT NULL COMMENT '负责人',
  `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
  `email`       VARCHAR(50)  DEFAULT NULL COMMENT '邮箱',
  `status`      INT(1)       NOT NULL DEFAULT 1 COMMENT '状态(0-停用,1-正常)',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`     INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表(树形,数据权限基础)';

-- sys_user 增加部门归属字段
ALTER TABLE `sys_user`
    ADD COLUMN `dept_id` BIGINT DEFAULT NULL COMMENT '所属部门ID(数据权限,可空)' AFTER `username`;

INSERT INTO `sys_permission` (`id`, `name`, `code`, `description`, `type`, `status`, `create_time`, `update_time`, `version`, `deleted`) VALUES
(2028, '部门列表', 'system:dept:list', '查询部门列表', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2029, '部门详情', 'system:dept:query', '查询部门详情', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2030, '部门新增', 'system:dept:add', '新增部门', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2031, '部门编辑', 'system:dept:edit', '编辑部门', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0),
(2032, '部门删除', 'system:dept:remove', '删除部门', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `sort`, `visible`, `status`, `create_time`, `update_time`, `version`, `deleted`) VALUES
(1008, 1000, '部门管理', 1, 'system/dept', 'system/dept/index', 'system:dept:list', 'Cluster', 8, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);

-- 根部门示例（可选，生产按需调整）
INSERT INTO `sys_dept` (`id`, `parent_id`, `ancestors`, `name`, `order_num`, `status`, `create_time`, `update_time`, `version`, `deleted`) VALUES
(1, 0, '0', 'Apex 科技', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0);
-- 脚本用途：补齐 API 调用日志的请求标识、调用时间字段及请求标识索引。
-- 业务背景：修复持久化模型与 V1 表结构不一致导致的日志写入和查询异常。
-- 前置依赖：V1 已创建 sys_api_call_log，且目标列与 idx_request_id 尚不存在。
-- 影响范围：变更 sys_api_call_log 表结构并新增普通索引，不改写存量日志值。
-- 幂等性：ALTER TABLE 非幂等，重复执行会因列或索引已存在而失败。
-- MySQL/Flyway 兼容性：面向 MySQL 8.x DDL，由 Flyway V7 在前序迁移后执行。
-- 数据风险：大日志表执行 DDL 可能锁表、占用额外空间并造成复制延迟。
-- 运维注意：生产执行前评估在线 DDL 与磁盘余量，执行后验证字段、索引及日志链路。
-- 迁移元信息：版本 V7；执行阶段为 API 调用日志结构修复阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ================================================================
-- Apex Scaffold V7 迁移：sys_api_call_log 补齐 request_id / call_time 列
-- ================================================================
-- 背景：业务 PO（ApiCallLogPO）与 API 调用日志查询依赖 request_id、call_time
--       两列，但 V1 基线建表时遗漏（V1 仅含 trace_id 等基础列）；旧 it 测试
--       基于历史 schema-it.sql（含这两列）运行，掩盖了缺陷；切换为
--       「各环境统一 MySQL 迁移集」后暴露（真实 MySQL 上写入/查询
--       sys_api_call_log 会报 Unknown column 'request_id' / 'call_time'）。
-- 修复：新增本迁移补列并加请求ID索引；已发布基线 V1 不做修改
--       （避免破坏 Flyway checksum）。
-- ================================================================

ALTER TABLE `sys_api_call_log`
    ADD COLUMN `request_id` VARCHAR(64) DEFAULT NULL COMMENT '请求唯一标识UUID' AFTER `trace_id`,
    ADD COLUMN `call_time` DATETIME DEFAULT NULL COMMENT '调用发起时间' AFTER `request_id`,
    ADD KEY `idx_request_id` (`request_id`);
-- 脚本用途：修复后台菜单路由配置并初始化管理员账号、角色及关联数据。
-- 业务背景：解决基础种子菜单不可路由、状态语义错误及系统无可登录管理员的问题。
-- 前置依赖：V1-V7 已完成，目标系统表存在，固定用户、角色、菜单及关联 ID 可用。
-- 影响范围：更新既有菜单并写入管理员相关种子数据，直接影响登录和后台授权。
-- 幂等性：主要使用 UPDATE、ON DUPLICATE KEY UPDATE 与 INSERT IGNORE，设计为可重复执行。
-- MySQL/Flyway 兼容性：使用 MySQL 8.x 特有幂等 DML，由 Flyway V8 顺序执行。
-- 数据风险：会覆盖目标菜单配置；内置账号及密码哈希属于敏感初始化数据，部署后须治理。
-- 运维注意：执行后立即修改或禁用默认凭据，并核对菜单路由、角色权限及关联记录。
-- 迁移元信息：版本 V8；执行阶段为管理后台可用性修复与管理员引导阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ============================================================
-- V8: 修复管理后台菜单数据 + 种子管理员账号
-- ============================================================
-- 背景：V1-V7 仅初始化了菜单/权限的基础数据，存在以下问题：
--   1. 系统管理目录 menu_type=1（应为 0 目录），component='system/index'
--      与前端动态路由注册表不匹配，导致整个菜单树无法生成路由；
--   2. 全部菜单 status=1（停用），会被前端动态路由过滤；
--   3. 子菜单 path 为绝对形式（system/user），与目录 path 拼接后路由错乱；
--   4. 登录日志/审计日志 component 与前端 VIEW_REGISTRY（monitor/*）不一致；
--   5. 无任何 sys_user / sys_role / 关联数据，无法登录且无管理员角色。
-- 本脚本修复菜单数据并补充管理员账号（admin / admin123），幂等可重复执行。
-- ============================================================

-- 1. 修复系统管理目录（menu_type 1→0 目录、清除无效 component、启用）
UPDATE sys_menu
SET menu_type = 0,
    component = NULL,
    perms     = NULL,
    status    = 0,
    visible   = 0,
    sort      = 1,
    icon      = COALESCE(NULLIF(icon, ''), 'Setting')
WHERE id = 1000;

-- 2. 修复子菜单：启用 + 相对路径（与目录 path 拼接）+ 组件与前端注册表对齐
UPDATE sys_menu SET status = 0, visible = 0, path = 'user',      perms = 'system:user:list', sort = 1, icon = COALESCE(NULLIF(icon, ''), 'User')           WHERE id = 1001;
UPDATE sys_menu SET status = 0, visible = 0, path = 'menu',      sort = 3, icon = COALESCE(NULLIF(icon, ''), 'Menu')            WHERE id = 1002;
UPDATE sys_menu SET status = 0, visible = 0, path = 'dict',      sort = 5, icon = COALESCE(NULLIF(icon, ''), 'Collection')      WHERE id = 1003;
UPDATE sys_menu SET status = 0, visible = 0, path = 'config',    sort = 6, icon = COALESCE(NULLIF(icon, ''), 'Tools')           WHERE id = 1004;
UPDATE sys_menu SET status = 0, visible = 0, path = 'job',       sort = 7, icon = COALESCE(NULLIF(icon, ''), 'Timer')           WHERE id = 1005;
UPDATE sys_menu SET status = 0, visible = 0, path = 'login-log', component = 'monitor/loginlog/index', sort = 8, icon = COALESCE(NULLIF(icon, ''), 'Document') WHERE id = 1006;
UPDATE sys_menu SET status = 0, visible = 0, path = 'audit-log', component = 'monitor/audit/index',    sort = 9, icon = COALESCE(NULLIF(icon, ''), 'View')     WHERE id = 1007;
UPDATE sys_menu SET status = 0, visible = 0, path = 'dept',      sort = 4, icon = COALESCE(NULLIF(icon, ''), 'OfficeBuilding')  WHERE id = 1008;

-- 3. 补充角色管理 / 在线用户菜单（前端视图已注册，后端此前缺菜单项）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1011, 1000, '角色管理', 1, 'role', 'system/role/index', 'system:role:list', 'UserFilled', 2, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '角色管理', parent_id = 1000, menu_type = 1, path = 'role',
                        component = 'system/role/index', perms = 'system:role:list', status = 0, visible = 0, sort = 2;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, visible, status)
VALUES (1012, 1000, '在线用户', 1, 'online', 'monitor/online/index', 'system:online-user:list', 'Monitor', 10, 0, 0)
ON DUPLICATE KEY UPDATE menu_name = '在线用户', parent_id = 1000, menu_type = 1, path = 'online',
                        component = 'monitor/online/index', perms = 'system:online-user:list', status = 0, visible = 0, sort = 10;

-- 4. 补充用户管理 / 角色管理权限码（与前端 v-permission 按钮权限码对齐）
INSERT INTO sys_permission (id, name, code, description, type, status) VALUES
    (2033, '用户列表', 'system:user:list',   '查询后台用户列表', 1, 1),
    (2034, '用户查询', 'system:user:query',  '查询后台用户详情', 1, 1),
    (2035, '用户新增', 'system:user:create', '创建后台用户',     1, 1),
    (2036, '用户编辑', 'system:user:update', '更新后台用户',     1, 1),
    (2037, '用户删除', 'system:user:remove', '删除后台用户',     1, 1),
    (2038, '角色列表', 'system:role:list',   '查询后台角色列表', 1, 1),
    (2039, '角色新增', 'system:role:create', '创建后台角色',     1, 1),
    (2040, '角色编辑', 'system:role:update', '更新后台角色',     1, 1),
    (2041, '角色删除', 'system:role:remove', '删除后台角色',     1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = 1;

-- 5. 种子角色：超级管理员（ADMIN，对应 SecurityProperties.adminRoles 默认值）
INSERT INTO sys_role (id, name, code, description, status)
VALUES (1, '超级管理员', 'ADMIN', '系统内置超级管理员角色，拥有全部管理权限', 1)
ON DUPLICATE KEY UPDATE name = '超级管理员', status = 1;

-- 6. 种子用户：admin / admin123（BCrypt，open_id 作为 JWT subject，鉴权链路必需）
INSERT INTO sys_user (id, open_id, username, dept_id, password, nick_name, gender, status, version)
VALUES (1, 'admin-openid', 'admin', 1, '$2a$10$3WRG9DszA2YX58nDBTKpFON71ZbLSkJUQrAWnyfaavl39OqBUPITW', '系统管理员', 0, 1, 0)
ON DUPLICATE KEY UPDATE password = VALUES(password), status = 1, nick_name = '系统管理员';

-- 7. 用户-角色关联（表无唯一键且 id 非自增，先清后插保证幂等）
DELETE FROM sys_user_role WHERE user_id = 1;
INSERT INTO sys_user_role (id, user_id, role_id) VALUES (1, 1, 1);

-- 8. ADMIN 角色授予全部可用权限（表无唯一键且 id 非自增，先清后插保证幂等）
DELETE FROM sys_role_permission WHERE role_id = 1;
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT ROW_NUMBER() OVER (ORDER BY id), 1, id FROM sys_permission WHERE status = 1 AND deleted = 0;
-- 脚本用途：为系统用户补充生日与新手引导状态字段（通用用户属性）。
-- 业务背景：修复 UserMapper 查询列与数据库结构不一致引发的登录鉴权异常。
-- 前置依赖：V1-V8 已完成，sys_user 存在且目标字段尚未创建。
-- 影响范围：变更 sys_user 表结构；新增字段允许 NULL，不主动回填存量用户数据。
-- 幂等性：ALTER TABLE 非幂等，重复执行会因列已存在而失败。
-- MySQL/Flyway 兼容性：面向 MySQL 8.x DDL，由 Flyway V9 顺序执行。
-- 数据风险：大表 DDL 可能锁表或增加复制延迟；空值语义需由应用层兼容。
-- 运维注意：执行前评估用户表规模和备份，执行后验证映射查询及登录鉴权链路。
-- 迁移元信息：版本 V9；执行阶段为用户模型与数据库结构对齐阶段。
-- Checksum 提示：修改已执行迁移的注释也会改变 Flyway checksum；存量环境需评估后 repair，禁止盲目重跑。
-- ============================================================
-- V9: sys_user 表补齐 UserMapper 查询所需的通用用户属性字段
-- ============================================================
-- 背景：UserMapper 查询列包含 birthday / onboarding_status，
--   但 V1 建表脚本未创建这两列，导致按 username/openId 查询用户时
--   抛出 SQLSyntaxErrorException: Unknown column 'birthday'。
-- 本脚本补齐列定义（与 UserEntity 字段类型对齐），修复登录/鉴权链路。
--
-- 字段说明：
--   - birthday: 用户生日，常见用户属性，可用于生日提醒、年龄验证等通用场景
--   - onboarding_status: 新手引导状态，记录用户是否完成系统引导流程（通用功能）
-- ============================================================

ALTER TABLE sys_user
    ADD COLUMN birthday DATE NULL COMMENT '生日（通用用户属性）' AFTER gender,
    ADD COLUMN onboarding_status VARCHAR(50) NULL COMMENT '新手引导状态（PENDING-待引导/COMPLETED-已完成）' AFTER status;
