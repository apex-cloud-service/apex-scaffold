-- ================================================================
-- Apex Scaffold 数据源动态切换 - 从库初始化脚本
-- ================================================================
-- 功能：创建本地演示用只读从库 apex_data_slave，并分别在
--       主库/从库创建路由探针表 routing_probe（标记值不同），
--       用于验证 @DataSource("slave") 是否真正路由到从库。
--
-- 用法：mysql -uroot -p < scripts/datasource/init-slave.sql
--       （或执行 scripts/datasource/setup-slave.sh）
--
-- 说明：
--   - 本地演示：从库与主库同实例，仅用于验证路由；
--   - 生产从库：将 application-dev.yml / application-prod.yml 中
--     DB_SLAVE_URL 指向真实只读副本，无需执行本脚本的建库部分。
-- ================================================================

-- 1) 创建从库（演示用；生产环境指向真实只读副本）
CREATE DATABASE IF NOT EXISTS `apex_data_slave`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `apex_data_slave`;

-- 2) 从库路由探针表（标记值 = SLAVE）
CREATE TABLE IF NOT EXISTS `routing_probe` (
  `id`     INT         NOT NULL,
  `source` VARCHAR(20) NOT NULL COMMENT '数据源标识(MASTER/SLAVE)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源路由探针表(从库)';

INSERT INTO `routing_probe` (`id`, `source`) VALUES (1, 'SLAVE')
ON DUPLICATE KEY UPDATE `source` = 'SLAVE';

-- 3) 主库路由探针表（标记值 = MASTER）
USE `apex_data`;

CREATE TABLE IF NOT EXISTS `routing_probe` (
  `id`     INT         NOT NULL,
  `source` VARCHAR(20) NOT NULL COMMENT '数据源标识(MASTER/SLAVE)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源路由探针表(主库)';

INSERT INTO `routing_probe` (`id`, `source`) VALUES (1, 'MASTER')
ON DUPLICATE KEY UPDATE `source` = 'MASTER';

-- 4) 验证
SELECT 'slave' AS ds, `source` FROM `apex_data_slave`.`routing_probe`
UNION ALL
SELECT 'master' AS ds, `source` FROM `apex_data`.`routing_probe`;
