-- =============================================================================
-- apex_saga_it_business.sql
-- 用途：真实 Seata SAGA 双数据库集成测试的账户余额与执行阶段审计表（MySQL 侧夹具）（原首行说明：真实 Seata SAGA 双数据库集成测试的账户余额与执行阶段审计表（MySQL）。）
-- 适用：mysql（非 Flyway：测试夹具，由集成测试脚本显式执行）
-- 作者：Apex Team
-- 创建：2026-10-26
-- =============================================================================
CREATE TABLE IF NOT EXISTS apex_seata_saga_account (
    id BIGINT NOT NULL,
    balance INT NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS apex_seata_saga_action_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    phase VARCHAR(16) NOT NULL,
    xid VARCHAR(128) NOT NULL,
    branch_type VARCHAR(16) NOT NULL,
    amount INT NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;
