-- ================================================================
-- V1: 通用脚手架初始化基线
-- ================================================================
-- 用途: Apex Scaffold 通用数据库初始化脚本，仅包含通用能力表。
-- 背景: v2.0 通用化重构后重建干净迁移基线，业务表已全部移除。
-- 影响: 新数据库首次部署时创建全部通用表结构、索引和约束。
-- ================================================================

-- ------------------------------------------------------------------
-- sys_user
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表(微信认证+手机号注册)';

-- ------------------------------------------------------------------
-- sys_role
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- ------------------------------------------------------------------
-- sys_permission
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';

-- ------------------------------------------------------------------
-- sys_user_role
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- ------------------------------------------------------------------
-- sys_role_permission
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ------------------------------------------------------------------
-- sys_operation_log
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- ------------------------------------------------------------------
-- sys_file
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件信息表';

-- ------------------------------------------------------------------
-- sys_api_call_log
-- ------------------------------------------------------------------
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

-- ============================================================
-- V33: 对齐 API 调用日志表的 call_time 列
-- ============================================================
-- 用途: 为 sys_api_call_log 表补充遗漏的 call_time 字段。
-- 背景: V32 首次建表（sys_api_call_log）时遗漏了 call_time 列，
--       而 ApiCallLogPO 实体类会向该列写入 API 调用的实际发生时间。
--       MyBatis-Plus 插入时找不到该列会抛出异常，
--       导致 API 调用日志写入功能完全不可用。
--       Flyway 要求不修改历史迁移，因此采用后续 V33 迁移补齐。
-- 影响: sys_api_call_log 表新增 call_time 列（DATETIME，默认 NULL），
--       记录外部 API 调用的实际发生时间。
-- 回滚: ALTER TABLE sys_api_call_log DROP COLUMN call_time。
-- @author Apex Team
-- ============================================================

ALTER TABLE `sys_api_call_log`
  ADD COLUMN `call_time` DATETIME DEFAULT NULL
    COMMENT '外部 API 调用实际发生时间';

-- ============================================================
-- V34: 优化 Outbox 事件认领后的 Worker 回查索引
-- ============================================================
-- 用途: 为 sys_domain_event_outbox 表新增联合索引 idx_outbox_claimed_worker。
-- 背景: Outbox Dispatcher 在认领事件后将状态设为 PROCESSING 并记录 locked_by，
--       之后需要按 worker 令牌回查属于自己的 PROCESSING 事件进行后续处理。
--       原表缺少覆盖此查询模式的索引，导致全表扫描。
-- 查询模式: WHERE status='PROCESSING' AND locked_by=? ORDER BY id
-- 影响: sys_domain_event_outbox 表新增联合索引 (status, locked_by, id)，
--       使用 ALGORITHM=INPLACE, LOCK=NONE 避免锁表。
-- 回滚: ALTER TABLE sys_domain_event_outbox DROP INDEX idx_outbox_claimed_worker。
-- @author Apex Team
-- ============================================================

ALTER TABLE `sys_domain_event_outbox`
  ADD INDEX `idx_outbox_claimed_worker` (`status`, `locked_by`, `id`),
  ALGORITHM=INPLACE,
  LOCK=NONE;

-- ============================================================
-- V35: 优化照片展示历史表用户时间范围覆盖索引
-- ============================================================
-- 用途: 为 biz_photo_show_history 表新增联合索引
--       idx_show_history_user_time_photo。
-- 背景: 照片展示去重逻辑需要查询"指定用户在指定时间范围内已看过的照片"，
--       即 WHERE user_id=? AND create_time>=?，
--       当仅需要 photo_id 时，覆盖索引可避免回表，大幅提升查询效率。
-- 查询模式: WHERE user_id=? AND create_time>=?（仅返回 photo_id）
-- 影响: biz_photo_show_history 表新增联合索引 (user_id, create_time, photo_id)，
--       使用 ALGORITHM=INPLACE, LOCK=NONE 避免锁表。
-- 回滚: ALTER TABLE biz_photo_show_history DROP INDEX idx_show_history_user_time_photo。
-- @author Apex Team
-- ============================================================

ALTER TABLE `biz_photo_show_history`
  ADD INDEX `idx_show_history_user_time_photo`
    (`user_id`, `create_time`, `photo_id`),
  ALGORITHM=INPLACE,
  LOCK=NONE;

