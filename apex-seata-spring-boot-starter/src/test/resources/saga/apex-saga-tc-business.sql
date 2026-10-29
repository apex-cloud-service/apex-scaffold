-- =============================================================================
-- apex-saga-tc-business.sql
-- 用途：真实 Seata SAGA TC 集成测试的账户余额与执行阶段审计表（业务库侧夹具）（原首行说明：真实 Seata SAGA 双数据库集成测试的账户余额与执行阶段审计表。）
-- 适用：mysql / postgresql 语义按测试配置（非 Flyway：测试夹具，由集成测试脚本显式执行）
-- 作者：Apex Team
-- 创建：2026-10-26
-- =============================================================================
CREATE TABLE IF NOT EXISTS apex_saga_tc_account (
    business_key VARCHAR(48) NOT NULL,
    balance INT NOT NULL,
    PRIMARY KEY (business_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS apex_saga_tc_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    business_key VARCHAR(48) NOT NULL,
    phase VARCHAR(16) NOT NULL,
    xid VARCHAR(128) NOT NULL,
    branch_type VARCHAR(16) NOT NULL,
    amount INT NOT NULL,
    jdbc_thread VARCHAR(128) NOT NULL,
    source_thread VARCHAR(128) NOT NULL,
    call_stack TEXT NOT NULL,
    from_tc_rollback BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_saga_tc_business_phase (business_key, phase)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
