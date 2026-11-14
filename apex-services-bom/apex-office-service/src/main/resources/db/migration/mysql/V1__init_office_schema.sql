-- ============================================================
-- Apex Scaffold - 办公服务独立库初始化脚本（MySQL）
-- ============================================================
-- 【用途】
--   办公服务（apex-office-service）独立数据库 apex_office 的 Flyway 基线脚本，
--   与旧单体的迁移版本链解耦，新库从 V1 重新开始（迁移方案 §9.3 规则）。
--
-- 【表清单】（迁移方案 Phase 5）
--   office_platform_app          办公平台应用配置
--   office_identity_binding      外部身份绑定
--   office_organization_mapping  组织映射
--   office_sync_task             同步任务游标
--   office_message               办公消息发送记录
--   office_approval_process      审批流程
--   office_calendar_event        日历事件
--   office_document_info         文档信息
--
-- 【字段来源原则】
--   以代码实现（PO 类）为唯一事实来源（对齐 apex-file-service V1 /
--   apex-interfaces V40 迁移原则）。列名与以下类字段严格一一对应
--   （MyBatis-Plus map-underscore-to-camel-case）：
--     com.apex.scaffold.office.infrastructure.persistence.OfficeAppPO
--     com.apex.scaffold.office.infrastructure.persistence.OfficeBindingPO
--     com.apex.scaffold.office.infrastructure.persistence.OfficeOrganizationMappingPO
--     com.apex.scaffold.office.infrastructure.persistence.OfficeSyncTaskPO
--     com.apex.scaffold.office.infrastructure.persistence.po.OfficeMessagePO
--     com.apex.scaffold.office.infrastructure.po.ApprovalProcessPO
--     com.apex.scaffold.office.infrastructure.po.CalendarEventPO
--     com.apex.scaffold.office.infrastructure.po.DocumentInfoPO
--   注意：office_message 采用 PO 显式列名（target_type/target_user_ids/
--   send_status/failure_reason），与旧单体 V31 的 recipient_type/status/
--   result_message 命名不一致，一律以 PO 为准。
--
-- 【不迁移的表】
--   旧单体 off_department / off_employee / off_sync_history / off_message /
--   off_message_recipient / off_message_template 等组织与消息域表归属单体的
--   org/message 模块，不在本服务承载范围内，故不建入本库。
--
-- 【主键策略】
--   全部表使用数据库自增（应用侧 IdType.AUTO）：
--     MySQL 使用 BIGINT AUTO_INCREMENT，PostgreSQL 使用 BIGSERIAL。
--
-- 【方言约定】
--   MySQL 统一使用 DATETIME，禁止 TIMESTAMP；字符集 utf8mb4。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