-- ============================================================
-- V36: 优化照片展示统计表唯一约束与时间范围索引
-- ============================================================
-- 用途: 为 biz_photo_show_stats 表新增唯一索引和联合索引：
--       1. uk_show_stats_user_photo：保证一名用户对一张照片只有一条展示统计
--       2. idx_show_stats_user_last_time：覆盖用户最近展示时间范围查询
-- 背景: 照片展示统计数据按 (user_id, photo_id) 维度聚合并持续更新，
--       原表缺少唯一约束可能导致同一用户对同一照片产生多条统计记录（数据不一致）；
--       同时按用户查询最近展示照片时需要按 (user_id, last_show_time) 高效过滤。
-- 查询模式:
--       - user_id + photo_id 唯一约束
--       - WHERE user_id=? ORDER BY last_show_time（覆盖索引避免回表）
-- 前置条件: 执行前必须运行 scripts/operations/preflight-v33-v41-index-optimization.sql；
--       若存在重复数据，ADD UNIQUE INDEX 会安全失败，禁止自动删除业务数据。
-- 影响: biz_photo_show_stats 表新增两个索引，使用 ALGORITHM=INPLACE, LOCK=NONE。
-- 回滚: ALTER TABLE biz_photo_show_stats DROP INDEX uk_show_stats_user_photo,
--                                     DROP INDEX idx_show_stats_user_last_time。
-- @author Apex Team
-- ============================================================

ALTER TABLE `biz_photo_show_stats`
  ADD UNIQUE INDEX `uk_show_stats_user_photo` (`user_id`, `photo_id`),
  ADD INDEX `idx_show_stats_user_last_time`
    (`user_id`, `last_show_time`, `photo_id`),
  ALGORITHM=INPLACE,
  LOCK=NONE;

-- ============================================================
-- V37: 优化 ELO 评分变更日志表联合查询索引
-- ============================================================
-- 用途: 为 elo_change_log 表新增两个联合索引：
--       1. idx_elo_log_battle_photo_deleted：覆盖按对局查询照片评分的 JOIN 查询
--       2. idx_elo_log_photo_deleted_time：覆盖按照片查询评分历史的时间范围回溯
-- 背景: 投票结算分析需要按对局查询本次对局中各照片的评分变化（JOIN 场景），
--       同时需要按照片查询其评分变化历史（按时间倒序分页）。
--       原表索引无法高效覆盖这两种查询模式。
-- 查询模式:
--       - 按对局+照片: WHERE battle_id=? AND photo_id=? AND deleted=0
--       - 按照片+时间: WHERE photo_id=? AND deleted=0 ORDER BY change_time DESC, id DESC
-- 影响: elo_change_log 表新增两个索引，使用 ALGORITHM=INPLACE, LOCK=NONE。
-- 回滚: ALTER TABLE elo_change_log DROP INDEX idx_elo_log_battle_photo_deleted,
--                                   DROP INDEX idx_elo_log_photo_deleted_time。
-- @author Apex Team
-- ============================================================

ALTER TABLE `elo_change_log`
  ADD INDEX `idx_elo_log_battle_photo_deleted`
    (`battle_id`, `photo_id`, `deleted`),
  ADD INDEX `idx_elo_log_photo_deleted_time`
    (`photo_id`, `deleted`, `change_time`, `id`),
  ALGORITHM=INPLACE,
  LOCK=NONE;

-- ============================================================
-- V38: 优化投票记录表用户时间线索引
-- ============================================================
-- 用途: 为 biz_vote 表新增联合索引 idx_vote_user_deleted_time_id，
--       覆盖用户有效投票时间线查询并按时间倒序排序。
-- 背景: 投票历史查询是最频繁的操作之一，需要按投票者查询其有效投票记录
--       并支持按创建时间倒序分页。原 idx_vote_create_time 仅服务全局时间范围回补，
--       保留该索引不删除，经运行期 Invisible Index 观察后再独立评估。
-- 查询模式: WHERE voter_user_id=? AND deleted=0 ORDER BY create_time DESC, id DESC
-- 影响: biz_vote 表新增联合索引 (voter_user_id, deleted, create_time DESC, id DESC)，
--       使用 ALGORITHM=INPLACE, LOCK=NONE 避免锁表。
-- 回滚: ALTER TABLE biz_vote DROP INDEX idx_vote_user_deleted_time_id。
-- @author Apex Team
-- ============================================================

