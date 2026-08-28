-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: apex_data
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `apex_data`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `apex_data` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `apex_data`;

--
-- Table structure for table `audit_log`
--

DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `operator_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人姓名',
  `operator_ip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人IP',
  `module` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作模块',
  `operation_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作类型',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作描述',
  `method` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求方法',
  `request_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求URL',
  `request_params` longtext COLLATE utf8mb4_unicode_ci COMMENT '请求参数(JSON)',
  `response_result` longtext COLLATE utf8mb4_unicode_ci COMMENT '响应结果(JSON)',
  `duration` bigint DEFAULT NULL COMMENT '耗时(毫秒)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态(SUCCESS/FAILED)',
  `error_msg` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `business_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务ID',
  `tenant_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '租户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_module` (`module`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_log`
--

LOCK TABLES `audit_log` WRITE;
/*!40000 ALTER TABLE `audit_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_admin_review_audit`
--

DROP TABLE IF EXISTS `biz_admin_review_audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_admin_review_audit` (
  `id` bigint NOT NULL,
  `aggregate_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `aggregate_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `before_state` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `after_state` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `comment` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trace_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_review_audit_aggregate` (`aggregate_type`,`aggregate_id`,`created_at`),
  KEY `idx_admin_review_audit_actor` (`actor_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台审核不可变业务审计';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_admin_review_audit`
--

LOCK TABLES `biz_admin_review_audit` WRITE;
/*!40000 ALTER TABLE `biz_admin_review_audit` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_admin_review_audit` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_admin_review_reason`
--

DROP TABLE IF EXISTS `biz_admin_review_reason`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_admin_review_reason` (
  `id` bigint NOT NULL,
  `review_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PHOTO/INTEREST',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `label` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` int NOT NULL DEFAULT '1',
  `require_comment` int NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` int NOT NULL DEFAULT '0',
  `deleted` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_review_reason` (`review_type`,`code`),
  KEY `idx_admin_review_reason_enabled` (`review_type`,`enabled`,`sort_order`),
  CONSTRAINT `ck_admin_review_reason_comment` CHECK ((`require_comment` in (0,1))),
  CONSTRAINT `ck_admin_review_reason_enabled` CHECK ((`enabled` in (0,1))),
  CONSTRAINT `ck_admin_review_reason_type` CHECK ((`review_type` in (_utf8mb4'PHOTO',_utf8mb4'INTEREST')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台审核原因配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_admin_review_reason`
--

LOCK TABLES `biz_admin_review_reason` WRITE;
/*!40000 ALTER TABLE `biz_admin_review_reason` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_admin_review_reason` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_blacklist`
--

DROP TABLE IF EXISTS `biz_blacklist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_blacklist` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '拉黑者用户ID',
  `target_user_id` bigint NOT NULL COMMENT '被拉黑用户ID',
  `enabled` int NOT NULL COMMENT '是否生效(0-取消拉黑,1-拉黑)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`,`target_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='黑名单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_blacklist`
--

LOCK TABLES `biz_blacklist` WRITE;
/*!40000 ALTER TABLE `biz_blacklist` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_blacklist` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_content_moderation_review`
--

DROP TABLE IF EXISTS `biz_content_moderation_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_content_moderation_review` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ANONYMOUS_MESSAGE/TOPIC/TOPIC_RESPONSE/OPENING_TEXT',
  `user_id` bigint NOT NULL COMMENT '内容作者用户ID',
  `content_text` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文本内容快照',
  `media_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '媒体引用快照',
  `provider` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '审核供应商',
  `provider_request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商请求ID(百度log_id)',
  `findings_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '供应商命中详情JSON',
  `content_hash` char(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容指纹(作者+来源+文本MD5)',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/REVIEWING/APPROVED/REJECTED',
  `reviewer_open_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '认领复核人openId',
  `review_comment` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '复核意见',
  `claimed_at` datetime DEFAULT NULL COMMENT '认领时间',
  `decided_at` datetime DEFAULT NULL COMMENT '裁决时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_content_moderation_hash` (`user_id`,`source_type`,`content_hash`),
  KEY `idx_content_moderation_status` (`status`,`id`),
  KEY `idx_content_moderation_reviewer` (`reviewer_open_id`,`status`),
  CONSTRAINT `ck_content_moderation_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'REVIEWING',_utf8mb4'APPROVED',_utf8mb4'REJECTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容人工复核任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_content_moderation_review`
--

LOCK TABLES `biz_content_moderation_review` WRITE;
/*!40000 ALTER TABLE `biz_content_moderation_review` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_content_moderation_review` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_credit_account`
--

DROP TABLE IF EXISTS `biz_credit_account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_credit_account` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `balance` bigint NOT NULL COMMENT '余额(最小单位)',
  `today_login_bonus` tinyint(1) DEFAULT '0' COMMENT '今日登录奖励已领取',
  `daily_favorite_total` bigint DEFAULT '0' COMMENT '当日收藏累计积分(V2: 日上限200)',
  `daily_favorite_date` date DEFAULT NULL COMMENT '收藏积分统计日期',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='算力账户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_credit_account`
--

LOCK TABLES `biz_credit_account` WRITE;
/*!40000 ALTER TABLE `biz_credit_account` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_credit_account` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_credit_tx`
--

DROP TABLE IF EXISTS `biz_credit_tx`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_credit_tx` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `direction` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方向(IN-收入/OUT-支出)',
  `amount` bigint NOT NULL COMMENT '变动金额(最小单位)',
  `reason` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变动原因',
  `ref_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联类型',
  `ref_id` bigint DEFAULT NULL COMMENT '关联ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_direction` (`direction`),
  KEY `idx_ref_type_id` (`ref_type`,`ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='算力交易流水表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_credit_tx`
--

LOCK TABLES `biz_credit_tx` WRITE;
/*!40000 ALTER TABLE `biz_credit_tx` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_credit_tx` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_policy_version`
--

DROP TABLE IF EXISTS `biz_policy_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_policy_version` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `policy_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '策略类型(USER_AGREEMENT/PRIVACY_POLICY)',
  `version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号(业务字段)',
  `effective_at` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '生效日期(字符串)',
  `content_url` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容URL',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_policy_type` (`policy_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='策略版本表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_policy_version`
--

LOCK TABLES `biz_policy_version` WRITE;
/*!40000 ALTER TABLE `biz_policy_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_policy_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_quiz_question`
--

DROP TABLE IF EXISTS `biz_quiz_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_quiz_question` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `question_id` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目标识',
  `question_text` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目内容',
  `options_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选项(JSON)',
  `dimension` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评分维度',
  `k_factor` int DEFAULT '32' COMMENT 'ELO K系数',
  `enabled` int DEFAULT NULL COMMENT '是否启用(0-禁用,1-启用)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_id` (`question_id`),
  KEY `idx_dimension` (`dimension`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='答题题目表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_quiz_question`
--

LOCK TABLES `biz_quiz_question` WRITE;
/*!40000 ALTER TABLE `biz_quiz_question` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_quiz_question` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_report`
--

DROP TABLE IF EXISTS `biz_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_report` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `report_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报告类型(FIRST_IMPRESSION/AESTHETIC_DNA/ANIMAL)',
  `owner_user_id` bigint NOT NULL COMMENT '归属用户ID',
  `photo_id` bigint DEFAULT NULL COMMENT '关联照片ID',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态(PENDING/RUNNING/DONE/FAILED)',
  `progress` int NOT NULL COMMENT '进度(0-100)',
  `data_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '报告数据(JSON)',
  `created_at` datetime NOT NULL COMMENT '报告生成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_owner_user_id` (`owner_user_id`),
  KEY `idx_report_type` (`report_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_report`
--

LOCK TABLES `biz_report` WRITE;
/*!40000 ALTER TABLE `biz_report` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_report_ticket`
--

DROP TABLE IF EXISTS `biz_report_ticket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_report_ticket` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `reporter_user_id` bigint NOT NULL COMMENT '举报者用户ID',
  `target_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '举报目标类型(USER/PHOTO/MESSAGE)',
  `target_id` bigint NOT NULL COMMENT '举报目标ID',
  `reason` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '举报原因',
  `evidence` longtext COLLATE utf8mb4_unicode_ci COMMENT '证据(JSON)',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态(PENDING/PROCESSING/RESOLVED/REJECTED)',
  `result` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理结果',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_reporter_user_id` (`reporter_user_id`),
  KEY `idx_target_type_id` (`target_type`,`target_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报工单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_report_ticket`
--

LOCK TABLES `biz_report_ticket` WRITE;
/*!40000 ALTER TABLE `biz_report_ticket` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_report_ticket` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_sample`
--

DROP TABLE IF EXISTS `biz_sample`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_sample` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '采样用户ID',
  `photo_id` bigint DEFAULT NULL COMMENT '关联照片ID',
  `bucket_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储桶名称',
  `file_name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对象名(文件名)',
  `mode` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模式(TWO_CHOICE/FOUR_PICK_TWO)',
  `face_type` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '脸型分类(CLOSE_UP/ATMOSPHERE)',
  `crop_x` int DEFAULT NULL COMMENT '裁切坐标X',
  `crop_y` int DEFAULT NULL COMMENT '裁切坐标Y',
  `crop_w` int DEFAULT NULL COMMENT '裁切宽度',
  `crop_h` int DEFAULT NULL COMMENT '裁切高度',
  `pitch` double DEFAULT NULL COMMENT '俯仰角,用于计算FRONTAL/NON_FRONTAL(P0-3修复)',
  `yaw` double DEFAULT NULL COMMENT '偏航角,用于计算FRONTAL/NON_FRONTAL(P0-3修复)',
  `roll` double DEFAULT NULL COMMENT '翻滚角,用于计算FRONTAL/NON_FRONTAL(P0-3修复)',
  `face_ratio` double DEFAULT NULL COMMENT '人脸占画面比例,用于计算PORTRAIT_CLOSE/HALF/FULL(P0-3修复)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_photo_id` (`photo_id`),
  KEY `idx_mode` (`mode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采样记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_sample`
--

LOCK TABLES `biz_sample` WRITE;
/*!40000 ALTER TABLE `biz_sample` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_sample` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_subscription`
--

DROP TABLE IF EXISTS `biz_subscription`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_subscription` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `level` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会员等级(VIP/SVIP)',
  `start_at` datetime NOT NULL COMMENT '开始时间',
  `end_at` datetime NOT NULL COMMENT '结束时间',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态(ACTIVE/EXPIRED/CANCELED)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员订阅表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_subscription`
--

LOCK TABLES `biz_subscription` WRITE;
/*!40000 ALTER TABLE `biz_subscription` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_subscription` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_system_message`
--

DROP TABLE IF EXISTS `biz_system_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_system_message` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '接收用户ID',
  `msg_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息类型',
  `content` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文案内容',
  `ref_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联类型',
  `ref_id` bigint DEFAULT NULL COMMENT '关联ID',
  `read_at` datetime DEFAULT NULL COMMENT '阅读时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_msg_type` (`msg_type`),
  KEY `idx_user_read` (`user_id`,`read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统消息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_system_message`
--

LOCK TABLES `biz_system_message` WRITE;
/*!40000 ALTER TABLE `biz_system_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_system_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_user_compliance`
--

DROP TABLE IF EXISTS `biz_user_compliance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_user_compliance` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `teen_mode` int NOT NULL COMMENT '青少年模式(0-关闭,1-开启)',
  `teen_mode_set_at` datetime DEFAULT NULL COMMENT '青少年模式设置时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户合规表(青少年模式)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_user_compliance`
--

LOCK TABLES `biz_user_compliance` WRITE;
/*!40000 ALTER TABLE `biz_user_compliance` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_user_compliance` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_user_consent`
--

DROP TABLE IF EXISTS `biz_user_consent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_user_consent` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `policy_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '策略类型(USER_AGREEMENT/PRIVACY_POLICY)',
  `version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '同意版本号(业务字段)',
  `agreed` int NOT NULL COMMENT '是否同意(0-否,1-是)',
  `agreed_at` datetime NOT NULL COMMENT '同意时间',
  `channel` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '渠道',
  `device_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备ID摘要',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户协议同意记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_user_consent`
--

LOCK TABLES `biz_user_consent` WRITE;
/*!40000 ALTER TABLE `biz_user_consent` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_user_consent` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_user_profile`
--

DROP TABLE IF EXISTS `biz_user_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_user_profile` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `dating_goal` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '交友目的',
  `mbti` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MBTI人格类型',
  `height_cm` int DEFAULT NULL COMMENT '身高(厘米)',
  `school` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学校',
  `job` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '职业',
  `bio` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '个人简介',
  `social_user` int NOT NULL COMMENT '是否社交用户(0-否,1-是)',
  `social_user_at` datetime DEFAULT NULL COMMENT '成为社交用户时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户资料表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_user_profile`
--

LOCK TABLES `biz_user_profile` WRITE;
/*!40000 ALTER TABLE `biz_user_profile` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_user_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'1','init schema','SQL','V1__init_schema.sql',1191834698,'root','2026-08-27 11:53:43',5653,1),(2,'2','seed system permissions','SQL','V2__seed_system_permissions.sql',1142213608,'root','2026-08-27 11:53:43',21,1),(3,'3','add username password to sys user','SQL','V3__add_username_password_to_sys_user.sql',2060156030,'root','2026-08-27 11:53:44',193,1),(4,'4','seed audit log permissions','SQL','V4__seed_audit_log_permissions.sql',-2068191422,'root','2026-08-27 11:53:44',13,1),(5,'5','seed online user permission','SQL','V5__seed_online_user_permission.sql',-1059429359,'root','2026-08-27 11:53:44',9,1),(6,'6','create dept and data permission','SQL','V6__create_dept_and_data_permission.sql',857247919,'root','2026-08-27 11:53:44',292,1),(7,'7','add call time to api call log','SQL','V7__add_call_time_to_api_call_log.sql',1924085351,'root','2026-08-27 12:17:40',165,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_account_deletion_task`
--

DROP TABLE IF EXISTS `sys_account_deletion_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_account_deletion_task` (
  `id` bigint NOT NULL COMMENT '清理任务主键（雪花算法）',
  `user_id` bigint NOT NULL COMMENT '已注销账号用户ID',
  `request_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '注销请求标识',
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLEANING/COMPLETED/FAILED',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '清理尝试次数',
  `last_error` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次脱敏错误摘要',
  `locked_at` datetime DEFAULT NULL COMMENT '任务领取时间',
  `locked_by` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务领取实例',
  `completed_at` datetime DEFAULT NULL COMMENT '清理完成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` int NOT NULL DEFAULT '0',
  `deleted` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_deletion_request` (`request_id`),
  KEY `idx_account_deletion_status` (`status`,`locked_at`,`id`),
  KEY `idx_account_deletion_user` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号注销异步清理任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_account_deletion_task`
--

LOCK TABLES `sys_account_deletion_task` WRITE;
/*!40000 ALTER TABLE `sys_account_deletion_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_account_deletion_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_api_call_log`
--

DROP TABLE IF EXISTS `sys_api_call_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_api_call_log` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花算法）',
  `service_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标服务名称，如 wechat / chuanglan / baidu-image / face-analysis',
  `method` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'HTTP请求方法，如 GET / POST / PUT / DELETE',
  `path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求路径（不含host），如 /api/v1/login',
  `url` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '完整请求URL（包含query参数）',
  `request_headers` text COLLATE utf8mb4_unicode_ci COMMENT '请求头（JSON格式），敏感字段如Authorization/ApiKey等会被脱敏',
  `request_body` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '请求体（超过默认限制时截断），二进制用base64编码',
  `response_status` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'HTTP响应状态码，如 200 / 404 / 500。无响应时为 null',
  `response_headers` text COLLATE utf8mb4_unicode_ci COMMENT '响应头（JSON格式）',
  `response_body` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '响应体（超过默认限制时截断）',
  `execution_time` bigint DEFAULT NULL COMMENT '执行耗时（毫秒），从发起请求到收到响应的时间',
  `status` int NOT NULL DEFAULT '0' COMMENT '调用状态：0-成功（HTTP 2xx） 1-客户端错误（4xx） 2-服务端错误（5xx） 3-网络异常',
  `error_msg` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息，异常时记录异常的堆栈前3行',
  `trace_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '链路追踪ID，关联前端请求与后端API调用',
  `request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求唯一标识UUID',
  `call_time` datetime DEFAULT NULL COMMENT '调用发起时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（保留字段）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_service_name` (`service_name`),
  KEY `idx_path` (`path`(100)),
  KEY `idx_status` (`status`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_service_status_create` (`service_name`,`status`,`create_time`),
  KEY `idx_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API调用日志表（记录对外部HTTP服务的每次调用详情）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_api_call_log`
--

LOCK TABLES `sys_api_call_log` WRITE;
/*!40000 ALTER TABLE `sys_api_call_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_api_call_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `config_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '参数键值',
  `config_type` int NOT NULL DEFAULT '1' COMMENT '参数类型(0-内置,1-用户)',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-停用,1-正常)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_config`
--

LOCK TABLES `sys_config` WRITE;
/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父部门ID(0为根部门)',
  `ancestors` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '祖级列表(逗号分隔,如 0,100)',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门名称',
  `order_num` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `leader` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-停用,1-正常)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表(树形,数据权限基础)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dept`
--

LOCK TABLES `sys_dept` WRITE;
/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT INTO `sys_dept` VALUES (1,0,'0','Apex 科技',1,NULL,NULL,NULL,1,'2026-08-27 11:53:44','2026-08-27 11:53:44',0,0);
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_data`
--

DROP TABLE IF EXISTS `sys_dict_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_data` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `dict_sort` int NOT NULL DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '所属字典类型编码',
  `is_default` int NOT NULL DEFAULT '0' COMMENT '是否默认(0-否,1-是)',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-停用,1-正常)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_dict_data_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_data`
--

LOCK TABLES `sys_dict_data` WRITE;
/*!40000 ALTER TABLE `sys_dict_data` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_dict_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_type`
--

DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `dict_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字典类型编码',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-停用,1-正常)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_code` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_type`
--

LOCK TABLES `sys_dict_type` WRITE;
/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_domain_event_outbox`
--

DROP TABLE IF EXISTS `sys_domain_event_outbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_domain_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '领域事件唯一标识',
  `event_class` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '领域事件 Java 类型',
  `payload` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件 JSON 负载',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DELIVERED/DEAD',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已失败次数',
  `next_attempt_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下次投递时间',
  `locked_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '投递实例令牌',
  `locked_at` datetime(6) DEFAULT NULL COMMENT '领取时间',
  `last_error` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次错误摘要',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `delivered_at` datetime(6) DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_dispatch` (`status`,`next_attempt_at`,`id`),
  KEY `idx_outbox_locked` (`status`,`locked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='领域事件可靠投递表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_domain_event_outbox`
--

LOCK TABLES `sys_domain_event_outbox` WRITE;
/*!40000 ALTER TABLE `sys_domain_event_outbox` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_domain_event_outbox` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_file`
--

DROP TABLE IF EXISTS `sys_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_file` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件名称',
  `original_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原始文件名',
  `file_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件路径',
  `file_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件访问URL',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
  `file_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件类型',
  `content_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容类型(MIME)',
  `bucket_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '存储桶名称',
  `md5` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件MD5',
  `user_id` bigint DEFAULT NULL COMMENT '上传用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_md5` (`md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_file`
--

LOCK TABLES `sys_file` WRITE;
/*!40000 ALTER TABLE `sys_file` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_file` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job`
--

DROP TABLE IF EXISTS `sys_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `job_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DEFAULT' COMMENT '任务分组',
  `invoke_target` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '调用目标(beanName.methodName或带参数)',
  `cron_expression` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'Cron表达式',
  `misfire_policy` int NOT NULL DEFAULT '0' COMMENT '错失执行策略(0-默认,1-立即补执行,2-放弃)',
  `concurrent` int NOT NULL DEFAULT '1' COMMENT '是否允许并发(0-禁止,1-允许)',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-停用,1-正常)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_job_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job`
--

LOCK TABLES `sys_job` WRITE;
/*!40000 ALTER TABLE `sys_job` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job_log`
--

DROP TABLE IF EXISTS `sys_job_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job_log` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `job_id` bigint NOT NULL COMMENT '任务ID',
  `job_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '任务名称(执行快照)',
  `job_group` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DEFAULT' COMMENT '任务分组(执行快照)',
  `invoke_target` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '调用目标(执行快照)',
  `job_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行消息',
  `status` int NOT NULL DEFAULT '0' COMMENT '执行状态(0-成功,1-失败,2-执行中)',
  `exception_info` text COLLATE utf8mb4_unicode_ci COMMENT '异常信息',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration` bigint DEFAULT NULL COMMENT '执行耗时(毫秒)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_job_log_job_id` (`job_id`),
  KEY `idx_job_log_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务执行日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job_log`
--

LOCK TABLES `sys_job_log` WRITE;
/*!40000 ALTER TABLE `sys_job_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_job_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_login_log`
--

DROP TABLE IF EXISTS `sys_login_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '登录账号',
  `ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端IP',
  `browser` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '浏览器信息',
  `os` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作系统信息',
  `status` int NOT NULL DEFAULT '1' COMMENT '登录状态(0-成功,1-失败)',
  `message` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登录消息',
  `login_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_login_log_username` (`username`),
  KEY `idx_login_log_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_login_log`
--

LOCK TABLES `sys_login_log` WRITE;
/*!40000 ALTER TABLE `sys_login_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_login_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父菜单ID，0表示顶级菜单',
  `menu_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '菜单名称',
  `menu_type` int NOT NULL DEFAULT '1' COMMENT '菜单类型(0-目录,1-菜单,2-按钮)',
  `path` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '路由地址',
  `component` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组件路径',
  `perms` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '菜单图标',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `visible` int NOT NULL DEFAULT '1' COMMENT '是否显示(0-隐藏,1-显示)',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-停用,1-正常)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_menu_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_menu`
--

LOCK TABLES `sys_menu` WRITE;
/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` VALUES (1000,0,'系统管理',0,'system','system/index',NULL,'Setting',1,1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(1001,1000,'用户管理',1,'system/user','system/user/index','staff:manage','User',1,1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(1002,1000,'菜单管理',1,'system/menu','system/menu/index','system:menu:list','Menu',2,1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(1003,1000,'字典管理',1,'system/dict','system/dict/index','system:dict:list','Collection',3,1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(1004,1000,'参数配置',1,'system/config','system/config/index','system:config:list','Tools',4,1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(1005,1000,'定时任务',1,'system/job','system/job/index','system:job:list','Timer',5,1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(1006,1000,'登录日志',1,'system/login-log','system/login-log/index','system:login-log:list','Document',6,1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(1007,1000,'审计日志',1,'system/audit-log','system/audit-log/index','system:audit-log:list','Audit',7,1,1,'2026-08-27 11:53:44','2026-08-27 11:53:44',0,0),(1008,1000,'部门管理',1,'system/dept','system/dept/index','system:dept:list','Cluster',8,1,1,'2026-08-27 11:53:44','2026-08-27 11:53:44',0,0);
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_operation_log`
--

DROP TABLE IF EXISTS `sys_operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `module` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作模块',
  `operation_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作类型',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作描述',
  `request_method` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求方法',
  `request_uri` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求URI',
  `request_params` text COLLATE utf8mb4_unicode_ci COMMENT '请求参数',
  `response_result` text COLLATE utf8mb4_unicode_ci COMMENT '响应结果',
  `status` int DEFAULT NULL COMMENT '操作状态(0-成功,1-失败)',
  `error_msg` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `execution_time` bigint DEFAULT NULL COMMENT '执行时间(毫秒)',
  `ip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作IP',
  `location` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作地点',
  `user_id` bigint DEFAULT NULL COMMENT '操作用户ID',
  `user_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作用户名',
  `operation_time` datetime DEFAULT NULL COMMENT '操作时间',
  `device_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备类型',
  `browser` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '浏览器类型',
  `os` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作系统',
  `trace_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '追踪ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_module` (`module`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_status` (`status`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_operation_log`
--

LOCK TABLES `sys_operation_log` WRITE;
/*!40000 ALTER TABLE `sys_operation_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_operation_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_permission`
--

DROP TABLE IF EXISTS `sys_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限编码',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限描述',
  `type` int DEFAULT NULL COMMENT '权限类型',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-禁用,1-正常)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_permission`
--

LOCK TABLES `sys_permission` WRITE;
/*!40000 ALTER TABLE `sys_permission` DISABLE KEYS */;
INSERT INTO `sys_permission` VALUES (2001,'员工管理','staff:manage','后台员工创建/编辑/启停/角色配置',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2002,'会话撤销','staff:session:revoke','强制撤销指定用户全部会话',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2003,'菜单列表','system:menu:list','查询系统菜单树',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2004,'菜单查询','system:menu:query','查询菜单详情',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2005,'菜单新增','system:menu:add','创建系统菜单',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2006,'菜单编辑','system:menu:edit','更新系统菜单',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2007,'菜单删除','system:menu:remove','删除系统菜单',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2008,'字典列表','system:dict:list','查询字典类型与数据',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2009,'字典查询','system:dict:query','查询字典详情',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2010,'字典新增','system:dict:add','创建字典类型与数据',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2011,'字典编辑','system:dict:edit','更新字典类型与数据',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2012,'字典删除','system:dict:remove','删除字典类型与数据',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2013,'参数列表','system:config:list','查询系统参数配置',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2014,'参数查询','system:config:query','查询参数配置详情',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2015,'参数新增','system:config:add','创建系统参数',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2016,'参数编辑','system:config:edit','更新系统参数',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2017,'参数删除','system:config:remove','删除系统参数',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2018,'任务列表','system:job:list','查询定时任务',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2019,'任务查询','system:job:query','查询任务详情与执行日志',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2020,'任务新增','system:job:add','创建定时任务',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2021,'任务编辑','system:job:edit','更新定时任务',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2022,'任务删除','system:job:remove','删除定时任务',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2023,'任务执行','system:job:run','手动触发定时任务',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2024,'登录日志','system:login-log:list','查询登录日志',1,1,'2026-08-27 11:53:43','2026-08-27 11:53:43',0,0),(2025,'审计日志列表','system:audit-log:list','查询操作审计日志',1,1,'2026-08-27 11:53:44','2026-08-27 11:53:44',0,0),(2026,'审计日志详情','system:audit-log:query','查询操作审计日志详情',1,1,'2026-08-27 11:53:44','2026-08-27 11:53:44',0,0),(2027,'在线用户列表','system:online-user:list','查询在线用户（会话管理）',1,1,'2026-08-27 11:53:44','2026-08-27 11:53:44',0,0),(2028,'部门列表','system:dept:list','查询部门列表',1,1,'2026-08-27 11:53:44','2026-08-27 11:53:44',0,0),(2029,'部门详情','system:dept:query','查询部门详情',1,1,'2026-08-27 11:53:44','2026-08-27 11:53:44',0,0),(2030,'部门新增','system:dept:add','新增部门',1,1,'2026-08-27 11:53:44','2026-08-27 11:53:44',0,0),(2031,'部门编辑','system:dept:edit','编辑部门',1,1,'2026-08-27 11:53:44','2026-08-27 11:53:44',0,0),(2032,'部门删除','system:dept:remove','删除部门',1,1,'2026-08-27 11:53:44','2026-08-27 11:53:44',0,0);
/*!40000 ALTER TABLE `sys_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色描述',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-禁用,1-正常)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_permission`
--

DROP TABLE IF EXISTS `sys_role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_permission`
--

LOCK TABLES `sys_role_permission` WRITE;
/*!40000 ALTER TABLE `sys_role_permission` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_role_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `open_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信OpenID(手机号注册用户可为NULL)',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登录账号（账号密码登录，微信/手机号用户可为NULL）',
  `dept_id` bigint DEFAULT NULL COMMENT '所属部门ID(数据权限,可空)',
  `password` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登录密码（BCrypt哈希，仅账号密码登录使用）',
  `union_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信UnionID',
  `access_token` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信AccessToken',
  `refresh_token` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信RefreshToken',
  `token_expire_time` datetime DEFAULT NULL COMMENT 'Token过期时间',
  `nick_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `gender` int DEFAULT NULL COMMENT '性别(0-未知,1-男,2-女)',
  `country` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '国家',
  `province` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '省份',
  `city` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '城市',
  `language` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '语言',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-禁用,1-正常)',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后登录IP',
  `last_heartbeat_time` datetime DEFAULT NULL COMMENT '最后心跳时间(V2: 在线判定)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_open_id` (`open_id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_union_id` (`union_id`),
  KEY `idx_phone` (`phone`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表(微信认证+手机号注册)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'apex_data'
--

--
-- Current Database: `apex_test`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `apex_test` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `apex_test`;

--
-- Table structure for table `audit_log`
--

DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `operator_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人ID',
  `operator_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人姓名',
  `operator_ip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人IP',
  `module` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作模块',
  `operation_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作类型',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作描述',
  `method` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求方法',
  `request_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求URL',
  `request_params` longtext COLLATE utf8mb4_unicode_ci COMMENT '请求参数(JSON)',
  `response_result` longtext COLLATE utf8mb4_unicode_ci COMMENT '响应结果(JSON)',
  `duration` bigint DEFAULT NULL COMMENT '耗时(毫秒)',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '状态(SUCCESS/FAILED)',
  `error_msg` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `business_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务ID',
  `tenant_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '租户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_operator_id` (`operator_id`),
  KEY `idx_module` (`module`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_log`
--

LOCK TABLES `audit_log` WRITE;
/*!40000 ALTER TABLE `audit_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_admin_review_audit`
--

DROP TABLE IF EXISTS `biz_admin_review_audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_admin_review_audit` (
  `id` bigint NOT NULL,
  `aggregate_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `aggregate_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `before_state` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `after_state` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `comment` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trace_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_review_audit_aggregate` (`aggregate_type`,`aggregate_id`,`created_at`),
  KEY `idx_admin_review_audit_actor` (`actor_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台审核不可变业务审计';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_admin_review_audit`
--

LOCK TABLES `biz_admin_review_audit` WRITE;
/*!40000 ALTER TABLE `biz_admin_review_audit` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_admin_review_audit` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_admin_review_reason`
--

DROP TABLE IF EXISTS `biz_admin_review_reason`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_admin_review_reason` (
  `id` bigint NOT NULL,
  `review_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PHOTO/INTEREST',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `label` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enabled` int NOT NULL DEFAULT '1',
  `require_comment` int NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` int NOT NULL DEFAULT '0',
  `deleted` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_review_reason` (`review_type`,`code`),
  KEY `idx_admin_review_reason_enabled` (`review_type`,`enabled`,`sort_order`),
  CONSTRAINT `ck_admin_review_reason_comment` CHECK ((`require_comment` in (0,1))),
  CONSTRAINT `ck_admin_review_reason_enabled` CHECK ((`enabled` in (0,1))),
  CONSTRAINT `ck_admin_review_reason_type` CHECK ((`review_type` in (_utf8mb4'PHOTO',_utf8mb4'INTEREST')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台审核原因配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_admin_review_reason`
--

LOCK TABLES `biz_admin_review_reason` WRITE;
/*!40000 ALTER TABLE `biz_admin_review_reason` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_admin_review_reason` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_blacklist`
--

DROP TABLE IF EXISTS `biz_blacklist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_blacklist` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '拉黑者用户ID',
  `target_user_id` bigint NOT NULL COMMENT '被拉黑用户ID',
  `enabled` int NOT NULL COMMENT '是否生效(0-取消拉黑,1-拉黑)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`,`target_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='黑名单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_blacklist`
--

LOCK TABLES `biz_blacklist` WRITE;
/*!40000 ALTER TABLE `biz_blacklist` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_blacklist` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_content_moderation_review`
--

DROP TABLE IF EXISTS `biz_content_moderation_review`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_content_moderation_review` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ANONYMOUS_MESSAGE/TOPIC/TOPIC_RESPONSE/OPENING_TEXT',
  `user_id` bigint NOT NULL COMMENT '内容作者用户ID',
  `content_text` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文本内容快照',
  `media_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '媒体引用快照',
  `provider` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '审核供应商',
  `provider_request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '供应商请求ID(百度log_id)',
  `findings_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '供应商命中详情JSON',
  `content_hash` char(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容指纹(作者+来源+文本MD5)',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/REVIEWING/APPROVED/REJECTED',
  `reviewer_open_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '认领复核人openId',
  `review_comment` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '复核意见',
  `claimed_at` datetime DEFAULT NULL COMMENT '认领时间',
  `decided_at` datetime DEFAULT NULL COMMENT '裁决时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_content_moderation_hash` (`user_id`,`source_type`,`content_hash`),
  KEY `idx_content_moderation_status` (`status`,`id`),
  KEY `idx_content_moderation_reviewer` (`reviewer_open_id`,`status`),
  CONSTRAINT `ck_content_moderation_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'REVIEWING',_utf8mb4'APPROVED',_utf8mb4'REJECTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容人工复核任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_content_moderation_review`
--

LOCK TABLES `biz_content_moderation_review` WRITE;
/*!40000 ALTER TABLE `biz_content_moderation_review` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_content_moderation_review` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_credit_account`
--

DROP TABLE IF EXISTS `biz_credit_account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_credit_account` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `balance` bigint NOT NULL COMMENT '余额(最小单位)',
  `today_login_bonus` tinyint(1) DEFAULT '0' COMMENT '今日登录奖励已领取',
  `daily_favorite_total` bigint DEFAULT '0' COMMENT '当日收藏累计积分(V2: 日上限200)',
  `daily_favorite_date` date DEFAULT NULL COMMENT '收藏积分统计日期',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='算力账户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_credit_account`
--

LOCK TABLES `biz_credit_account` WRITE;
/*!40000 ALTER TABLE `biz_credit_account` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_credit_account` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_credit_tx`
--

DROP TABLE IF EXISTS `biz_credit_tx`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_credit_tx` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `direction` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方向(IN-收入/OUT-支出)',
  `amount` bigint NOT NULL COMMENT '变动金额(最小单位)',
  `reason` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变动原因',
  `ref_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联类型',
  `ref_id` bigint DEFAULT NULL COMMENT '关联ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_direction` (`direction`),
  KEY `idx_ref_type_id` (`ref_type`,`ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='算力交易流水表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_credit_tx`
--

LOCK TABLES `biz_credit_tx` WRITE;
/*!40000 ALTER TABLE `biz_credit_tx` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_credit_tx` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_policy_version`
--

DROP TABLE IF EXISTS `biz_policy_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_policy_version` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `policy_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '策略类型(USER_AGREEMENT/PRIVACY_POLICY)',
  `version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号(业务字段)',
  `effective_at` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '生效日期(字符串)',
  `content_url` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容URL',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_policy_type` (`policy_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='策略版本表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_policy_version`
--

LOCK TABLES `biz_policy_version` WRITE;
/*!40000 ALTER TABLE `biz_policy_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_policy_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_quiz_question`
--

DROP TABLE IF EXISTS `biz_quiz_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_quiz_question` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `question_id` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目标识',
  `question_text` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题目内容',
  `options_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选项(JSON)',
  `dimension` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评分维度',
  `k_factor` int DEFAULT '32' COMMENT 'ELO K系数',
  `enabled` int DEFAULT NULL COMMENT '是否启用(0-禁用,1-启用)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_id` (`question_id`),
  KEY `idx_dimension` (`dimension`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='答题题目表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_quiz_question`
--

LOCK TABLES `biz_quiz_question` WRITE;
/*!40000 ALTER TABLE `biz_quiz_question` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_quiz_question` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_report`
--

DROP TABLE IF EXISTS `biz_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_report` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `report_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报告类型(FIRST_IMPRESSION/AESTHETIC_DNA/ANIMAL)',
  `owner_user_id` bigint NOT NULL COMMENT '归属用户ID',
  `photo_id` bigint DEFAULT NULL COMMENT '关联照片ID',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态(PENDING/RUNNING/DONE/FAILED)',
  `progress` int NOT NULL COMMENT '进度(0-100)',
  `data_json` longtext COLLATE utf8mb4_unicode_ci COMMENT '报告数据(JSON)',
  `created_at` datetime NOT NULL COMMENT '报告生成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_owner_user_id` (`owner_user_id`),
  KEY `idx_report_type` (`report_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_report`
--

LOCK TABLES `biz_report` WRITE;
/*!40000 ALTER TABLE `biz_report` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_report_ticket`
--

DROP TABLE IF EXISTS `biz_report_ticket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_report_ticket` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `reporter_user_id` bigint NOT NULL COMMENT '举报者用户ID',
  `target_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '举报目标类型(USER/PHOTO/MESSAGE)',
  `target_id` bigint NOT NULL COMMENT '举报目标ID',
  `reason` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '举报原因',
  `evidence` longtext COLLATE utf8mb4_unicode_ci COMMENT '证据(JSON)',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态(PENDING/PROCESSING/RESOLVED/REJECTED)',
  `result` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理结果',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_reporter_user_id` (`reporter_user_id`),
  KEY `idx_target_type_id` (`target_type`,`target_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报工单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_report_ticket`
--

LOCK TABLES `biz_report_ticket` WRITE;
/*!40000 ALTER TABLE `biz_report_ticket` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_report_ticket` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_sample`
--

DROP TABLE IF EXISTS `biz_sample`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_sample` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '采样用户ID',
  `photo_id` bigint DEFAULT NULL COMMENT '关联照片ID',
  `bucket_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储桶名称',
  `file_name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对象名(文件名)',
  `mode` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模式(TWO_CHOICE/FOUR_PICK_TWO)',
  `face_type` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '脸型分类(CLOSE_UP/ATMOSPHERE)',
  `crop_x` int DEFAULT NULL COMMENT '裁切坐标X',
  `crop_y` int DEFAULT NULL COMMENT '裁切坐标Y',
  `crop_w` int DEFAULT NULL COMMENT '裁切宽度',
  `crop_h` int DEFAULT NULL COMMENT '裁切高度',
  `pitch` double DEFAULT NULL COMMENT '俯仰角,用于计算FRONTAL/NON_FRONTAL(P0-3修复)',
  `yaw` double DEFAULT NULL COMMENT '偏航角,用于计算FRONTAL/NON_FRONTAL(P0-3修复)',
  `roll` double DEFAULT NULL COMMENT '翻滚角,用于计算FRONTAL/NON_FRONTAL(P0-3修复)',
  `face_ratio` double DEFAULT NULL COMMENT '人脸占画面比例,用于计算PORTRAIT_CLOSE/HALF/FULL(P0-3修复)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_photo_id` (`photo_id`),
  KEY `idx_mode` (`mode`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采样记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_sample`
--

LOCK TABLES `biz_sample` WRITE;
/*!40000 ALTER TABLE `biz_sample` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_sample` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_subscription`
--

DROP TABLE IF EXISTS `biz_subscription`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_subscription` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `level` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会员等级(VIP/SVIP)',
  `start_at` datetime NOT NULL COMMENT '开始时间',
  `end_at` datetime NOT NULL COMMENT '结束时间',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态(ACTIVE/EXPIRED/CANCELED)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员订阅表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_subscription`
--

LOCK TABLES `biz_subscription` WRITE;
/*!40000 ALTER TABLE `biz_subscription` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_subscription` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_system_message`
--

DROP TABLE IF EXISTS `biz_system_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_system_message` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '接收用户ID',
  `msg_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息类型',
  `content` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文案内容',
  `ref_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联类型',
  `ref_id` bigint DEFAULT NULL COMMENT '关联ID',
  `read_at` datetime DEFAULT NULL COMMENT '阅读时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_msg_type` (`msg_type`),
  KEY `idx_user_read` (`user_id`,`read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统消息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_system_message`
--

LOCK TABLES `biz_system_message` WRITE;
/*!40000 ALTER TABLE `biz_system_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_system_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_user_compliance`
--

DROP TABLE IF EXISTS `biz_user_compliance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_user_compliance` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `teen_mode` int NOT NULL COMMENT '青少年模式(0-关闭,1-开启)',
  `teen_mode_set_at` datetime DEFAULT NULL COMMENT '青少年模式设置时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户合规表(青少年模式)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_user_compliance`
--

LOCK TABLES `biz_user_compliance` WRITE;
/*!40000 ALTER TABLE `biz_user_compliance` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_user_compliance` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_user_consent`
--

DROP TABLE IF EXISTS `biz_user_consent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_user_consent` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `policy_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '策略类型(USER_AGREEMENT/PRIVACY_POLICY)',
  `version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '同意版本号(业务字段)',
  `agreed` int NOT NULL COMMENT '是否同意(0-否,1-是)',
  `agreed_at` datetime NOT NULL COMMENT '同意时间',
  `channel` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '渠道',
  `device_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备ID摘要',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户协议同意记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_user_consent`
--

LOCK TABLES `biz_user_consent` WRITE;
/*!40000 ALTER TABLE `biz_user_consent` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_user_consent` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `biz_user_profile`
--

DROP TABLE IF EXISTS `biz_user_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `biz_user_profile` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `dating_goal` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '交友目的',
  `mbti` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MBTI人格类型',
  `height_cm` int DEFAULT NULL COMMENT '身高(厘米)',
  `school` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学校',
  `job` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '职业',
  `bio` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '个人简介',
  `social_user` int NOT NULL COMMENT '是否社交用户(0-否,1-是)',
  `social_user_at` datetime DEFAULT NULL COMMENT '成为社交用户时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户资料表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `biz_user_profile`
--

LOCK TABLES `biz_user_profile` WRITE;
/*!40000 ALTER TABLE `biz_user_profile` DISABLE KEYS */;
/*!40000 ALTER TABLE `biz_user_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'1','init schema','SQL','V1__init_schema.sql',1191834698,'root','2026-08-27 12:22:45',2576,1),(2,'2','seed system permissions','SQL','V2__seed_system_permissions.sql',1142213608,'root','2026-08-27 12:22:45',29,1),(3,'3','add username password to sys user','SQL','V3__add_username_password_to_sys_user.sql',2060156030,'root','2026-08-27 12:22:45',177,1),(4,'4','seed audit log permissions','SQL','V4__seed_audit_log_permissions.sql',-2068191422,'root','2026-08-27 12:22:45',14,1),(5,'5','seed online user permission','SQL','V5__seed_online_user_permission.sql',-1059429359,'root','2026-08-27 12:22:45',11,1),(6,'6','create dept and data permission','SQL','V6__create_dept_and_data_permission.sql',857247919,'root','2026-08-27 12:22:46',228,1),(7,'7','add call time to api call log','SQL','V7__add_call_time_to_api_call_log.sql',1924085351,'root','2026-08-27 12:22:48',2456,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_account_deletion_task`
--

DROP TABLE IF EXISTS `sys_account_deletion_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_account_deletion_task` (
  `id` bigint NOT NULL COMMENT '清理任务主键（雪花算法）',
  `user_id` bigint NOT NULL COMMENT '已注销账号用户ID',
  `request_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '注销请求标识',
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLEANING/COMPLETED/FAILED',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '清理尝试次数',
  `last_error` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次脱敏错误摘要',
  `locked_at` datetime DEFAULT NULL COMMENT '任务领取时间',
  `locked_by` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务领取实例',
  `completed_at` datetime DEFAULT NULL COMMENT '清理完成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` int NOT NULL DEFAULT '0',
  `deleted` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_deletion_request` (`request_id`),
  KEY `idx_account_deletion_status` (`status`,`locked_at`,`id`),
  KEY `idx_account_deletion_user` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号注销异步清理任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_account_deletion_task`
--

LOCK TABLES `sys_account_deletion_task` WRITE;
/*!40000 ALTER TABLE `sys_account_deletion_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_account_deletion_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_api_call_log`
--

DROP TABLE IF EXISTS `sys_api_call_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_api_call_log` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花算法）',
  `service_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标服务名称，如 wechat / chuanglan / baidu-image / face-analysis',
  `method` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'HTTP请求方法，如 GET / POST / PUT / DELETE',
  `path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求路径（不含host），如 /api/v1/login',
  `url` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '完整请求URL（包含query参数）',
  `request_headers` text COLLATE utf8mb4_unicode_ci COMMENT '请求头（JSON格式），敏感字段如Authorization/ApiKey等会被脱敏',
  `request_body` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '请求体（超过默认限制时截断），二进制用base64编码',
  `response_status` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'HTTP响应状态码，如 200 / 404 / 500。无响应时为 null',
  `response_headers` text COLLATE utf8mb4_unicode_ci COMMENT '响应头（JSON格式）',
  `response_body` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '响应体（超过默认限制时截断）',
  `execution_time` bigint DEFAULT NULL COMMENT '执行耗时（毫秒），从发起请求到收到响应的时间',
  `status` int NOT NULL DEFAULT '0' COMMENT '调用状态：0-成功（HTTP 2xx） 1-客户端错误（4xx） 2-服务端错误（5xx） 3-网络异常',
  `error_msg` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息，异常时记录异常的堆栈前3行',
  `trace_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '链路追踪ID，关联前端请求与后端API调用',
  `request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求唯一标识UUID',
  `call_time` datetime DEFAULT NULL COMMENT '调用发起时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（保留字段）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除标记：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_service_name` (`service_name`),
  KEY `idx_path` (`path`(100)),
  KEY `idx_status` (`status`),
  KEY `idx_trace_id` (`trace_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_service_status_create` (`service_name`,`status`,`create_time`),
  KEY `idx_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API调用日志表（记录对外部HTTP服务的每次调用详情）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_api_call_log`
--

LOCK TABLES `sys_api_call_log` WRITE;
/*!40000 ALTER TABLE `sys_api_call_log` DISABLE KEYS */;
INSERT INTO `sys_api_call_log` VALUES (2092961201950715905,'end-to-end-test-service','GET','/api/v1/health','http://localhost:61037/api/v1/health','{}','{\"testKey\":\"end-to-end-verification\"}','200','{\"X-Trace-Id\":\"99122aa3527d4ea7a87e0bb183a354d9\",\"X-Request-Id\":\"c2f2379e27914570\",\"Vary\":\"Origin,Access-Control-Request-Method,Access-Control-Request-Headers\",\"Content-Type\":\"application/json\",\"Cache-Control\":\"no-cache, no-store, max-age=0, must-revalidate\",\"Pragma\":\"no-cache\",\"Expires\":\"0\",\"X-Content-Type-Options\":\"nosniff\",\"X-Frame-Options\":\"DENY\",\"X-XSS-Protection\":\"0\",\"Referrer-Policy\":\"no-referrer\",\"transfer-encoding\":\"chunked\"}','{\"code\":0,\"msg\":\"成功\",\"data\":{\"application\":\"apex-scaffold\",\"version\":\"1.1.3\",\"status\":\"UP\",\"timestamp\":\"2026-08-27T21:03:30.530577\"},\"traceId\":null}',28,0,NULL,'1db0aa53f5d148ac9f62346cba563c1d','08574185a567447ebdb3702a51f7a974','2026-08-27 21:03:31','2026-08-27 21:03:31','2026-08-27 21:03:31',0,0);
/*!40000 ALTER TABLE `sys_api_call_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `config_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '参数键值',
  `config_type` int NOT NULL DEFAULT '1' COMMENT '参数类型(0-内置,1-用户)',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-停用,1-正常)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统参数配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_config`
--

LOCK TABLES `sys_config` WRITE;
/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父部门ID(0为根部门)',
  `ancestors` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '祖级列表(逗号分隔,如 0,100)',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门名称',
  `order_num` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `leader` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-停用,1-正常)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表(树形,数据权限基础)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dept`
--

LOCK TABLES `sys_dept` WRITE;
/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT INTO `sys_dept` VALUES (1,0,'0','Apex 科技',1,NULL,NULL,NULL,1,'2026-08-27 12:22:46','2026-08-27 12:22:46',0,0);
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_data`
--

DROP TABLE IF EXISTS `sys_dict_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_data` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `dict_sort` int NOT NULL DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '所属字典类型编码',
  `is_default` int NOT NULL DEFAULT '0' COMMENT '是否默认(0-否,1-是)',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-停用,1-正常)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_dict_data_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_data`
--

LOCK TABLES `sys_dict_data` WRITE;
/*!40000 ALTER TABLE `sys_dict_data` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_dict_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_type`
--

DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `dict_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字典类型编码',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-停用,1-正常)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_code` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_type`
--

LOCK TABLES `sys_dict_type` WRITE;
/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_domain_event_outbox`
--

DROP TABLE IF EXISTS `sys_domain_event_outbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_domain_event_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '领域事件唯一标识',
  `event_class` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '领域事件 Java 类型',
  `payload` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件 JSON 负载',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DELIVERED/DEAD',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已失败次数',
  `next_attempt_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下次投递时间',
  `locked_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '投递实例令牌',
  `locked_at` datetime(6) DEFAULT NULL COMMENT '领取时间',
  `last_error` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次错误摘要',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  `delivered_at` datetime(6) DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outbox_event_id` (`event_id`),
  KEY `idx_outbox_dispatch` (`status`,`next_attempt_at`,`id`),
  KEY `idx_outbox_locked` (`status`,`locked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='领域事件可靠投递表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_domain_event_outbox`
--

LOCK TABLES `sys_domain_event_outbox` WRITE;
/*!40000 ALTER TABLE `sys_domain_event_outbox` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_domain_event_outbox` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_file`
--

DROP TABLE IF EXISTS `sys_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_file` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件名称',
  `original_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原始文件名',
  `file_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件路径',
  `file_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件访问URL',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
  `file_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件类型',
  `content_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容类型(MIME)',
  `bucket_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '存储桶名称',
  `md5` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件MD5',
  `user_id` bigint DEFAULT NULL COMMENT '上传用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_md5` (`md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_file`
--

LOCK TABLES `sys_file` WRITE;
/*!40000 ALTER TABLE `sys_file` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_file` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job`
--

DROP TABLE IF EXISTS `sys_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `job_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DEFAULT' COMMENT '任务分组',
  `invoke_target` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '调用目标(beanName.methodName或带参数)',
  `cron_expression` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'Cron表达式',
  `misfire_policy` int NOT NULL DEFAULT '0' COMMENT '错失执行策略(0-默认,1-立即补执行,2-放弃)',
  `concurrent` int NOT NULL DEFAULT '1' COMMENT '是否允许并发(0-禁止,1-允许)',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-停用,1-正常)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_job_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job`
--

LOCK TABLES `sys_job` WRITE;
/*!40000 ALTER TABLE `sys_job` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job_log`
--

DROP TABLE IF EXISTS `sys_job_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job_log` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `job_id` bigint NOT NULL COMMENT '任务ID',
  `job_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '任务名称(执行快照)',
  `job_group` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DEFAULT' COMMENT '任务分组(执行快照)',
  `invoke_target` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '调用目标(执行快照)',
  `job_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行消息',
  `status` int NOT NULL DEFAULT '0' COMMENT '执行状态(0-成功,1-失败,2-执行中)',
  `exception_info` text COLLATE utf8mb4_unicode_ci COMMENT '异常信息',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration` bigint DEFAULT NULL COMMENT '执行耗时(毫秒)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_job_log_job_id` (`job_id`),
  KEY `idx_job_log_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务执行日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job_log`
--

LOCK TABLES `sys_job_log` WRITE;
/*!40000 ALTER TABLE `sys_job_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_job_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_login_log`
--

DROP TABLE IF EXISTS `sys_login_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '登录账号',
  `ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端IP',
  `browser` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '浏览器信息',
  `os` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作系统信息',
  `status` int NOT NULL DEFAULT '1' COMMENT '登录状态(0-成功,1-失败)',
  `message` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登录消息',
  `login_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_login_log_username` (`username`),
  KEY `idx_login_log_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_login_log`
--

LOCK TABLES `sys_login_log` WRITE;
/*!40000 ALTER TABLE `sys_login_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_login_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父菜单ID，0表示顶级菜单',
  `menu_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '菜单名称',
  `menu_type` int NOT NULL DEFAULT '1' COMMENT '菜单类型(0-目录,1-菜单,2-按钮)',
  `path` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '路由地址',
  `component` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组件路径',
  `perms` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '菜单图标',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `visible` int NOT NULL DEFAULT '1' COMMENT '是否显示(0-隐藏,1-显示)',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-停用,1-正常)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_menu_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_menu`
--

LOCK TABLES `sys_menu` WRITE;
/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` VALUES (1000,0,'系统管理',0,'system','system/index',NULL,'Setting',1,1,1,'2026-08-27 12:22:45','2026-08-27 12:22:45',0,0),(1001,1000,'用户管理',1,'system/user','system/user/index','staff:manage','User',1,1,1,'2026-08-27 12:22:45','2026-08-27 12:22:45',0,0),(1002,1000,'菜单管理',1,'system/menu','system/menu/index','system:menu:list','Menu',2,1,1,'2026-08-27 12:22:45','2026-08-27 12:22:45',0,0),(1003,1000,'字典管理',1,'system/dict','system/dict/index','system:dict:list','Collection',3,1,1,'2026-08-27 12:22:45','2026-08-27 12:22:45',0,0),(1004,1000,'参数配置',1,'system/config','system/config/index','system:config:list','Tools',4,1,1,'2026-08-27 12:22:45','2026-08-27 12:22:45',0,0),(1005,1000,'定时任务',1,'system/job','system/job/index','system:job:list','Timer',5,1,1,'2026-08-27 12:22:45','2026-08-27 12:22:45',0,0),(1006,1000,'登录日志',1,'system/login-log','system/login-log/index','system:login-log:list','Document',6,1,1,'2026-08-27 12:22:45','2026-08-27 12:22:45',0,0),(1007,1000,'审计日志',1,'system/audit-log','system/audit-log/index','system:audit-log:list','Audit',7,1,1,'2026-08-27 12:22:45','2026-08-27 12:22:45',0,0),(1008,1000,'部门管理',1,'system/dept','system/dept/index','system:dept:list','Cluster',8,1,1,'2026-08-27 12:22:46','2026-08-27 12:22:46',0,0);
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_operation_log`
--

DROP TABLE IF EXISTS `sys_operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `module` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作模块',
  `operation_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作类型',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作描述',
  `request_method` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求方法',
  `request_uri` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求URI',
  `request_params` text COLLATE utf8mb4_unicode_ci COMMENT '请求参数',
  `response_result` text COLLATE utf8mb4_unicode_ci COMMENT '响应结果',
  `status` int DEFAULT NULL COMMENT '操作状态(0-成功,1-失败)',
  `error_msg` text COLLATE utf8mb4_unicode_ci COMMENT '错误信息',
  `execution_time` bigint DEFAULT NULL COMMENT '执行时间(毫秒)',
  `ip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作IP',
  `location` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作地点',
  `user_id` bigint DEFAULT NULL COMMENT '操作用户ID',
  `user_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作用户名',
  `operation_time` datetime DEFAULT NULL COMMENT '操作时间',
  `device_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设备类型',
  `browser` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '浏览器类型',
  `os` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作系统',
  `trace_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '追踪ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  KEY `idx_module` (`module`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_status` (`status`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_operation_log`
--

LOCK TABLES `sys_operation_log` WRITE;
/*!40000 ALTER TABLE `sys_operation_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_operation_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_permission`
--

DROP TABLE IF EXISTS `sys_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限编码',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限描述',
  `type` int DEFAULT NULL COMMENT '权限类型',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-禁用,1-正常)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_permission`
--

LOCK TABLES `sys_permission` WRITE;
/*!40000 ALTER TABLE `sys_permission` DISABLE KEYS */;
INSERT INTO `sys_permission` VALUES (5,'用户查看','user:view',NULL,NULL,1,'2026-08-27 13:03:54','2026-08-27 13:03:54',0,0);
/*!40000 ALTER TABLE `sys_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色描述',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-禁用,1-正常)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1,'普通用户','ROLE_USER',NULL,1,'2026-08-27 13:03:54','2026-08-27 13:03:54',0,0);
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_permission`
--

DROP TABLE IF EXISTS `sys_role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `permission_id` bigint NOT NULL COMMENT '权限ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_permission`
--

LOCK TABLES `sys_role_permission` WRITE;
/*!40000 ALTER TABLE `sys_role_permission` DISABLE KEYS */;
INSERT INTO `sys_role_permission` VALUES (6,1,5,'2026-08-27 13:03:54','2026-08-27 13:03:54',0,0);
/*!40000 ALTER TABLE `sys_role_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `open_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信OpenID(手机号注册用户可为NULL)',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登录账号（账号密码登录，微信/手机号用户可为NULL）',
  `dept_id` bigint DEFAULT NULL COMMENT '所属部门ID(数据权限,可空)',
  `password` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登录密码（BCrypt哈希，仅账号密码登录使用）',
  `union_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信UnionID',
  `access_token` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信AccessToken',
  `refresh_token` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '微信RefreshToken',
  `token_expire_time` datetime DEFAULT NULL COMMENT 'Token过期时间',
  `nick_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `gender` int DEFAULT NULL COMMENT '性别(0-未知,1-男,2-女)',
  `country` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '国家',
  `province` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '省份',
  `city` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '城市',
  `language` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '语言',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `status` int NOT NULL DEFAULT '1' COMMENT '状态(0-禁用,1-正常)',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后登录IP',
  `last_heartbeat_time` datetime DEFAULT NULL COMMENT '最后心跳时间(V2: 在线判定)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_open_id` (`open_id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_union_id` (`union_id`),
  KEY `idx_phone` (`phone`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表(微信认证+手机号注册)';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,'test_open_id_001',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'测试用户',NULL,NULL,NULL,NULL,NULL,NULL,NULL,1,NULL,NULL,NULL,'2026-08-27 13:03:54','2026-08-27 13:03:54',0,0);
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '版本号（乐观锁）',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (1,1,1,'2026-08-27 13:03:54','2026-08-27 13:03:54',0,0);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'apex_test'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-28  2:50:34
