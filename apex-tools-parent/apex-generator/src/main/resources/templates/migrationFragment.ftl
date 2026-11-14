-- ================================================================
-- V${migrationVersion}: 创建 ${tableComment}表
-- ================================================================
-- 用途: ${tableComment}业务表（由 apex-generator 代码生成器生成）
-- 说明: 请将本片段合并入 Flyway 迁移目录（db/migration），
--       文件名为 V${migrationVersion}__create_${tableName}.sql
-- ================================================================

CREATE TABLE IF NOT EXISTS `${tableName}` (
  `id`          BIGINT       NOT NULL COMMENT '主键ID(雪花算法)',
<#if tenantAware>  `tenant_id`   VARCHAR(64)  NOT NULL COMMENT '租户ID',</#if>
<#list fields as field>
  `${field.columnName}` ${field.sqlType}<#if field.required> NOT NULL</#if> DEFAULT NULL COMMENT '${field.comment}',
</#list>
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version`     INT(10)      NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
  `deleted`     INT(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除(0-未删,1-已删)',
  PRIMARY KEY (`id`)<#if tenantAware>,
  KEY `idx_${tableName}_tenant_id` (`tenant_id`)</#if>
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='${tableComment}';