ALTER TABLE `biz_vote`
  ADD INDEX `idx_vote_user_deleted_time_id`
    (`voter_user_id`, `deleted`, `create_time` DESC, `id` DESC),
  ALGORITHM=INPLACE,
  LOCK=NONE;

-- ============================================================
-- V39: 优化对局表用户活跃对局查询索引
-- ============================================================
-- 用途: 为 biz_battle 表新增联合索引 idx_battle_user_status_type_time，
--       覆盖用户指定状态、对局类型的最新活跃对局查询。
-- 背景: 用户对局的查询需要按 user_id + status + battle_type 组合过滤，
--       并按创建时间倒序获取最新对局。原 idx_user_status 仅覆盖 user_id + status，
--       不包含 battle_type，导致需要额外过滤步骤。
-- 查询模式: WHERE user_id=? AND status=? AND battle_type=? AND deleted=0
--           ORDER BY create_time DESC, id DESC
-- 影响: biz_battle 表新增联合索引
--       (user_id, status, battle_type, deleted, create_time DESC, id DESC)，
--       使用 ALGORITHM=INPLACE, LOCK=NONE 避免锁表。
-- 回滚: ALTER TABLE biz_battle DROP INDEX idx_battle_user_status_type_time。
-- @author Apex Team
-- ============================================================

ALTER TABLE `biz_battle`
  ADD INDEX `idx_battle_user_status_type_time`
    (`user_id`, `status`, `battle_type`, `deleted`, `create_time` DESC, `id` DESC),
  ALGORITHM=INPLACE,
  LOCK=NONE;

-- ============================================================
-- V3: 补充剩余字段（历史兼容迁移，无实际操作）
-- ============================================================
-- 用途: 本迁移原计划为 biz_photo 表补充剩余业务字段，但这些字段
--       已收敛进 V1 的 biz_photo 建表语句中，空库执行时不应再次
--       ADD COLUMN，否则会导致重复列错误。
-- 背景: 早期迭代中 V1 建表语句经过整合修订，将后续计划补充的
--       字段直接纳入初始 DDL，因此 V3 不再需要执行任何 DDL。
-- 影响: 无。已存在数据库的 flyway_schema_history 中保留此版本
--       记录以维持版本连续性，新库执行时不会有任何副作用。
-- 回滚: 无需回滚，本迁移不产生任何 Schema 变更。
-- @author Apex Team
-- ============================================================
-- 历史兼容迁移。
-- 这些字段已经收敛进 V1 的 biz_photo 建表语句，空库执行时不应再次 ADD COLUMN。
-- 保留 V3 文件以维持 Flyway 版本连续性；已存在数据库不得删除历史版本号。

-- ============================================================
-- V40: 优化算力流水表用户时间线分页索引
-- ============================================================
-- 用途: 为 biz_credit_tx 表新增联合索引 idx_credit_tx_user_time_id，
--       覆盖用户算力流水按创建时间倒序分页查询。
-- 背景: 算力流水页面是用户高频访问的功能，需要按用户查询其有效流水记录
--       并支持按创建时间倒序分页。原表缺乏覆盖此模式的索引。
-- 查询模式: WHERE user_id=? AND deleted=0 ORDER BY create_time DESC, id DESC
-- 影响: biz_credit_tx 表新增联合索引 (user_id, deleted, create_time DESC, id DESC)，
--       使用 ALGORITHM=INPLACE, LOCK=NONE 避免锁表。
-- 回滚: ALTER TABLE biz_credit_tx DROP INDEX idx_credit_tx_user_time_id。
-- @author Apex Team
-- ============================================================

ALTER TABLE `biz_credit_tx`
  ADD INDEX `idx_credit_tx_user_time_id`
    (`user_id`, `deleted`, `create_time` DESC, `id` DESC),
  ALGORITHM=INPLACE,
  LOCK=NONE;

-- ============================================================
-- V41: 优化系统消息表用户时间线分页索引
-- ============================================================
-- 用途: 为 biz_system_message 表新增联合索引 idx_system_message_user_time_id，
--       覆盖用户系统消息按创建时间倒序分页查询。
-- 背景: 系统消息列表是用户高频访问的功能，需要按用户查询其有效消息
--       并支持按创建时间倒序分页。原表缺乏覆盖此模式的索引。
-- 查询模式: WHERE user_id=? AND deleted=0 ORDER BY create_time DESC, id DESC
-- 影响: biz_system_message 表新增联合索引
--       (user_id, deleted, create_time DESC, id DESC)，
--       使用 ALGORITHM=INPLACE, LOCK=NONE 避免锁表。
-- 回滚: ALTER TABLE biz_system_message DROP INDEX idx_system_message_user_time_id。
-- @author Apex Team
-- ============================================================