-- ------------------------------------------------------------
-- 办公平台应用配置
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `office_platform_app` (
    `id`                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`                VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `platform`                 VARCHAR(32)  NOT NULL COMMENT '办公平台类型（FEISHU/DINGTALK/WECOM）',
    `app_id`                   VARCHAR(128) NOT NULL COMMENT '应用ID',
    `secret_reference`         VARCHAR(255) NOT NULL COMMENT '应用密钥引用',
    `redirect_uri`             VARCHAR(512)          DEFAULT NULL COMMENT 'OAuth回跳地址',
    `signing_secret_reference` VARCHAR(255)          DEFAULT NULL COMMENT '签名密钥引用',
    `capabilities`             TEXT                  DEFAULT NULL COMMENT '平台能力标识（逗号分隔）',
    `enabled`                  TINYINT      NOT NULL DEFAULT 0 COMMENT '启用状态（0-禁用 1-启用）',
    `version`                  INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`                  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `create_time`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_office_app_tenant_platform` (`tenant_id`, `platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公平台应用配置';

-- ------------------------------------------------------------
-- 外部身份绑定
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `office_identity_binding` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`        VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `user_id`          BIGINT       NOT NULL COMMENT '本地用户ID',
    `platform`         VARCHAR(32)  NOT NULL COMMENT '办公平台类型（FEISHU/DINGTALK/WECOM）',
    `external_user_id` VARCHAR(255) NOT NULL COMMENT '外部用户标识',
    `status`           VARCHAR(32)  NOT NULL COMMENT '绑定状态',
    `enabled`          TINYINT      NOT NULL DEFAULT 1 COMMENT '启用状态（0-禁用 1-启用）',
    `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_office_binding_external` (`tenant_id`, `platform`, `external_user_id`),
    UNIQUE KEY `uk_office_binding_user` (`tenant_id`, `platform`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公平台身份绑定';

-- ------------------------------------------------------------
-- 组织映射
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `office_organization_mapping` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`        VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `platform`         VARCHAR(32)  NOT NULL COMMENT '办公平台类型（FEISHU/DINGTALK/WECOM）',
    `external_org_id`  VARCHAR(255) NOT NULL COMMENT '外部组织ID',
    `external_user_id` VARCHAR(255)          DEFAULT NULL COMMENT '外部用户标识',
    `apex_dept_id`     VARCHAR(64)           DEFAULT NULL COMMENT '本地部门ID',
    `apex_user_id`     VARCHAR(64)           DEFAULT NULL COMMENT '本地用户ID',
    `version`          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_office_org` (`tenant_id`, `platform`, `external_org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公平台组织映射';

-- ------------------------------------------------------------
-- 同步任务游标
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `office_sync_task` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`       VARCHAR(64)  NOT NULL COMMENT '租户ID',
    `platform`        VARCHAR(32)  NOT NULL COMMENT '办公平台类型（FEISHU/DINGTALK/WECOM）',
    `cursor_value`    VARCHAR(512)          DEFAULT NULL COMMENT '增量同步游标',
    `state`           VARCHAR(32)  NOT NULL COMMENT '同步状态',
    `failure_message` VARCHAR(1000)         DEFAULT NULL COMMENT '失败原因',
    `version`         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_office_sync` (`tenant_id`, `platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公平台同步任务';

-- ------------------------------------------------------------
-- 办公消息发送记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `office_message` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `tenant_id`           BIGINT       NOT NULL COMMENT '租户ID',
    `platform`            VARCHAR(50)  NOT NULL COMMENT '办公平台类型（FEISHU/DINGTALK/WECOM）',
    `app_id`              BIGINT       NOT NULL COMMENT '应用ID',
    `message_type`        VARCHAR(50)  NOT NULL COMMENT '消息类型（TEXT/MARKDOWN/CARD/IMAGE/FILE）',
    `target_type`         VARCHAR(50)  NOT NULL COMMENT '接收者类型（USER/DEPARTMENT/GROUP/USER_LIST）',
    `target_user_ids`     TEXT         NOT NULL COMMENT '接收者ID列表（JSON数组）',
    `title`               VARCHAR(500)          DEFAULT NULL COMMENT '消息标题',
    `content`             TEXT         NOT NULL COMMENT '消息内容',
    `extra_data`          TEXT                  DEFAULT NULL COMMENT '消息附加数据（JSON）',
    `send_status`         VARCHAR(50)  NOT NULL DEFAULT 'PENDING' COMMENT '发送状态（PENDING/SENDING/SUCCESS/FAILED/PARTIAL_SUCCESS）',
    `platform_message_id` VARCHAR(200)          DEFAULT NULL COMMENT '平台返回的消息ID',
    `failure_reason`      VARCHAR(1000)         DEFAULT NULL COMMENT '发送结果/失败原因',
    `retry_count`         INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    `max_retry_count`     INT          NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    `sent_at`             DATETIME              DEFAULT NULL COMMENT '发送时间',
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by`          BIGINT                DEFAULT NULL COMMENT '创建者ID',
    `updated_by`          BIGINT                DEFAULT NULL COMMENT '更新者ID',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `version`             INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_office_message_tenant_platform` (`tenant_id`, `platform`),
    KEY `idx_office_message_tenant_status` (`tenant_id`, `send_status`),
    KEY `idx_office_message_platform_message_id` (`platform_message_id`),
    KEY `idx_office_message_sent_at` (`sent_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公平台消息发送记录';

-- ------------------------------------------------------------
-- 审批流程
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `office_approval_process` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`             BIGINT       NOT NULL COMMENT '租户ID',
    `platform`              VARCHAR(50)  NOT NULL COMMENT '办公平台类型（FEISHU/DINGTALK/WECOM）',
    `app_id`                BIGINT                DEFAULT NULL COMMENT '应用ID',
    `process_definition_id` VARCHAR(200)          DEFAULT NULL COMMENT '流程定义ID',
    `process_instance_id`   VARCHAR(200)          DEFAULT NULL COMMENT '业务流程实例ID',
    `platform_instance_id`  VARCHAR(200)          DEFAULT NULL COMMENT '平台审批实例ID',
    `process_name`          VARCHAR(500)          DEFAULT NULL COMMENT '流程名称',
    `initiator_id`          VARCHAR(100)          DEFAULT NULL COMMENT '发起人ID',
    `initiator_name`        VARCHAR(100)          DEFAULT NULL COMMENT '发起人姓名',
    `status`                VARCHAR(50)           DEFAULT NULL COMMENT '审批状态（PENDING/IN_PROGRESS/APPROVED/REJECTED/CANCELLED/TRANSFERRED）',
    `current_node`          VARCHAR(200)          DEFAULT NULL COMMENT '当前审批节点',
    `current_approver_ids`  TEXT                  DEFAULT NULL COMMENT '当前审批人ID列表（JSON数组）',
    `form_data`             TEXT                  DEFAULT NULL COMMENT '表单数据（JSON）',
    `records`               TEXT                  DEFAULT NULL COMMENT '审批记录列表（JSON数组）',
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `completed_at`          DATETIME              DEFAULT NULL COMMENT '完成时间',
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `version`               INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_office_approval_tenant` (`tenant_id`),
    KEY `idx_office_approval_platform_instance` (`platform`, `platform_instance_id`),
    KEY `idx_office_approval_process_instance` (`process_instance_id`),
    KEY `idx_office_approval_initiator` (`initiator_id`),
    KEY `idx_office_approval_status` (`status`),
    KEY `idx_office_approval_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公平台审批流程';

-- ------------------------------------------------------------
-- 日历事件
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `office_calendar_event` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`         BIGINT       NOT NULL COMMENT '租户ID',
    `platform`          VARCHAR(50)  NOT NULL COMMENT '办公平台类型（FEISHU/DINGTALK/WECOM）',
    `platform_event_id` VARCHAR(200) NOT NULL COMMENT '平台事件ID',
    `title`             VARCHAR(500) NOT NULL COMMENT '事件标题',
    `description`       TEXT                  DEFAULT NULL COMMENT '事件描述',
    `event_type`        VARCHAR(50)  NOT NULL COMMENT '事件类型（MEETING/TASK/REMINDER/VACATION/BIRTHDAY/OTHER）',
    `organizer_id`      VARCHAR(100) NOT NULL COMMENT '创建人ID',
    `organizer_name`    VARCHAR(100) NOT NULL COMMENT '创建人姓名',
    `start_time`        DATETIME     NOT NULL COMMENT '开始时间',
    `end_time`          DATETIME     NOT NULL COMMENT '结束时间',
    `is_all_day`        TINYINT(1)            DEFAULT 0 COMMENT '是否全天事件（0-否 1-是）',
    `location`          VARCHAR(500)          DEFAULT NULL COMMENT '事件地点',
    `participant_ids`   TEXT                  DEFAULT NULL COMMENT '参与人ID列表（JSON）',
    `participant_names` TEXT                  DEFAULT NULL COMMENT '参与人姓名列表（JSON）',
    `meeting_room_id`   VARCHAR(100)          DEFAULT NULL COMMENT '会议室ID',
    `reminder_minutes`  INT                   DEFAULT -1 COMMENT '提醒时间（分钟，-1表示不提醒）',
    `is_recurring`      TINYINT(1)            DEFAULT 0 COMMENT '是否重复事件（0-否 1-是）',
    `recurrence_rule`   TEXT                  DEFAULT NULL COMMENT '重复规则（RRULE）',
    `status`            VARCHAR(50)           DEFAULT 'TENTATIVE' COMMENT '事件状态（TENTATIVE/CONFIRMED/CANCELLED）',
    `version`           INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_office_calendar_tenant` (`tenant_id`),
    KEY `idx_office_calendar_platform_event` (`platform`, `platform_event_id`),
    KEY `idx_office_calendar_organizer` (`organizer_id`),
    KEY `idx_office_calendar_time_range` (`start_time`, `end_time`),
    KEY `idx_office_calendar_status` (`status`),
    KEY `idx_office_calendar_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公平台日历事件';

-- ------------------------------------------------------------
-- 文档信息
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `office_document_info` (
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id`             BIGINT        NOT NULL COMMENT '租户ID',
    `platform`              VARCHAR(50)   NOT NULL COMMENT '办公平台类型（FEISHU/DINGTALK/WECOM）',
    `platform_document_id`  VARCHAR(200)  NOT NULL COMMENT '平台文档ID',
    `document_type`         VARCHAR(50)   NOT NULL COMMENT '文档类型（DOC/SHEET/SLIDE/MINDMAP/FOLDER/FILE）',
    `title`                 VARCHAR(500)  NOT NULL COMMENT '文档标题',
    `path`                  VARCHAR(1000)          DEFAULT NULL COMMENT '文档路径',
    `url`                   VARCHAR(1000)          DEFAULT NULL COMMENT '文档URL',
    `owner_id`              VARCHAR(100)  NOT NULL COMMENT '所有者ID',
    `owner_name`            VARCHAR(100)  NOT NULL COMMENT '所有者姓名',
    `parent_folder_id`      VARCHAR(200)           DEFAULT NULL COMMENT '父文件夹ID',
    `file_size`             BIGINT                 DEFAULT NULL COMMENT '文档大小（字节）',
    `file_extension`        VARCHAR(50)            DEFAULT NULL COMMENT '扩展名',
    `mime_type`             VARCHAR(100)           DEFAULT NULL COMMENT 'MIME类型',
    `is_folder`             TINYINT(1)             DEFAULT 0 COMMENT '是否文件夹（0-否 1-是）',
    `permission`            VARCHAR(50)            DEFAULT 'PRIVATE' COMMENT '权限类型（PRIVATE/PUBLIC/SHARED）',
    `collaborator_ids`      TEXT                   DEFAULT NULL COMMENT '协作者ID列表（JSON）',
    `version`               VARCHAR(50)            DEFAULT NULL COMMENT '文档版本号',
    `last_modified_by_id`   VARCHAR(100)           DEFAULT NULL COMMENT '最后修改人ID',
    `last_modified_by_name` VARCHAR(100)           DEFAULT NULL COMMENT '最后修改人姓名',
    `last_modified_time`    DATETIME               DEFAULT NULL COMMENT '最后修改时间',
    `is_archived`           TINYINT(1)             DEFAULT 0 COMMENT '是否已归档（0-否 1-是）',
    `version_number`        INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`               TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除标识（0-未删除 1-已删除）',
    `create_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_office_document_tenant` (`tenant_id`),
    KEY `idx_office_document_platform_doc` (`platform`, `platform_document_id`),
    KEY `idx_office_document_owner` (`owner_id`),
    KEY `idx_office_document_parent` (`parent_folder_id`),
    KEY `idx_office_document_title` (`title`(100)),
    KEY `idx_office_document_archived` (`is_archived`),
    KEY `idx_office_document_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='办公平台文档信息';
