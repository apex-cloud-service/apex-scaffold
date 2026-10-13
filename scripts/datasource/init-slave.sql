-- 脚本用途：初始化本地动态数据源演示库，并在主从库创建可识别的路由探针。
-- 业务背景：通过 MASTER/SLAVE 固定标记验证 @DataSource("slave") 的实际连接路由结果。
-- 前置依赖：MySQL 服务及 apex_system 主库已存在，执行账号具备建库、切库、建表和写数据权限。
-- 影响范围：创建 apex_system_slave，并在 apex_system_slave、apex_system 中创建或更新 routing_probe。
-- 幂等性：建库建表使用 IF NOT EXISTS，探针使用 ON DUPLICATE KEY UPDATE，可重复执行。
-- MySQL/Flyway 兼容性：面向 MySQL 8.x 客户端脚本；含 CREATE DATABASE/USE，不属于 Flyway 迁移。
-- 数据风险：会把两库中 routing_probe 主键 1 的 source 强制改为固定标记，且不配置真实复制关系。
-- 运维注意：仅用于本地路由验证；生产只读副本禁止运行，执行前确认主从库名称和连接实例。
-- 版本与阶段：无 Flyway 版本；执行阶段为动态数据源本地联调环境准备。
-- Checksum 提示：不受 Flyway checksum 管理，不得作为生产主从复制或正式库变更脚本使用。
-- ================================================================
-- Apex Scaffold 数据源动态切换 - 从库初始化脚本
-- ================================================================
-- 功能：创建本地演示用只读从库 apex_system_slave，并分别在
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
-- 仅创建演示库对象，不负责配置复制拓扑、账号权限或只读策略。
CREATE DATABASE IF NOT EXISTS `apex_system_slave`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `apex_system_slave`;

-- 2) 从库路由探针表（标记值 = SLAVE）
CREATE TABLE IF NOT EXISTS `routing_probe` (
  `id`     INT         NOT NULL,
  `source` VARCHAR(20) NOT NULL COMMENT '数据源标识(MASTER/SLAVE)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源路由探针表(从库)';

INSERT INTO `routing_probe` (`id`, `source`) VALUES (1, 'SLAVE')
ON DUPLICATE KEY UPDATE `source` = 'SLAVE';

-- 3) 主库路由探针表（标记值 = MASTER）
USE `apex_system`;

CREATE TABLE IF NOT EXISTS `routing_probe` (
  `id`     INT         NOT NULL,
  `source` VARCHAR(20) NOT NULL COMMENT '数据源标识(MASTER/SLAVE)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源路由探针表(主库)';

INSERT INTO `routing_probe` (`id`, `source`) VALUES (1, 'MASTER')
ON DUPLICATE KEY UPDATE `source` = 'MASTER';

-- 4) 验证
SELECT 'slave' AS ds, `source` FROM `apex_system_slave`.`routing_probe`
UNION ALL
SELECT 'master' AS ds, `source` FROM `apex_system`.`routing_probe`;