ALTER TABLE `biz_system_message`
  ADD INDEX `idx_system_message_user_time_id`
    (`user_id`, `deleted`, `create_time` DESC, `id` DESC),
  ALGORITHM=INPLACE,
  LOCK=NONE;

-- ============================================================
-- V42: API 调用日志表新增 request_id 字段
-- ============================================================
-- 用途: 为 sys_api_call_log 表新增 request_id 字段及索引，
--       为每次 API 调用生成唯一请求标识（UUID）。
-- 背景: trace_id 关联前端请求到后端 API 调用的整条链路，
--       但同一前端请求可能触发多次外部 API 调用（如同时调用微信和创蓝），
--       每两次调用因并发关系需并行查询，此时仅有 trace_id 不够对标到具体调用。
--       新增 request_id 为每次调用分配独特标识，与 trace_id 互补定位。
-- 影响: sys_api_call_log 表新增 request_id 列（VARCHAR(64)，默认 NULL），
--       并创建 idx_request_id 索引（B-tree）。
--       request_id 由 ApiCallLogFilter 自动生成（UUID 格式）。
-- 回滚: ALTER TABLE sys_api_call_log DROP INDEX idx_request_id,
--                            DROP COLUMN request_id。
-- @author Apex Team
-- ============================================================

-- 为每次 API 调用生成唯一请求标识（UUID），由 ApiCallLogFilter 自动生成。
-- 与 trace_id 互补：trace_id 关联前端请求链路，request_id 标识单次调用。
ALTER TABLE `sys_api_call_log`
  ADD COLUMN `request_id` VARCHAR(64) DEFAULT NULL
    COMMENT '请求唯一标识（UUID），由 ApiCallLogFilter 自动生成';

CREATE INDEX `idx_request_id` ON `sys_api_call_log` (`request_id`);

-- ============================================================
-- V43: 为 ELO 评分表添加 dimension 评分维度列
-- ============================================================
-- 用途: 为 biz_elo_rating 表补齐 dimension 列（VARCHAR(32)，默认 'OVERALL'）。
-- 背景: 代码层面所有 INSERT 语句均已统一包含 dimension = 'OVERALL'（纯 Elo 模式），
--       但 V1 init_schema 中缺少该列的定义。新库从 Flyway 创建后若没有该列
--       会导致代码运行时 SQL 异常，无法正常写入 ELO 评分数据。
-- 策略: 使用存储过程检查 INFORMATION_SCHEMA 中列是否存在，
--       仅在不存在时添加（幂等性）。已存在该列的数据库（dev/prod）不受影响。
-- 影响: biz_elo_rating 表新增 dimension 列，位于 photo_id 之后，
--       使用存储过程实现幂等添加。
-- 回滚: ALTER TABLE biz_elo_rating DROP COLUMN dimension，
--       但回滚前必须修改所有 INSERT 语句去除 dimension 字段。
-- @author Apex Team
-- ============================================================

DELIMITER //

CREATE PROCEDURE IF NOT EXISTS add_dimension_if_missing()
BEGIN
    DECLARE col_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO col_count
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME   = 'biz_elo_rating'
       AND COLUMN_NAME  = 'dimension';

    IF col_count = 0 THEN
        ALTER TABLE `biz_elo_rating`
            ADD COLUMN `dimension` VARCHAR(32) NOT NULL DEFAULT 'OVERALL'
            COMMENT '评分维度（纯Elo模式固定为OVERALL）'
            AFTER `photo_id`;
    END IF;
END //

DELIMITER ;

CALL add_dimension_if_missing();

DROP PROCEDURE IF EXISTS add_dimension_if_missing;

-- ============================================================
-- V44: 回填照片历史总被投票数
-- ============================================================
-- 用途: 通过统计历史投票数据，回填 biz_photo.total_voted_count 字段。
-- 背景: 历史上投票结算只更新 biz_elo_rating.battles_count，
--       未同步更新 biz_photo.total_voted_count，
--       导致所有照片的 total_voted_count 始终为 0，
--       照片永远停留在冷启动校准期（需要达到一定投票数才能进入正常 ELO 模式），
--       影响投票分桶和权重计算的准确性。
-- 策略:
--       1. 仅统计真实父对局（parent_battle_id IS NULL），
--          四选二分析子对局不重复计数，每张候选照片在一局中只计一次（COUNT DISTINCT）。
--       2. 只统计状态为 SUBMITTED（已完成投票）的有效对局。
--       3. 覆盖所有对局类型（TWO_CHOICE / CHOICE_2 / FOUR_PICK_TWO_V2 等）。
--       4. 使用 LEFT JOIN + COALESCE 确保没有历史记录的照片 total_voted_count = 0。
-- 幂等性: 每次执行可通过 LEFT JOIN 的 GROUP BY 得到相同总数，
--        最终写入 COALESCE(historical.submitted_battle_count, 0) 仅取决于数据现状。
--        重复执行不会累加，但会重新扫描全表，大表下应通过 staging_count 预检。
-- 影响: biz_photo 表的 total_voted_count 和 update_time 字段。
--       仅更新 deleted=0 的有效照片。
-- 回滚: 无法回滚到回填前的精确值，因为 total_voted_count 在回填前均为 0。
--       如需逆操作可设为 0：UPDATE biz_photo SET total_voted_count = 0 WHERE deleted = 0。
-- @author Apex Team
-- ============================================================

-- 仅统计真实父对局；四选二分析子对局不重复计数。每张候选照片在一局中只计一次。
UPDATE `biz_photo` p
LEFT JOIN (
    SELECT bi.`photo_id`, COUNT(DISTINCT b.`id`) AS `submitted_battle_count`
    FROM `biz_battle_item` bi
    INNER JOIN `biz_battle` b
        ON b.`id` = bi.`battle_id`
       AND b.`deleted` = 0
       AND b.`parent_battle_id` IS NULL
       AND b.`status` = 'SUBMITTED'
       AND b.`battle_type` IN (
           'TWO_CHOICE',
           'ONE_VS_ONE',
           'CHOICE_2',
           'CHOICE_2_SELF_ANCHOR',
           'CHOICE_2_GLOBAL_CALIBRATION',
           'FOUR_PICK_TWO',
           'FOUR_PICK_TWO_V2'
       )
    WHERE bi.`deleted` = 0
    GROUP BY bi.`photo_id`
) historical ON historical.`photo_id` = p.`id`
SET p.`total_voted_count` = COALESCE(historical.`submitted_battle_count`, 0),
    p.`update_time` = NOW()
WHERE p.`deleted` = 0;

-- ============================================================
-- V45: 为 Future Partner 结果表设置必填字段默认值
-- ============================================================
-- 用途: 为 biz_future_partner_result 表的两个字段设置非空默认值：
--       1. deleted_by_user：明确标记用户未删除（默认 0）
--       2. created_at：记录结果生成时间（默认 CURRENT_TIMESTAMP）
-- 背景: 原表定义中 deleted_by_user 和 created_at 缺少 DEFAULT 值，
--       代码插入时依赖显式赋值，若某条 INSERT 遗漏这两个字段会导致
--       写入 NULL 或默认值错误，影响后续业务逻辑（如删除状态判断）。
-- 影响: biz_future_partner_result 表的 deleted_by_user 和 created_at 列
--       均改为 NOT NULL 并附带 DEFAULT 值。
-- 回滚: ALTER TABLE biz_future_partner_result
--       MODIFY COLUMN deleted_by_user INT(1),
--       MODIFY COLUMN created_at DATETIME。
-- @author Apex Team
-- ============================================================

-- deleted_by_user：标记用户是否删除该结果（0-否，1-是）
-- created_at：结果生成时间，默认为记录插入时的时间戳
ALTER TABLE `biz_future_partner_result`
  MODIFY COLUMN `deleted_by_user` INT(1) NOT NULL DEFAULT 0 COMMENT '用户是否删除(0-否,1-是)',
  MODIFY COLUMN `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '结果生成时间';

-- ============================================================
-- V46: 按照片所属用户修正 owner_gender（已暂停执行）
-- ============================================================
-- 用途: 原计划将 biz_photo.owner_gender 与 sys_user.gender 对齐，
--       修正批量上传照片中 owner_user_id 与 owner_gender 不匹配的问题。
-- 背景: 批量照片上传会重新分配 owner_user_id，但旧登记逻辑仍使用上传事件中的
--       sex 初始化 owner_gender，导致同一照片的 owner_user_id 与 owner_gender
--       可能来自不同用户，进而破坏二选一和四选二的性别过滤。
-- 暂停原因: 2026-07-30 评审决定暂停 owner_gender 数据对齐，
--       在照片人物性别与所有者性别的字段语义最终确认前，
--       不允许迁移自动覆盖现有 owner_gender。
-- 影响: V46 为空迁移，不修改任何数据。原计划 DML 全部保留为注释。
-- 恢复执行: 不得直接取消注释当前迁移。应新增更高版本迁移（如 V49），
--       并重新执行只读预检、备份、影响行数、锁等待和回滚评审。
-- 回滚: 无需回滚（空迁移）。
-- @author Apex Team
-- ============================================================

-- 已暂停，禁止在当前版本执行：
-- 原计划 UPDATE：
-- UPDATE `biz_photo` p
-- JOIN `sys_user` u ON u.`id` = p.`owner_user_id`
-- SET p.`owner_gender` = u.`gender`
-- WHERE NOT (p.`owner_gender` <=> u.`gender`);

-- ============================================================================
-- V47: 投票计数、对局项完整性与偏爱补偿索引优化
-- ============================================================================
-- 用途:
--   1. 为用户有效父对局计数提供专用索引，避免 user/status 与 parent_battle_id 索引合并；
--   2. 从数据库层禁止同一照片重复进入同一对局；
--   3. 覆盖按 battle_id 撤销偏爱信号的补偿查询。
-- 背景:
--   用户投票计数查询需要按 user_id + status + parent_battle_id + deleted + battle_type
--   组合过滤，原索引无法高效覆盖此多列组合；
--   同时需要从数据库引擎层保证对局项的唯一性（同一对局内一张照片只能出现一次）。
-- 前置条件:
--   必须先执行 scripts/operations/preflight-v47-vote-index-optimization.sql；
--   对局项重复组必须为 0，索引名称冲突必须为 0。
-- 影响:
--   仅新增索引，不修改业务数据。索引创建使用 ALGORITHM=INPLACE, LOCK=NONE；
--   若目标 MySQL 或表状态不支持在线 DDL，迁移应安全失败，禁止改用高锁级别强行执行。
-- 回滚:
--   经执行计划和写入负载复核后，可在独立回滚窗口 DROP 下列三个索引；
--   唯一索引回滚会失去数据库级重复照片保护，必须由业务负责人确认。
-- @author Apex Team
-- ============================================================================

-- 用户有效投票统计：
-- WHERE user_id=? AND status='SUBMITTED' AND parent_battle_id IS NULL
--   AND deleted=0 AND battle_type IN (...) [AND create_time>=?]
ALTER TABLE `biz_battle`
  ADD INDEX `idx_battle_valid_vote_count`
    (`user_id`, `status`, `parent_battle_id`, `deleted`, `battle_type`, `create_time`),
  ALGORITHM=INPLACE,
  LOCK=NONE;

-- 同一对局内一张照片只能出现一次；左前缀仍可服务 battle_id 对局项读取。
ALTER TABLE `biz_battle_item`
  ADD UNIQUE INDEX `uk_battle_item_battle_photo` (`battle_id`, `photo_id`),
  ALGORITHM=INPLACE,
  LOCK=NONE;

-- 偏爱信号按来源对局软删除的补偿路径。
ALTER TABLE `biz_prefer_signal`
  ADD INDEX `idx_prefer_signal_battle_deleted` (`battle_id`, `deleted`),
  ALGORITHM=INPLACE,
  LOCK=NONE;

-- ============================================================
-- V48: 创建投票对局客户端交付槽位表
-- ============================================================
-- 用途: 创建 biz_battle_delivery 表，支持二选一/四选二对局的预取和交付管理：
--       1. 在当前局展示期间幂等预留一个下一局（预取槽位）
--       2. 通过 slot_key 唯一约束保证单用户、单模式最多一个 CURRENT 和 RESERVED
--       3. 将交付位置状态与 biz_battle 的 CREATED/SUBMITTED 等业务状态分离
-- 背景: 客户端需要在对局展示期间提前预取下一局以提升用户体验（减少等待时间），
--       但原有架构缺少专用的交付状态管理层，预取逻辑需要独立的状态机来管理
--       交付生命周期（CURRENT → RESERVED → CONSUMED/ABANDONED/EXPIRED）。
-- 影响: 新增 biz_battle_delivery 表，包含以下关键设计：
--       - uk_battle_delivery_battle：每个 battle_id 只能对应一条交付记录
--       - uk_battle_delivery_slot：通过 slot_key 保证槽位唯一性
--       - idx_battle_delivery_user_mode_state：按用户+模式查询有效槽位
--       - idx_battle_delivery_expire：定时清理过期交付记录
--       - delivery_state 状态机：CURRENT/RESERVED/CONSUMED/ABANDONED/EXPIRED
--       - based_on_battle_id：记录触发预留的当前局 ID，用于追溯
-- 回滚: 应用回滚时先关闭预取开关并停止写入；该表可以保留，不影响旧版取局和结算。
--       如需最终删除，必须在确认无旧应用实例读写后通过后续 Flyway 迁移执行。
-- @author Apex Team
-- ============================================================

CREATE TABLE `biz_battle_delivery` (
  `id`                  BIGINT       NOT NULL COMMENT '主键ID（雪花算法）',
  `user_id`             BIGINT       NOT NULL COMMENT '对局所属用户ID',
  `mode`                VARCHAR(32)  NOT NULL COMMENT '交付模式（CHOICE_2/FOUR_PICK_TWO_V2）',
  `battle_id`           BIGINT       NOT NULL COMMENT '关联biz_battle.id',
  `based_on_battle_id`  BIGINT       DEFAULT NULL COMMENT '触发预留的当前局ID',
  `delivery_state`      VARCHAR(16)  NOT NULL COMMENT 'CURRENT/RESERVED/CONSUMED/ABANDONED/EXPIRED',
  `client_request_id`   VARCHAR(64)  DEFAULT NULL COMMENT '客户端预取幂等请求ID',
  `slot_key`            VARCHAR(128) NOT NULL COMMENT '有效槽位或历史记录唯一键',
  `expires_at`          DATETIME     NOT NULL COMMENT '交付记录失效时间',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`             INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted`             INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除（0未删，1已删）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_battle_delivery_battle` (`battle_id`),
  UNIQUE KEY `uk_battle_delivery_slot` (`slot_key`),
  KEY `idx_battle_delivery_user_mode_state` (`user_id`, `mode`, `delivery_state`, `deleted`),
  KEY `idx_battle_delivery_expire` (`delivery_state`, `expires_at`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投票对局客户端交付槽位';

-- ------------------------------------------------------------------
-- sys_domain_event_outbox
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='领域事件可靠投递表';

-- ------------------------------------------------------------------
-- sys_account_deletion_task
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号注销异步清理任务';

-- ------------------------------------------------------------------
-- biz_user_profile
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户资料表';

-- ------------------------------------------------------------------
-- biz_user_compliance
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户合规表(青少年模式)';

-- ------------------------------------------------------------------
-- biz_user_consent
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户协议同意记录表';

-- ------------------------------------------------------------------
-- biz_blacklist
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='黑名单表';

-- ------------------------------------------------------------------
-- biz_sample
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采样记录表';

-- ------------------------------------------------------------------
-- biz_subscription
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员订阅表';

-- ------------------------------------------------------------------
-- biz_credit_account
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='算力账户表';

-- ------------------------------------------------------------------
-- biz_credit_tx
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='算力交易流水表';

-- ------------------------------------------------------------------
-- biz_system_message
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统消息表';

-- ------------------------------------------------------------------
-- biz_policy_version
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='策略版本表';

-- ------------------------------------------------------------------
-- biz_quiz_question
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='答题题目表';

-- ------------------------------------------------------------------
-- biz_report
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告表';

-- ------------------------------------------------------------------
-- biz_report_ticket
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报工单表';

-- ------------------------------------------------------------------
-- biz_content_moderation_review
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容人工复核任务';

-- ------------------------------------------------------------------
-- biz_admin_review_audit
-- ------------------------------------------------------------------
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
-- biz_admin_review_reason
-- ------------------------------------------------------------------
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台审核原因配置';

-- ------------------------------------------------------------------
-- audit_log
-- ------------------------------------------------------------------
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
